package com.gb.sched.model;

import java.util.List;

public record Staff(String name, String department, String position, List<String> skills) {}
