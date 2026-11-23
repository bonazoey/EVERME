"use client";

import { Fragment, useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { usePathname, useRouter } from "next/navigation";
import Link from "next/link";
import EvermeMascot from "@/components/EvermeMascot";
import Icon from "@/components/Icon";
import Modal from "@/components/Modal";
import Field from "@/components/Field";
import Button from "@/components/Button";
import { getInitialConversations, useChatSessions } from "./chat/ChatSessions";
const historyItemClasses =
  "h-[32px] flex items-center justify-between relative gap-1 rounded-md px-[5px] text-[13px] text-green-light [&.ui-appsidebar-selected]:font-bold [&_>_a]:overflow-hidden [&_>_a]:text-ellipsis [&_>_a]:whitespace-nowrap [&_.ui-history-menu-trigger]:opacity-0 [&:hover_.ui-history-menu-trigger]:opacity-100 [&:focus-within_.ui-history-menu-trigger]:opacity-100 [&_.ui-history-menu-trigger[aria-expanded=true]]:opacity-100 [@media(hover:_none)]:[&_.ui-history-menu-trigger]:opacity-100";

export default function AppSidebar({ viewer, active, chat }) {
  const router = useRouter();
  const pathname = usePathname();
  const [historyReferenceTime] = useState(() => Date.now());
  const [historyOpen, setHistoryOpen] = useState(false);
  const {
    conversations,
    startConversation,
    renameConversation,
    deleteConversation,
  } = useChatSessions();
  const [selectedConversation, setSelectedConversation] = useState(null);
  const mode = viewer ? "viewer" : "user";
  const [conversationAction, setConversationAction] = useState(null);
  const [conversationTitle, setConversationTitle] = useState("");
  const [historyMenu, setHistoryMenu] = useState(null);
  const historyMenuRef = useRef(null);
  const historyTriggerRef = useRef(null);
  function toggleHistoryMenu(event, conversation) {
    if (historyMenu?.conversation.id === conversation.id) {
      setHistoryMenu(null);
      return;
    }
    const trigger = event.currentTarget;
    const bounds = trigger.getBoundingClientRect();
    const top = bounds.bottom + 4;
    historyTriggerRef.current = trigger;
    setHistoryMenu({
      conversation,
      top,
      left: Math.max(8, Math.min(bounds.right + 8, window.innerWidth - 148)),
      maxHeight: Math.max(0, window.innerHeight - top - 8),
    });
  }
  useEffect(() => {
    if (!historyMenu) return;
    historyMenuRef.current
      ?.querySelector("button")
      ?.focus({ preventScroll: true });
    function dismiss(event) {
      if (
        !historyMenuRef.current?.contains(event.target) &&
        !historyTriggerRef.current?.contains(event.target)
      ) {
        setHistoryMenu(null);
      }
    }
    function escape(event) {
      if (event.key === "Escape") {
        event.preventDefault();
        setHistoryMenu(null);
        historyTriggerRef.current?.focus({ preventScroll: true });
      } else if (event.key === "Tab") {
        setHistoryMenu(null);
        historyTriggerRef.current?.focus({ preventScroll: true });
      }
    }
    function closeOnScroll(event) {
      if (!historyMenuRef.current?.contains(event.target)) setHistoryMenu(null);
    }
    function closeOnResize() {
      setHistoryMenu(null);
    }
    document.addEventListener("pointerdown", dismiss);
    document.addEventListener("keydown", escape);
    document.addEventListener("scroll", closeOnScroll, true);
    window.addEventListener("resize", closeOnResize);
    return () => {
      document.removeEventListener("pointerdown", dismiss);
      document.removeEventListener("keydown", escape);
      document.removeEventListener("scroll", closeOnScroll, true);
      window.removeEventListener("resize", closeOnResize);
    };
  }, [historyMenu]);
  const closeConversationModal = useCallback(
    () => setConversationAction(null),
    [],
  );
  function openConversationAction(action, conversation) {
    setHistoryMenu(null);
    historyTriggerRef.current?.focus({ preventScroll: true });
    setConversationTitle(conversation.title);
    setConversationAction({ action, conversation });
  }
  function confirmConversationAction(event) {
    event.preventDefault();
    const { action, conversation } = conversationAction;
    if (action === "edit") {
      if (!conversationTitle.trim()) return;
      renameConversation(mode, conversation.id, conversationTitle);
    } else {
      deleteConversation(mode, conversation.id);
      const current = new URLSearchParams(window.location.search).get(
        "conversation",
      );
      if (
        current === conversation.id ||
        (chat &&
          !current &&
          !selectedConversation &&
          recentConversations[0]?.id === conversation.id)
      ) {
        const draftId = startConversation();
        setSelectedConversation(draftId);
        const path = pathname.startsWith("/viewer/chat/park")
          ? "/viewer/chat/park"
          : viewer
            ? "/viewer/chat"
            : "/chat";

        router.push(path + "?conversation=" + draftId);
      }
    }
    closeConversationModal();
  }

  const recentConversations =
    conversations[mode] ?? getInitialConversations(mode);
  const datedConversations = [...recentConversations].sort(
    (a, b) =>
      Date.parse(b.updatedAt ?? "1970-01-01") -
      Date.parse(a.updatedAt ?? "1970-01-01"),
  );
  function historyPeriod(conversation) {
    const days =
      (historyReferenceTime -
        Date.parse(conversation.updatedAt ?? "1970-01-01")) /
      86400000;
    return days <= 7
      ? ""
      : days <= 14
        ? "지난주"
        : days <= 31
          ? "지난달"
          : "오래전";
  }
  function createConversation() {
    const id = startConversation();
    setSelectedConversation(id);
    const path = pathname.startsWith("/viewer/chat/park")
      ? "/viewer/chat/park"
      : base + "/chat";
    router.push(path + "?conversation=" + id);
  }
  const [profileOpen, setProfileOpen] = useState(false);
  const profileRef = useRef(null);
  const profileButtonRef = useRef(null);
  useEffect(() => {
    if (!profileOpen) return;
    function dismiss(event) {
      if (!profileRef.current?.contains(event.target)) setProfileOpen(false);
    }
    function escape(event) {
      if (event.key === "Escape") {
        setProfileOpen(false);
        profileButtonRef.current?.focus();
      }
    }
    document.addEventListener("pointerdown", dismiss);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("pointerdown", dismiss);
      document.removeEventListener("keydown", escape);
    };
  }, [profileOpen]);
  const base = viewer ? "/viewer" : "";
  const links = viewer
    ? [
      ["clones", "이용 가능 클론", "user", "/viewer/clones"],
      ["invitations", "받은 초대", "users", "/viewer/invitations"],
      ["chat", "AI 대변인", "chat", "/viewer/chat"],
    ]
    : [
      ["chat", "AI 대화", "chat", "/chat"],
      ["clone", "AI 클론", "user", "/clone"],
      ["members", "정보이용자 관리", "users", "/members"],
    ];
  return (
    <>
      <aside className="[--line:var(--green-light)] w-full min-w-0 h-dvh sticky top-0 self-start [&_svg]:shrink-0 [&_nav]:min-w-0 [&_nav_>_div]:min-w-0 [&_>_*]:shrink-0 p-[24px_16px] flex flex-col bg-green text-green-light [&_button:focus-visible]:outline-green-light [&_a:focus-visible]:outline-green-light [scrollbar-color:var(--green-light)_transparent] z-10 overflow-x-hidden overflow-y-auto [&_>_.ui-brand]:flex [&_>_.ui-brand]:items-center [&_>_.ui-brand]:gap-2.5 [&_>_.ui-brand]:m-[0_12px] [&_>_nav]:[border-top:1px_solid_var(--line)] [&_>_nav]:pt-4 [@media(max-width:_760px)]:static [@media(max-width:_760px)]:w-full [@media(max-width:_760px)]:h-auto [@media(max-width:_760px)]:p-5 [@media(max-width:_760px)]:[&_>_.ui-brand]:m-0 [@media(max-width:_760px)]:[&_nav]:flex [@media(max-width:_760px)]:[&_nav]:gap-1.5 [@media(max-width:_760px)]:[&_nav]:overflow-visible [@media(max-width:_760px)]:[&_nav]:flex-wrap">
        <Link
          className="ui-brand min-w-0 flex-wrap text-[clamp(22px,2.4vw,32px)] [font-family:'Trebuchet_MS',Verdana,sans-serif] font-semibold tracking-[1.5px] text-green-light leading-[1.2]"
          href={viewer ? "/viewer/clones" : "/chat"}
        >
          <span className="size-9 shrink-0">
            <EvermeMascot animated={false} />
          </span>
          EVERME
        </Link>
        <p className="text-[12px] text-green-light m-[24px_6px_8px] pt-4 [border-top:1px_solid_var(--line)] [@media(max-width:_760px)]:m-[4px_0_16px]">
          현재 이용 모드
        </p>
        <Link
          className="m-[0_4px_28px] relative border-0 rounded-lg p-[15px] list-none cursor-pointer text-[length:inherit] font-bold text-green-light [&:hover]:text-green [&:hover_span]:text-green flex items-center gap-2.5 [&_>_span:last-child]:ml-auto [&_>_span:last-child]:text-green-light [&:hover]:bg-green-light min-w-0 flex-wrap [@media(max-width:_760px)]:w-fit [@media(max-width:_760px)]:max-w-full [@media(max-width:_760px)]:m-0 [@media(max-width:_760px)]:p-2.5 [@media(max-width:_760px)]:text-[13px]"
          href={viewer ? "/chat" : "/viewer/clones"}
          aria-label={viewer ? "사용자 모드로 전환" : "정보이용자 모드로 전환"}
          title={viewer ? "사용자 모드로 전환" : "정보이용자 모드로 전환"}
        >
          <Icon name="user" size={18} />
          <span>{viewer ? "정보이용자 모드" : "사용자 모드"}</span>
          <span aria-hidden="true">⇄</span>
        </Link>
        <nav className="min-w-0" aria-label="주 메뉴">
          {links.map(([key, label, icon, href]) => (
            <div key={key}>
              {key === "chat" ? (
                <div
                  className={`relative flex items-center rounded-lg min-h-12 mb-2 font-bold text-green-light ${active === key ? "bg-[color-mix(in_srgb,var(--green)_88%,black)]" : ""}`}
                >
                  {active === key && (
                    <span
                      className="absolute inset-y-0 left-0 w-[5px] rounded-full bg-green-light"
                      aria-hidden="true"
                    />
                  )}
                  <button
                    type="button"
                    className="border-0 bg-transparent [color:inherit] cursor-pointer flex items-center gap-3.5 flex-1 min-w-0 p-[12px_0_12px_16px] text-left break-words"
                    aria-expanded={historyOpen}
                    aria-controls="recent-conversations"
                    onClick={() => setHistoryOpen((open) => !open)}
                  >
                    <Icon name={icon} />
                    {label}
                  </button>
                  <button
                    type="button"
                    className="border border-green-light bg-transparent text-green-light cursor-pointer w-6 h-6 shrink-0 mr-1.5 rounded-full flex items-center justify-center text-[24px] leading-none hover:bg-[color-mix(in_srgb,var(--green)_88%,black)]"
                    aria-label="새 대화 생성"
                    title="새 대화"
                    onClick={createConversation}
                  >
                    +
                  </button>
                </div>
              ) : (
                <Link
                  className={`relative flex items-center gap-3.5 min-h-12 p-[12px_16px] font-bold text-green-light break-words rounded-lg mb-2 [@media(max-width:_760px)]:text-[12px] [@media(max-width:_760px)]:gap-1.5 [@media(max-width:_760px)]:p-2 [@media(max-width:_760px)]:min-h-10 [@media(max-width:_760px)]:m-0 [@media(max-width:_760px)]:[&_svg]:w-4 ${active === key ? "bg-[color-mix(in_srgb,var(--green)_88%,black)]" : ""}`}
                  href={href}
                  aria-current={active === key ? "page" : undefined}
                >
                  {active === key && (
                    <span
                      className="absolute inset-y-0 left-0 w-[5px] rounded-full bg-green-light"
                      aria-hidden="true"
                    />
                  )}
                  <Icon name={icon} />
                  {label}
                </Link>
              )}
              {key === "chat" && historyOpen && (
                <div
                  className="pl-6 pr-3 mb-2 [&_>_small]:text-green-light [&_>_small]:block [&_>_small]:p-[0_4px] [&_>_small]:mb-3"
                  id="recent-conversations"
                >
                  <div className="max-h-[404px] min-w-0 overflow-x-hidden overflow-y-auto [scrollbar-width:thin]">
                    {datedConversations.map((conversation, i) => {
                      const isSelected =
                        selectedConversation === conversation.id ||
                        (!selectedConversation && chat && i === 0);
                      return (
                        <Fragment key={conversation.id}>
                          {historyPeriod(conversation) &&
                            (i === 0 ||
                              historyPeriod(datedConversations[i - 1]) !==
                              historyPeriod(conversation)) && (
                              <h3 className="m-0 flex h-7 items-center px-[5px] text-[11px] font-medium text-green-light/70">
                                {historyPeriod(conversation)}
                              </h3>
                            )}
                          <div
                            className={`${historyItemClasses} ${isSelected ? "ui-appsidebar-selected" : ""}`}
                            key={conversation.id}
                          >
                            <Link
                              className="flex min-w-0 flex-1 items-center gap-2"
                              aria-current={isSelected ? "page" : undefined}
                              href={
                                (conversation.path ?? base + "/chat") +
                                "?conversation=" +
                                encodeURIComponent(conversation.id)
                              }
                              onClick={() =>
                                setSelectedConversation(conversation.id)
                              }
                            >
                              <span
                                className="flex w-2 shrink-0 items-center justify-center"
                                aria-hidden="true"
                              >
                                {isSelected ? (
                                  <span className="h-4 w-[3px] rounded-full bg-green-light" />
                                ) : (
                                  "·"
                                )}
                              </span>
                              <span className="min-w-0 truncate">
                                {conversation.title}
                              </span>
                            </Link>
                            <button
                              className="ui-history-menu-trigger shrink-0 cursor-pointer text-[18px] leading-none"
                              type="button"
                              aria-label={conversation.title + " 메뉴"}
                              aria-haspopup="menu"
                              aria-expanded={
                                historyMenu?.conversation.id === conversation.id
                              }
                              aria-controls={
                                historyMenu?.conversation.id === conversation.id
                                  ? "conversation-menu"
                                  : undefined
                              }
                              onClick={(event) =>
                                toggleHistoryMenu(event, conversation)
                              }
                            >
                              ···
                            </button>
                          </div>
                        </Fragment>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>
          ))}
        </nav>
        <div
          className="relative m-[auto_4px_0] pt-6 [border-top:1px_solid_var(--line)] [&_small]:block [&_small]:text-green-light [&_small]:text-[10px] [&_small]:mt-[3px] [@media(max-width:_760px)]:block [@media(max-width:_760px)]:m-[16px_0_0] [@media(max-width:_760px)]:pt-3"
          ref={profileRef}
        >
          {profileOpen && (
            <div
              className="absolute bottom-[calc(100%_+_8px)] left-0 right-0 p-2 [border:1px_solid_var(--line)] rounded-xl bg-green-light text-green shadow-[0_8px_32px_#17212b20] z-40 [&_a]:flex [&_a]:gap-2.5 [&_a]:items-center [&_a]:rounded-md [&_a]:p-[10px_12px] [&_a]:text-[13px] [&_a]:text-green [&_a:hover]:bg-green [&_a:hover]:text-green-light [&_a:focus-visible]:bg-green [&_a:focus-visible]:text-green-light [@media(max-width:_760px)]:max-w-65"
              id="profile-popup"
              role="dialog"
              aria-label="계정 메뉴"
            >
              <p className="text-[12px] text-green m-[4px_10px_8px]">내 계정</p>
              <Link
                href={base + "/settings"}
                onClick={() => {
                  setProfileOpen(false);
                }}
              >
                <Icon name="settings" size={18} />
                계정 설정
              </Link>
              <Link
                href="/"
                onClick={() => {
                  setProfileOpen(false);
                }}
              >
                <Icon name="logout" size={18} />
                로그아웃
              </Link>
            </div>
          )}
          <button
            className="w-full border-0 bg-transparent rounded-lg px-3 py-2 text-green-light text-left flex items-center gap-3 cursor-pointer list-none text-[13px] [&_>_span:last-child]:text-green-light [&_>_span:last-child]:ml-auto hover:bg-[color-mix(in_srgb,var(--green)_88%,black)] aria-expanded:bg-[color-mix(in_srgb,var(--green)_88%,black)]"
            type="button"
            ref={profileButtonRef}
            aria-expanded={profileOpen}
            aria-controls={profileOpen ? "profile-popup" : undefined}
            aria-haspopup="dialog"
            onClick={() => setProfileOpen(!profileOpen)}
          >
            <span className="ui-avatar w-10 h-10 bg-green-light text-green rounded-full inline-flex items-center justify-center shrink-0 font-semibold [&.ui-common-large]:w-16 [&.ui-common-large]:h-16 [&.ui-common-large]:text-[22px]">
              영
            </span>
            <div className="min-w-0 flex-1 [&_strong]:block [&_strong]:truncate [&_small]:truncate">
              <strong>김영희</strong>
              <small>younghee@email.com</small>
            </div>
            <span
              className={`transition-transform duration-200 motion-reduce:transition-none ${profileOpen ? "rotate-180" : ""}`}
              aria-hidden="true"
            >
              <Icon name="chevron-up" size={18} />
            </span>
          </button>
        </div>
      </aside>
      {historyMenu &&
        historyOpen &&
        createPortal(
          <div
            ref={historyMenuRef}
            id="conversation-menu"
            role="menu"
            aria-label={historyMenu.conversation.title + " 메뉴"}
            className="fixed z-50 w-35 overflow-y-auto bg-white [--line:color-mix(in_srgb,_var(--green)_18%,_#f0f8f3)] border border-line rounded-lg p-1.5 text-[13px] shadow-[0_6px_16px_#00000008] [&_button]:block [&_button]:w-full [&_button]:rounded-md [&_button]:p-2.5 [&_button]:text-left [&_button:hover]:bg-green-light [&_button:focus-visible]:bg-green-light"
            style={{
              top: historyMenu.top,
              left: historyMenu.left,
              maxHeight: historyMenu.maxHeight,
            }}
            onKeyDown={(event) => {
              if (!["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key))
                return;
              event.preventDefault();
              const items = [
                ...event.currentTarget.querySelectorAll('[role="menuitem"]'),
              ];
              const index = items.indexOf(document.activeElement);
              const next =
                event.key === "Home"
                  ? 0
                  : event.key === "End"
                    ? items.length - 1
                    : (index +
                      (event.key === "ArrowDown" ? 1 : -1) +
                      items.length) %
                    items.length;
              items[next]?.focus({ preventScroll: true });
            }}
          >
            <button
              type="button"
              role="menuitem"
              onClick={() =>
                openConversationAction("edit", historyMenu.conversation)
              }
            >
              제목 수정
            </button>
            <button
              type="button"
              role="menuitem"
              className="text-[#dc4040]"
              onClick={() =>
                openConversationAction("delete", historyMenu.conversation)
              }
            >
              삭제
            </button>
          </div>,
          document.body,
        )}
      {conversationAction && (
        <Modal
          title={
            conversationAction.action === "edit"
              ? "대화 제목 수정"
              : "대화를 삭제하시겠어요?"
          }
          onClose={closeConversationModal}
        >
          <form onSubmit={confirmConversationAction}>
            {conversationAction.action === "edit" ? (
              <Field
                label="대화 제목"
                value={conversationTitle}
                onChange={(event) => setConversationTitle(event.target.value)}
                required
              />
            ) : (
              <p className="text-muted">삭제한 대화는 다시 확인할 수 없어요.</p>
            )}
            <div className="flex gap-3 mt-9 [&_.ui-button]:flex-1">
              <Button variant="secondary" onClick={closeConversationModal}>
                취소
              </Button>
              <Button
                type="submit"
                variant={
                  conversationAction.action === "delete" ? "danger" : "primary"
                }
              >
                {conversationAction.action === "delete" ? "삭제" : "저장"}
              </Button>
            </div>
          </form>
        </Modal>
      )}
    </>
  );
}
