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
  heartRate: number | null;
  spo2: number | null;
  respiratoryRate: number | null;
  systolicPressure: number | null;
  diastolicPressure: number | null;
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
  emergencyLoading: boolean;
  error: string | null;
}

const defaultTelemetry: CurrentTelemetry = {
  heartRate: null,
  spo2: null,
  respiratoryRate: null,
  systolicPressure: null,
  diastolicPressure: null,
};

const toTelemetryNumber = (value?: number | null): number | null =>
  value != null && !Number.isNaN(value) ? value : null;

const initialState: PatientDetailState = {
  patient: null,
  alerts: [],
  telemetry: defaultTelemetry,
  chartData: [],
  loading: false,
  emergencyLoading: false,
  error: null,
};

const formatTime = (date: Date): string =>
  `${date.getHours()}:${date.getMinutes().toString().padStart(2, '0')}:${date.getSeconds().toString().padStart(2, '0')}`;

// Backend stores telemetry recordedAt in UTC, so the range query must be built
// in UTC too — using local wall-clock here silently returns an empty window for
// any client whose timezone differs from UTC (e.g. UTC-3).
const formatUtcDateTime = (date: Date): string =>
  `${date.getUTCFullYear()}-${(date.getUTCMonth() + 1).toString().padStart(2, '0')}-${date.getUTCDate().toString().padStart(2, '0')}T${date.getUTCHours().toString().padStart(2, '0')}:${date.getUTCMinutes().toString().padStart(2, '0')}:${date.getUTCSeconds().toString().padStart(2, '0')}`;

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
      const endTime = formatUtcDateTime(new Date());
      const startTime = formatUtcDateTime(new Date(Date.now() - 15 * 60000));
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
    { alertId, alertIds, userName = 'Médico Asignado' }: { alertId?: string; alertIds?: string[]; userName?: string },
    { rejectWithValue },
  ) => {
    try {
      const ids = Array.from(new Set(alertIds ?? (alertId ? [alertId] : [])));
      if (ids.length === 0) {
        throw new Error('No hay alertas para reconocer');
      }

      await Promise.all(
        ids.map((id) =>
          apiFetch(
            `/alerts/${id}/acknowledge?acknowledgedBy=${encodeURIComponent(userName)}`,
            { method: 'PATCH' },
          ),
        ),
      );
      return ids;
    } catch (error) {
      return rejectWithValue((error as Error).message);
    }
  },
);

export const triggerEmergency = createAsyncThunk(
  'patientDetail/triggerEmergency',
  async (id: string, { rejectWithValue, dispatch }) => {
    try {
      await apiFetch(`/alerts/patient/${id}/emergency`, { method: 'POST' });
      // Refresh alerts after triggering emergency
      void dispatch(fetchPatientAlerts(id));
      return true;
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
  respiratory_rate?: number;
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
        heartRate: toTelemetryNumber(t.heart_rate),
        spo2: toTelemetryNumber(t.spo2),
        respiratoryRate: toTelemetryNumber(t.respiratory_rate),
        systolicPressure: toTelemetryNumber(t.systolic_pressure),
        diastolicPressure: toTelemetryNumber(t.diastolic_pressure),
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
          heartRate: toTelemetryNumber(latest.heartRate),
          spo2: toTelemetryNumber(latest.spO2),
          respiratoryRate: null,
          systolicPressure: toTelemetryNumber(latest.systolicPressure),
          diastolicPressure: toTelemetryNumber(latest.diastolicPressure),
        };
      })
      .addCase(acknowledgeAlertInDetail.fulfilled, (state, action) => {
        const acknowledgedIds = new Set(action.payload);
        state.alerts = state.alerts.map((a) =>
          acknowledgedIds.has(a.id) ? { ...a, acknowledged: true } : a,
        );
      })
      .addCase(triggerEmergency.pending, (state) => {
        state.emergencyLoading = true;
      })
      .addCase(triggerEmergency.fulfilled, (state) => {
        state.emergencyLoading = false;
      })
      .addCase(triggerEmergency.rejected, (state, action) => {
        state.emergencyLoading = false;
        state.error = action.payload as string;
      });
  },
});

export const { clearPatientDetail, liveUpdate } = patientDetailSlice.actions;
export default patientDetailSlice.reducer;
