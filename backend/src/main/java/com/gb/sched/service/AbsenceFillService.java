package com.gb.sched.service;

import com.gb.sched.config.AppConstants;
import com.gb.sched.model.Absence;
import com.gb.sched.model.AbsenceCandidate;
import com.gb.sched.model.CreateAbsenceRequest;
import com.gb.sched.model.FillAudit;
import com.gb.sched.model.FillRequest;
import com.gb.sched.model.FillResult;
import com.gb.sched.model.ScheduleItem;
import com.gb.sched.model.Staff;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 缺岗补位服务：
 * 1) 从日期 + 班次发起缺岗，依据班表原安排定位缺勤人；
 * 2) 按同科室、同岗位、技能标签列出当天未排班或休息的候选人；
 * 3) 选定后依次校验同日重复排班、夜班接白班、连续工作上限，不合规则拒绝并说明；
 * 4) 通过后替补人员班次与缺岗状态同步更新，并写审计（发起人/原安排/补位人/处理时间）。
 */
@Service
public class AbsenceFillService {
  private final ScheduleStore store;

  public AbsenceFillService(ScheduleStore store) {
    this.store = store;
  }

  // ---------- 发起缺岗 ----------

  public Absence createAbsence(CreateAbsenceRequest request) {
    validateText(request.date(), "缺岗日期");
    validateText(request.department(), "科室");
    validateText(request.shift(), "班次");
    validateText(request.initiatedBy(), "发起人");
    validateText(request.staffName(), "缺勤人员");
    LocalDate date = parseDate(request.date());
    if (date.isBefore(store.scheduleStart()) || date.isAfter(store.scheduleEnd())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST,
          "日期超出当前班表范围（%s 至 %s）".formatted(store.scheduleStart(), store.scheduleEnd()));
    }

    List<ScheduleItem> originals = store.findItems(request.date(), request.department(), request.shift()).stream()
        .filter(item -> item.staffName().equals(request.staffName()))
        .toList();
    if (originals.isEmpty()) {
      throw new BusinessException(HttpStatus.NOT_FOUND,
          "%s %s 的%s没有 %s 的排班安排".formatted(request.date(), request.department(), request.staffName(), request.shift()));
    }
    ScheduleItem original = originals.get(0);
    if (original.absent()) {
      throw new BusinessException(HttpStatus.CONFLICT,
          "%s %s 的%s班次已登记缺岗，不能重复发起".formatted(request.date(), request.department(), request.shift()));
    }

