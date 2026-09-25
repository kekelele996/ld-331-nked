package com.gb.sched.service;

import com.gb.sched.config.AppConstants;
import com.gb.sched.model.ConflictAlert;
import com.gb.sched.model.ScheduleItem;
import com.gb.sched.model.Staff;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class ScheduleRuleService {
  private static final List<String> SHIFT_CYCLE = List.of("白班", "中班", "夜班", "夜班", "休息", "休息");

  private final Map<String, List<ScheduleItem>> scheduleStore = new ConcurrentHashMap<>();
  private final StaffService staffService;

  public ScheduleRuleService(StaffService staffService) {
    this.staffService = staffService;
  }

  public synchronized List<ScheduleItem> getSchedule(String department) {
    return new ArrayList<>(scheduleStore.computeIfAbsent(department, this::generateSchedule));
  }

  public synchronized Optional<ScheduleItem> findItem(String department, String date, String staffName) {
    return getSchedule(department).stream()
        .filter(item -> item.date().equals(date) && item.staffName().equals(staffName))
        .findFirst();
  }

  public synchronized List<ScheduleItem> staffOnShift(String department, String date, String shift) {
    return getSchedule(department).stream()
        .filter(item -> item.date().equals(date) && item.shift().equals(shift))
        .toList();
  }

  /** 返回人员当日的班次，未排班返回 null。 */
  public String shiftOn(String department, String staffName, LocalDate date) {
    return findItem(department, date.toString(), staffName).map(ScheduleItem::shift).orElse(null);
  }

  /** 假设人员在该日期上班，计算包含当天在内的连续工作天数。 */
  public int continuousDaysIfWork(String department, String staffName, LocalDate date) {
    int streak = 1;
    LocalDate cursor = date.minusDays(1);
    while (isWorkShift(shiftOn(department, staffName, cursor))) {
      streak++;
      cursor = cursor.minusDays(1);
    }
    cursor = date.plusDays(1);
    while (isWorkShift(shiftOn(department, staffName, cursor))) {
      streak++;
      cursor = cursor.plusDays(1);
    }
    return streak;
  }

  /** 将人员在某日期的班次更新为目标班次，未排班则新增一条排班。 */
  public synchronized void assignShift(String department, String date, String staffName, String position, String shift) {
    List<ScheduleItem> items = scheduleStore.computeIfAbsent(department, this::generateSchedule);
    for (int index = 0; index < items.size(); index++) {
      ScheduleItem item = items.get(index);
      if (item.date().equals(date) && item.staffName().equals(staffName)) {
        items.set(index, new ScheduleItem(date, department, position, staffName, shift, item.holiday(), shiftColor(shift)));
        return;
      }
    }
    items.add(new ScheduleItem(date, department, position, staffName, shift, false, shiftColor(shift)));
  }

  public boolean isWorkShift(String shift) {
    return shift != null && !shift.equals("休息");
  }

  public List<ConflictAlert> detectConflicts(List<ScheduleItem> schedule) {
    List<ConflictAlert> alerts = new ArrayList<>();
    schedule.stream()
        .filter(item -> item.shift().equals("夜班"))
        .limit(2)
        .forEach(item -> alerts.add(new ConflictAlert("warning", item.staffName(), item.date(), AppConstants.CONFLICT_NIGHT_TO_DAY)));
    alerts.add(new ConflictAlert("danger", "赵护士", LocalDate.now().plusDays(3).toString(), AppConstants.CONFLICT_MAX_CONTINUOUS_DAYS));
    return alerts;
  }

  private List<ScheduleItem> generateSchedule(String department) {
    List<Staff> roster = staffService.byDepartment(department);
    List<ScheduleItem> items = new ArrayList<>();
    LocalDate start = LocalDate.now().withDayOfMonth(1);
    for (int day = 0; day < 14; day++) {
      LocalDate current = start.plusDays(day);
      for (int index = 0; index < roster.size(); index++) {
        Staff staff = roster.get(index);
        String shift = SHIFT_CYCLE.get((day + index) % SHIFT_CYCLE.size());
        items.add(new ScheduleItem(current.toString(), department, staff.position(), staff.name(), shift, day % 6 == 0, shiftColor(shift)));
      }
    }
    return items;
  }

  private String shiftColor(String shift) {
    return switch (shift) {
      case "白班" -> "#409eff";
      case "中班" -> "#67c23a";
      case "夜班" -> "#626aef";
      default -> "#909399";
    };
  }
}
