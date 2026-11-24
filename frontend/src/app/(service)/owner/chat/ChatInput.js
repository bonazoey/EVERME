"use client";

import { useEffect, useRef, useState } from "react";
import Icon from "@/components/Icon";
export default function ChatInput({ onSend }) {
  const [message, setMessage] = useState("");
  const [recording, setRecording] = useState(false);
  const [seconds, setSeconds] = useState(0);
  const inputRef = useRef(null);
  const finishRef = useRef(null);
  useEffect(() => {
    if (!recording) return;
    finishRef.current?.focus({ preventScroll: true });
    const timer = setInterval(() => setSeconds((value) => value + 1), 1000);
    return () => clearInterval(timer);
  }, [recording]);
  function startRecording() {
    setSeconds(0);
    setRecording(true);
  }
  function finishRecording() {
    setRecording(false);
    requestAnimationFrame(() =>
      inputRef.current?.focus({ preventScroll: true }),
    );
  }
  function send() {
    if (recording) return;
    const text = message.trim();
    if (!text) return;
    onSend(text);
    setMessage("");
  }
  return (
    <>
      <div
        className="w-full max-w-200 mx-auto h-19 flex items-center gap-3 [border:1px_solid_var(--line)] rounded-[38px] p-3 mb-3.5 [&_input]:flex-1 [&_input]:min-w-0 [&_input]:min-h-12.5 [&_input]:rounded-[25px] [&_input]:border-0 [&_input]:p-[4px_8px] [&_input]:bg-transparent [&_input]:text-ink"
        onKeyDown={(event) => {
          if (recording && event.key === "Escape") {
            event.preventDefault();
            finishRecording();
          }
        }}
      >
        {recording ? (
          <>
            <div
              className="flex min-w-0 flex-1 items-center justify-center gap-1 overflow-hidden"
              aria-hidden="true"
            >
              {[12, 20, 30, 18, 36, 24, 14, 28, 38, 22, 16, 32, 20, 12, 26].map(
                (height, index) => (
                  <span
                    key={index}
                    className="w-[3px] shrink-0 rounded-full bg-green motion-safe:animate-pulse"
                    style={{ height, animationDelay: `${index * 80}ms` }}
                  />
                ),
              )}
            </div>
            <span
              className="shrink-0 text-[12px] tabular-nums text-green"
              aria-label="음성 입력 경과 시간"
            >
              {String(Math.floor(seconds / 60)).padStart(2, "0")}:
              {String(seconds % 60).padStart(2, "0")}
            </span>
            <button
              ref={finishRef}
              type="button"
              className="size-11 shrink-0 rounded-full flex items-center justify-center bg-green text-white text-[24px]"
              onClick={() => finishRecording()}
              aria-label="음성 입력 닫기"
              title="닫기"
            >
              ×
            </button>
          </>
        ) : (
          <>
            <input
              ref={inputRef}
              className="focus:outline-none focus:ring-0"
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
            <div className="shrink-0 flex items-center gap-3">
              <button
                type="button"
                className="w-11 h-11 rounded-full flex items-center justify-center ml-auto bg-surface text-green"
                onClick={startRecording}
                aria-label="음성 입력 시작"
              >
                <Icon name="mic" />
              </button>
              <button
                type="button"
                onClick={send}
                className="w-11 h-11 rounded-full flex items-center justify-center bg-green text-white"
                aria-label="메시지 보내기"
              >
                <Icon name="send" />
              </button>
            </div>
          </>
        )}
      </div>
    </>
  );
}
