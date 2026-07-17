import { useEffect } from 'react';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Typography from '@mui/material/Typography';
import { API_BASE_URL } from '../services/api';

const SsoRedirect = () => {
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const ticket = params.get('ticket');
    const redirect = params.get('redirect') ?? '/login';

    if (!ticket) {
      window.location.replace('/login');
      return;
    }

    const backendSsoUrl = new URL(`${API_BASE_URL}/auth/sso`);
    backendSsoUrl.searchParams.set('ticket', ticket);
    backendSsoUrl.searchParams.set('redirect', redirect);

    window.location.replace(backendSsoUrl.toString());
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
