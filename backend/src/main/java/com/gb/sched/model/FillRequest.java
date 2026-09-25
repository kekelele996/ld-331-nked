package com.gb.sched.model;

/**
 * 补位请求体。operator 为发起补位的主管，replacementStaffId 为选定的候选人。
 */
public record FillRequest(Long replacementStaffId, String operator, String remark) {}
