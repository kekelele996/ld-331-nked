package com.gb.sched.model;

/**
 * 补位成功后返回：缺岗状态、替补班次和审计记录一并同步。
 */
public record FillResult(Absence absence, ScheduleItem scheduleItem, FillAudit audit) {}
