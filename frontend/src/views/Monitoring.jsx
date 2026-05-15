import React, { useState, useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { Activity, Settings, AlertTriangle, TrendingUp, TrendingDown } from 'lucide-react';
import './Monitoring.css';
import RuleEngineModal from '../components/RuleEngineModal';
import { getPatients, acknowledgeAlert } from '../services/api';
import { WebSocketService } from '../services/websocket';

const Monitoring = () => {
  const [showModal, setShowModal] = useState(false);
  const [patients, setPatients] = useState([]);
  const [loading, setLoading] = useState(true);
  const [ackLoading, setAckLoading] = useState(false);
  const wsService = useRef(null);

  useEffect(() => {
    // 1. Fetch initial patients
    getPatients().then((data) => {
      setPatients(data);
      setLoading(false);
      
      // 2. Connect to WebSocket
      wsService.current = new WebSocketService(() => {
        // On Connect, subscribe to each patient
        data.forEach(p => {
          wsService.current.subscribeToPatient(p.patient_id, (update) => {
            setPatients(prev => prev.map(patient => {
              if (patient.patient_id === update.patient_id) {
                // Update specific metrics, this requires merging logic depending on payload
                return {
                  ...patient,
                  latest_metrics: {
                    ...patient.latest_metrics,
                    heart_rate: { ...patient.latest_metrics?.heart_rate, value: update.heart_rate },
                    spo2: { ...patient.latest_metrics?.spo2, value: update.sp_o2 },
                    systolic_pressure: { ...patient.latest_metrics?.systolic_pressure, value: update.systolic_pressure },
                    diastolic_pressure: { ...patient.latest_metrics?.diastolic_pressure, value: update.diastolic_pressure },
                  }
                };
              }
              return patient;
            }));
          });
        });
      });
      wsService.current.connect();
    });

    return () => {
      if (wsService.current) wsService.current.disconnect();
    };
  }, []);

  const handleAcknowledge = async (alertId) => {
    setAckLoading(true);
    const success = await acknowledgeAlert(alertId, "Enfermería Central");
    if (success) {
      // Optimistic update: remove the alert from the patient's active_alerts
      setPatients(prev => prev.map(p => ({
          ...p,
          active_alerts: (p.active_alerts || []).filter(a => a.id !== alertId)
      })));
    }
    setAckLoading(false);
  };

  // Derive critical alerts from current patients state
  const criticalAlerts = patients.flatMap(p => 
    (p.active_alerts || [])
      .filter(a => a.severity === 'CRITICAL')
      .map(a => ({ ...a, patient_name: p.patient_name, room: p.room, bed: p.bed, patient_id: p.patient_id }))
  );
  
  const currentCriticalAlert = criticalAlerts.length > 0 ? criticalAlerts[0] : null;

  return (
    <div className="page-container monitoring-view">
      <div className="monitoring-header">
        <div>
          <h1>Monitoreo</h1>
          <p>Telemetría en tiempo real y monitoreo basado en reglas para 12 camas de pacientes activas.</p>
        </div>
        <div className="monitoring-stats">
          <div className="stat-box">
            <span className="stat-label">PACIENTES ACTIVOS</span>
            <span className="stat-val">12/14</span>
          </div>
          <div className="stat-box dark">
            <span className="stat-label">MOTOR DE REGLAS</span>
            <span className="stat-val"><Activity size={16}/> Activo</span>
          </div>
        </div>
      </div>

      <div className="monitoring-dash-grid">
        {currentCriticalAlert && (
        <div className="card critical-alert-banner">
          <div className="alert-badge"><AlertTriangle size={14}/> ALERTA CÓDIGO ROJO</div>
          <div className="alert-content-row">
            <div className="alert-text">
              <h2>{currentCriticalAlert.message || "Taquicardia Crítica"}</h2>
              <p>Paciente: <b>{currentCriticalAlert.patient_name} (Hab {currentCriticalAlert.room} Cama {currentCriticalAlert.bed})</b>. Se requiere intervención inmediata.</p>
              <div style={{ display: 'flex', gap: '10px', marginTop: '16px' }}>
                <button 
                  className="btn-outline white" 
                  onClick={() => handleAcknowledge(currentCriticalAlert.id)}
                  disabled={ackLoading}
                >
                  {ackLoading ? 'Procesando...' : 'Reconocer y Atender'}
                </button>
                <Link to={`/paciente/${currentCriticalAlert.patient_id}`} className="btn-outline white" style={{ textDecoration: 'none' }}>
                  Ver Paciente
                </Link>
              </div>
            </div>
            <div className="alert-vital">
              <span className="vital-label">HORA DE ALERTA</span>
              <div className="vital-num" style={{ fontSize: '1.5rem' }}>{new Date(currentCriticalAlert.createdAt || Date.now()).toLocaleTimeString()}</div>
            </div>
          </div>
        </div>
        )}

        <div className="card rule-engine-card">
          <div className="rule-header">
            <h3>Motor de Reglas</h3>
            <button className="icon-btn" onClick={() => setShowModal(true)}><Settings size={18} /></button>
          </div>
          <p className="rule-desc">Última actualización de reglas: hace 2 minutos.</p>
          
          <ul className="rule-list">
            <li>
              <span>Monitoreo de Varianza de FC</span>
              <span className="badge badge-green">ÓPTIMO</span>
            </li>
            <li>
              <span>Protocolo de Taquicardia</span>
              <span className="badge badge-red">ACTIVADO</span>
            </li>
            <li>
              <span>Umbral de Desaturación de O2</span>
              <span className="badge badge-orange">ADVERTENCIA</span>
            </li>
          </ul>
        </div>
      </div>

      <div className="telemetry-section">
        <h3 className="section-title">Telemetría de Pacientes en Vivo</h3>
        
        <div className="patient-grid">
          {loading ? <p>Cargando pacientes...</p> : patients.map((p, idx) => (
            <Link to={`/paciente/${p.patient_id}`} key={idx} style={{ textDecoration: 'none', color: 'inherit' }}>
              <div className={`card patient-card ${p.status?.toLowerCase() || 'normal'}`}>
                <div className="p-card-header">
                  <div className="p-info">
                    <div className={`status-indicator ${p.status?.toLowerCase() || 'normal'}`}></div>
                    <div>
                      <div className="p-bed">{p.room} {p.bed} — {p.patient_name}</div>
                      {p.active_alerts && p.active_alerts.length > 0 && <div className="p-alert-text">{p.active_alerts[0].message}</div>}
                    </div>
                  </div>
                </div>
                <div className="p-vitals">
                  <div className="p-vital-col">
                    <span className="p-v-label">FC</span>
                    <div className={`p-v-val ${p.status === 'CRITICAL' ? 'text-red' : ''}`}>{p.latest_metrics?.heart_rate?.value ? Math.round(p.latest_metrics.heart_rate.value) : '--'}</div>
                  </div>
                  <div className="p-vital-col">
                    <span className="p-v-label">SpO2</span>
                    <div className={`p-v-val ${p.status === 'WARNING' ? 'text-orange' : ''}`}>{p.latest_metrics?.spo2?.value ? Math.round(p.latest_metrics.spo2.value) : '--'}<span className="percent">%</span></div>
                  </div>
                </div>
              </div>
            </Link>
          ))}
        </div>
      </div>

      {showModal && <RuleEngineModal onClose={() => setShowModal(false)} />}
    </div>
  );
};

export default Monitoring;
