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
import MonitorHeartIcon from '@mui/icons-material/MonitorHeart';
import SettingsIcon from '@mui/icons-material/Settings';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import RuleEngineModal from '../components/RuleEngineModal';
import { useAppDispatch, useAppSelector } from '../store';
import { fetchPatients, acknowledgeAlert } from '../store/slices/patientsSlice';
import { wsConnectMonitoring, wsDisconnect } from '../store/middleware/websocketMiddleware';

const Monitoring = () => {
  const dispatch = useAppDispatch();
  const { patients, loading, ackLoading } = useAppSelector((s) => s.patients);
  const [showModal, setShowModal] = useState(false);

  useEffect(() => {
    void dispatch(fetchPatients()).then((result) => {
      if (fetchPatients.fulfilled.match(result)) {
        const ids = result.payload.map((p) => p.patient_id);
        dispatch(wsConnectMonitoring(ids));
      }
    });
    return () => {
      dispatch(wsDisconnect());
    };
  }, [dispatch]);

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

  return (
    <Box sx={{ p: 3 }}>
      {/* Page header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>
            Monitoreo
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Telemetría en tiempo real y monitoreo basado en reglas para 12 camas de pacientes activas.
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
            <Typography variant="h6" sx={{ fontWeight: 700 }}>
              12/14
            </Typography>
          </Paper>
          <Paper
            elevation={0}
            sx={{
              px: 2,
              py: 1,
              bgcolor: 'primary.dark',
              borderRadius: 2,
              color: '#fff',
            }}
          >
            <Typography variant="caption" sx={{ display: 'block', fontWeight: 700, opacity: 0.8 }}>
              MOTOR DE REGLAS
            </Typography>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
              <MonitorHeartIcon fontSize="small" />
              <Typography variant="body2" sx={{ fontWeight: 700 }}>
                Activo
              </Typography>
            </Box>
          </Paper>
        </Box>
      </Box>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        {/* Critical alert banner */}
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
                        dispatch(acknowledgeAlert({ alertId: currentCriticalAlert.id }))
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
                    {new Date(currentCriticalAlert.createdAt ?? Date.now()).toLocaleTimeString()}
                  </Typography>
                </Box>
              </Box>
            </Paper>
          </Grid>
        )}

        {/* Rule engine card */}
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
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 2 }}>
              Última actualización de reglas: hace 2 minutos.
            </Typography>
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

      {/* Patient grid */}
      <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
        Telemetría de Pacientes en Vivo
      </Typography>

      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', pt: 6 }}>
          <CircularProgress />
        </Box>
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

      {showModal && <RuleEngineModal onClose={() => setShowModal(false)} />}
    </Box>
  );
};

export default Monitoring;
