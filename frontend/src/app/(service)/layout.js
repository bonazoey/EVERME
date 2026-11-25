"use client";

import { Suspense } from "react";
import { usePathname } from "next/navigation";
import AppSidebar from "./AppSidebar";
import { ChatSessionsProvider } from "@/app/context/ChatSessions";
import { ServiceModeProvider, useServiceMode } from "@/app/context/ServiceMode";
export default function ServiceLayout({ children }) {
  return (
    <ServiceModeProvider>
      <ServiceContent>{children}</ServiceContent>
    </ServiceModeProvider>
  );
}

function ServiceContent({ children }) {
  const pathname = usePathname();
  const { viewer } = useServiceMode();
  const section = pathname === "/settings" ? "settings" : pathname.split("/")[2];
  const active = section;
  const chat = section === "chat";
  return (
    <ChatSessionsProvider>
      <div className={`[--sidebar-width:300px] grid grid-cols-[var(--sidebar-width)_minmax(0,1fr)] items-start [@media(max-width:_1100px)]:[--sidebar-width:212px] has-[>aside[data-collapsed]]:[--sidebar-width:80px] [@media(max-width:_760px)]:grid-cols-[minmax(0,1fr)] ${chat ? "h-dvh grid-rows-[minmax(0,1fr)] overflow-hidden [@media(max-width:_760px)]:grid-rows-[auto_minmax(0,1fr)] [@media(max-width:_760px)]:[&>aside]:max-h-[38dvh]" : "min-h-screen"}`}>
        <Suspense fallback={<aside aria-hidden="true" className="h-dvh bg-green [@media(max-width:_760px)]:h-0" />}>
          <AppSidebar viewer={viewer} active={active} chat={chat} />
        </Suspense>
        <div className={`w-full min-w-0 max-w-full overflow-x-clip ${chat ? "min-h-0 self-stretch" : ""}`}>
          {children}
        </div>
      </div>
    </ChatSessionsProvider>
  );
}
