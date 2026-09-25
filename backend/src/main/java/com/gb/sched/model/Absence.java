package com.gb.sched.model;

import java.util.List;

public record Absence(
    long id,
    String date,
    String department,
    String position,
    String shift,
    String staffName,
    List<String> skills,
    String reason,
    String status,
    String reportedBy,
    String substitute,
    String createdAt,
    String processedAt) {

  public Absence fill(String substituteName, String processedAtTime) {
    return new Absence(id, date, department, position, shift, staffName, skills, reason,
        "已补位", reportedBy, substituteName, createdAt, processedAtTime);
  }
}
