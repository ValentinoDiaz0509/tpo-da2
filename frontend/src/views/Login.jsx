import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Mail, Lock, Activity } from 'lucide-react';
import './Login.css';

const Login = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);

    const result = await login(email, password);
    
    if (result.success) {
      navigate('/monitoreo');
    } else {
      setError(result.error || 'Error al iniciar sesión');
    }
    
    setIsLoading(false);
  };

  return (
    <div className="login-container">
      <div className="login-left">
        <div className="login-branding">
          <h1>Health<br/>Grid <Activity size={36} className="logo-icon" /></h1>
          <div className="branding-divider"></div>
          <p>SISTEMA DE CLINICA</p>
        </div>
      </div>
      <div className="login-right">
        <div className="login-card">
          <h2>Acceso al Sistema</h2>
          <p className="login-subtitle">Ingrese sus credenciales para continuar</p>
          
          {error && <div className="login-error">{error}</div>}

          <form onSubmit={handleSubmit}>
            <div className="input-group">
              <label>CORREO ELECTRÓNICO</label>
              <div className="input-wrapper">
                <Mail className="input-icon" size={18} />
                <input
                  type="email"
                  placeholder="ejemplo@healthgrid.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
              </div>
            </div>

            <div className="input-group">
              <label>CONTRASEÑA</label>
              <div className="input-wrapper">
                <Lock className="input-icon" size={18} />
                <input
                  type="password"
                  placeholder="••••••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
              </div>
            </div>

            <div className="forgot-password">
              <a href="#">¿Olvidaste tu contraseña?</a>
            </div>

            <button type="submit" className="login-button" disabled={isLoading}>
              {isLoading ? 'Iniciando...' : 'Iniciar Sesión'}
            </button>

            <div className="register-link">
              <span>¿No tenés cuenta? </span>
              <a href="#">Registrate</a>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
};

export default Login;
