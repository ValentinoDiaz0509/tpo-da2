import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';

export interface VitalCardProps {
  label: string;
  value: string;
  unit: string;
  color?: string;
}

const VitalCard = ({
  label,
  value,
  unit,
  color = 'text.primary',
}: VitalCardProps) => (
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

export default VitalCard;
