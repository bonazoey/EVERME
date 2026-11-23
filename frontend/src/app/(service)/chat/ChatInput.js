"use client";

import chatInputStyles from "./ChatInput.module.css";
import classNames from "../../../lib/classNames.js";
import Link from "next/link";
import { useState } from "react";
import { useRouter } from "next/navigation";
import Icon from "@/components/Icon";
import { useChatSessions } from "./ChatSessions";
const styles = {
  ...chatInputStyles,
};
export default function ChatInput({ base, viewer, empty }) {
  const router = useRouter();
  const [message, setMessage] = useState("");
  const { completeConversation } = useChatSessions();
  function send() {
    const text = message.trim();
    if (!text) return;
    const title = empty
      ? completeConversation(viewer ? "viewer" : "user", text)
      : text.slice(0, 30);
    setMessage("");
    router.push(base + "?title=" + encodeURIComponent(title));
  }
  return (
    <div className={classNames("composer", styles)}>
      <input
        type="text"
        name="message"
        value={message}
        onChange={(event) => setMessage(event.target.value)}
        onKeyDown={(event) => {
          if (
            event.key === "Enter" &&
            !event.nativeEvent.isComposing &&
            event.keyCode !== 229
          ) {
            event.preventDefault();
            send();
          }
        }}
        aria-label="대화 입력"
        placeholder="편하게 이야기해 주세요"
      />
      <div>
        <Link
          className={classNames("mic-button", styles)}
          href={base + "/voice"}
          aria-label="음성 대화로 전환"
        >
          <Icon name="mic" />
        </Link>
        <button
          type="button"
          onClick={send}
          className={classNames("send-button", styles)}
          aria-label="메시지 보내기"
        >
          <Icon name="send" />
        </button>
      </div>
    </div>
  );
}
