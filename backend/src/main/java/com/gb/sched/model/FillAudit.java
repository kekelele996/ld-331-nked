package com.gb.sched.model;

/**
 * 补位审计：发起人、原安排、补位人、处理时间全程留痕。
 */
public record FillAudit(
    Long id,
    Long absenceId,
    String department,
    String operator,
    String originalDate,
    String originalShift,
    String originalPosition,
    String originalStaff,
    String replacementStaff,
    String processedAt,
    String result,
    String remark) {}
