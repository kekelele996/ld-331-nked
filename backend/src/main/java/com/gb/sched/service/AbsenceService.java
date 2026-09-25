package com.gb.sched.service;

import com.gb.sched.config.ApiException;
import com.gb.sched.config.AppConstants;
import com.gb.sched.model.Absence;
import com.gb.sched.model.BackfillAudit;
import com.gb.sched.model.BackfillCandidate;
import com.gb.sched.model.ReportAbsenceRequest;
import com.gb.sched.model.ScheduleItem;
import com.gb.sched.model.Staff;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AbsenceService {
  public static final String STATUS_PENDING = "待补位";
  public static final String STATUS_FILLED = "已补位";

  private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final Map<Long, Absence> absences = new ConcurrentHashMap<>();
  private final List<BackfillAudit> audits = new CopyOnWriteArrayList<>();
  private final AtomicLong absenceIds = new AtomicLong(1);
  private final AtomicLong auditIds = new AtomicLong(1);

  private final ScheduleRuleService scheduleRuleService;
  private final StaffService staffService;

  public AbsenceService(ScheduleRuleService scheduleRuleService, StaffService staffService) {
    this.scheduleRuleService = scheduleRuleService;
    this.staffService = staffService;
  }

  public List<Absence> listAbsences(String department) {
    return absences.values().stream()
        .filter(absence -> absence.department().equals(department))
        .sorted((left, right) -> Long.compare(right.id(), left.id()))
        .toList();
  }

  /** 从日期和班次发起缺岗登记，原安排必须存在于当前班表。 */
  public Absence report(ReportAbsenceRequest request) {
    requireText(request.date(), "日期不能为空");
    requireText(request.department(), "科室不能为空");
    requireText(request.shift(), "班次不能为空");
    requireText(request.staffName(), "缺岗人不能为空");
    if ("休息".equals(request.shift())) {
      throw ApiException.badRequest("休息班次无需登记缺岗");
    }
    parseDate(request.date());

    ScheduleItem original = scheduleRuleService.findItem(request.department(), request.date(), request.staffName())
        .orElseThrow(() -> ApiException.badRequest(request.date() + " 未找到 " + request.staffName() + " 的排班，无法登记缺岗"));
    if (!original.shift().equals(request.shift())) {
      throw ApiException.badRequest(request.staffName() + " 当日班次为「" + original.shift() + "」，与所选缺岗班次不一致");
    }

    boolean alreadyReported = absences.values().stream()
        .anyMatch(absence -> absence.department().equals(request.department())
            && absence.date().equals(request.date())
            && absence.shift().equals(request.shift())
            && absence.staffName().equals(request.staffName())
            && absence.status().equals(STATUS_PENDING));
    if (alreadyReported) {
      throw ApiException.conflict("该缺岗已登记，正在等待补位，请勿重复发起");
    }

    Staff staff = staffService.find(request.department(), request.staffName())
        .orElseThrow(() -> ApiException.badRequest(request.staffName() + " 不在 " + request.department() + " 人员名册中"));

    Absence absence = new Absence(
        absenceIds.getAndIncrement(),
        request.date(),
        request.department(),
        staff.position(),
        request.shift(),
        staff.name(),
        staff.skills(),
        StringUtils.hasText(request.reason()) ? request.reason().trim() : "临时缺岗",
        STATUS_PENDING,
        StringUtils.hasText(request.operator()) ? request.operator().trim() : "主管",
        null,
        DATE_TIME.format(LocalDateTime.now()),
        null);
    absences.put(absence.id(), absence);
    return absence;
  }

  /** 候选人：同科室、同岗位、技能标签匹配，且当天未排班或休息；已排入该班次的不在候选内。 */
  public List<BackfillCandidate> listCandidates(long absenceId) {
    Absence absence = pendingAbsence(absenceId);
    LocalDate date = parseDate(absence.date());
    Set<String> onShift = scheduleRuleService.staffOnShift(absence.department(), absence.date(), absence.shift())
        .stream().map(ScheduleItem::staffName).collect(Collectors.toSet());

    return staffService.byDepartment(absence.department()).stream()
        .filter(staff -> !staff.name().equals(absence.staffName()))
        .filter(staff -> staff.position().equals(absence.position()))
        .filter(staff -> !onShift.contains(staff.name()))
        .map(staff -> toCandidate(staff, absence, date))
        .filter(Objects::nonNull)
        .toList();
  }

  /** 选定候选人后执行补位：规则校验不通过则拒绝并说明，通过后同步更新班次与缺岗状态并留审计。 */
  public synchronized Absence backfill(long absenceId, String candidateName, String operator) {
    Absence absence = pendingAbsence(absenceId);
    requireText(candidateName, "补位人不能为空");
    LocalDate date = parseDate(absence.date());

    Staff substitute = staffService.find(absence.department(), candidateName)
        .orElseThrow(() -> ApiException.badRequest(candidateName + " 不在 " + absence.department() + " 人员名册中"));
    if (substitute.name().equals(absence.staffName())) {
      throw ApiException.badRequest("补位人不能是缺岗人本人");
    }
    if (!substitute.position().equals(absence.position())) {
      throw ApiException.badRequest("岗位不一致：该缺岗需「" + absence.position() + "」，" + candidateName + " 为「" + substitute.position() + "」");
    }
    if (matchedSkills(substitute.skills(), absence.skills()).isEmpty()) {
      throw ApiException.badRequest("技能标签不匹配：" + candidateName + " 的技能 " + substitute.skills() + " 与岗位要求 " + absence.skills() + " 无交集");
    }

    String todayShift = scheduleRuleService.shiftOn(absence.department(), candidateName, date);
    if (scheduleRuleService.isWorkShift(todayShift)) {
      throw ApiException.unprocessable("同日重复排班：" + candidateName + " 当天已排「" + todayShift + "」，不能再排「" + absence.shift() + "」");
    }
    if ("白班".equals(absence.shift()) && "夜班".equals(scheduleRuleService.shiftOn(absence.department(), candidateName, date.minusDays(1)))) {
      throw ApiException.unprocessable("夜班后不能接白班：" + candidateName + " 前一日为夜班，不能补位白班");
    }
    int continuousDays = scheduleRuleService.continuousDaysIfWork(absence.department(), candidateName, date);
    if (continuousDays > AppConstants.MAX_CONTINUOUS_WORK_DAYS) {
      throw ApiException.unprocessable("超过连续工作上限：补位后 " + candidateName + " 将连续工作 " + continuousDays
          + " 天（上限 " + AppConstants.MAX_CONTINUOUS_WORK_DAYS + " 天）");
    }

    // 替补人员班次与缺岗状态同步更新；缺岗人当日转为休息，原安排留存在审计中。
    scheduleRuleService.assignShift(absence.department(), absence.date(), substitute.name(), substitute.position(), absence.shift());
    scheduleRuleService.assignShift(absence.department(), absence.date(), absence.staffName(), absence.position(), "休息");

    String processedAt = DATE_TIME.format(LocalDateTime.now());
    Absence filled = absence.fill(substitute.name(), processedAt);
    absences.put(absence.id(), filled);

    String auditOperator = StringUtils.hasText(operator) ? operator.trim() : absence.reportedBy();
    audits.add(0, new BackfillAudit(
        auditIds.getAndIncrement(),
        absence.id(),
        absence.department(),
        absence.date(),
        absence.shift(),
        absence.staffName(),
        absence.shift(),
        substitute.name(),
        auditOperator,
        processedAt));
    return filled;
  }

  public List<BackfillAudit> listAudits(String department) {
    return audits.stream().filter(audit -> audit.department().equals(department)).toList();
  }

  private Absence pendingAbsence(long absenceId) {
    Absence absence = Optional.ofNullable(absences.get(absenceId))
        .orElseThrow(() -> ApiException.notFound("缺岗记录不存在：" + absenceId));
    if (!STATUS_PENDING.equals(absence.status())) {
      throw ApiException.conflict("该缺岗已完成补位，不能重复补位");
    }
    return absence;
  }

  private BackfillCandidate toCandidate(Staff staff, Absence absence, LocalDate date) {
    String shift = scheduleRuleService.shiftOn(absence.department(), staff.name(), date);
    if (scheduleRuleService.isWorkShift(shift)) {
      return null;
    }
    List<String> matched = matchedSkills(staff.skills(), absence.skills());
    if (matched.isEmpty()) {
      return null;
    }
    return new BackfillCandidate(staff.name(), staff.position(), staff.skills(), matched, shift == null ? "未排班" : shift);
  }

  private List<String> matchedSkills(List<String> staffSkills, List<String> requiredSkills) {
    return staffSkills.stream().filter(requiredSkills::contains).toList();
  }

  private LocalDate parseDate(String date) {
    try {
      return LocalDate.parse(date);
    } catch (RuntimeException exception) {
      throw ApiException.badRequest("日期格式不正确：" + date);
    }
  }

  private void requireText(String value, String message) {
    if (!StringUtils.hasText(value)) {
      throw ApiException.badRequest(message);
    }
  }
}
