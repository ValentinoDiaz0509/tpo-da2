import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts';
import { AlertCircle, FileText, Activity } from 'lucide-react';
import { getPatientById, getPatientTelemetry, getPatientAlerts, acknowledgeAlert } from '../services/api';
import { WebSocketService } from '../services/websocket';
import './PatientDetail.css';

const PatientDetail = () => {
  const { id } = useParams();
  const [patient, setPatient] = useState(null);
  const [telemetry, setTelemetry] = useState({
    heartRate: '--',
    spo2: '--',
    respiratoryRate: '--',
    bloodPressure: '--/--'
  });
  const [heartRateData, setHeartRateData] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [loading, setLoading] = useState(true);
  const wsService = React.useRef(null);

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      // Fetch Patient Basic Info
      const patientData = await getPatientById(id);
      setPatient(patientData);

      // Fetch Alerts
      const alertsData = await getPatientAlerts(id);
      setAlerts(alertsData);

      // Fetch Historical Telemetry for Charts
      const historyData = await getPatientTelemetry(id);
      if (historyData && historyData.length > 0) {
        // Map backend history to chart format
        const chartData = historyData.map(reading => {
          const date = new Date(reading.timestamp);
          return {
            time: `${date.getHours()}:${date.getMinutes().toString().padStart(2, '0')}:${date.getSeconds().toString().padStart(2, '0')}`,
            value: reading.heartRate,
            spo2: reading.spo2
          };
        }).reverse(); // Reverse if they come newest first, we want oldest to newest left to right
        setHeartRateData(chartData);

        // Set latest telemetry values
        const latest = historyData[0];
        setTelemetry({
          heartRate: latest.heartRate || '--',
          spo2: latest.spo2 || '--',
          respiratoryRate: latest.respiratoryRate || '--',
          bloodPressure: `${latest.bloodPressureSystolic || '--'}/${latest.bloodPressureDiastolic || '--'}`
        });
      }
      setLoading(false);
    };

    fetchData();

    // Subscribe to real-time updates for this patient
    wsService.current = new WebSocketService(() => {
      wsService.current.subscribeToPatient(id, (update) => {
        // Update current telemetry
        setTelemetry({
          heartRate: update.telemetry?.heartRate || '--',
          spo2: update.telemetry?.spo2 || '--',
          respiratoryRate: update.telemetry?.respiratoryRate || '--',
          bloodPressure: `${update.telemetry?.bloodPressureSystolic || '--'}/${update.telemetry?.bloodPressureDiastolic || '--'}`
        });

        // Add to chart
        const date = new Date();
        const newPoint = {
            time: `${date.getHours()}:${date.getMinutes().toString().padStart(2, '0')}:${date.getSeconds().toString().padStart(2, '0')}`,
            value: update.telemetry?.heartRate,
            spo2: update.telemetry?.spo2
        };

        setHeartRateData(prevData => {
            const newData = [...prevData, newPoint];
            // Keep only the last 30 points to avoid chart overflow
            if (newData.length > 30) {
                return newData.slice(newData.length - 30);
            }
            return newData;
        });
      });
    });
    
    wsService.current.connect();

    return () => {
      if (wsService.current) wsService.current.disconnect();
    };
  }, [id]);

  if (loading) {
    return <div className="page-container patient-detail"><h2>Cargando paciente...</h2></div>;
  }

  if (!patient) {
    return (
        <div className="page-container patient-detail">
            <h2>Paciente no encontrado</h2>
            <p>Es posible que no exista o que el servidor no responda.</p>
            <Link to="/monitoreo" className="btn-primary" style={{ display: 'inline-block', marginTop: '16px', textDecoration: 'none' }}>
                Volver al Dashboard
            </Link>
        </div>
    );
  }

  const handleAcknowledge = async (alertId) => {
    const success = await acknowledgeAlert(alertId, "Médico Asignado");
    if (success) {
      setAlerts(alerts.map(a => a.id === alertId ? { ...a, acknowledged: true } : a));
    }
  };

  // Check if patient has any active unacknowledged critical alert
  const hasCriticalAlert = alerts.some(a => !a.acknowledged && a.severity === 'CRITICAL');

  return (
    <div className="page-container patient-detail">
      <div className="header-info" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
            <Link to="/monitoreo" className="btn-text" style={{ textDecoration: 'none', display: 'inline-flex', alignItems: 'center', gap: '8px', color: 'var(--text-muted)' }}>
                <span style={{ fontSize: '1.2rem' }}>←</span> Volver al Dashboard
            </Link>
        </div>
        <div>
            {hasCriticalAlert && <span className="badge badge-red" style={{ marginRight: '16px' }}>Alerta Crítica</span>}
            <span className="room-info">Habitación {patient.roomNumber}, Cama {patient.bedNumber}</span>
        </div>
      </div>

      <div className="patient-header card">
        <div className="patient-name-block">
          <h1>{patient.lastName},<br/>{patient.firstName}</h1>
          <div className="patient-demographics">
            <span>ID: {patient.id.substring(0,8)}...</span>
          </div>
        </div>
        <div className="patient-actions">
          <button className="btn-primary">Solicitar Intervención<br/>de Emergencia</button>
        </div>
      </div>

      <div className="profile-grid">
        <div className="card medical-profile">
          <h3 className="section-title">Perfil Médico</h3>
          
          <div className="profile-section">
            <h4 className="subsection-title">Resumen de Historia</h4>
            <div className="profile-item">
              <span className="profile-label">Diagnósticos Primarios</span>
              <p>Condición médica en evaluación (Integración con HCE pendiente)</p>
            </div>
            
            <div className="doctor-info">
              <div className="doc-avatar"></div>
              <div>
                <div className="doc-role">MÉDICO A CARGO</div>
                <div className="doc-name">Dr. Asignado</div>
              </div>
              <button className="btn-outline">Llamar Médico</button>
            </div>
          </div>
        </div>

        <div className="card alert-history">
          <div className="alert-header-row">
            <h3 className="section-title">Historial de Alertas</h3>
            <a href="#" className="link-text">Ver Todo</a>
          </div>
          
          <div className="alert-events">
            {alerts.length === 0 ? (
                <p>No hay alertas registradas para este paciente.</p>
            ) : (
                alerts.slice(0, 4).map(alert => (
                    <div key={alert.id} className={`alert-event ${alert.severity === 'CRITICAL' ? 'critical' : 'warning'} ${alert.acknowledged ? 'acknowledged' : ''}`}>
                    <div className="event-icon"><AlertCircle size={16} /></div>
                    <div className="event-content" style={{ flex: 1 }}>
                        <h4>{alert.rule ? alert.rule.description : alert.severity} {alert.acknowledged && "(Atendida)"}</h4>
                        <p>{alert.message}</p>
                        <span className="event-time">{new Date(alert.createdAt).toLocaleTimeString()}</span>
                    </div>
                    {!alert.acknowledged && (
                        <button 
                            className="btn-outline" 
                            style={{ padding: '4px 8px', fontSize: '0.75rem', height: 'fit-content' }}
                            onClick={() => handleAcknowledge(alert.id)}
                        >
                            Visto
                        </button>
                    )}
                    </div>
                ))
            )}
          </div>
        </div>
      </div>

      <div className="vitals-dashboard">
        <div className="card chart-card dark-card">
          <div className="chart-header">
            <h4>FRECUENCIA CARDÍACA (LPM)</h4>
            {telemetry.heartRate > 120 && <span className="badge badge-red">TAQUICARDIA</span>}
          </div>
          <div className="chart-value">{telemetry.heartRate} <span className="unit">LPM</span></div>
          <div className="chart-container">
            <ResponsiveContainer width="100%" height={120}>
              <AreaChart data={heartRateData}>
                <defs>
                  <linearGradient id="colorValue" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#22c55e" stopOpacity={0.8}/>
                    <stop offset="95%" stopColor="#22c55e" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <XAxis dataKey="time" stroke="#666" tick={{fill: '#666', fontSize: 10}} />
                <Tooltip contentStyle={{backgroundColor: '#1f2937', border: 'none', color: '#fff'}} />
                <Area type="monotone" dataKey="value" stroke="#22c55e" fillOpacity={1} fill="url(#colorValue)" isAnimationActive={false} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>
        
        <div className="vitals-side">
          <div className="card vital-card">
            <h4>SPO2 ACTUAL</h4>
            <div className={`vital-value ${telemetry.spo2 < 92 ? 'red-text' : 'dark-text'}`}>{telemetry.spo2} <span className="unit">%</span></div>
          </div>
          <div className="card vital-card">
            <h4>FREC. RESP (RPM)</h4>
            <div className="vital-value orange-text">{telemetry.respiratoryRate} <span className="unit">RPM</span></div>
          </div>
          <div className="card vital-card">
            <h4>PRESIÓN ARTERIAL</h4>
            <div className="vital-value blue-text">{telemetry.bloodPressure} <span className="unit">mmHg</span></div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default PatientDetail;
