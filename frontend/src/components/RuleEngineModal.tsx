import { useEffect, useState } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import CircularProgress from '@mui/material/CircularProgress';
import CloseIcon from '@mui/icons-material/Close';
import DeleteIcon from '@mui/icons-material/Delete';
import PowerSettingsNewIcon from '@mui/icons-material/PowerSettingsNew';
import { useAppDispatch, useAppSelector } from '../store';
import {
  fetchRules,
  createRule,
  toggleRule,
  deleteRule,
} from '../store/slices/rulesSlice';
import type { CreateRulePayload } from '../store/slices/rulesSlice';

interface Props {
  onClose: () => void;
}

const METRICS = [
  { value: 'heart_rate', label: 'Frecuencia Cardíaca' },
  { value: 'spo2', label: 'Saturación de O2' },
  { value: 'respiratory_rate', label: 'Frecuencia Respiratoria' },
  { value: 'systolic_bp', label: 'Presión Sistólica' },
];

const OPERATORS = [
  { value: 'GREATER_THAN', label: 'Mayor que (>)' },
  { value: 'LESS_THAN', label: 'Menor que (<)' },
  { value: 'EQUALS', label: 'Igual a (=)' },
];

const SEVERITIES = [
  { value: 'CRITICAL', label: 'CRITICAL (Código Rojo)' },
  { value: 'WARNING', label: 'WARNING (Advertencia)' },
  { value: 'INFO', label: 'INFO (Notificación)' },
];

const operatorLabel = (op: string) =>
  op === 'GREATER_THAN' ? '>' : op === 'LESS_THAN' ? '<' : '=';

