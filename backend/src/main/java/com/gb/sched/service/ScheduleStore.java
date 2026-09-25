package com.gb.sched.service;

import com.gb.sched.config.AppConstants;
import com.gb.sched.model.Absence;
import com.gb.sched.model.FillAudit;
import com.gb.sched.model.ScheduleItem;
import com.gb.sched.model.Staff;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/**
 * 排班状态内存仓库：统一维护花名册、班表、缺岗和审计，
 * 补位后班表与缺岗状态在同一处同步更新。
 */
@Service
public class ScheduleStore {
  private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final int SCHEDULE_DAYS = 14;

  private final Map<Long, Staff> staff = new ConcurrentHashMap<>();
  private final List<ScheduleItem> schedule = new ArrayList<>();
  private final Map<Long, Absence> absences = new ConcurrentHashMap<>();
  private final Map<Long, FillAudit> audits = new ConcurrentHashMap<>();

  private final AtomicLong absenceIdSeq = new AtomicLong(100);
  private final AtomicLong auditIdSeq = new AtomicLong(100);

  private final LocalDate scheduleStart = LocalDate.now().withDayOfMonth(1);

  public ScheduleStore() {
    seed();
  }

  // ---------- 人员 ----------

  public List<Staff> listStaff(String department) {
    return staff.values().stream()
        .filter(s -> department == null || department.isBlank() || s.department().equals(department))
        .sorted(Comparator.comparing(Staff::id))
        .toList();
  }

  public Optional<Staff> findStaff(Long id) {
    return Optional.ofNullable(staff.get(id));
  }

  public Optional<Staff> findStaffByName(String name) {
    return staff.values().stream().filter(s -> s.name().equals(name)).findFirst();
  }

  // ---------- 班表 ----------

  public LocalDate scheduleStart() {
    return scheduleStart;
  }

  public LocalDate scheduleEnd() {
    return scheduleStart.plusDays(SCHEDULE_DAYS - 1);
  }

  public synchronized List<ScheduleItem> listSchedule(String department) {
    return schedule.stream()
        .filter(item -> department == null || department.isBlank() || item.department().equals(department))
        .sorted(Comparator.comparing(ScheduleItem::date).thenComparing(ScheduleItem::position))
        .map(item -> item)
        .toList();
  }

  /** 查询某人在指定日期的排班；被补位移除的休息条目自然查不到。 */
  public synchronized Optional<ScheduleItem> findItem(String date, String staffName) {
    return schedule.stream()
        .filter(item -> item.date().equals(date) && item.staffName().equals(staffName))
        .findFirst();
  }

  /** 按日期 + 班次查询班表原安排（排除已缺岗的条目）。 */
  public synchronized List<ScheduleItem> findItems(String date, String department, String shift) {
    return schedule.stream()
        .filter(item -> item.date().equals(date))
        .filter(item -> item.department().equals(department))
        .filter(item -> item.shift().equals(shift))
        .toList();
  }

  /** 该日期该班次是否已有缺岗记录，避免重复发起。 */
  public synchronized Optional<Absence> findOpenAbsence(String date, String department, String shift, String staffName) {
    return absences.values().stream()
        .filter(a -> a.date().equals(date) && a.department().equals(department))
        .filter(a -> a.shift().equals(shift) && a.staffName().equals(staffName))
        .findFirst();
  }

  /** 发起缺岗时把班表原安排标记为缺岗，与缺岗状态保持同步。 */
  public synchronized void markItemAbsent(String date, String department, String staffName, String shift) {
    for (int i = 0; i < schedule.size(); i++) {
      ScheduleItem item = schedule.get(i);
      if (item.date().equals(date) && item.department().equals(department)
          && item.staffName().equals(staffName) && item.shift().equals(shift)) {
        schedule.set(i, copy(item, true));
        return;
      }
    }
    throw new IllegalStateException("班表条目不存在，无法标记缺岗");
  }

