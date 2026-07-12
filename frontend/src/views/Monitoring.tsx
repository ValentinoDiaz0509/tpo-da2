import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import InputBase from '@mui/material/InputBase';
import CircularProgress from '@mui/material/CircularProgress';
import Pagination from '@mui/material/Pagination';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import SearchIcon from '@mui/icons-material/Search';
import SettingsIcon from '@mui/icons-material/Settings';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import RuleEngineModal from '../components/RuleEngineModal';
import HiddenAlertsBanner from '../components/HiddenAlertsBanner';
import { useAppDispatch, useAppSelector } from '../store';
import {
  fetchPatientsPage,
  acknowledgeAlert,
  resetPatientFilters,
  setPatientPage,
  setPatientSearch,
  setPatientSort,
  PATIENT_SORT_OPTIONS,
} from '../store/slices/patientsSlice';
import type { ActiveAlert } from '../store/slices/patientsSlice';
import { wsConnectMonitoring, wsDisconnect } from '../store/middleware/websocketMiddleware';

const DATA_REFRESH_INTERVAL_MS = 5000;
const SEARCH_DEBOUNCE_MS = 300;

const getAlertTimestamp = (date?: string): number => {
  if (!date) return 0;
  const timestamp = new Date(date).getTime();
  return Number.isNaN(timestamp) ? 0 : timestamp;
};

const getLatestAlert = (alerts?: ActiveAlert[]): ActiveAlert | null => {
  return (alerts ?? []).reduce<ActiveAlert | null>((latest, alert) => {
    if (!latest || getAlertTimestamp(alert.triggered_at) >= getAlertTimestamp(latest.triggered_at)) {
      return alert;
    }
    return latest;
  }, null);
};

