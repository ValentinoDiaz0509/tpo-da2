import { useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Divider from '@mui/material/Divider';
import Avatar from '@mui/material/Avatar';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import AlertCircleIcon from '@mui/icons-material/Error';
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import { useAppDispatch, useAppSelector } from '../store';
import {
  fetchPatientDetail,
  fetchPatientAlerts,
  fetchPatientTelemetry,
  acknowledgeAlertInDetail,
  clearPatientDetail,
} from '../store/slices/patientDetailSlice';
import { wsConnectDetail, wsDisconnect } from '../store/middleware/websocketMiddleware';

const VitalCard = ({
  label,
  value,
  unit,
  color = 'text.primary',
}: {
  label: string;
  value: string | number;
  unit: string;
  color?: string;
}) => (
  <Paper
    elevation={0}
    sx={{ p: 2, border: '1px solid', borderColor: 'divider', borderRadius: 2, textAlign: 'center' }}
  >
    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, display: 'block' }}>
      {label}
    </Typography>
    <Typography variant="h5" sx={{ fontWeight: 700, color, mt: 0.5 }}>
      {value}{' '}
      <Typography component="span" variant="caption" color="text.secondary">
        {unit}
      </Typography>
    </Typography>
  </Paper>
);

const PatientDetail = () => {
  const { id } = useParams<{ id: string }>();
  const dispatch = useAppDispatch();
  const { patient, telemetry, chartData, alerts, loading } = useAppSelector(
    (s) => s.patientDetail,
  );

  useEffect(() => {
    if (!id) return;
    void dispatch(fetchPatientDetail(id));
    void dispatch(fetchPatientAlerts(id));
    void dispatch(fetchPatientTelemetry(id));
    dispatch(wsConnectDetail(id));

    return () => {
      dispatch(wsDisconnect());
      dispatch(clearPatientDetail());
    };
  }, [id, dispatch]);

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '60vh' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!patient) {
    return (
      <Box sx={{ p: 3 }}>
        <Typography variant="h5" sx={{ fontWeight: 700, mb: 1 }}>
          Paciente no encontrado
        </Typography>
        <Typography color="text.secondary" sx={{ mb: 2 }}>
          Es posible que no exista o que el servidor no responda.
        </Typography>
        <Button component={Link} to="/monitoreo" variant="contained" startIcon={<ArrowBackIcon />}>
          Volver al Dashboard
        </Button>
      </Box>
    );
  }

  const hasCriticalAlert = alerts.some((a) => !a.acknowledged && a.severity === 'CRITICAL');
  const spo2Value = typeof telemetry.spo2 === 'number' ? telemetry.spo2 : null;
  const hrValue = typeof telemetry.heartRate === 'number' ? telemetry.heartRate : null;

  return (
    <Box sx={{ p: 3 }}>
      {/* Top navigation bar */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2.5 }}>
        <Button
          component={Link}
          to="/monitoreo"
          startIcon={<ArrowBackIcon />}
          sx={{ color: 'text.secondary' }}
        >
          Volver al Dashboard
        </Button>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          {hasCriticalAlert && <Chip label="Alerta Crítica" color="error" size="small" />}
          <Typography variant="body2" color="text.secondary">
            Habitación {patient.roomNumber}, Cama {patient.bedNumber}
          </Typography>
        </Box>
      </Box>

      {/* Patient header card */}
      <Paper
        elevation={0}
        sx={{
          p: 2.5,
          mb: 2.5,
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 2,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          gap: 2,
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <Avatar sx={{ width: 56, height: 56, bgcolor: 'primary.main', fontSize: '1.25rem', fontWeight: 700 }}>
            {patient.firstName?.[0]}{patient.lastName?.[0]}
          </Avatar>
          <Box>
            <Typography variant="h5" sx={{ fontWeight: 700, lineHeight: 1.1 }}>
              {patient.lastName},<br />
              {patient.firstName}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              ID: {patient.id.substring(0, 8)}...
            </Typography>
          </Box>
        </Box>
        <Button variant="contained" color="error" size="small" sx={{ whiteSpace: 'nowrap' }}>
          Solicitar Intervención de Emergencia
        </Button>
      </Paper>

      <Grid container spacing={2.5} sx={{ mb: 2.5 }}>
        {/* Medical profile */}
        <Grid size={{ xs: 12, md: 5 }}>
          <Paper
            elevation={0}
            sx={{ p: 2.5, border: '1px solid', borderColor: 'divider', borderRadius: 2, height: '100%' }}
          >
            <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 2 }}>
              Perfil Médico
            </Typography>
            <Typography variant="overline" color="text.secondary">
              Resumen de Historia
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2.5 }}>
              Condición médica en evaluación (Integración con HCE pendiente)
            </Typography>
            <Divider sx={{ mb: 2 }} />
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
              <Avatar sx={{ bgcolor: 'primary.light', width: 40, height: 40 }} />
              <Box sx={{ flex: 1 }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, display: 'block' }}>
                  MÉDICO A CARGO
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  Dr. Asignado
                </Typography>
              </Box>
              <Button variant="outlined" size="small">
                Llamar Médico
              </Button>
            </Box>
          </Paper>
        </Grid>

        {/* Alert history */}
        <Grid size={{ xs: 12, md: 7 }}>
          <Paper
            elevation={0}
            sx={{ p: 2.5, border: '1px solid', borderColor: 'divider', borderRadius: 2, height: '100%' }}
          >
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
              <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                Historial de Alertas
              </Typography>
              <Typography variant="caption" color="primary.main" sx={{ cursor: 'pointer' }}>
                Ver Todo
              </Typography>
            </Box>

            {alerts.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                No hay alertas registradas para este paciente.
              </Typography>
            ) : (
              alerts.slice(0, 4).map((alert) => (
                <Box
                  key={alert.id}
                  sx={{
                    display: 'flex',
                    gap: 1.5,
                    py: 1.25,
                    borderBottom: '1px solid',
                    borderColor: 'divider',
                    '&:last-child': { borderBottom: 'none' },
                    opacity: alert.acknowledged ? 0.55 : 1,
                  }}
                >
                  <AlertCircleIcon
                    fontSize="small"
                    sx={{
                      mt: 0.25,
                      color: alert.severity === 'CRITICAL' ? 'error.main' : 'warning.main',
                      flexShrink: 0,
                    }}
                  />
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {alert.rule ? alert.rule.description : alert.severity}
                      {alert.acknowledged && ' (Atendida)'}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
                      {alert.message}
                    </Typography>
                    <Typography variant="caption" color="text.disabled">
                      {new Date(alert.createdAt).toLocaleTimeString()}
                    </Typography>
                  </Box>
                  {!alert.acknowledged && (
                    <Button
                      variant="outlined"
                      size="small"
                      sx={{ fontSize: '0.7rem', py: 0.25, px: 1, height: 'fit-content', alignSelf: 'center', flexShrink: 0 }}
                      onClick={() => dispatch(acknowledgeAlertInDetail({ alertId: alert.id }))}
                    >
                      Visto
                    </Button>
                  )}
                </Box>
              ))
            )}
          </Paper>
        </Grid>
      </Grid>

      {/* Vitals dashboard */}
      <Grid container spacing={2.5}>
        {/* Heart rate chart */}
        <Grid size={{ xs: 12, md: 8 }}>
          <Paper
            elevation={0}
            sx={{
              p: 2.5,
              bgcolor: '#1f2937',
              color: '#fff',
              borderRadius: 2,
              border: '1px solid',
              borderColor: 'divider',
            }}
          >
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 0.5 }}>
              <Typography variant="caption" sx={{ fontWeight: 700, opacity: 0.7 }}>
                FRECUENCIA CARDÍACA (LPM)
              </Typography>
              {hrValue !== null && hrValue > 120 && (
                <Chip label="TAQUICARDIA" color="error" size="small" />
              )}
            </Box>
            <Typography variant="h3" sx={{ fontWeight: 700, mb: 1 }}>
              {telemetry.heartRate}{' '}
              <Typography component="span" variant="caption" sx={{ opacity: 0.7 }}>
                LPM
              </Typography>
            </Typography>
            <ResponsiveContainer width="100%" height={120}>
              <AreaChart data={chartData}>
                <defs>
                  <linearGradient id="colorHR" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#22c55e" stopOpacity={0.8} />
                    <stop offset="95%" stopColor="#22c55e" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <XAxis dataKey="time" stroke="#666" tick={{ fill: '#666', fontSize: 10 }} />
                <YAxis hide />
                <Tooltip contentStyle={{ backgroundColor: '#111827', border: 'none', color: '#fff' }} />
                <Area
                  type="monotone"
                  dataKey="value"
                  stroke="#22c55e"
                  fillOpacity={1}
                  fill="url(#colorHR)"
                  isAnimationActive={false}
                />
              </AreaChart>
            </ResponsiveContainer>
          </Paper>
        </Grid>

        {/* Side vitals */}
        <Grid size={{ xs: 12, md: 4 }}>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, height: '100%' }}>
            <VitalCard
              label="SPO2 ACTUAL"
              value={telemetry.spo2}
              unit="%"
              color={spo2Value !== null && spo2Value < 92 ? 'error.main' : 'text.primary'}
            />
            <VitalCard
              label="FREC. RESP (RPM)"
              value={telemetry.respiratoryRate}
              unit="RPM"
              color="warning.main"
            />
            <VitalCard
              label="PRESIÓN ARTERIAL"
              value={telemetry.bloodPressure}
              unit="mmHg"
              color="info.main"
            />
          </Box>
        </Grid>
      </Grid>
    </Box>
  );
};

export default PatientDetail;
