import { useEffect } from 'react';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Typography from '@mui/material/Typography';
import { fetchCoreUserById, getUserIdFromToken } from '../store/slices/authSlice';

interface CoreSsoResponse {
  user: {
    id?: number;
    first_name: string;
    last_name: string;
    email: string;
  };
  token: string;
}

const CORE_API_URL = import.meta.env.VITE_CORE_API_URL ?? 'https://api.healthcare.cantero.ar';

const isSafeRedirect = (path: string | null): path is string => {
  return Boolean(path && path.startsWith('/') && !path.startsWith('//') && !path.startsWith('/\\'));
};

const SsoRedirect = () => {
  useEffect(() => {
    const exchangeTicket = async () => {
      const params = new URLSearchParams(window.location.search);
      const ticket = params.get('ticket');
      const redirect = params.get('redirect');
      const target = isSafeRedirect(redirect) ? redirect : '/monitoreo';

      if (!ticket) {
        window.location.replace('/login');
        return;
      }

      try {
        const response = await fetch(`${CORE_API_URL}/auth/sso-exchange`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ ticket }),
        });

        if (!response.ok) {
          window.location.replace('/login');
          return;
        }

        const data = (await response.json()) as CoreSsoResponse;
        const userId = data.user.id ?? getUserIdFromToken(data.token);

        if (!userId) {
          window.location.replace('/login');
          return;
        }

        const user = await fetchCoreUserById(CORE_API_URL, data.token, userId);

        localStorage.setItem('token', data.token);
        localStorage.setItem('user', JSON.stringify(user));

        window.location.replace(target);
      } catch {
        window.location.replace('/login');
      }
    };

    void exchangeTicket();
  }, []);

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexDirection: 'column',
        gap: 2,
        bgcolor: 'background.default',
      }}
    >
      <CircularProgress size={32} />
      <Typography variant="body2" color="text.secondary">
        Iniciando sesion...
      </Typography>
    </Box>
  );
};

export default SsoRedirect;
