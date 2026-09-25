export interface ScheduleItem {
  date: string;
  department: string;
  position: string;
  staffName: string;
  shift: string;
  holiday: boolean;
  color: string;
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

export interface Absence {
  id: number;
  date: string;
  department: string;
  position: string;
  shift: string;
  staffName: string;
  reason: string;
  status: string;
  reportedBy: string;
  substitute: string | null;
  createdAt: string;
  processedAt: string | null;
}

export interface BackfillCandidate {
  staffName: string;
  position: string;
  skills: string[];
  matchedSkills: string[];
  dayStatus: string;
}

export interface BackfillAudit {
  id: number;
  absenceId: number;
  department: string;
  date: string;
  shift: string;
  originalStaff: string;
  originalShift: string;
  substitute: string;
  operator: string;
  processedAt: string;
}

export interface DashboardData {
  rules: string[];
  schedule: ScheduleItem[];
  conflicts: ConflictAlert[];
  requests: ShiftRequest[];
  stats: WorkStats[];
}
