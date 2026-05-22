import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { apiFetch } from '../../services/api';

export interface Patient {
  id: string;
  firstName: string;
  lastName: string;
  roomNumber: string;
  bedNumber: string;
  status?: string;
}

export interface Alert {
  id: string;
  message: string;
  severity: 'CRITICAL' | 'WARNING' | string;
  acknowledged: boolean;
  createdAt: string;
  rule?: { description: string };
}

export interface TelemetryReading {
  timestamp: string;
  heartRate?: number;
  spo2?: number;
  respiratoryRate?: number;
  bloodPressureSystolic?: number;
  bloodPressureDiastolic?: number;
}

export interface CurrentTelemetry {
  heartRate: number | string;
  spo2: number | string;
  respiratoryRate: number | string;
  bloodPressure: string;
}

export interface ChartPoint {
  time: string;
  value?: number;
  spo2?: number;
}

interface PatientDetailState {
  patient: Patient | null;
  alerts: Alert[];
  telemetry: CurrentTelemetry;
  chartData: ChartPoint[];
  loading: boolean;
  error: string | null;
}

const defaultTelemetry: CurrentTelemetry = {
  heartRate: '--',
  spo2: '--',
  respiratoryRate: '--',
  bloodPressure: '--/--',
};

const initialState: PatientDetailState = {
  patient: null,
  alerts: [],
  telemetry: defaultTelemetry,
  chartData: [],
  loading: false,
  error: null,
};

const formatTime = (date: Date): string =>
  `${date.getHours()}:${date.getMinutes().toString().padStart(2, '0')}:${date.getSeconds().toString().padStart(2, '0')}`;

export const fetchPatientDetail = createAsyncThunk(
  'patientDetail/fetchPatient',
  async (id: string, { rejectWithValue }) => {
    try {
      return await apiFetch<Patient>(`/patients/${id}`);
    } catch (error) {
      return rejectWithValue((error as Error).message);
    }
  },
);

export const fetchPatientAlerts = createAsyncThunk(
  'patientDetail/fetchAlerts',
  async (id: string, { rejectWithValue }) => {
    try {
      const data = await apiFetch<Alert[]>(`/alerts/patient/${id}`);
      return data ?? [];
    } catch (error) {
      return rejectWithValue((error as Error).message);
    }
  },
);

export const fetchPatientTelemetry = createAsyncThunk(
  'patientDetail/fetchTelemetry',
  async (id: string, { rejectWithValue }) => {
    try {
      const endTime = new Date().toISOString();
      const startTime = new Date(Date.now() - 15 * 60000).toISOString();
      const data = await apiFetch<TelemetryReading[]>(
        `/telemetry-readings/patient/${id}/range?startTime=${startTime}&endTime=${endTime}`,
      );
      return data ?? [];
    } catch (error) {
      return rejectWithValue((error as Error).message);
    }
  },
);

export const acknowledgeAlertInDetail = createAsyncThunk(
  'patientDetail/acknowledgeAlert',
  async (
    { alertId, userName = 'Médico Asignado' }: { alertId: string; userName?: string },
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

export interface WsDetailUpdate {
  telemetry?: {
    heartRate?: number;
    spo2?: number;
    respiratoryRate?: number;
    bloodPressureSystolic?: number;
    bloodPressureDiastolic?: number;
  };
}

const patientDetailSlice = createSlice({
  name: 'patientDetail',
  initialState,
  reducers: {
    clearPatientDetail(state) {
      state.patient = null;
      state.alerts = [];
      state.telemetry = defaultTelemetry;
      state.chartData = [];
      state.error = null;
    },
    liveUpdate(state, action: PayloadAction<WsDetailUpdate>) {
      const t = action.payload.telemetry;
      if (!t) return;
      state.telemetry = {
        heartRate: t.heartRate ?? '--',
        spo2: t.spo2 ?? '--',
        respiratoryRate: t.respiratoryRate ?? '--',
        bloodPressure: `${t.bloodPressureSystolic ?? '--'}/${t.bloodPressureDiastolic ?? '--'}`,
      };
      const newPoint: ChartPoint = {
        time: formatTime(new Date()),
        value: t.heartRate,
        spo2: t.spo2,
      };
      const updated = [...state.chartData, newPoint];
      state.chartData = updated.length > 30 ? updated.slice(updated.length - 30) : updated;
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchPatientDetail.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchPatientDetail.fulfilled, (state, action) => {
        state.loading = false;
        state.patient = action.payload;
      })
      .addCase(fetchPatientDetail.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      })
      .addCase(fetchPatientAlerts.fulfilled, (state, action) => {
        state.alerts = action.payload;
      })
      .addCase(fetchPatientTelemetry.fulfilled, (state, action) => {
        const readings = action.payload;
        if (readings.length === 0) return;
        const chartData: ChartPoint[] = readings
          .map((r) => ({
            time: formatTime(new Date(r.timestamp)),
            value: r.heartRate,
            spo2: r.spo2,
          }))
          .reverse();
        state.chartData = chartData;
        const latest = readings[0];
        state.telemetry = {
          heartRate: latest.heartRate ?? '--',
          spo2: latest.spo2 ?? '--',
          respiratoryRate: latest.respiratoryRate ?? '--',
          bloodPressure: `${latest.bloodPressureSystolic ?? '--'}/${latest.bloodPressureDiastolic ?? '--'}`,
        };
      })
      .addCase(acknowledgeAlertInDetail.fulfilled, (state, action) => {
        const alertId = action.payload;
        state.alerts = state.alerts.map((a) =>
          a.id === alertId ? { ...a, acknowledged: true } : a,
        );
      });
  },
});

export const { clearPatientDetail, liveUpdate } = patientDetailSlice.actions;
export default patientDetailSlice.reducer;
