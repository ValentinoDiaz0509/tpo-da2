import React, { useState, useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { Search, Bell, AlertTriangle } from 'lucide-react';
import { getUnacknowledgedCriticalAlerts } from '../services/api';
import { useAuth } from '../context/AuthContext';
import './Header.css';

const Header = () => {
  const { user, logout } = useAuth();
  const [showNotifications, setShowNotifications] = useState(false);
  const [alerts, setAlerts] = useState([]);
  const dropdownRef = useRef(null);

  const fetchAlerts = async () => {
    const data = await getUnacknowledgedCriticalAlerts();
    setAlerts(data);
  };

  useEffect(() => {
    fetchAlerts();
    // Poll every 10 seconds for new critical alerts in the header
    const interval = setInterval(fetchAlerts, 10000);
    return () => clearInterval(interval);
  }, []);

  // Close dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setShowNotifications(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [dropdownRef]);

  return (
    <div className="header">
      <div className="header-search">
        <Search className="search-icon" size={18} />
        <input type="text" placeholder="Buscar pacientes..." className="search-input" />
      </div>
      <div className="header-actions" ref={dropdownRef}>
        
        {user && (
          <div className="user-profile">
            <span className="user-name">Hola, {user.first_name}</span>
            <button className="logout-btn" onClick={logout}>Salir</button>
          </div>
        )}

        <button 
          className="notification-btn" 
          onClick={() => {
            setShowNotifications(!showNotifications);
            if (!showNotifications) fetchAlerts();
          }}
        >
          <Bell size={20} />
          {alerts.length > 0 && <span className="notification-dot"></span>}
        </button>

        {showNotifications && (
          <div className="notifications-dropdown">
            <div className="notifications-header">
              <h3>Notificaciones</h3>
              <span className="badge badge-red">{alerts.length} Críticas</span>
            </div>
            <div className="notifications-list">
              {alerts.length === 0 ? (
                <div className="notification-empty">
                  No hay notificaciones nuevas.
                </div>
              ) : (
                alerts.map(alert => (
                  <Link 
                    to={`/paciente/${alert.patient.id}`} 
                    key={alert.id} 
                    className="notification-item"
                    onClick={() => setShowNotifications(false)}
                  >
                    <div className="notification-icon"><AlertTriangle size={16} className="text-red" /></div>
                    <div className="notification-content">
                      <h4>{alert.message || "Alerta Crítica"}</h4>
                      <p>Paciente ID: {alert.patient.id.substring(0,8)}</p>
                      <span>{new Date(alert.createdAt).toLocaleTimeString()}</span>
                    </div>
                  </Link>
                ))
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default Header;
