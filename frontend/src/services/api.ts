export const API_BASE_URL = 'http://localhost:8080/api/v1';

export const getHeaders = (): Record<string, string> => {
  const token = localStorage.getItem('token');
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
};

export const handleResponse = async <T>(response: Response): Promise<T | null> => {
  if (response.status === 401) {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    window.location.href = '/login';
    throw new Error('Unauthorized');
  }
  if (!response.ok) {
    throw new Error(`API error: ${response.status}`);
  }
  if (response.status === 204) return null;
  return (await response.json()) as T;
};

export const apiFetch = async <T>(
  path: string,
  options?: RequestInit,
): Promise<T | null> => {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: { ...getHeaders(), ...(options?.headers ?? {}) },
  });
  return handleResponse<T>(response);
};
