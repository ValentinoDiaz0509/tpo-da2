import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { apiFetch } from '../../services/api';

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
  id: string;
  message: string;
  severity: 'CRITICAL' | 'WARNING' | string;
  createdAt?: string;
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
  sp_o2?: number;
  systolic_pressure?: number;
  diastolic_pressure?: number;
}

export interface CriticalAlert {
  id: string;
  message: string;
  severity: string;
  createdAt: string;
  patient: { id: string };
}

interface PatientsState {
  patients: PatientMonitoring[];
  headerAlerts: CriticalAlert[];
  loading: boolean;
  ackLoading: boolean;
  error: string | null;
}

const initialState: PatientsState = {
  patients: [],
  headerAlerts: [],
  loading: false,
  ackLoading: false,
  error: null,
};

export const fetchPatients = createAsyncThunk(
  'patients/fetchAll',
  async (_, { rejectWithValue }) => {
    try {
      const data = await apiFetch<PatientMonitoring[]>('/patients/monitoring');
      return data ?? [];
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
            value: update.sp_o2 ?? patient.latest_metrics?.spo2?.value ?? 0,
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
      .addCase(fetchPatients.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchPatients.fulfilled, (state, action) => {
        state.loading = false;
        state.patients = action.payload;
      })
      .addCase(fetchPatients.rejected, (state, action) => {
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
          active_alerts: (p.active_alerts ?? []).filter((a) => a.id !== alertId),
        }));
      })
      .addCase(acknowledgeAlert.rejected, (state) => {
        state.ackLoading = false;
      });
  },
});

export const { patientUpdated } = patientsSlice.actions;
export default patientsSlice.reducer;
