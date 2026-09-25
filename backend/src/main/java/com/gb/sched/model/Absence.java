package com.gb.sched.model;

import java.util.List;

/**
 * 缺岗记录：班表仍保留原安排，原排班人员缺勤后由主管发起补位。
 * status 为 待补位 / 已补位，已补位记录不允许再次补位。
 */
public record Absence(
    Long id,
    String date,
    String department,
    String position,
    String shift,
    String staffName,
    List<String> requiredSkills,
    String reason,
    String status,
    Long replacementStaffId,
    String replacementStaffName,
    String initiatedBy,
    String createdAt,
    String processedAt) {}
