"use client";

import { useEffect, useRef } from "react";

export default function Modal({ title, onClose, children, drawer = false }) {
  const dialogRef = useRef(null);
  useEffect(() => {
    const previousFocus = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const dialog = dialogRef.current;
    const focusable = () =>
      [
        ...dialog.querySelectorAll(
          'button, a[href], input, select, textarea, [tabindex="0"]',
        ),
      ].filter(
        (element) => !element.disabled && element.getClientRects().length,
      );
    focusable()[0]?.focus();
    function handleKeyDown(event) {
      if (event.key === "Escape") {
        event.preventDefault();
        event.stopPropagation();
        onClose();
      } else if (event.key === "Tab") {
        const elements = focusable();
        const first = elements[0];
        const last = elements.at(-1);
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last?.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first?.focus();
        }
      }
    }
    document.addEventListener("keydown", handleKeyDown, true);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener("keydown", handleKeyDown, true);
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, [onClose]);
  return (
    <div
      className="fixed inset-0 bg-[#17212b55] z-100 flex items-center justify-center p-6"
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section
        ref={dialogRef}
        className={`max-w-full bg-white shadow-[0_20px_70px_#17212b30] overflow-auto [@media(max-width:_760px)]:p-6 ${
          drawer
            ? "absolute inset-y-0 right-0 w-110 max-h-dvh rounded-none p-7"
            : "w-140 max-h-[90dvh] rounded-2xl p-8"
        }`}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <header className="flex items-start justify-between gap-5 mb-6">
          <h2 className="m-0 text-[24px] [@media(max-width:_760px)]:text-[21px]">
            {title}
          </h2>
          <button
            type="button"
            className="text-[26px] text-muted leading-none"
            onClick={onClose}
            aria-label="닫기"
          >
            ×
          </button>
        </header>
        {children}
      </section>
    </div>
  );
}
