package com.gb.sched.service;

import com.gb.sched.model.Staff;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class StaffService {
  private static final List<Staff> ROSTER = List.of(
      new Staff("陈医生", "急诊科", "主任医师", List.of("急救", "质控")),
      new Staff("林医生", "急诊科", "主治医师", List.of("急救", "心电")),
      new Staff("孙医生", "急诊科", "主治医师", List.of("急救", "抢救")),
      new Staff("周护士", "急诊科", "护士", List.of("分诊", "抢救")),
      new Staff("赵护士", "急诊科", "护士", List.of("分诊")),
      new Staff("王护士", "急诊科", "护士", List.of("抢救", "输液")),
      new Staff("李护士", "急诊科", "护士", List.of("输液", "陪护")),
      new Staff("郑医生", "心内科", "主治医师", List.of("介入", "心电")),
      new Staff("吴医生", "心内科", "主治医师", List.of("心电", "随访")),
      new Staff("刘护士长", "心内科", "护士长", List.of("病区管理")),
      new Staff("张护士", "心内科", "护士", List.of("病区管理", "心电")),
      new Staff("马护士", "心内科", "护士", List.of("心电", "输液")));

  public List<Staff> byDepartment(String department) {
    return ROSTER.stream().filter(staff -> staff.department().equals(department)).toList();
  }

  public Optional<Staff> find(String department, String staffName) {
    return byDepartment(department).stream().filter(staff -> staff.name().equals(staffName)).findFirst();
  }
}