  /** 补位落账：原安排标记缺岗；替补人若当天为休息则移除休息条目，写入补位班次。 */
  public synchronized void applyFill(ScheduleItem original, Staff replacement, Absence absence) {
    for (int i = 0; i < schedule.size(); i++) {
      ScheduleItem item = schedule.get(i);
      if (item.date().equals(original.date()) && item.staffName().equals(original.staffName())) {
        schedule.set(i, copy(item, true));
      }
      if (item.date().equals(absence.date()) && item.staffName().equals(replacement.name())
          && item.shift().equals(AppConstants.SHIFT_REST)) {
        schedule.remove(i);
        i--;
      }
    }
    schedule.add(new ScheduleItem(absence.date(), absence.department(), absence.position(),
        replacement.name(), absence.shift(), false, colorOf(absence.shift()),
        AppConstants.SOURCE_FILL, false));
  }

  // ---------- 缺岗 ----------

  public synchronized Absence saveAbsence(Absence absence) {
    absences.put(absence.id(), absence);
    return absence;
  }

  public synchronized Optional<Absence> findAbsence(Long id) {
    return Optional.ofNullable(absences.get(id));
  }

  public synchronized List<Absence> listAbsences(String department) {
    return absences.values().stream()
        .filter(a -> department == null || department.isBlank() || a.department().equals(department))
        .sorted(Comparator.comparing(Absence::date).thenComparing(Absence::id))
        .toList();
  }

  public long nextAbsenceId() {
    return absenceIdSeq.incrementAndGet();
  }

  // ---------- 审计 ----------

  public synchronized FillAudit saveAudit(FillAudit audit) {
    audits.put(audit.id(), audit);
    return audit;
  }

  public synchronized List<FillAudit> listAudits(String department) {
    return audits.values().stream()
        .filter(a -> department == null || department.isBlank() || a.department().equals(department))
        .sorted(Comparator.comparing(FillAudit::processedAt).reversed().thenComparing(FillAudit::id))
        .toList();
  }

  public long nextAuditId() {
    return auditIdSeq.incrementAndGet();
  }

  public static String nowText() {
    return LocalDateTime.now().format(TS);
  }

  public static String colorOf(String shift) {
    return switch (shift) {
      case AppConstants.SHIFT_DAY -> "#409eff";
      case AppConstants.SHIFT_MIDDLE -> "#67c23a";
      case AppConstants.SHIFT_NIGHT -> "#626aef";
      default -> "#909399";
    };
  }

  private ScheduleItem copy(ScheduleItem item, boolean absent) {
    return new ScheduleItem(item.date(), item.department(), item.position(), item.staffName(),
        item.shift(), item.holiday(), item.color(), item.source(), absent);
  }

  // ---------- 种子数据 ----------

