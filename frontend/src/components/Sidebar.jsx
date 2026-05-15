import React from 'react';
import { NavLink } from 'react-router-dom';
import { Activity, LayoutDashboard, User, FileText, FlaskConical, Pill, Receipt, Users, Stethoscope } from 'lucide-react';
import './Sidebar.css';

const Sidebar = () => {
  return (
    <div className="sidebar">
      <div className="sidebar-brand">
        <Activity className="brand-icon" />
        <h2>HEALTH GRID</h2>
      </div>

      <div className="sidebar-section">
        <p className="sidebar-subtitle">MÓDULOS</p>
        <nav className="sidebar-nav">
          <NavLink to="/historia" className={({isActive}) => isActive ? "nav-link active" : "nav-link"}>
            <FileText size={18} /> Historia Clínica
          </NavLink>
          <NavLink to="/turnos" className={({isActive}) => isActive ? "nav-link active" : "nav-link"}>
            <LayoutDashboard size={18} /> Turnos y Agendas
          </NavLink>
          <NavLink to="/laboratorio" className={({isActive}) => isActive ? "nav-link active" : "nav-link"}>
            <FlaskConical size={18} /> Laboratorio
          </NavLink>
          <NavLink to="/farmacia" className={({isActive}) => isActive ? "nav-link active" : "nav-link"}>
            <Pill size={18} /> Farmacia e Insumos
          </NavLink>
          <NavLink to="/facturacion" className={({isActive}) => isActive ? "nav-link active" : "nav-link"}>
            <Receipt size={18} /> Facturación
          </NavLink>
          <NavLink to="/portal" className={({isActive}) => isActive ? "nav-link active" : "nav-link"}>
            <Users size={18} /> Portal del Paciente
          </NavLink>
          <NavLink to="/monitoreo" className={({isActive}) => isActive ? "nav-link active" : "nav-link"}>
            <Activity size={18} /> Monitoreo
          </NavLink>
          <NavLink to="/core" className={({isActive}) => isActive ? "nav-link active" : "nav-link"}>
            <Stethoscope size={18} /> Core
          </NavLink>
        </nav>
      </div>

      <div className="sidebar-profile">
        <div className="profile-avatar">GM</div>
        <div className="profile-info">
          <div className="profile-name">Gonzalez Maria Elena</div>
          <div className="profile-role">Enfermera</div>
        </div>
      </div>
    </div>
  );
};

export default Sidebar;
