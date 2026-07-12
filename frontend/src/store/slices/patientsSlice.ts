import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { apiFetch } from '../../services/api';
import type { RootState } from '../index';

export interface LatestMetricValue {
  value: number;
  unit?: string;
}

export interface LatestMetrics {
  heart_rate?: LatestMetricValue;
  spo2?: LatestMetricValue;
  systolic_pressure?: LatestMetricValue;
  diastolic_pressure?: LatestMetricValue;
}

export interface ActiveAlert {
  alert_id: string;
  message: string;
  severity: 'CRITICAL' | 'WARNING' | string;
  triggered_at?: string;
}

export interface PatientMonitoring {
  patient_id: string;
  patient_name: string;
  room: string;
  bed: string;
  status: string;
  latest_metrics?: LatestMetrics;
  active_alerts?: ActiveAlert[];
}

export interface WsPatientUpdate {
  patient_id: string;
  heart_rate?: number;
  spo2?: number;
  systolic_pressure?: number;
  diastolic_pressure?: number;
  temperature?: number;
}

export interface CriticalAlert {
  id: string;
  message: string;
  severity: string;
  triggeredAt: string;
  patientId: string;
}

export interface PatientsQuery {
  page: number;
  size: number;
  search: string;
  sort: string;
}

export interface PaginationState {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface HiddenAlertsSummary {
  critical_count: number;
  warning_count: number;
}

export interface PagedPatientMonitoringResponse {
  content: PatientMonitoring[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
  hidden_alerts_summary: HiddenAlertsSummary;
}

interface PatientsState {
  patients: PatientMonitoring[];
  pagination: PaginationState;
  hiddenAlertsSummary: HiddenAlertsSummary;
  query: PatientsQuery;
  headerAlerts: CriticalAlert[];
  loading: boolean;
  ackLoading: boolean;
  error: string | null;
}

const DEFAULT_QUERY: PatientsQuery = {
  page: 0,
  size: 24,
  search: '',
  sort: 'severity,desc',
};

export const PATIENT_SORT_OPTIONS = [
  { value: 'severity,desc', label: 'Severidad (críticos primero)' },
  { value: 'severity,asc', label: 'Severidad (estables primero)' },
  { value: 'name,asc', label: 'Nombre (A-Z)' },
  { value: 'name,desc', label: 'Nombre (Z-A)' },
  { value: 'room,asc', label: 'Habitación (A-Z)' },
  { value: 'room,desc', label: 'Habitación (Z-A)' },
  { value: 'bed,asc', label: 'Cama (A-Z)' },
  { value: 'bed,desc', label: 'Cama (Z-A)' },
] as const;

const initialState: PatientsState = {
  patients: [],
  pagination: {
    page: 0,
    size: DEFAULT_QUERY.size,
    totalElements: 0,
    totalPages: 0,
  },
  hiddenAlertsSummary: {
    critical_count: 0,
    warning_count: 0,
  },
  query: DEFAULT_QUERY,
  headerAlerts: [],
  loading: false,
  ackLoading: false,
  error: null,
};

const buildMonitoringQueryString = (query: PatientsQuery): string => {
  const params = new URLSearchParams({
    page: String(query.page),
    size: String(query.size),
    sort: query.sort,
  });
  if (query.search.trim()) {
    params.set('search', query.search.trim());
  }
  return params.toString();
};

export const fetchPatientsPage = createAsyncThunk(
  'patients/fetchPage',
  async (override: Partial<PatientsQuery> | undefined, { getState, rejectWithValue }) => {
    try {
      const state = getState() as RootState;
      const query = { ...state.patients.query, ...override };
      const data = await apiFetch<PagedPatientMonitoringResponse>(
        `/patients/monitoring?${buildMonitoringQueryString(query)}`,
      );
      if (!data) {
        return rejectWithValue('No se recibieron datos de monitoreo');
      }
      return { data, query };
    } catch (error) {
      return rejectWithValue((error as Error).message);
    }
  },
);

export const fetchCriticalAlerts = createAsyncThunk(
  'patients/fetchCriticalAlerts',
  async (_, { rejectWithValue }) => {
    try {
      const data = await apiFetch<CriticalAlert[]>('/alerts/unacknowledged/critical');
      return data ?? [];
    } catch (error) {
      return rejectWithValue((error as Error).message);
    }
  },
);

export const acknowledgeAlert = createAsyncThunk(
  'patients/acknowledgeAlert',
  async (
    { alertId, userName = 'Enfermería Central' }: { alertId: string; userName?: string },
    { rejectWithValue },
  ) => {
    try {
      await apiFetch(
        `/alerts/${alertId}/acknowledge?acknowledgedBy=${encodeURIComponent(userName)}`,
        { method: 'PATCH' },
      );
      return alertId;
    } catch (error) {
      return rejectWithValue((error as Error).message);
    }
  },
);

const patientsSlice = createSlice({
  name: 'patients',
  initialState,
  reducers: {
    setPatientSearch(state, action: PayloadAction<string>) {
      state.query.search = action.payload;
      state.query.page = 0;
    },
    setPatientPage(state, action: PayloadAction<number>) {
      state.query.page = action.payload;
    },
    setPatientSort(state, action: PayloadAction<string>) {
      state.query.sort = action.payload;
      state.query.page = 0;
    },
    resetPatientFilters(state) {
      state.query.search = '';
      state.query.page = 0;
      state.query.sort = 'severity,desc';
    },
    patientUpdated(state, action: PayloadAction<WsPatientUpdate>) {
      const update = action.payload;
      const idx = state.patients.findIndex(
        (p) => p.patient_id === update.patient_id,
      );
      if (idx === -1) return;
      const patient = state.patients[idx];
      state.patients[idx] = {
        ...patient,
        latest_metrics: {
          ...patient.latest_metrics,
          heart_rate: {
            ...patient.latest_metrics?.heart_rate,
            value: update.heart_rate ?? patient.latest_metrics?.heart_rate?.value ?? 0,
          },
          spo2: {
            ...patient.latest_metrics?.spo2,
            value: update.spo2 ?? patient.latest_metrics?.spo2?.value ?? 0,
          },
          systolic_pressure: {
            ...patient.latest_metrics?.systolic_pressure,
            value: update.systolic_pressure ?? patient.latest_metrics?.systolic_pressure?.value ?? 0,
          },
          diastolic_pressure: {
            ...patient.latest_metrics?.diastolic_pressure,
            value: update.diastolic_pressure ?? patient.latest_metrics?.diastolic_pressure?.value ?? 0,
          },
        },
      };
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchPatientsPage.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchPatientsPage.fulfilled, (state, action) => {
        const { data, query } = action.payload;
        state.loading = false;
        state.query = query;
        state.patients = data.content;
        state.pagination = {
          page: data.page,
          size: data.size,
          totalElements: data.total_elements,
          totalPages: data.total_pages,
        };
        state.hiddenAlertsSummary = data.hidden_alerts_summary ?? {
          critical_count: 0,
          warning_count: 0,
        };
      })
      .addCase(fetchPatientsPage.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      })
      .addCase(fetchCriticalAlerts.fulfilled, (state, action) => {
        state.headerAlerts = action.payload;
      })
      .addCase(acknowledgeAlert.pending, (state) => {
        state.ackLoading = true;
      })
      .addCase(acknowledgeAlert.fulfilled, (state, action) => {
        state.ackLoading = false;
        const alertId = action.payload;
        state.patients = state.patients.map((p) => ({
          ...p,
          active_alerts: (p.active_alerts ?? []).filter((a) => a.alert_id !== alertId),
        }));
      })
      .addCase(acknowledgeAlert.rejected, (state) => {
        state.ackLoading = false;
      });
  },
});

export const { setPatientSearch, setPatientPage, setPatientSort, resetPatientFilters, patientUpdated } =
  patientsSlice.actions;
export default patientsSlice.reducer;
