"use client";

import { createContext, useContext, useRef, useState } from "react";

const ChatSessionsContext = createContext(null);
export const initialConversations = [
  { id: "family", title: "가족과 함께한 저녁" },
  { id: "food", title: "좋아하는 음식 이야기" },
  { id: "travel", title: "오래 기억하고 싶은 여행" },
  { id: "values", title: "나에게 소중한 것들" },
];

export function ChatSessionsProvider({ children }) {
  const [conversations, setConversations] = useState({});
  const drafts = useRef({});
  function startConversation(mode) {
    const id = crypto.randomUUID();
    drafts.current[mode] = id;
    return id;
  }
  function completeConversation(mode, text) {
    const id = drafts.current[mode] ?? crypto.randomUUID();
    const title = text.slice(0, 30);
    setConversations((previous) => {
      const current = previous[mode] ?? initialConversations;
      if (current.some((item) => item.id === id)) return previous;
      return { ...previous, [mode]: [{ id, title }, ...current] };
    });
    drafts.current[mode] = id;
    return title;
  }
  return (
    <ChatSessionsContext.Provider
      value={{ conversations, startConversation, completeConversation }}
    >
      {children}
    </ChatSessionsContext.Provider>
  );
}

export function useChatSessions() {
  return useContext(ChatSessionsContext);
}
