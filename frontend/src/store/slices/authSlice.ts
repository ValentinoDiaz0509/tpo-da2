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

export const loginThunk = createAsyncThunk(
  'auth/login',
  async (
    { email, password: _password }: { email: string; password: string },
    { rejectWithValue },
  ) => {
    try {
      // Mock login — same behaviour as the old AuthContext
      // TODO: Replace with real M10 call when available:
      // body: JSON.stringify({ email, password: _password })
      const responseToken = 'mock-jwt-token-for-m6';
      const userData: User = {
        id: 1,
        first_name: email.split('@')[0].toUpperCase(),
        last_name: 'Grid',
        email,
        roles: [{ name: 'ENFERMERO' }],
      };

      localStorage.setItem('token', responseToken);
      localStorage.setItem('user', JSON.stringify(userData));
      return { token: responseToken, user: userData };
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
