package com.gb.sched.controller;

import com.gb.sched.model.Absence;
import com.gb.sched.model.BackfillAudit;
import com.gb.sched.model.BackfillCandidate;
import com.gb.sched.model.BackfillRequest;
import com.gb.sched.model.ReportAbsenceRequest;
import com.gb.sched.service.AbsenceService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AbsenceController {
  private final AbsenceService absenceService;

  public AbsenceController(AbsenceService absenceService) {
    this.absenceService = absenceService;
  }

  @GetMapping("/absences")
  public List<Absence> absences(@RequestParam(name = "department") String department) {
    return absenceService.listAbsences(department);
  }

  @PostMapping("/absences")
  public Absence report(@RequestBody ReportAbsenceRequest request) {
    return absenceService.report(request);
  }

  @GetMapping("/absences/{absenceId}/candidates")
  public List<BackfillCandidate> candidates(@PathVariable(name = "absenceId") long absenceId) {
    return absenceService.listCandidates(absenceId);
  }

  @PostMapping("/absences/{absenceId}/backfill")
  public Absence backfill(@PathVariable(name = "absenceId") long absenceId, @RequestBody BackfillRequest request) {
    return absenceService.backfill(absenceId, request.candidate(), request.operator());
  }

  @GetMapping("/backfill-audits")
  public List<BackfillAudit> audits(@RequestParam(name = "department") String department) {
    return absenceService.listAudits(department);
  }
}
