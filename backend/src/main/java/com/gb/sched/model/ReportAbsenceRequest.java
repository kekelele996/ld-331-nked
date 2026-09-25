package com.gb.sched.model;

public record ReportAbsenceRequest(
    String date,
    String department,
    String shift,
    String staffName,
    String reason,
    String operator) {}