  private void seed() {
    // 花名册：同科室同岗位多人，技能标签用于候选匹配
    addStaff(1L, "陈医生", "急诊科", "医生", List.of("急救", "质控"));
    addStaff(2L, "林医生", "急诊科", "医生", List.of("急救", "心电"));
    addStaff(3L, "孙医生", "急诊科", "医生", List.of("急救", "巡诊"));
    addStaff(4L, "周护士", "急诊科", "护士", List.of("分诊", "抢救"));
    addStaff(5L, "赵护士", "急诊科", "护士", List.of("抢救", "输液"));
    addStaff(6L, "王护士", "急诊科", "护士", List.of("分诊", "输液"));
    addStaff(7L, "钱护士", "急诊科", "护士", List.of("抢救", "心电"));
    addStaff(8L, "李护士", "急诊科", "护士", List.of("分诊", "抢救"));
    addStaff(12L, "蒋护士", "急诊科", "护士", List.of("分诊", "输液"));
    addStaff(13L, "韩护士", "急诊科", "护士", List.of("抢救", "心电"));
    addStaff(9L, "吴医生", "心内科", "医生", List.of("介入", "心电"));
    addStaff(10L, "郑医生", "心内科", "医生", List.of("心电", "巡诊"));
    addStaff(11L, "冯护士长", "心内科", "护士长", List.of("病区管理", "质控"));

    generateSeedSchedule();

    // 缺岗样例 1：待补位。周护士 day2 夜班，赵/韩护士当天休息、王护士当天未排班；
    // 王护士次日已排白班，选其补位会被“夜班后禁接白班”规则拒绝，赵/韩可正常补位
    Staff zhou = staff.get(4L);
    Absence open = new Absence(1L, dateText(2), "急诊科", "护士", AppConstants.SHIFT_NIGHT,
        zhou.name(), zhou.skills(), "临时发烧无法到岗", AppConstants.ABSENCE_OPEN,
        null, null, "张主管", dateText(2) + " 08:10:00", null);
    absences.put(1L, open);
    markAbsent(open);

    // 缺岗样例 2：已补位。原安排陈医生中班，孙医生当天休息，补位后留审计
    Absence filled = new Absence(2L, dateText(6), "急诊科", "医生", AppConstants.SHIFT_MIDDLE,
        "陈医生", List.of("急救", "质控"), "外出会诊", AppConstants.ABSENCE_FILLED,
        3L, "孙医生", "张主管", dateText(6) + " 07:45:00", dateText(6) + " 08:02:31");
    absences.put(2L, filled);
    markAbsent(filled);
    schedule.removeIf(item -> item.date().equals(dateText(6)) && item.staffName().equals("孙医生")
        && item.shift().equals(AppConstants.SHIFT_REST));
    schedule.add(new ScheduleItem(dateText(6), "急诊科", "医生", "孙医生",
        AppConstants.SHIFT_MIDDLE, false, colorOf(AppConstants.SHIFT_MIDDLE),
        AppConstants.SOURCE_FILL, false));
    audits.put(1L, new FillAudit(1L, 2L, "急诊科", "张主管", dateText(6),
        AppConstants.SHIFT_MIDDLE, "医生", "陈医生", "孙医生",
        dateText(6) + " 08:02:31", "补位成功", "校验通过：无同日重复排班、无夜班接白班、连续工作 4 天"));
  }

  private void addStaff(Long id, String name, String department, String position, List<String> skills) {
    staff.put(id, new Staff(id, name, department, position, skills));
  }

  /**
   * 生成班表：按科室分组、同岗位内按工号排序，
   * 每人班次取 (日期序号 + 岗位内序号) % 5：余数 0~3 对应白/中/夜/休息，
   * 余数 4 当天不排班，因此每个岗位每天都有休息或未排班的人可用于补位。
   */
  private void generateSeedSchedule() {
    Map<String, List<Staff>> byDept = staff.values().stream()
        .collect(java.util.stream.Collectors.groupingBy(Staff::department));
    byDept.forEach((department, deptStaff) -> {
      Map<String, List<Staff>> byPosition = deptStaff.stream()
          .collect(java.util.stream.Collectors.groupingBy(Staff::position));
      byPosition.forEach((position, members) -> {
        members.sort(Comparator.comparing(Staff::id));
        for (int day = 0; day < SCHEDULE_DAYS; day++) {
          for (int index = 0; index < members.size(); index++) {
            int slot = (day + index) % 5;
            if (slot >= AppConstants.SHIFT_TYPES.size()) {
              continue; // 未排班：班表中不存在该条目
            }
            Staff member = members.get(index);
            String shift = AppConstants.SHIFT_TYPES.get(slot);
            schedule.add(new ScheduleItem(dateText(day), department, position, member.name(),
                shift, day == 5, colorOf(shift), AppConstants.SOURCE_ORIGINAL, false));
          }
        }
      });
    });
  }

  private void markAbsent(Absence absence) {
    for (int i = 0; i < schedule.size(); i++) {
      ScheduleItem item = schedule.get(i);
      if (item.date().equals(absence.date()) && item.department().equals(absence.department())
          && item.staffName().equals(absence.staffName()) && item.shift().equals(absence.shift())) {
        schedule.set(i, copy(item, true));
      }
    }
  }

  private String dateText(int dayOffset) {
    return scheduleStart.plusDays(dayOffset).toString();
  }
}
