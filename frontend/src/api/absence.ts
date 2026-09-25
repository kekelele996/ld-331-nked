import axios from 'axios';
import { apiClient } from './client';
import type { Absence, BackfillAudit, BackfillCandidate, ScheduleItem } from '../types/schedule';

export interface ReportAbsencePayload {
  date: string;
  department: string;
  shift: string;
  staffName: string;
  reason: string;
  operator: string;
}

export async function fetchAbsences(department: string) {
  const { data } = await apiClient.get<Absence[]>('/absences', { params: { department } });
  return data;
}

export async function fetchShiftStaff(department: string, date: string, shift: string) {
  const { data } = await apiClient.get<ScheduleItem[]>('/schedule/shift', { params: { department, date, shift } });
  return data;
}

export async function createAbsence(payload: ReportAbsencePayload) {
  const { data } = await apiClient.post<Absence>('/absences', payload);
  return data;
}

export async function fetchCandidates(absenceId: number) {
  const { data } = await apiClient.get<BackfillCandidate[]>(`/absences/${absenceId}/candidates`);
  return data;
}

export async function backfillAbsence(absenceId: number, payload: { candidate: string; operator: string }) {
  const { data } = await apiClient.post<Absence>(`/absences/${absenceId}/backfill`, payload);
  return data;
}

export async function fetchBackfillAudits(department: string) {
  const { data } = await apiClient.get<BackfillAudit[]>('/backfill-audits', { params: { department } });
  return data;
}

export function errorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const message = (error.response?.data as { message?: string } | undefined)?.message;
    return message ?? error.message;
  }
  return String(error);
}
