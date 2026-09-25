package com.gb.sched.model;

import java.util.List;

public record BackfillCandidate(
    String staffName,
    String position,
    List<String> skills,
    List<String> matchedSkills,
    String dayStatus) {}
