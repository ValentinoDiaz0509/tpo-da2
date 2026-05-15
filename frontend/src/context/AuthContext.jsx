import React, { createContext, useState, useEffect, useContext } from 'react';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(localStorage.getItem('token') || null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    // Si hay un token en localstorage, cargamos el usuario guardado
    const savedToken = localStorage.getItem('token');
    const savedUser = localStorage.getItem('user');
    
    if (savedToken && savedUser) {
      try {
        setUser(JSON.parse(savedUser));
        setToken(savedToken);
      } catch (e) {
        console.error("Error parsing user from localStorage");
        logout();
      }
    }
    setIsLoading(false);
  }, []);

  const login = async (email, password) => {
    try {
      // Mock para testeo rápido o por si falla M10
      let responseToken = "mock-jwt-token-for-m6";
      let userData = {
        id: 1,
        first_name: email.split('@')[0].toUpperCase(),
        last_name: "Grid",
        email: email,
        roles: [{ name: "ENFERMERO" }]
      };

      // TODO: Descomentar esto cuando M10 esté levantado para pegarle real
      /*
      const response = await fetch("http://localhost:8010/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password })
      });
      if (!response.ok) throw new Error("Credenciales inválidas");
      const data = await response.json();
      responseToken = data.token;
      userData = data.user;
      */

      localStorage.setItem('token', responseToken);
      localStorage.setItem('user', JSON.stringify(userData));
      setToken(responseToken);
      setUser(userData);
      return { success: true };
    } catch (error) {
      console.error("Error en login:", error);
      return { success: false, error: error.message };
    }
  };

  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, token, login, logout, isAuthenticated: !!token }}>
      {!isLoading && children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
