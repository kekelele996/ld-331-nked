import { apiClient } from './client';
import type {
  Absence,
  AbsenceCandidate,
  CreateAbsencePayload,
  FillAudit,
  FillResult,
  ScheduleItem,
} from '../types/schedule';

export async function fetchAbsences(department: string): Promise<Absence[]> {
  const { data } = await apiClient.get<Absence[]>('/absences', { params: { department } });
  return data;
}

export async function fetchDutyStaff(params: {
  date: string;
  department: string;
  shift: string;
}): Promise<{ scheduleStart: string; scheduleEnd: string; items: ScheduleItem[] }> {
  const { data } = await apiClient.get('/schedule/duty', { params });
  return data;
}

export async function createAbsence(payload: CreateAbsencePayload): Promise<Absence> {
  const { data } = await apiClient.post<Absence>('/absences', payload);
  return data;
}

export async function fetchCandidates(absenceId: number, skillTag?: string): Promise<AbsenceCandidate[]> {
  const { data } = await apiClient.get<AbsenceCandidate[]>(`/absences/${absenceId}/candidates`, {
    params: skillTag ? { skillTag } : {},
  });
  return data;
}

export async function fillAbsence(
  absenceId: number,
  payload: { replacementStaffId: number; operator: string; remark?: string },
): Promise<FillResult> {
  const { data } = await apiClient.post<FillResult>(`/absences/${absenceId}/fill`, payload);
  return data;
}

export async function fetchAudits(department: string): Promise<FillAudit[]> {
  const { data } = await apiClient.get<FillAudit[]>('/fill-audits', { params: { department } });
  return data;
}
