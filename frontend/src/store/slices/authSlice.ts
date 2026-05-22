import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { API_BASE_URL } from '../../services/api';

export interface UserRole {
  name: string;
}

export interface User {
  id: number;
  first_name: string;
  last_name: string;
  email: string;
  roles: UserRole[];
}

interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  loading: boolean;
  error: string | null;
}

interface TokenResponse {
  token: string;
  module: string;
  userId: string;
}

const initialState: AuthState = {
  user: null,
  token: null,
  isAuthenticated: false,
  loading: false,
  error: null,
};

export const loginThunk = createAsyncThunk(
  'auth/login',
  async (
    { email, password }: { email: string; password: string },
    { rejectWithValue },
  ) => {
    try {
      if (!email.trim() || !password.trim()) {
        throw new Error('Email y contraseña son requeridos');
      }

      const response = await fetch(`${API_BASE_URL}/auth/token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ module: 'DASHBOARD', userId: email }),
      });

      if (!response.ok) {
        throw new Error(`Login failed: ${response.status}`);
      }

      const tokenData = (await response.json()) as TokenResponse;
      const userData: User = {
        id: 1,
        first_name: tokenData.userId.split('@')[0].toUpperCase(),
        last_name: 'Grid',
        email: tokenData.userId,
        roles: [{ name: 'ENFERMERO' }],
      };

      localStorage.setItem('token', tokenData.token);
      localStorage.setItem('user', JSON.stringify(userData));
      return { token: tokenData.token, user: userData };
    } catch (error) {
      return rejectWithValue((error as Error).message);
    }
  },
);

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    initAuth(state) {
      const savedToken = localStorage.getItem('token');
      const savedUser = localStorage.getItem('user');
      if (savedToken && savedUser) {
        try {
          state.user = JSON.parse(savedUser) as User;
          state.token = savedToken;
          state.isAuthenticated = true;
        } catch {
          localStorage.removeItem('token');
          localStorage.removeItem('user');
        }
      }
    },
    logout(state) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      state.user = null;
      state.token = null;
      state.isAuthenticated = false;
      state.error = null;
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(loginThunk.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(
        loginThunk.fulfilled,
        (state, action: PayloadAction<{ token: string; user: User }>) => {
          state.loading = false;
          state.token = action.payload.token;
          state.user = action.payload.user;
          state.isAuthenticated = true;
        },
      )
      .addCase(loginThunk.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      });
  },
});

export const { initAuth, logout } = authSlice.actions;
export default authSlice.reducer;
