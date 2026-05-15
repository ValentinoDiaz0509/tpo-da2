import React, { useState, useEffect } from 'react';
import { X, Trash2, Power, PowerOff } from 'lucide-react';
import { getRules, createRule, toggleRule, deleteRule } from '../services/api';
import './RuleEngineModal.css';

const RuleEngineModal = ({ onClose }) => {
  const [rules, setRules] = useState([]);
  const [loading, setLoading] = useState(true);
  
  // Form state
  const [metricName, setMetricName] = useState('heart_rate');
  const [operator, setOperator] = useState('GREATER_THAN');
  const [threshold, setThreshold] = useState(120);
  const [durationSeconds, setDurationSeconds] = useState(120);
  const [severity, setSeverity] = useState('CRITICAL');
  const [description, setDescription] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    fetchRules();
  }, []);

  const fetchRules = async () => {
    setLoading(true);
    const data = await getRules();
    setRules(data);
    setLoading(false);
  };

  const handleCreateRule = async (e) => {
    e.preventDefault();
    if (!description.trim()) {
        alert("Por favor ingresa una descripción para la regla.");
        return;
    }
    
    setIsSubmitting(true);
    try {
        await createRule({
            metricName,
            operator,
            threshold: parseFloat(threshold),
            durationSeconds: parseInt(durationSeconds),
            severity,
            description,
            enabled: true
        });
        
        // Reset form slightly
        setDescription('');
        await fetchRules();
    } catch (error) {
        alert("Error al crear la regla.");
    } finally {
        setIsSubmitting(false);
    }
  };

  const handleToggle = async (rule) => {
    const success = await toggleRule(rule.id, !rule.enabled);
    if (success) {
        setRules(rules.map(r => r.id === rule.id ? { ...r, enabled: !rule.enabled } : r));
    }
  };

  const handleDelete = async (id) => {
    if (window.confirm("¿Seguro que deseas eliminar esta regla?")) {
        const success = await deleteRule(id);
        if (success) {
            setRules(rules.filter(r => r.id !== id));
        }
    }
  };

  return (
    <div className="modal-overlay">
      <div className="modal-content rule-modal-wide">
        <div className="modal-header">
          <h2>Configuración del Motor de Reglas</h2>
          <button className="close-btn" onClick={onClose}><X size={20} /></button>
        </div>
        
        <div className="rules-layout">
            {/* PANEL IZQUIERDO: LISTA DE REGLAS */}
            <div className="rules-list-section">
                <h3>Reglas Activas en el Sistema</h3>
                <div className="rules-list-container">
                    {loading ? (
                        <p>Cargando reglas...</p>
                    ) : rules.length === 0 ? (
                        <p>No hay reglas configuradas.</p>
                    ) : (
                        rules.map(rule => (
                            <div key={rule.id} className={`rule-item ${!rule.enabled ? 'rule-disabled' : ''}`}>
                                <div className="rule-item-info">
                                    <div className="rule-item-header">
                                        <h4>{rule.description}</h4>
                                        <span className={`badge ${rule.severity === 'CRITICAL' ? 'badge-red' : 'badge-orange'}`}>
                                            {rule.severity}
                                        </span>
                                    </div>
                                    <p>
                                        Si <b>{rule.metricName}</b> {rule.operator === 'GREATER_THAN' ? '&gt;' : rule.operator === 'LESS_THAN' ? '&lt;' : '='} <b>{rule.threshold}</b> por <b>{rule.durationSeconds}s</b>
                                    </p>
                                </div>
                                <div className="rule-item-actions">
                                    <button 
                                        className={`icon-btn ${rule.enabled ? 'text-green' : 'text-gray'}`} 
                                        onClick={() => handleToggle(rule)}
                                        title={rule.enabled ? "Desactivar" : "Activar"}
                                    >
                                        {rule.enabled ? <Power size={18}/> : <PowerOff size={18}/>}
                                    </button>
                                    <button className="icon-btn text-red" onClick={() => handleDelete(rule.id)} title="Eliminar">
                                        <Trash2 size={18}/>
                                    </button>
                                </div>
                            </div>
                        ))
                    )}
                </div>
            </div>

            {/* PANEL DERECHO: CREAR NUEVA REGLA */}
            <div className="rule-create-section">
                <h3>Crear Nueva Regla</h3>
                <form className="rule-form" onSubmit={handleCreateRule}>
                <div className="form-group">
                    <label>DESCRIPCIÓN (Ej: Taquicardia Moderada)</label>
                    <input type="text" value={description} onChange={e => setDescription(e.target.value)} required placeholder="Nombre de la alerta" />
                </div>

                <div className="form-row split">
                    <div className="form-group">
                    <label>MÉTRICA VITAL</label>
                    <select value={metricName} onChange={e => setMetricName(e.target.value)}>
                        <option value="heart_rate">Frecuencia Cardíaca</option>
                        <option value="spo2">Saturación de O2</option>
                        <option value="respiratory_rate">Frecuencia Respiratoria</option>
                        <option value="systolic_bp">Presión Sistólica</option>
                    </select>
                    </div>
                    
                    <div className="form-group">
                    <label>CONDICIÓN</label>
                    <select value={operator} onChange={e => setOperator(e.target.value)}>
                        <option value="GREATER_THAN">Mayor que (&gt;)</option>
                        <option value="LESS_THAN">Menor que (&lt;)</option>
                        <option value="EQUALS">Igual a (=)</option>
                    </select>
                    </div>
                </div>
                
                <div className="form-row split">
                    <div className="form-group">
                    <label>UMBRAL (Valor)</label>
                    <input type="number" step="0.1" value={threshold} onChange={e => setThreshold(e.target.value)} required />
                    </div>
                    
                    <div className="form-group">
                    <label>DURACIÓN (Segundos)</label>
                    <input type="number" value={durationSeconds} onChange={e => setDurationSeconds(e.target.value)} required />
                    </div>
                </div>
                
                <div className="form-group">
                    <label>SEVERIDAD / ACCIÓN</label>
                    <select value={severity} onChange={e => setSeverity(e.target.value)}>
                    <option value="CRITICAL">CRITICAL (Código Rojo)</option>
                    <option value="WARNING">WARNING (Advertencia)</option>
                    <option value="INFO">INFO (Notificación)</option>
                    </select>
                </div>
                
                <div className="modal-footer">
                    <button type="submit" className="btn-primary w-full" disabled={isSubmitting}>
                        {isSubmitting ? 'Guardando...' : 'Guardar Regla'}
                    </button>
                </div>
                </form>
            </div>
        </div>
      </div>
    </div>
  );
};

export default RuleEngineModal;
