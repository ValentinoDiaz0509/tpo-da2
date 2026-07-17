import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';

export interface UserRole {
  id?: number;
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
    id?: number;
    first_name?: string;
    last_name?: string;
    email: string;
    roles?: Array<UserRole | string>;
  };
}

interface CoreUserResponse {
  id: number;
  first_name?: string;
  last_name?: string;
  email: string;
  roles?: Array<UserRole | string>;
}

interface CoreErrorResponse {
  error?: string;
}

interface TokenPayload {
  user_id?: number | string;
  sub?: number | string;
}

const normalizeRoles = (roles?: Array<UserRole | string>): UserRole[] => {
  if (!roles || roles.length === 0) return [];

  return roles
    .map((role) => (typeof role === 'string' ? { name: role } : { id: role.id, name: role.name }))
    .filter((role): role is UserRole => Boolean(role?.name));
};

const decodeTokenPayload = (token: string): TokenPayload | null => {
  try {
    const encodedPayload = token.split('.')[1];
    if (!encodedPayload) return null;

    const base64Payload = encodedPayload.replace(/-/g, '+').replace(/_/g, '/');
    const paddedPayload = base64Payload.padEnd(base64Payload.length + ((4 - base64Payload.length % 4) % 4), '=');
    return JSON.parse(atob(paddedPayload)) as TokenPayload;
  } catch {
    return null;
  }
};

export const getUserIdFromToken = (token: string): number | null => {
  const payload = decodeTokenPayload(token);
  const rawUserId = payload?.user_id ?? payload?.sub;
  const userId = Number(rawUserId);
  return Number.isFinite(userId) && userId > 0 ? userId : null;
};

const toUser = (data: CoreUserResponse): User => ({
  id: data.id,
  first_name: data.first_name ?? '',
  last_name: data.last_name ?? '',
  email: data.email,
  roles: normalizeRoles(data.roles),
});

export const fetchCoreUserById = async (coreUrl: string, token: string, userId: number): Promise<User> => {
  const response = await fetch(`${coreUrl}/users/${userId}`, {
    method: 'GET',
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    let errorMessage = `Get user failed: ${response.status}`;

    try {
      const errorBody = (await response.json()) as CoreErrorResponse;
      errorMessage = errorBody.error ?? errorMessage;
    } catch {
      // Keep the HTTP status as the error detail when Core does not return JSON.
    }

    throw new Error(errorMessage);
  }

  return toUser((await response.json()) as CoreUserResponse);
};

export const loginThunk = createAsyncThunk(
  'auth/login',
  async (
    { email, password }: { email: string; password: string },
    { rejectWithValue },
  ) => {
    try {
      if (!email.trim() || !password.trim()) {
        throw new Error('Email y contrasena son requeridos');
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
      const userId = data.user.id ?? getUserIdFromToken(data.token);

      if (!userId) {
        throw new Error('No se pudo obtener el user_id del token');
      }

      const userData = await fetchCoreUserById(coreUrl, data.token, userId);

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
