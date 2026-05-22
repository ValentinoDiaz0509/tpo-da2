import Box from '@mui/material/Box';
import Sidebar from './Sidebar';
import Header from './Header';

const SIDEBAR_WIDTH = 250;
const HEADER_HEIGHT = 70;

interface Props {
  children: React.ReactNode;
}

const AuthenticatedLayout = ({ children }: Props) => {
  return (
    <Box sx={{ display: 'flex', height: '100vh', width: '100vw', overflow: 'hidden' }}>
      <Sidebar width={SIDEBAR_WIDTH} />
      <Box
        component="main"
        sx={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
          minWidth: 0,
        }}
      >
        <Header height={HEADER_HEIGHT} />
        <Box sx={{ flex: 1, overflowY: 'auto', bgcolor: 'background.default' }}>
          {children}
        </Box>
      </Box>
    </Box>
  );
};

export default AuthenticatedLayout;
