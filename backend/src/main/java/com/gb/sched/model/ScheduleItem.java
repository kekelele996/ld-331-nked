package com.gb.sched.model;

/**
 * 排班条目。source 为 原安排 / 补位；absent 标记原安排人员已缺岗。
 */
public record ScheduleItem(
    String date,
    String department,
    String position,
    String staffName,
    String shift,
    boolean holiday,
    String color,
    String source,
    boolean absent) {}
