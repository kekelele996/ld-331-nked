package com.gb.sched.service;

import com.gb.sched.config.AppConstants;
import com.gb.sched.model.ConflictAlert;
import com.gb.sched.model.ScheduleItem;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ScheduleRuleService {
  private final ScheduleStore store;

  public ScheduleRuleService(ScheduleStore store) {
    this.store = store;
  }

  public List<ScheduleItem> generateMonthlySchedule(String department) {
    return store.listSchedule(department);
  }

  public List<ConflictAlert> detectConflicts(List<ScheduleItem> schedule) {
    List<ConflictAlert> alerts = new ArrayList<>();
    // 演示用：抽取班表中夜班条目提示夜班衔接风险
    schedule.stream()
        .filter(item -> item.shift().equals("夜班") && !item.absent())
        .limit(2)
        .forEach(item -> alerts.add(new ConflictAlert("warning", item.staffName(), item.date(),
            AppConstants.CONFLICT_NIGHT_TO_DAY)));
    alerts.add(new ConflictAlert("danger", "赵护士", store.scheduleStart().plusDays(3).toString(),
        AppConstants.CONFLICT_MAX_CONTINUOUS_DAYS));
    return alerts;
  }
}
