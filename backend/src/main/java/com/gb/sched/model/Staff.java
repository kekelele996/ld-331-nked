package com.gb.sched.model;

import java.util.List;

public record Staff(Long id, String name, String department, String position, List<String> skills) {}