const RuleEngineModal = ({ onClose }: Props) => {
  const dispatch = useAppDispatch();
  const { rules, loading } = useAppSelector((s) => s.rules);

  const [form, setForm] = useState<CreateRulePayload & { durationSeconds: number }>({
    name: '',
    description: '',
    metricType: 'heart_rate',
    operator: 'GREATER_THAN',
    threshold: 120,
    severity: 'CRITICAL',
    durationSeconds: 120,
  });
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formError, setFormError] = useState('');

  useEffect(() => {
    void dispatch(fetchRules());
  }, [dispatch]);

  const handleChange = (field: string) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm((prev) => ({ ...prev, [field]: e.target.value }));
    setFormError('');
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.description?.trim()) {
      setFormError('Por favor ingresa una descripción para la regla.');
      return;
    }
    setIsSubmitting(true);
    try {
      await dispatch(
        createRule({
          name: form.description,
          description: form.description,
          metricType: form.metricType,
          operator: form.operator,
          threshold: parseFloat(String(form.threshold)),
          severity: form.severity,
        }),
      ).unwrap();
      setForm((prev) => ({ ...prev, description: '' }));
      void dispatch(fetchRules());
    } catch {
      setFormError('Error al crear la regla.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleToggle = (id: string, currentActive: boolean) => {
    void dispatch(toggleRule({ id, enable: !currentActive }));
  };

  const handleDelete = (id: string) => {
    if (window.confirm('¿Seguro que deseas eliminar esta regla?')) {
      void dispatch(deleteRule(id));
    }
  };

  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle
        sx={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          pb: 1,
        }}
      >
        <Typography variant="h6" sx={{ fontWeight: 700 }}>
          Configuración del Motor de Reglas
        </Typography>
        <IconButton onClick={onClose} size="small">
          <CloseIcon />
        </IconButton>
      </DialogTitle>

      <Divider />

      <DialogContent sx={{ p: 0 }}>
        <Box sx={{ display: 'flex', minHeight: 420 }}>
          {/* Left: rule list */}
          <Box
            sx={{
              flex: 1,
              p: 2.5,
              borderRight: '1px solid',
              borderColor: 'divider',
              overflowY: 'auto',
              maxHeight: 520,
            }}
          >
            <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1.5 }}>
              Reglas Activas en el Sistema
            </Typography>

            {loading ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', pt: 4 }}>
                <CircularProgress size={24} />
              </Box>
            ) : rules.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                No hay reglas configuradas.
              </Typography>
            ) : (
              rules.map((rule) => (
                <Box
                  key={rule.id}
                  sx={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'flex-start',
                    p: 1.5,
                    mb: 1,
                    border: '1px solid',
                    borderColor: 'divider',
                    borderRadius: 2,
                    opacity: rule.active ? 1 : 0.5,
                    bgcolor: 'background.default',
                  }}
                >
                  <Box sx={{ flex: 1, minWidth: 0, mr: 1 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
                      <Typography variant="body2" noWrap sx={{ fontWeight: 600 }}>
                        {rule.description}
                      </Typography>
                      <Chip
                        label={rule.severity}
                        size="small"
                        color={rule.severity === 'CRITICAL' ? 'error' : 'warning'}
                      />
                    </Box>
                    <Typography variant="caption" color="text.secondary">
                      Si <strong>{rule.metricType}</strong>{' '}
                      {operatorLabel(rule.operator)}{' '}
                      <strong>{rule.threshold}</strong>
                    </Typography>
                  </Box>
                  <Box sx={{ display: 'flex', gap: 0.5, flexShrink: 0 }}>
                    <IconButton
                      size="small"
                      title={rule.active ? 'Desactivar' : 'Activar'}
                      onClick={() => handleToggle(rule.id, rule.active)}
                      sx={{ color: rule.active ? 'success.main' : 'text.disabled' }}
                    >
                      <PowerSettingsNewIcon fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      title="Eliminar"
                      onClick={() => handleDelete(rule.id)}
                      sx={{ color: 'error.main' }}
                    >
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Box>
                </Box>
              ))
            )}
          </Box>

          {/* Right: create form */}
          <Box sx={{ flex: 1, p: 2.5 }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1.5 }}>
              Crear Nueva Regla
            </Typography>

            <Box component="form" onSubmit={handleSubmit} sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              <TextField
                label="Descripción"
                placeholder="Ej: Taquicardia Moderada"
                value={form.description}
                onChange={handleChange('description')}
                required
                fullWidth
                error={!!formError}
                helperText={formError}
              />

              <Box sx={{ display: 'flex', gap: 2 }}>
                <TextField
                  label="Métrica Vital"
                  select
                  value={form.metricType}
                  onChange={handleChange('metricType')}
                  fullWidth
                >
                  {METRICS.map((m) => (
                    <MenuItem key={m.value} value={m.value}>
                      {m.label}
                    </MenuItem>
                  ))}
                </TextField>

                <TextField
                  label="Condición"
                  select
                  value={form.operator}
                  onChange={handleChange('operator')}
                  fullWidth
                >
                  {OPERATORS.map((o) => (
                    <MenuItem key={o.value} value={o.value}>
                      {o.label}
                    </MenuItem>
                  ))}
                </TextField>
              </Box>

              <Box sx={{ display: 'flex', gap: 2 }}>
                <TextField
                  label="Umbral (Valor)"
                  type="number"
                  value={form.threshold}
                  onChange={handleChange('threshold')}
                  required
                  fullWidth
                  slotProps={{ htmlInput: { step: '0.1' } }}
                />
                <TextField
                  label="Duración (Segundos)"
                  type="number"
                  value={form.durationSeconds}
                  onChange={handleChange('durationSeconds')}
                  required
                  fullWidth
                />
              </Box>

              <TextField
                label="Severidad / Acción"
                select
                value={form.severity}
                onChange={handleChange('severity')}
                fullWidth
              >
                {SEVERITIES.map((s) => (
                  <MenuItem key={s.value} value={s.value}>
                    {s.label}
                  </MenuItem>
                ))}
              </TextField>

              <Button
                type="submit"
                variant="contained"
                color="primary"
                disabled={isSubmitting}
                fullWidth
                sx={{ mt: 1 }}
              >
                {isSubmitting ? 'Guardando...' : 'Guardar Regla'}
              </Button>
            </Box>
          </Box>
        </Box>
      </DialogContent>

      <DialogActions sx={{ px: 2.5, py: 1.5 }}>
        <Button onClick={onClose} color="inherit">
          Cerrar
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default RuleEngineModal;
