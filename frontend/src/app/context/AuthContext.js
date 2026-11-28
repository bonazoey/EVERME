"use client";

import { createContext, useEffect, useState } from "react";
import api from "../../../apis/axios";

export const AuthContext = createContext(null);

export default function AuthContextProvider({ children }) {
  const [jwt, setJwt] = useState("");
  const [isInitialized, setIsInitialized] = useState(false);

  useEffect(() => {

    const savedJwt = localStorage.getItem("jwt");

    if (savedJwt) {
      api.defaults.headers.common.Authorization = `Bearer ${savedJwt}`;
      /* eslint-disable react-hooks/set-state-in-effect */
      setJwt(savedJwt);
    }
    setIsInitialized(true);
    /* eslint-enable react-hooks/set-state-in-effect */
  }, []);

  function login(token) {
    localStorage.setItem("jwt", token);
    api.defaults.headers.common.Authorization = `Bearer ${token}`;
    setJwt(token);
  }

  function logout() {
    localStorage.removeItem("jwt");
    delete api.defaults.headers.common.Authorization;
    setJwt("");
  }

  return (
    <AuthContext.Provider value={{ jwt, login, logout, isInitialized }}>
      {children}
    </AuthContext.Provider>
  );
}
