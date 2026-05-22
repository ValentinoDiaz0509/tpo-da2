import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { apiFetch } from '../../services/api';

export interface Patient {
  id: string;
  name: string;
  room: string;
  bed: string;
  status?: string;
}

export interface Alert {
  id: string;
  message: string;
  severity: 'CRITICAL' | 'WARNING' | string;
  acknowledged: boolean;
  triggeredAt: string;
  rule?: { description: string };
}

export interface TelemetryReading {
  recordedAt: string;
  heartRate?: number;
  spO2?: number;
  systolicPressure?: number;
  diastolicPressure?: number;
  temperature?: number;
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

const formatLocalDateTime = (date: Date): string =>
  `${date.getFullYear()}-${(date.getMonth() + 1).toString().padStart(2, '0')}-${date.getDate().toString().padStart(2, '0')}T${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}:${date.getSeconds().toString().padStart(2, '0')}`;

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
      const endTime = formatLocalDateTime(new Date());
      const startTime = formatLocalDateTime(new Date(Date.now() - 15 * 60000));
      const data = await apiFetch<TelemetryReading[]>(
        `/telemetry/patient/${id}/range?startTime=${startTime}&endTime=${endTime}`,
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
  heart_rate?: number;
  spo2?: number;
  systolic_pressure?: number;
  diastolic_pressure?: number;
  temperature?: number;
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
      const t = action.payload;
      state.telemetry = {
        heartRate: t.heart_rate ?? '--',
        spo2: t.spo2 ?? '--',
        respiratoryRate: '--',
        bloodPressure: `${t.systolic_pressure ?? '--'}/${t.diastolic_pressure ?? '--'}`,
      };
      const newPoint: ChartPoint = {
        time: formatTime(new Date()),
        value: t.heart_rate,
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
            time: formatTime(new Date(r.recordedAt)),
            value: r.heartRate,
            spo2: r.spO2,
          }))
          .reverse();
        state.chartData = chartData;
        const latest = readings[0];
        state.telemetry = {
          heartRate: latest.heartRate ?? '--',
          spo2: latest.spO2 ?? '--',
          respiratoryRate: '--',
          bloodPressure: `${latest.systolicPressure ?? '--'}/${latest.diastolicPressure ?? '--'}`,
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