const Monitoring = () => {
  const dispatch = useAppDispatch();
  const {
    patients,
    loading,
    ackLoading,
    pagination,
    hiddenAlertsSummary,
    query,
    error,
  } = useAppSelector((s) => s.patients);
  const [showModal, setShowModal] = useState(false);
  const [searchValue, setSearchValue] = useState(query.search);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    setSearchValue(query.search);
  }, [query.search]);

  useEffect(() => {
    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
    }

    debounceRef.current = setTimeout(() => {
      if (searchValue !== query.search) {
        dispatch(setPatientSearch(searchValue));
      }
    }, SEARCH_DEBOUNCE_MS);

    return () => {
      if (debounceRef.current) {
        clearTimeout(debounceRef.current);
      }
    };
  }, [searchValue, query.search, dispatch]);

  useEffect(() => {
    void dispatch(fetchPatientsPage(undefined));

    const interval = setInterval(() => {
      void dispatch(fetchPatientsPage(undefined));
    }, DATA_REFRESH_INTERVAL_MS);

    return () => {
      clearInterval(interval);
    };
  }, [dispatch, query.page, query.search, query.sort, query.size]);

  useEffect(() => {
    return () => {
      dispatch(wsDisconnect());
    };
  }, [dispatch]);

  useEffect(() => {
    if (patients.length > 0) {
      const ids = patients.map((p) => p.patient_id);
      dispatch(wsConnectMonitoring(ids));
    } else {
      dispatch(wsDisconnect());
    }
  }, [patients, dispatch]);

  const criticalAlerts = patients
    .map((p) => {
      const patientAlertIds = (p.active_alerts ?? []).map((alert) => alert.alert_id);
      const latestCriticalAlert = getLatestAlert(
        (p.active_alerts ?? []).filter((a) => a.severity === 'CRITICAL'),
      );
      if (!latestCriticalAlert) return null;
      return {
        ...latestCriticalAlert,
        patient_name: p.patient_name,
        room: p.room,
        bed: p.bed,
        patient_id: p.patient_id,
        alert_ids: patientAlertIds,
      };
    })
    .filter((alert): alert is NonNullable<typeof alert> => alert !== null)
    .sort((a, b) => getAlertTimestamp(b.triggered_at) - getAlertTimestamp(a.triggered_at));

  const handlePageChange = (_event: React.ChangeEvent<unknown>, page: number) => {
    dispatch(setPatientPage(page - 1));
  };

  const handleViewCritical = () => {
    dispatch(resetPatientFilters());
  };

  return (
    <Box sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'flex-start', alignItems: 'stretch', mb: 3, gap: 2, flexWrap: 'wrap' }}>
        <Paper
          elevation={0}
          sx={{
            px: 2,
            py: 1,
            minWidth: 150,
            minHeight: 68,
            border: '1px solid',
            borderColor: 'divider',
            borderRadius: 2,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', fontWeight: 700 }}>
            PACIENTES ACTIVOS
          </Typography>
          <Typography variant="h6" sx={{ fontWeight: 700, textAlign: 'center' }}>
            {pagination.totalElements}
          </Typography>
        </Paper>
        <Button
          variant="outlined"
          startIcon={<SettingsIcon fontSize="small" />}
          onClick={() => setShowModal(true)}
          sx={{
            minHeight: 68,
            px: 2.5,
            bgcolor: 'background.paper',
            borderColor: 'divider',
            borderRadius: 2,
            color: 'text.primary',
            alignSelf: { xs: 'stretch', sm: 'auto' },
          }}
        >
          Configurar reglas
        </Button>
      </Box>

      <HiddenAlertsBanner summary={hiddenAlertsSummary} onViewCritical={handleViewCritical} />

      <Grid container spacing={2} sx={{ mb: 3 }}>
        {criticalAlerts.length > 0 && (
          <Grid size={12}>
            <Paper
              elevation={0}
              sx={{
                overflow: 'hidden',
                bgcolor: '#7f1d1d',
                color: '#fff',
                borderRadius: 2,
                border: '1px solid #991b1b',
              }}
            >
              <Box
                sx={{
                  display: 'flex',
                  alignItems: { xs: 'flex-start', sm: 'center' },
                  justifyContent: 'space-between',
                  gap: 2,
                  px: 2.5,
                  py: 2,
                  borderBottom: '1px solid rgba(255,255,255,0.18)',
                }}
              >
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, minWidth: 0 }}>
                  <Box
                    sx={{
                      width: 36,
                      height: 36,
                      borderRadius: '50%',
                      bgcolor: 'rgba(255,255,255,0.14)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                    }}
                  >
                    <WarningAmberIcon sx={{ color: '#fff', fontSize: 20 }} />
                  </Box>
                  <Box sx={{ minWidth: 0 }}>
                    <Typography variant="caption" sx={{ display: 'block', fontWeight: 800, opacity: 0.78 }}>
                      CÓDIGO ROJO
                    </Typography>
                    <Typography variant="h6" sx={{ fontWeight: 800, lineHeight: 1.15 }}>
                      Atención inmediata
                    </Typography>
                  </Box>
                </Box>
                <Chip
                  label={`${criticalAlerts.length} ${criticalAlerts.length === 1 ? 'alerta' : 'alertas'}`}
                  size="small"
                  sx={{
                    bgcolor: '#fff',
                    color: '#7f1d1d',
                    fontWeight: 800,
                    flexShrink: 0,
                  }}
                />
              </Box>
              <Box
                sx={{
                  display: 'grid',
                  gridTemplateColumns: { xs: '1fr', lg: 'repeat(2, minmax(0, 1fr))' },
                  maxHeight: { xs: 380, md: 320 },
                  overflowY: 'auto',
                }}
              >
                {criticalAlerts.map((alert, index) => (
                  <Box
                    key={`${alert.patient_id}-${alert.alert_id}`}
                    sx={{
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 1.25,
                      p: 2,
                      minWidth: 0,
                      borderTop: { xs: index === 0 ? 'none' : '1px solid rgba(255,255,255,0.16)', lg: index < 2 ? 'none' : '1px solid rgba(255,255,255,0.16)' },
                      borderLeft: { xs: 'none', lg: index % 2 === 1 ? '1px solid rgba(255,255,255,0.16)' : 'none' },
                    }}
                  >
                    <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 1.5 }}>
                      <Box sx={{ minWidth: 0 }}>
                        <Typography variant="subtitle1" sx={{ fontWeight: 800, lineHeight: 1.2 }} noWrap>
                          {alert.patient_name}
                        </Typography>
                        <Typography variant="caption" sx={{ opacity: 0.78, fontWeight: 700 }}>
                          Hab {alert.room} · Cama {alert.bed}
                        </Typography>
                      </Box>
                      <Box sx={{ textAlign: 'right', flexShrink: 0 }}>
                        <Typography variant="caption" sx={{ opacity: 0.7, display: 'block', lineHeight: 1 }}>
                          Hora
                        </Typography>
                        <Typography variant="body2" sx={{ fontWeight: 800, whiteSpace: 'nowrap' }}>
                          {new Date(alert.triggered_at ?? Date.now()).toLocaleTimeString()}
                        </Typography>
                      </Box>
                    </Box>
                    <Typography
                      variant="body2"
                      sx={{
                        fontWeight: 700,
                        lineHeight: 1.25,
                        minHeight: 36,
                        overflow: 'hidden',
                        display: '-webkit-box',
                        WebkitBoxOrient: 'vertical',
                        WebkitLineClamp: 2,
                      }}
                    >
                      {alert.message || 'Taquicardia Crítica'}
                    </Typography>
                    <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mt: 'auto' }}>
                      <Button
                        variant="outlined"
                        size="small"
                        disabled={ackLoading}
                        onClick={() =>
                          dispatch(acknowledgeAlert({ alertIds: alert.alert_ids, patientId: alert.patient_id }))
                        }
                        sx={{ color: '#fff', borderColor: 'rgba(255,255,255,0.55)', '&:hover': { borderColor: '#fff', bgcolor: 'rgba(255,255,255,0.1)' } }}
                      >
                        {ackLoading ? 'Procesando...' : 'Reconocer'}
                      </Button>
                      <Button
                        component={Link}
                        to={`/paciente/${alert.patient_id}`}
                        variant="outlined"
                        size="small"
                        sx={{ color: '#fff', borderColor: 'rgba(255,255,255,0.55)', '&:hover': { borderColor: '#fff', bgcolor: 'rgba(255,255,255,0.1)' } }}
                      >
                        Ver Paciente
                      </Button>
                    </Box>
                  </Box>
                ))}
              </Box>
            </Paper>
          </Grid>
        )}

      </Grid>

      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2, gap: 2, flexWrap: 'wrap' }}>
        <Typography variant="h6" sx={{ fontWeight: 700 }}>
          Pacientes
        </Typography>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: { xs: 'stretch', sm: 'flex-end' }, gap: 2, flexWrap: 'wrap', flex: 1 }}>
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
              bgcolor: 'background.paper',
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: 2,
              px: 1.5,
              py: 0.5,
              width: { xs: '100%', sm: 320, md: 400 },
              minHeight: 40,
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
          <FormControl size="small" sx={{ minWidth: 220 }}>
            <InputLabel id="patient-sort-label">Ordenar por</InputLabel>
            <Select
              labelId="patient-sort-label"
              label="Ordenar por"
              value={query.sort}
              onChange={(event) => dispatch(setPatientSort(event.target.value))}
            >
              {PATIENT_SORT_OPTIONS.map((option) => (
                <MenuItem key={option.value} value={option.value}>
                  {option.label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          {pagination.totalPages > 1 && (
            <Typography variant="body2" color="text.secondary">
              Página {pagination.page + 1} de {pagination.totalPages}
            </Typography>
          )}
        </Box>
      </Box>

      {loading && patients.length === 0 ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', pt: 6 }}>
          <CircularProgress />
        </Box>
      ) : patients.length === 0 ? (
        <Paper
          elevation={0}
          sx={{ p: 4, textAlign: 'center', border: '1px solid', borderColor: 'divider', borderRadius: 2 }}
        >
          <Typography variant="body1" color="text.secondary">
            {query.search
              ? `No se encontraron pacientes para «${query.search}».`
              : 'No hay pacientes para mostrar.'}
          </Typography>
          {error && (
            <Typography variant="body2" color="error" sx={{ mt: 1 }}>
              {error}
            </Typography>
          )}
        </Paper>
      ) : (
        <Grid container spacing={2}>
          {patients.map((p) => {
            const isCritical = p.status === 'CRITICAL';
            const isWarning = p.status === 'WARNING';
            const latestAlert = getLatestAlert(p.active_alerts);

            return (
              <Grid key={p.patient_id} size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
                <Link to={`/paciente/${p.patient_id}`} style={{ textDecoration: 'none' }}>
                  <Paper
                    elevation={0}
                    sx={{
                      p: 2,
                      border: '2px solid',
                      borderColor: isCritical ? 'error.main' : isWarning ? 'warning.main' : 'divider',
                      borderRadius: 2,
                      cursor: 'pointer',
                      transition: 'box-shadow 0.15s',
                      '&:hover': { boxShadow: 3 },
                    }}
                  >
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
                      <Box
                        sx={{
                          width: 10,
                          height: 10,
                          borderRadius: '50%',
                          bgcolor: isCritical ? 'error.main' : isWarning ? 'warning.main' : 'success.main',
                          flexShrink: 0,
                        }}
                      />
                      <Box sx={{ minWidth: 0, flex: 1 }}>
                        <Typography variant="body2" noWrap sx={{ fontWeight: 600 }}>
                          {p.room} {p.bed} — {p.patient_name}
                        </Typography>
                        {latestAlert && (
                          <Typography
                            variant="caption"
                            color="error.main"
                            sx={{
                              display: '-webkit-box',
                              mt: 0.25,
                              lineHeight: 1.25,
                              overflow: 'hidden',
                              overflowWrap: 'anywhere',
                              WebkitBoxOrient: 'vertical',
                              WebkitLineClamp: 2,
                            }}
                          >
                            {latestAlert.message}
                          </Typography>
                        )}
                      </Box>
                    </Box>
                    <Box sx={{ display: 'flex', gap: 3 }}>
                      <Box>
                        <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                          FC
                        </Typography>
                        <Typography
                          variant="h6"
                          sx={{ fontWeight: 700, color: isCritical ? 'error.main' : 'text.primary' }}
                        >
                          {p.latest_metrics?.heart_rate?.value
                            ? Math.round(p.latest_metrics.heart_rate.value)
                            : '--'}
                        </Typography>
                      </Box>
                      <Box>
                        <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                          SpO2
                        </Typography>
                        <Typography
                          variant="h6"
                          sx={{ fontWeight: 700, color: isWarning ? 'warning.main' : 'text.primary' }}
                        >
                          {p.latest_metrics?.spo2?.value
                            ? Math.round(p.latest_metrics.spo2.value)
                            : '--'}
                          <Typography component="span" variant="caption" color="text.secondary">
                            %
                          </Typography>
                        </Typography>
                      </Box>
                    </Box>
                  </Paper>
                </Link>
              </Grid>
            );
          })}
        </Grid>
      )}

      {pagination.totalPages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
          <Pagination
            count={pagination.totalPages}
            page={pagination.page + 1}
            onChange={handlePageChange}
            color="primary"
            disabled={loading}
          />
        </Box>
      )}

      {showModal && <RuleEngineModal onClose={() => setShowModal(false)} />}
    </Box>
  );
};

export default Monitoring;
