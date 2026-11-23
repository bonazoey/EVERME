"use client";

import { usePathname } from "next/navigation";
import AppSidebar from "./AppSidebar";
import { ChatSessionsProvider } from "./chat/ChatSessions";
export default function ServiceLayout({ children }) {
  const pathname = usePathname();
  const viewer = pathname.startsWith("/viewer/");
  const section = pathname.split("/")[viewer ? 2 : 1];
  const active = section;
  return (
    <ChatSessionsProvider>
      <div className="[--sidebar-width:300px] min-h-screen grid grid-cols-[var(--sidebar-width)_minmax(0,1fr)] items-start [@media(max-width:_1100px)]:[--sidebar-width:212px] [@media(max-width:_760px)]:grid-cols-[minmax(0,1fr)]">
        <AppSidebar viewer={viewer} active={active} chat={section === "chat"} />
        <div className="w-full min-w-0 max-w-full overflow-x-clip">
          {children}
        </div>
      </div>
    </ChatSessionsProvider>
  );
}
