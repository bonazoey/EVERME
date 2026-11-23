"use client";

import layoutStyles from "./layout.module.css";
import classNames from "../../lib/classNames.js";
import { usePathname } from "next/navigation";
import AppSidebar from "./AppSidebar";
import { ChatSessionsProvider } from "./chat/ChatSessions";
const styles = {
  ...layoutStyles,
};
export default function ServiceLayout({ children }) {
  const pathname = usePathname();
  const viewer = pathname.startsWith("/viewer/");
  const section = pathname.split("/")[viewer ? 2 : 1];
  const active =
    section === "clone" && pathname.endsWith("/history") ? "history" : section;
  const titles = {
    chat: viewer ? "AI \uB300\uBCC0\uC778" : "AI \uB300\uD654",
    clone: "AI \uD074\uB860 / \uD074\uB860 \uD604\uD669",
    history: "AI \uD074\uB860 / \uC811\uADFC \uC774\uB825",
    members: "\uC815\uBCF4\uC774\uC6A9\uC790 \uAD00\uB9AC",
    settings: "\uACC4\uC815 \uC124\uC815",
    clones: "\uC774\uC6A9 \uAC00\uB2A5 \uD074\uB860",
    invitations: "\uBC1B\uC740 \uCD08\uB300",
  };
  return (
    <ChatSessionsProvider>
      <div className={classNames("app-layout", styles)}>
        <AppSidebar viewer={viewer} active={active} chat={section === "chat"} />
        <div className={classNames("app-main", styles)}>{children}</div>
      </div>
    </ChatSessionsProvider>
  );
}
