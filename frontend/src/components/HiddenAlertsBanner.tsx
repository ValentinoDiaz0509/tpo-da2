import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import type { HiddenAlertsSummary } from '../store/slices/patientsSlice';

interface Props {
  summary: HiddenAlertsSummary;
  onViewCritical: () => void;
}

const HiddenAlertsBanner = ({ summary, onViewCritical }: Props) => {
  const { critical_count: criticalCount, warning_count: warningCount } = summary;

  if (criticalCount === 0 && warningCount === 0) {
    return null;
  }

  const parts: string[] = [];
  if (criticalCount > 0) {
    parts.push(
      `${criticalCount} paciente${criticalCount === 1 ? '' : 's'} crítico${criticalCount === 1 ? '' : 's'}`,
    );
  }
  if (warningCount > 0) {
    parts.push(
      `${warningCount} con advertencia${warningCount === 1 ? '' : 's'}`,
    );
  }

  return (
    <Paper
      elevation={0}
      sx={{
        p: 2,
        mb: 2,
        border: '1px solid',
        borderColor: criticalCount > 0 ? 'error.light' : 'warning.light',
        bgcolor: criticalCount > 0 ? 'error.50' : 'warning.50',
        borderRadius: 2,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 2,
        flexWrap: 'wrap',
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
        <WarningAmberIcon color={criticalCount > 0 ? 'error' : 'warning'} />
        <Typography variant="body2">
          Hay <strong>{parts.join(' y ')}</strong> fuera de esta página.
        </Typography>
      </Box>
      <Button
        variant="outlined"
        size="small"
        color={criticalCount > 0 ? 'error' : 'warning'}
        onClick={onViewCritical}
      >
        Ver críticos
      </Button>
    </Paper>
  );
};

export default HiddenAlertsBanner;