    Staff staff = store.findStaffByName(request.staffName())
        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "花名册中不存在人员：" + request.staffName()));
    long id = store.nextAbsenceId();
    Absence absence = new Absence(id, request.date(), request.department(), original.position(), request.shift(),
        request.staffName(), staff.skills(), blankToDefault(request.reason()), AppConstants.ABSENCE_OPEN,
        null, null, request.initiatedBy(), ScheduleStore.nowText(), null);
    store.saveAbsence(absence);
    store.markItemAbsent(request.date(), request.department(), request.staffName(), request.shift());
    return absence;
  }

  // ---------- 候选人 ----------

  public List<AbsenceCandidate> listCandidates(Long absenceId, String skillTag) {
    Absence absence = getOpenAbsence(absenceId);
    List<AbsenceCandidate> candidates = new ArrayList<>();
    for (Staff member : store.listStaff(absence.department())) {
      if (!member.position().equals(absence.position())) {
        continue; // 同岗位
      }
      if (member.name().equals(absence.staffName())) {
        continue; // 缺勤人本人不作为候选
      }
      List<String> matched = matchedSkills(member, absence.requiredSkills());
      if (matched.isEmpty()) {
        continue; // 技能标签至少命中一个
      }
      if (skillTag != null && !skillTag.isBlank() && !member.skills().contains(skillTag)) {
        continue; // 前端技能筛选
      }
      var existing = store.findItem(absence.date(), member.name());
      if (existing.isPresent()) {
        ScheduleItem item = existing.get();
        if (!item.shift().equals(AppConstants.SHIFT_REST) || item.absent()) {
          continue; // 已排入该日工作班次的人不再出现
        }
      }
      String availability = existing.map(item -> AppConstants.SHIFT_REST).orElse("未排班");
      candidates.add(new AbsenceCandidate(member.id(), member.name(), member.department(),
          member.position(), member.skills(), matched, availability));
    }
    return candidates;
  }

  // ---------- 执行补位 ----------

  public FillResult fill(Long absenceId, FillRequest request) {
    if (request == null || request.replacementStaffId() == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "必须指定补位人员");
    }
    String operator = request.operator() == null || request.operator().isBlank() ? "未知主管" : request.operator();
    Absence absence = store.findAbsence(absenceId)
        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "缺岗记录不存在：#" + absenceId));

    // 规则 0：处理过的缺岗不能重复补位
    if (AppConstants.ABSENCE_FILLED.equals(absence.status())) {
      throw new BusinessException(HttpStatus.CONFLICT,
          "缺岗 #%d（%s %s %s，原安排 %s）已由 %s 于 %s 补位，不能重复处理".formatted(
              absence.id(), absence.date(), absence.department(), absence.shift(),
              absence.staffName(), absence.replacementStaffName(), absence.processedAt()));
    }

    Staff replacement = store.findStaff(request.replacementStaffId())
        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "补位人员不存在：#" + request.replacementStaffId()));

    // 同科室、同岗位、技能匹配复核
    if (!replacement.department().equals(absence.department()) || !replacement.position().equals(absence.position())) {
      throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
          "%s 不属于%s%s，不能补位".formatted(replacement.name(), absence.department(), absence.position()));
    }
    if (matchedSkills(replacement, absence.requiredSkills()).isEmpty()) {
      throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
          "%s 的技能标签 %s 不满足岗位要求（%s）".formatted(
              replacement.name(), replacement.skills(), absence.requiredSkills()));
    }

    // 规则 1：同日重复排班——当天已排入其他工作班次则拒绝
    ScheduleItem todays = store.findItem(absence.date(), replacement.name()).orElse(null);
    if (todays != null && !AppConstants.SHIFT_REST.equals(todays.shift())) {
      throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
          "%s 在 %s 当天已排入%s，存在同日重复排班，不能再补%s".formatted(
              replacement.name(), absence.date(), todays.shift(), absence.shift()));
    }

    LocalDate date = parseDate(absence.date());

    // 规则 2：夜班后不能直接接白班（对方班次已缺岗则不再构成实际衔接）
    //  2a) 补位班次为白班：候选人前一天不能是夜班
    if (AppConstants.SHIFT_DAY.equals(absence.shift())) {
      ScheduleItem previous = store.findItem(date.minusDays(1).toString(), replacement.name()).orElse(null);
      if (previous != null && AppConstants.SHIFT_NIGHT.equals(previous.shift()) && !previous.absent()) {
        throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
            "%s 在 %s 上了夜班，%s 不能直接接白班（夜班后禁接白班）".formatted(
                replacement.name(), date.minusDays(1), absence.date()));
      }
    }
    //  2b) 补位班次为夜班：候选人次日不能是白班
    if (AppConstants.SHIFT_NIGHT.equals(absence.shift())) {
      ScheduleItem next = store.findItem(date.plusDays(1).toString(), replacement.name()).orElse(null);
      if (next != null && AppConstants.SHIFT_DAY.equals(next.shift()) && !next.absent()) {
        throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
            "%s %s 补夜班后，%s 已排白班，违反夜班后禁接白班规则".formatted(
                replacement.name(), absence.date(), date.plusDays(1)));
      }
    }

    // 规则 3：连续工作上限（含补位当天）
    int consecutive = consecutiveWorkDays(replacement.name(), date) + 1;
    if (consecutive > AppConstants.MAX_CONSECUTIVE_WORK_DAYS) {
      throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
          "%s 此前已连续工作 %d 天，补位当天将达到 %d 天，超过连续工作上限 %d 天".formatted(
              replacement.name(), consecutive - 1, consecutive, AppConstants.MAX_CONSECUTIVE_WORK_DAYS));
    }

    // 校验通过：同步更新班表与缺岗状态
    List<ScheduleItem> originals = store.findItems(absence.date(), absence.department(), absence.shift()).stream()
        .filter(item -> item.staffName().equals(absence.staffName()))
        .toList();
    if (originals.isEmpty() || !originals.get(0).absent()) {
      throw new BusinessException(HttpStatus.CONFLICT, "原安排已变动，请刷新后重试");
    }
    String processedAt = ScheduleStore.nowText();
    store.applyFill(originals.get(0), replacement, absence);

    Absence updated = new Absence(absence.id(), absence.date(), absence.department(), absence.position(),
        absence.shift(), absence.staffName(), absence.requiredSkills(), absence.reason(),
        AppConstants.ABSENCE_FILLED, replacement.id(), replacement.name(),
        absence.initiatedBy(), absence.createdAt(), processedAt);
    store.saveAbsence(updated);

    String remark = "校验通过：无同日重复排班、无夜班接白班、补位后连续工作 %d 天".formatted(consecutive);
    FillAudit audit = new FillAudit(store.nextAuditId(), absence.id(), absence.department(), operator,
        absence.date(), absence.shift(), absence.position(), absence.staffName(),
        replacement.name(), processedAt, "补位成功", remark);
    store.saveAudit(audit);

    ScheduleItem filledItem = store.findItem(absence.date(), replacement.name())
        .orElseThrow(() -> new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "补位班次写入失败"));
    return new FillResult(updated, filledItem, audit);
  }

  // ---------- 私有方法 ----------

  private Absence getOpenAbsence(Long absenceId) {
    Absence absence = store.findAbsence(absenceId)
        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "缺岗记录不存在：#" + absenceId));
    if (AppConstants.ABSENCE_FILLED.equals(absence.status())) {
      throw new BusinessException(HttpStatus.CONFLICT, "缺岗 #" + absenceId + " 已补位，无需再找候选人");
    }
    return absence;
  }

  /** 从补位前一天向前统计连续工作天数，遇到休息、未排班或缺岗条目即中断。 */
  private int consecutiveWorkDays(String staffName, LocalDate date) {
    int count = 0;
    LocalDate cursor = date.minusDays(1);
    for (int i = 0; i < AppConstants.MAX_CONSECUTIVE_WORK_DAYS; i++) {
      ScheduleItem item = store.findItem(cursor.toString(), staffName).orElse(null);
      if (item == null || AppConstants.SHIFT_REST.equals(item.shift()) || item.absent()) {
        break;
      }
      count++;
      cursor = cursor.minusDays(1);
    }
    return count;
  }

  private List<String> matchedSkills(Staff member, List<String> required) {
    Set<String> matched = new LinkedHashSet<>(member.skills());
    matched.retainAll(required);
    return new ArrayList<>(matched);
  }

  private LocalDate parseDate(String text) {
    try {
      return LocalDate.parse(text);
    } catch (RuntimeException ex) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "日期格式应为 yyyy-MM-dd：" + text);
    }
  }

  private void validateText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, field + "不能为空");
    }
  }

  private String blankToDefault(String value) {
    return value == null || value.isBlank() ? "未填写" : value;
  }
}
