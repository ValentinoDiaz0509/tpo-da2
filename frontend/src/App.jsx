import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import Header from './components/Header';
import PatientDetail from './views/PatientDetail';
import Monitoring from './views/Monitoring';
import Login from './views/Login';
import { AuthProvider, useAuth } from './context/AuthContext';

// Componente para proteger rutas privadas
const ProtectedRoute = ({ children }) => {
  const { isAuthenticated } = useAuth();
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return children;
};

// Layout para páginas autenticadas (con Sidebar y Header)
const AuthenticatedLayout = ({ children }) => {
  return (
    <div className="app-container">
      <Sidebar />
      <div className="main-content">
        <Header />
        {children}
      </div>
    </div>
  );
};

function App() {
  return (
    <AuthProvider>
      <Router>
        <Routes>
          {/* Ruta pública */}
          <Route path="/login" element={<Login />} />

          {/* Rutas privadas */}
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
          
          {/* Default redirect */}
          <Route path="/" element={<Navigate to="/monitoreo" replace />} />
        </Routes>
      </Router>
    </AuthProvider>
  );
}

export default App;
