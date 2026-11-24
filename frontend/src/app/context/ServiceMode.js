"use client";

import { createContext, useContext, useEffect, useSyncExternalStore } from "react";
import { usePathname } from "next/navigation";

const ServiceModeContext = createContext(null);
const storageKey = "everme-service-mode";
const changeEvent = "everme-service-mode-change";
let memoryMode = "owner";

function subscribe(onChange) {
  window.addEventListener(changeEvent, onChange);
  window.addEventListener("storage", onChange);
  return () => {
    window.removeEventListener(changeEvent, onChange);
    window.removeEventListener("storage", onChange);
  };
}

function getSnapshot() {
  try {
    const stored = sessionStorage.getItem(storageKey);
    return stored === "viewer" || stored === "owner" ? stored : memoryMode;
  } catch {
    return memoryMode;
  }
}

function getServerSnapshot() {
  return "owner";
}

export function ServiceModeProvider({ children }) {
  const pathname = usePathname();
  const savedMode = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
  const routeMode = pathname.startsWith("/viewer/")
    ? "viewer"
    : pathname.startsWith("/owner/")
      ? "owner"
      : null;
  const mode = routeMode ?? savedMode;

  useEffect(() => {
    if (!routeMode) return;
    memoryMode = routeMode;
    try {
      sessionStorage.setItem(storageKey, routeMode);
    } catch {
      // Keep the mode in memory when browser storage is unavailable.
    }
    window.dispatchEvent(new Event(changeEvent));
  }, [routeMode]);

  return (
    <ServiceModeContext.Provider value={{ mode, viewer: mode === "viewer" }}>
      {children}
    </ServiceModeContext.Provider>
  );
}

export function useServiceMode() {
  return useContext(ServiceModeContext);
}
