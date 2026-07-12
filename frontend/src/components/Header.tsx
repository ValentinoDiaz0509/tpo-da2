import { useState, useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import Box from '@mui/material/Box';
import InputBase from '@mui/material/InputBase';
import IconButton from '@mui/material/IconButton';
import Badge from '@mui/material/Badge';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Chip from '@mui/material/Chip';
import SearchIcon from '@mui/icons-material/Search';
import NotificationsIcon from '@mui/icons-material/Notifications';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import { useAppDispatch, useAppSelector } from '../store';
import { logout } from '../store/slices/authSlice';
import { fetchCriticalAlerts, setPatientSearch } from '../store/slices/patientsSlice';

interface Props {
  height: number;
}

const SEARCH_DEBOUNCE_MS = 300;

const Header = ({ height }: Props) => {
  const dispatch = useAppDispatch();
  const user = useAppSelector((s) => s.auth.user);
  const headerAlerts = useAppSelector((s) => s.patients.headerAlerts);
  const patientSearch = useAppSelector((s) => s.patients.query.search);

  const [searchValue, setSearchValue] = useState(patientSearch);
  const [showNotifications, setShowNotifications] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const refreshAlerts = () => {
    void dispatch(fetchCriticalAlerts());
  };

  useEffect(() => {
    setSearchValue(patientSearch);
  }, [patientSearch]);

  useEffect(() => {
    refreshAlerts();
    const interval = setInterval(refreshAlerts, 10000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setShowNotifications(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  useEffect(() => {
    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
    }

    debounceRef.current = setTimeout(() => {
      if (searchValue !== patientSearch) {
        dispatch(setPatientSearch(searchValue));
      }
    }, SEARCH_DEBOUNCE_MS);

    return () => {
      if (debounceRef.current) {
        clearTimeout(debounceRef.current);
      }
    };
  }, [searchValue, patientSearch, dispatch]);

  return (
    <AppBar
      position="static"
      elevation={0}
      sx={{
        bgcolor: 'background.paper',
        borderBottom: '1px solid',
        borderColor: 'divider',
        height,
        justifyContent: 'center',
      }}
    >
      <Toolbar sx={{ gap: 2, minHeight: `${height}px !important` }}>
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
            bgcolor: 'background.default',
            border: '1px solid',
            borderColor: 'divider',
            borderRadius: 2,
            px: 1.5,
            py: 0.5,
            flex: 1,
            maxWidth: 400,
          }}
        >
          <SearchIcon sx={{ color: 'text.secondary', fontSize: 18 }} />
          <InputBase
            placeholder="Buscar por nombre o cama..."
            value={searchValue}
            onChange={(event) => setSearchValue(event.target.value)}
            sx={{ fontSize: '0.875rem', flex: 1, color: 'text.primary' }}
            inputProps={{ 'aria-label': 'buscar pacientes' }}
          />
        </Box>

        <Box sx={{ flex: 1 }} />

        {user && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              Hola, <strong>{user.first_name}</strong>
            </Typography>
            <Button
              variant="outlined"
              size="small"
              onClick={() => dispatch(logout())}
              sx={{ color: 'text.secondary', borderColor: 'divider', fontSize: '0.75rem' }}
            >
              Salir
            </Button>
          </Box>
        )}

        <Box ref={dropdownRef} sx={{ position: 'relative' }}>
          <IconButton
            onClick={() => {
              setShowNotifications((v) => !v);
              refreshAlerts();
            }}
            sx={{ color: 'text.primary' }}
          >
            <Badge badgeContent={headerAlerts.length} color="error" max={9}>
              <NotificationsIcon />
            </Badge>
          </IconButton>

          {showNotifications && (
            <Paper
              elevation={4}
              sx={{
                position: 'absolute',
                right: 0,
                top: 'calc(100% + 8px)',
                width: 320,
                zIndex: 1300,
                overflow: 'hidden',
                borderRadius: 2,
              }}
            >
              <Box
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  px: 2,
                  py: 1.5,
                  borderBottom: '1px solid',
                  borderColor: 'divider',
                }}
              >
                <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                  Notificaciones
                </Typography>
                {headerAlerts.length > 0 && (
                  <Chip label={`${headerAlerts.length} Críticas`} color="error" size="small" />
                )}
              </Box>

              <Box sx={{ maxHeight: 320, overflowY: 'auto' }}>
                {headerAlerts.length === 0 ? (
                  <Typography variant="body2" sx={{ color: 'text.secondary', p: 2 }}>
                    No hay notificaciones nuevas.
                  </Typography>
                ) : (
                  headerAlerts.map((alert) => (
                    <Link
                      key={alert.id}
                      to={`/paciente/${alert.patientId}`}
                      style={{ textDecoration: 'none', color: 'inherit' }}
                      onClick={() => setShowNotifications(false)}
                    >
                      <Box
                        sx={{
                          display: 'flex',
                          gap: 1.5,
                          px: 2,
                          py: 1.5,
                          borderBottom: '1px solid',
                          borderColor: 'divider',
                          '&:hover': { bgcolor: 'action.hover' },
                          cursor: 'pointer',
                        }}
                      >
                        <WarningAmberIcon color="error" fontSize="small" sx={{ mt: 0.25 }} />
                        <Box>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>
                            {alert.message || 'Alerta Crítica'}
                          </Typography>
                          <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                            {new Date(alert.triggeredAt).toLocaleTimeString()}
                          </Typography>
                        </Box>
                      </Box>
                    </Link>
                  ))
                )}
              </Box>
            </Paper>
          )}
        </Box>
      </Toolbar>
    </AppBar>
  );
};

export default Header;
