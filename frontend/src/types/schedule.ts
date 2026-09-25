export interface ScheduleItem {
  date: string;
  department: string;
  position: string;
  staffName: string;
  shift: string;
  holiday: boolean;
  color: string;
  source?: string;
  absent?: boolean;
}

export interface ConflictAlert {
  level: string;
  staffName: string;
  date: string;
  message: string;
}

export interface ShiftRequest {
  id: number;
  applicant: string;
  replacement: string;
  date: string;
  reason: string;
  status: string;
}

export interface WorkStats {
  staffName: string;
  dayShift: number;
  middleShift: number;
  nightShift: number;
  restDays: number;
  overtimeHours: number;
}

export interface DashboardData {
  rules: string[];
  schedule: ScheduleItem[];
  conflicts: ConflictAlert[];
  requests: ShiftRequest[];
  stats: WorkStats[];
}

export interface Absence {
  id: number;
  date: string;
  department: string;
  position: string;
  shift: string;
  staffName: string;
  requiredSkills: string[];
  reason: string;
  status: string;
  replacementStaffId: number | null;
  replacementStaffName: string | null;
  initiatedBy: string;
  createdAt: string;
  processedAt: string | null;
}

export interface AbsenceCandidate {
  staffId: number;
  staffName: string;
  department: string;
  position: string;
  skills: string[];
  matchedSkills: string[];
  availability: string;
}

export interface FillAudit {
  id: number;
  absenceId: number;
  department: string;
  operator: string;
  originalDate: string;
  originalShift: string;
  originalPosition: string;
  originalStaff: string;
  replacementStaff: string;
  processedAt: string;
  result: string;
  remark: string;
}

export interface FillResult {
  absence: Absence;
  scheduleItem: ScheduleItem;
  audit: FillAudit;
}

export interface CreateAbsencePayload {
  date: string;
  department: string;
  shift: string;
  staffName: string;
  reason: string;
  initiatedBy: string;
}
