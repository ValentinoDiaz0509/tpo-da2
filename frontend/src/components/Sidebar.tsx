import { useLocation } from 'react-router-dom';
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
const CORE_API_URL = import.meta.env.VITE_CORE_API_URL ?? 'https://api.healthcare.cantero.ar';

const navItems = [
  {
    href: 'https://healthgrid-hce-frontend-olive.vercel.app',
    ssoPath: '/auth/sso',
    icon: <FolderSharedIcon fontSize="small" />,
    label: 'Historia Clinica',
  },
  {
    href: 'https://turnos.solefrancisco.com',
    ssoPath: '/auth/sso',
    icon: <CalendarMonthIcon fontSize="small" />,
    label: 'Turnos y Agendas',
  },
  {
    href: 'https://front-modulo3-farmacia.vercel.app',
    icon: <LocalPharmacyIcon fontSize="small" />,
    label: 'Farmacia e Insumos',
  },
  {
    href: 'https://modulo-laboratorio.up.railway.app',
    ssoPath: '/auth/sso',
    icon: <ScienceIcon fontSize="small" />,
    label: 'Laboratorio',
  },
  {
    href: 'https://uade-da-2-frontend.vercel.app',
    ssoPath: '/auth/sso',
    redirect: '/MENU',
    icon: <ReceiptIcon fontSize="small" />,
    label: 'Imagenes',
  },
  {
    href: 'https://internaciones-y-camas.vercel.app',
    ssoPath: '/auth/sso',
    icon: <MedicalServicesIcon fontSize="small" />,
    label: 'Internaciones y Camas',
  },
  {
    href: 'https://modulo7-frontend.onrender.com',
    ssoPath: '/auth/sso',
    icon: <GroupIcon fontSize="small" />,
    label: 'Facturacion',
  },
  {
    href: 'https://da2frontend.onrender.com',
    ssoPath: '/auth/sso',
    icon: <GroupIcon fontSize="small" />,
    label: 'Paciente',
  },
  {
    href: 'https://dzp5goz8czibt.cloudfront.net',
    ssoPath: '/auth/sso',
    redirect: '/login',
    icon: <MonitorHeartIcon fontSize="small" />,
    label: 'Monitoreo',
    activePath: '/monitoreo',
  },
  {
    href: 'https://healthgrid.cantero.ar',
    ssoPath: '/auth/sso',
    icon: <MedicalServicesIcon fontSize="small" />,
    label: 'Core',
  },
];

type NavItem = (typeof navItems)[number];

interface SsoTicketResponse {
  ticket: string;
}

const fetchSsoTicket = async (token: string): Promise<string> => {
  const response = await fetch(`${CORE_API_URL}/auth/sso-ticket`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    throw new Error(`SSO ticket failed: ${response.status}`);
  }

  const data = (await response.json()) as SsoTicketResponse;
  if (!data.ticket) {
    throw new Error('Core did not return an SSO ticket');
  }

  return data.ticket;
};

const buildSsoUrl = (item: NavItem, ticket: string): string => {
  if (!('ssoPath' in item) || !item.ssoPath) {
    return item.href;
  }

  const url = new URL(item.ssoPath, item.href);
  url.searchParams.set('ticket', ticket);

  if ('redirect' in item && item.redirect) {
    url.searchParams.set('redirect', item.redirect);
  }

  return url.toString();
};

interface Props {
  width: number;
}

const Sidebar = ({ width }: Props) => {
  const location = useLocation();
  const user = useAppSelector((s) => s.auth.user);
  const token = useAppSelector((s) => s.auth.token);

  const initials = user
    ? `${user.first_name[0] ?? ''}${user.last_name[0] ?? ''}`.toUpperCase()
    : 'HG';

  const displayName = user ? `${user.first_name} ${user.last_name}` : 'Health Grid';
  const role = user?.roles[0]?.name ?? 'Usuario';

  const navigateToModule = async (item: NavItem) => {
    if (!('ssoPath' in item) || !item.ssoPath || !token) {
      window.location.assign(item.href);
      return;
    }

    try {
      const ticket = await fetchSsoTicket(token);
      window.location.assign(buildSsoUrl(item, ticket));
    } catch (error) {
      window.alert((error as Error).message);
    }
  };

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
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, px: 2.5, py: 2.5 }}>
        <MonitorHeartIcon sx={{ color: '#fff', fontSize: 28 }} />
        <Typography variant="h6" sx={{ fontWeight: 700, letterSpacing: 1, color: '#fff' }}>
          HEALTH GRID
        </Typography>
      </Box>

      <Box sx={{ flex: 1, px: 1.5, overflowY: 'auto' }}>
        <Typography
          variant="caption"
          sx={{ color: TEXT_MUTED, fontWeight: 700, letterSpacing: 1.5, px: 1, display: 'block', mb: 0.5 }}
        >
          MODULOS
        </Typography>

        {navItems.map((item) => {
          const { href, icon, label, activePath } = item;
          const isActive = Boolean(activePath && location.pathname.startsWith(activePath));

          return (
            <Box
              key={href}
              component="a"
              href={href}
              onClick={(event) => {
                event.preventDefault();
                void navigateToModule(item);
              }}
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
                textDecoration: 'none',
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
          );
        })}
      </Box>

      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, px: 2.5, py: 2, borderTop: '1px solid rgba(255,255,255,0.1)' }}>
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
