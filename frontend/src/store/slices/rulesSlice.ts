import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import { apiFetch } from '../../services/api';

export interface Rule {
  id: string;
  name: string;
  description?: string;
  metricType: string;
  operator: string;
  threshold: number;
  severity: string;
  active: boolean;
}

export interface CreateRulePayload {
  name: string;
  description?: string;
  metricType: string;
  operator: string;
  threshold: number;
  severity: string;
}

interface RulesState {
  rules: Rule[];
  loading: boolean;
  error: string | null;
}

const initialState: RulesState = {
  rules: [],
  loading: false,
  error: null,
};

export const fetchRules = createAsyncThunk(
  'rules/fetchAll',
  async (_, { rejectWithValue }) => {
    try {
      const data = await apiFetch<Rule[]>('/rules');
      return data ?? [];
    } catch (error) {
      return rejectWithValue((error as Error).message);
    }
  },
);

export const createRule = createAsyncThunk(
  'rules/create',
  async (ruleData: CreateRulePayload, { rejectWithValue }) => {
    try {
      const data = await apiFetch<Rule>('/rules', {
        method: 'POST',
        body: JSON.stringify(ruleData),
      });
      return data;
    } catch (error) {
      return rejectWithValue((error as Error).message);
    }
  },
);

export const toggleRule = createAsyncThunk(
  'rules/toggle',
  async ({ id, enable }: { id: string; enable: boolean }, { rejectWithValue }) => {
    try {
      const action = enable ? 'enable' : 'disable';
      await apiFetch(`/rules/${id}/${action}`, { method: 'PATCH' });
      return { id, enable };
    } catch (error) {
      return rejectWithValue((error as Error).message);
    }
  },
);

export const deleteRule = createAsyncThunk(
  'rules/delete',
  async (id: string, { rejectWithValue }) => {
    try {
      await apiFetch(`/rules/${id}`, { method: 'DELETE' });
      return id;
    } catch (error) {
      return rejectWithValue((error as Error).message);
    }
  },
);

const rulesSlice = createSlice({
  name: 'rules',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(fetchRules.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchRules.fulfilled, (state, action) => {
        state.loading = false;
        state.rules = action.payload;
      })
      .addCase(fetchRules.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      })
      .addCase(createRule.fulfilled, (state, action) => {
        if (action.payload) state.rules.push(action.payload);
      })
      .addCase(toggleRule.fulfilled, (state, action) => {
        const { id, enable } = action.payload;
        const rule = state.rules.find((r) => r.id === id);
        if (rule) rule.active = enable;
      })
      .addCase(deleteRule.fulfilled, (state, action) => {
        state.rules = state.rules.filter((r) => r.id !== action.payload);
      });
  },
});

export default rulesSlice.reducer;
