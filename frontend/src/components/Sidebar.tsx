import { NavLink } from 'react-router-dom';
import Box from '@mui/material/Box';
import Drawer from '@mui/material/Drawer';
import Typography from '@mui/material/Typography';
import Avatar from '@mui/material/Avatar';
import MonitorHeartIcon from '@mui/icons-material/MonitorHeart';
import FolderSharedIcon from '@mui/icons-material/FolderShared';
import CalendarMonthIcon from '@mui/icons-material/CalendarMonth';
import ScienceIcon from '@mui/icons-material/Science';
import LocalPharmacyIcon from '@mui/icons-material/LocalPharmacy';
import ReceiptIcon from '@mui/icons-material/Receipt';
import GroupIcon from '@mui/icons-material/Group';
import MedicalServicesIcon from '@mui/icons-material/MedicalServices';
import { useAppSelector } from '../store';

const SIDEBAR_BG = '#0f3c2b';
const SIDEBAR_ACTIVE = '#17523d';
const TEXT_MUTED = '#8aa99c';

const navItems = [
  { to: '/historia', icon: <FolderSharedIcon fontSize="small" />, label: 'Historia Clínica' },
  { to: '/turnos', icon: <CalendarMonthIcon fontSize="small" />, label: 'Turnos y Agendas' },
  { to: '/laboratorio', icon: <ScienceIcon fontSize="small" />, label: 'Laboratorio' },
  { to: '/farmacia', icon: <LocalPharmacyIcon fontSize="small" />, label: 'Farmacia e Insumos' },
  { to: '/facturacion', icon: <ReceiptIcon fontSize="small" />, label: 'Facturación' },
  { to: '/portal', icon: <GroupIcon fontSize="small" />, label: 'Portal del Paciente' },
  { to: '/monitoreo', icon: <MonitorHeartIcon fontSize="small" />, label: 'Monitoreo' },
  { to: '/core', icon: <MedicalServicesIcon fontSize="small" />, label: 'Core' },
];

interface Props {
  width: number;
}

const Sidebar = ({ width }: Props) => {
  const user = useAppSelector((s) => s.auth.user);

  const initials = user
    ? `${user.first_name[0] ?? ''}${user.last_name[0] ?? ''}`.toUpperCase()
    : 'HG';

  const displayName = user ? `${user.first_name} ${user.last_name}` : 'Health Grid';
  const role = user?.roles[0]?.name ?? 'Usuario';

  return (
    <Drawer
      variant="permanent"
      sx={{
        width,
        flexShrink: 0,
        '& .MuiDrawer-paper': {
          width,
          boxSizing: 'border-box',
          bgcolor: SIDEBAR_BG,
          color: '#fff',
          border: 'none',
          display: 'flex',
          flexDirection: 'column',
        },
      }}
    >
      {/* Brand */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, px: 2.5, py: 2.5 }}>
        <MonitorHeartIcon sx={{ color: '#fff', fontSize: 28 }} />
        <Typography variant="h6" sx={{ fontWeight: 700, letterSpacing: 1, color: '#fff' }}>
          HEALTH GRID
        </Typography>
      </Box>

      {/* Nav */}
      <Box sx={{ flex: 1, px: 1.5, overflowY: 'auto' }}>
        <Typography
          variant="caption"
          sx={{ color: TEXT_MUTED, fontWeight: 700, letterSpacing: 1.5, px: 1, display: 'block', mb: 0.5 }}
        >
          MÓDULOS
        </Typography>

        {navItems.map(({ to, icon, label }) => (
          <NavLink key={to} to={to} style={{ textDecoration: 'none' }}>
            {({ isActive }) => (
              <Box
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1.5,
                  px: 1.5,
                  py: 1,
                  borderRadius: 1,
                  mb: 0.25,
                  cursor: 'pointer',
                  bgcolor: isActive ? SIDEBAR_ACTIVE : 'transparent',
                  color: isActive ? '#fff' : TEXT_MUTED,
                  fontWeight: isActive ? 600 : 400,
                  fontSize: '0.875rem',
                  transition: 'background-color 0.15s, color 0.15s',
                  '&:hover': {
                    bgcolor: SIDEBAR_ACTIVE,
                    color: '#fff',
                  },
                }}
              >
                {icon}
                {label}
              </Box>
            )}
          </NavLink>
        ))}
      </Box>

      {/* User profile */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, px: 2.5, py: 2, borderTop: `1px solid rgba(255,255,255,0.1)` }}>
        <Avatar sx={{ bgcolor: '#156d4e', width: 36, height: 36, fontSize: '0.8rem', fontWeight: 700 }}>
          {initials}
        </Avatar>
        <Box>
          <Typography variant="body2" sx={{ color: '#fff', fontWeight: 600, lineHeight: 1.2 }}>
            {displayName}
          </Typography>
          <Typography variant="caption" sx={{ color: TEXT_MUTED }}>
            {role}
          </Typography>
        </Box>
      </Box>
    </Drawer>
  );
};

export default Sidebar;
