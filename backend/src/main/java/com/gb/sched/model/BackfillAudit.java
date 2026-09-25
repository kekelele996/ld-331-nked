package com.gb.sched.model;

public record BackfillAudit(
    long id,
    long absenceId,
    String department,
    String date,
    String shift,
    String originalStaff,
    String originalShift,
    String substitute,
    String operator,
    String processedAt) {}
