package com.gb.sched.config;

import java.util.List;

public final class AppConstants {
  private AppConstants() {}

  public static final List<String> SHIFT_TYPES = List.of("白班", "中班", "夜班", "休息");
  public static final List<String> ROLES = List.of("管理员", "主管", "医护人员");
  public static final String CONFLICT_NIGHT_TO_DAY = "夜班后不能直接接白班";
  public static final String CONFLICT_MAX_CONTINUOUS_DAYS = "连续工作天数超过上限";

  public static final String SHIFT_DAY = "白班";
  public static final String SHIFT_MIDDLE = "中班";
  public static final String SHIFT_NIGHT = "夜班";
  public static final String SHIFT_REST = "休息";

  public static final String SOURCE_ORIGINAL = "原安排";
  public static final String SOURCE_FILL = "补位";

  public static final String ABSENCE_OPEN = "待补位";
  public static final String ABSENCE_FILLED = "已补位";

  /** 连续工作天数上限（含补位当天）。 */
  public static final int MAX_CONSECUTIVE_WORK_DAYS = 5;
}
