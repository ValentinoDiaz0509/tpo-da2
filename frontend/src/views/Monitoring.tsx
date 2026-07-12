import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Divider from '@mui/material/Divider';
import Pagination from '@mui/material/Pagination';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
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
  setPatientSort,
  PATIENT_SORT_OPTIONS,
} from '../store/slices/patientsSlice';
import { wsConnectMonitoring, wsDisconnect } from '../store/middleware/websocketMiddleware';

const DATA_REFRESH_INTERVAL_MS = 5000;

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

  const criticalAlerts = patients.flatMap((p) =>
    (p.active_alerts ?? [])
      .filter((a) => a.severity === 'CRITICAL')
      .map((a) => ({
        ...a,
        patient_name: p.patient_name,
        room: p.room,
        bed: p.bed,
        patient_id: p.patient_id,
      })),
  );
  const currentCriticalAlert = criticalAlerts[0] ?? null;

  const handlePageChange = (_event: React.ChangeEvent<unknown>, page: number) => {
    dispatch(setPatientPage(page - 1));
  };

  const handleViewCritical = () => {
    dispatch(resetPatientFilters());
  };

  return (
    <Box sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>
            Monitoreo
          </Typography>
        </Box>
        <Box sx={{ display: 'flex', gap: 1.5 }}>
          <Paper
            elevation={0}
            sx={{ px: 2, py: 1, border: '1px solid', borderColor: 'divider', borderRadius: 2 }}
          >
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', fontWeight: 700 }}>
              PACIENTES ACTIVOS
            </Typography>
            <Typography variant="h6" sx={{ fontWeight: 700, textAlign: 'center' }}>
              {pagination.totalElements}
            </Typography>
          </Paper>
        </Box>
      </Box>

      <HiddenAlertsBanner summary={hiddenAlertsSummary} onViewCritical={handleViewCritical} />

      <Grid container spacing={2} sx={{ mb: 3 }}>
        {currentCriticalAlert && (
          <Grid size={12}>
            <Paper
              elevation={0}
              sx={{
                p: 2.5,
                bgcolor: '#7f1d1d',
                color: '#fff',
                borderRadius: 2,
                border: '1px solid #991b1b',
              }}
            >
              <Chip
                icon={<WarningAmberIcon sx={{ color: '#fff !important', fontSize: '14px !important' }} />}
                label="ALERTA CÓDIGO ROJO"
                size="small"
                sx={{
                  bgcolor: 'rgba(255,255,255,0.15)',
                  color: '#fff',
                  fontWeight: 700,
                  mb: 1.5,
                  fontSize: '0.7rem',
                }}
              />
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 2 }}>
                <Box sx={{ flex: 1 }}>
                  <Typography variant="h6" sx={{ fontWeight: 700, mb: 0.5 }}>
                    {currentCriticalAlert.message || 'Taquicardia Crítica'}
                  </Typography>
                  <Typography variant="body2" sx={{ opacity: 0.85 }}>
                    Paciente:{' '}
                    <strong>
                      {currentCriticalAlert.patient_name} (Hab {currentCriticalAlert.room} Cama{' '}
                      {currentCriticalAlert.bed})
                    </strong>
                    . Se requiere intervención inmediata.
                  </Typography>
                  <Box sx={{ display: 'flex', gap: 1.5, mt: 2 }}>
                    <Button
                      variant="outlined"
                      size="small"
                      disabled={ackLoading}
                      onClick={() =>
                        dispatch(acknowledgeAlert({ alertId: currentCriticalAlert.alert_id }))
                      }
                      sx={{ color: '#fff', borderColor: 'rgba(255,255,255,0.5)', '&:hover': { borderColor: '#fff', bgcolor: 'rgba(255,255,255,0.1)' } }}
                    >
                      {ackLoading ? 'Procesando...' : 'Reconocer y Atender'}
                    </Button>
                    <Button
                      component={Link}
                      to={`/paciente/${currentCriticalAlert.patient_id}`}
                      variant="outlined"
                      size="small"
                      sx={{ color: '#fff', borderColor: 'rgba(255,255,255,0.5)', '&:hover': { borderColor: '#fff', bgcolor: 'rgba(255,255,255,0.1)' } }}
                    >
                      Ver Paciente
                    </Button>
                  </Box>
                </Box>
                <Box sx={{ textAlign: 'right', flexShrink: 0 }}>
                  <Typography variant="caption" sx={{ opacity: 0.7, display: 'block' }}>
                    HORA DE ALERTA
                  </Typography>
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>
                    {new Date(currentCriticalAlert.triggered_at ?? Date.now()).toLocaleTimeString()}
                  </Typography>
                </Box>
              </Box>
            </Paper>
          </Grid>
        )}

        <Grid size={{ xs: 12, md: 4 }}>
          <Paper elevation={0} sx={{ p: 2.5, border: '1px solid', borderColor: 'divider', borderRadius: 2, height: '100%' }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
              <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                Motor de Reglas
              </Typography>
              <Button
                size="small"
                variant="outlined"
                startIcon={<SettingsIcon fontSize="small" />}
                onClick={() => setShowModal(true)}
                sx={{ fontSize: '0.75rem' }}
              >
                Configurar
              </Button>
            </Box>
            <Divider sx={{ mb: 1.5 }} />
            {[
              { label: 'Monitoreo de Varianza de FC', color: 'success' as const, status: 'ÓPTIMO' },
              { label: 'Protocolo de Taquicardia', color: 'error' as const, status: 'ACTIVADO' },
              { label: 'Umbral de Desaturación de O2', color: 'warning' as const, status: 'ADVERTENCIA' },
            ].map((r) => (
              <Box
                key={r.label}
                sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', py: 0.75 }}
              >
                <Typography variant="body2">{r.label}</Typography>
                <Chip label={r.status} color={r.color} size="small" />
              </Box>
            ))}
          </Paper>
        </Grid>
      </Grid>

      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2, gap: 2, flexWrap: 'wrap' }}>
        <Typography variant="h6" sx={{ fontWeight: 700 }}>
          Telemetría de Pacientes en Vivo
        </Typography>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
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
                      <Box sx={{ minWidth: 0 }}>
                        <Typography variant="body2" noWrap sx={{ fontWeight: 600 }}>
                          {p.room} {p.bed} — {p.patient_name}
                        </Typography>
                        {p.active_alerts && p.active_alerts.length > 0 && (
                          <Typography variant="caption" color="error.main" noWrap>
                            {p.active_alerts[0].message}
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
