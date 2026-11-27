"use client";

import { createContext, useEffect, useState } from "react";
import api from "../../../apis/axios";

export const AuthContext = createContext(null);

export default function AuthContextProvider({ children }) {
  const [email, setEmail] = useState("");
  const [jwt, setJwt] = useState("");
  const [isInitialized, setIsInitialized] = useState(false);

  useEffect(() => {
    const savedEmail = localStorage.getItem("email");
    const savedJwt = localStorage.getItem("jwt");

    if (savedEmail && savedJwt) {
      api.defaults.headers.common.Authorization = `Bearer ${savedJwt}`;
      /* eslint-disable react-hooks/set-state-in-effect */
      setEmail(savedEmail);
      setJwt(savedJwt);
    }
    setIsInitialized(true);
    /* eslint-enable react-hooks/set-state-in-effect */
  }, []);

  function login(email, token) {
    localStorage.setItem("email", email);
    localStorage.setItem("jwt", token);
    api.defaults.headers.common.Authorization = `Bearer ${token}`;
    setEmail(email);
    setJwt(token);
  }

  function logout() {
    localStorage.removeItem("email");
    localStorage.removeItem("jwt");
    delete api.defaults.headers.common.Authorization;
    setEmail("");
    setJwt("");
  }

  return (
    <AuthContext.Provider value={{ email, jwt, login, logout, isInitialized }}>
      {children}
    </AuthContext.Provider>
  );
}
