package com.gb.sched.model;

import java.util.List;

/**
 * 补位候选人：同科室、同岗位、技能标签匹配，且当天未排班或休息。
 * availability 取 休息 / 未排班。
 */
public record AbsenceCandidate(
    Long staffId,
    String staffName,
    String department,
    String position,
    List<String> skills,
    List<String> matchedSkills,
    String availability) {}
