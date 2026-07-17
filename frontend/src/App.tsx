import { useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { useAppDispatch } from './store';
import { initAuth } from './store/slices/authSlice';
import ProtectedRoute from './components/ProtectedRoute';
import AuthenticatedLayout from './components/AuthenticatedLayout';
import PatientDetail from './views/PatientDetail';
import Monitoring from './views/Monitoring';
import Login from './views/Login';
import SsoRedirect from './views/SsoRedirect';

function App() {
  const dispatch = useAppDispatch();

  useEffect(() => {
    dispatch(initAuth());
  }, [dispatch]);

  return (
    <Router>
      <Routes>
        <Route path="/auth/sso" element={<SsoRedirect />} />
        <Route path="/login" element={<Login />} />

        <Route
          path="/paciente/:id"
          element={
            <ProtectedRoute>
              <AuthenticatedLayout>
                <PatientDetail />
              </AuthenticatedLayout>
            </ProtectedRoute>
          }
        />
        <Route
          path="/monitoreo"
          element={
            <ProtectedRoute>
              <AuthenticatedLayout>
                <Monitoring />
              </AuthenticatedLayout>
            </ProtectedRoute>
          }
        />

        <Route path="/" element={<Navigate to="/monitoreo" replace />} />
      </Routes>
    </Router>
  );
}

export default App;
