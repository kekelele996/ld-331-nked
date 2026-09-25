package com.gb.sched.controller;

import com.gb.sched.model.Absence;
import com.gb.sched.model.AbsenceCandidate;
import com.gb.sched.model.CreateAbsenceRequest;
import com.gb.sched.model.FillAudit;
import com.gb.sched.model.FillRequest;
import com.gb.sched.model.FillResult;
import com.gb.sched.model.ScheduleItem;
import com.gb.sched.model.Staff;
import com.gb.sched.service.AbsenceFillService;
import com.gb.sched.service.ScheduleStore;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AbsenceFillController {
  private final AbsenceFillService absenceFillService;
  private final ScheduleStore store;

  public AbsenceFillController(AbsenceFillService absenceFillService, ScheduleStore store) {
    this.absenceFillService = absenceFillService;
    this.store = store;
  }

  /** 缺岗列表（含待补位与已补位）。 */
  @GetMapping("/absences")
  public List<Absence> absences(@RequestParam(name = "department", required = false) String department) {
    return store.listAbsences(department);
  }

  /** 从日期 + 班次查看班表原安排（已缺岗条目不返回，避免重复发起）。 */
  @GetMapping("/schedule/duty")
  public Map<String, Object> duty(@RequestParam("date") String date,
      @RequestParam("department") String department,
      @RequestParam("shift") String shift) {
    List<ScheduleItem> items = store.findItems(date, department, shift).stream()
        .filter(item -> !item.absent())
        .toList();
    return Map.of(
        "scheduleStart", store.scheduleStart().toString(),
        "scheduleEnd", store.scheduleEnd().toString(),
        "items", items);
  }

  /** 发起缺岗。 */
  @PostMapping("/absences")
  public Absence createAbsence(@RequestBody CreateAbsenceRequest request) {
    return absenceFillService.createAbsence(request);
  }

  /** 按同科室、同岗位、技能标签列出当天未排班或休息的候选人。 */
  @GetMapping("/absences/{id}/candidates")
  public List<AbsenceCandidate> candidates(@PathVariable("id") Long id,
      @RequestParam(name = "skillTag", required = false) String skillTag) {
    return absenceFillService.listCandidates(id, skillTag);
  }

  /** 选定候选人执行补位，服务端逐条校验规则，不合规返回 422 与原因。 */
  @PostMapping("/absences/{id}/fill")
  public FillResult fill(@PathVariable("id") Long id, @RequestBody FillRequest request) {
    return absenceFillService.fill(id, request);
  }

  /** 补位审计：发起人、原安排、补位人、处理时间。 */
  @GetMapping("/fill-audits")
  public List<FillAudit> audits(@RequestParam(name = "department", required = false) String department) {
    return store.listAudits(department);
  }

  @GetMapping("/staff")
  public List<Staff> staff(@RequestParam(name = "department", required = false) String department) {
    return store.listStaff(department);
  }
}
