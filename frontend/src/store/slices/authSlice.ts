import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';

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

const initialState: AuthState = {
  user: null,
  token: null,
  isAuthenticated: false,
  loading: false,
  error: null,
};

interface CoreLoginResponse {
  token: string;
  user: {
    id: number;
    first_name?: string;
    last_name?: string;
    email: string;
    roles?: Array<UserRole | string>;
  };
}

interface TokenPayload {
  roles?: Array<UserRole | string>;
  permissions?: string[];
}

const normalizeRoles = (roles?: Array<UserRole | string>): UserRole[] => {
  if (!roles || roles.length === 0) return [];

  return roles
    .map((role) => (typeof role === 'string' ? { name: role } : role))
    .filter((role): role is UserRole => Boolean(role?.name));
};

const getRolesFromToken = (token: string): UserRole[] => {
  try {
    const encodedPayload = token.split('.')[1];
    const base64Payload = encodedPayload.replace(/-/g, '+').replace(/_/g, '/');
    const paddedPayload = base64Payload.padEnd(base64Payload.length + ((4 - base64Payload.length % 4) % 4), '=');
    const payload = JSON.parse(atob(paddedPayload)) as TokenPayload;
    const roles = normalizeRoles(payload.roles);
    if (roles.length > 0) return roles;

    return (payload.permissions ?? []).map((permission) => ({ name: permission }));
  } catch {
    return [];
  }
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

      const coreUrl = import.meta.env.VITE_CORE_API_URL ?? 'http://localhost:8081';
      const response = await fetch(`${coreUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });

      if (!response.ok) {
        throw new Error(`Login failed: ${response.status}`);
      }

      const data = (await response.json()) as CoreLoginResponse;
      const roles = normalizeRoles(data.user.roles);
      const tokenRoles = getRolesFromToken(data.token);

      const userData: User = {
        id: data.user.id,
        first_name: data.user.first_name ?? '',
        last_name: data.user.last_name ?? '',
        email: data.user.email,
        roles: roles.length > 0 ? roles : tokenRoles.length > 0 ? tokenRoles : [{ name: 'ENFERMERO' }],
      };

      localStorage.setItem('token', data.token);
      localStorage.setItem('user', JSON.stringify(userData));
      return { token: data.token, user: userData };
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
