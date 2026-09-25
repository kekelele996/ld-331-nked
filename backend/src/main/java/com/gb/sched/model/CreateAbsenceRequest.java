package com.gb.sched.model;

/**
 * 从日期 + 班次发起缺岗：根据班表原安排定位缺勤人员。
 */
public record CreateAbsenceRequest(
    String date,
    String department,
    String shift,
    String staffName,
    String reason,
    String initiatedBy) {}
