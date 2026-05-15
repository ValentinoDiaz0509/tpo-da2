const API_BASE_URL = 'http://localhost:8080/api/v1';

const getHeaders = () => {
    const token = localStorage.getItem('token');
    return {
        'Content-Type': 'application/json',
        ...(token ? { 'Authorization': `Bearer ${token}` } : {})
    };
};

const handleResponse = async (response) => {
    if (response.status === 401) {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        window.location.href = '/login';
        throw new Error('Unauthorized');
    }
    if (!response.ok) {
        throw new Error(`API error: ${response.status}`);
    }
    // Si no hay body (204 No Content), devolver null
    if (response.status === 204) return null;
    return await response.json();
};

export const getPatients = async () => {
    try {
        const response = await fetch(`${API_BASE_URL}/patients/monitoring`, { headers: getHeaders() });
        return await handleResponse(response);
    } catch (error) {
        console.error("Get Patients Error:", error);
        return [];
    }
};

export const getPatientById = async (id) => {
    try {
        const response = await fetch(`${API_BASE_URL}/patients/${id}`, { headers: getHeaders() });
        return await handleResponse(response);
    } catch (error) {
        console.error("Get Patient Error:", error);
        return null;
    }
};

export const getPatientTelemetry = async (id) => {
    try {
        const endTime = new Date().toISOString();
        const startTime = new Date(Date.now() - 15 * 60000).toISOString(); // Last 15 minutes
        const response = await fetch(`${API_BASE_URL}/telemetry-readings/patient/${id}/range?startTime=${startTime}&endTime=${endTime}`, { headers: getHeaders() });
        return await handleResponse(response);
    } catch (error) {
        console.error("Get Telemetry Error:", error);
        return [];
    }
};

export const getPatientAlerts = async (id) => {
    try {
        const response = await fetch(`${API_BASE_URL}/alerts/patient/${id}`, { headers: getHeaders() });
        return await handleResponse(response);
    } catch (error) {
        console.error("Get Alerts Error:", error);
        return [];
    }
};

// --- RULES API ---

export const getRules = async () => {
    try {
        const response = await fetch(`${API_BASE_URL}/rules`, { headers: getHeaders() });
        return await handleResponse(response);
    } catch (error) {
        console.error("Get Rules Error:", error);
        return [];
    }
};

export const createRule = async (ruleData) => {
    try {
        const response = await fetch(`${API_BASE_URL}/rules`, {
            method: 'POST',
            headers: getHeaders(),
            body: JSON.stringify(ruleData)
        });
        return await handleResponse(response);
    } catch (error) {
        console.error("Create Rule Error:", error);
        throw error;
    }
};

export const toggleRule = async (id, enable) => {
    try {
        const action = enable ? 'enable' : 'disable';
        const response = await fetch(`${API_BASE_URL}/rules/${id}/${action}`, {
            method: 'PATCH',
            headers: getHeaders()
        });
        await handleResponse(response);
        return true;
    } catch (error) {
        console.error("Toggle Rule Error:", error);
        return false;
    }
};

export const deleteRule = async (id) => {
    try {
        const response = await fetch(`${API_BASE_URL}/rules/${id}`, {
            method: 'DELETE',
            headers: getHeaders()
        });
        await handleResponse(response);
        return true;
    } catch (error) {
        console.error("Delete Rule Error:", error);
        return false;
    }
};

// --- ALERTS EXTRA API ---

export const getUnacknowledgedCriticalAlerts = async () => {
    try {
        const response = await fetch(`${API_BASE_URL}/alerts/unacknowledged/critical`, { headers: getHeaders() });
        return await handleResponse(response);
    } catch (error) {
        console.error("Get Critical Alerts Error:", error);
        return [];
    }
};

export const acknowledgeAlert = async (id, userName = "Enfermería Central") => {
    try {
        const response = await fetch(`${API_BASE_URL}/alerts/${id}/acknowledge?acknowledgedBy=${encodeURIComponent(userName)}`, {
            method: 'PATCH',
            headers: getHeaders()
        });
        await handleResponse(response);
        return true;
    } catch (error) {
        console.error("Acknowledge Alert Error:", error);
        return false;
    }
};
