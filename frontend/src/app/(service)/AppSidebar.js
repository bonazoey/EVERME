"use client";

import appSidebarStyles from "./AppSidebar.module.css";
import commonStyles from "../../components/Common.module.css";
import classNames from "../../lib/classNames.js";
import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import Image from "next/image";
import Icon from "@/components/Icon";
import { initialConversations, useChatSessions } from "./chat/ChatSessions";
const styles = {
  ...appSidebarStyles,
  ...commonStyles,
};
export default function AppSidebar({ viewer, active, chat }) {
  const router = useRouter();
  const [expandedMenu, setExpandedMenu] = useState(
    active === "clone" || active === "history" ? "clone" : null,
  );
  const historyOpen = expandedMenu === "chat";
  const cloneOpen = expandedMenu === "clone";
  const { conversations, startConversation } = useChatSessions();
  const [selectedConversation, setSelectedConversation] = useState(null);
  const mode = viewer ? "viewer" : "user";
  const recentConversations = conversations[mode] ?? initialConversations;
  function createConversation() {
    const id = startConversation(mode);
    setSelectedConversation(id);
    setExpandedMenu("chat");
    router.push(base + "/chat/empty?conversation=" + id);
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
    <aside className={classNames("sidebar", styles)}>
      <Link
        className={classNames("brand", styles)}
        href={viewer ? "/viewer/clones" : "/chat"}
        onClick={() => setExpandedMenu(null)}
      >
        <Image src="/everme_logo.svg" alt="" width={36} height={36} />
        EVERME
      </Link>
      <p className={classNames("brand-caption", styles)}>현재 이용 모드</p>
      <Link
        className={classNames("mode-switch", styles)}
        onClick={() => setExpandedMenu(null)}
        href={viewer ? "/chat" : "/viewer/clones"}
        aria-label={viewer ? "사용자 모드로 전환" : "정보이용자 모드로 전환"}
        title={viewer ? "사용자 모드로 전환" : "정보이용자 모드로 전환"}
      >
        <Icon name="user" size={18} />
        <span>{viewer ? "정보이용자 모드" : "사용자 모드"}</span>
        <span aria-hidden="true">⇄</span>
      </Link>
      <nav aria-label="주 메뉴">
        {links.map(([key, label, icon, href]) => (
          <div key={key}>
            {key === "chat" ? (
              <div
                className={classNames(
                  `chat-nav-row ${active === key ? "is-active" : ""}`,
                  styles,
                )}
              >
                <button
                  type="button"
                  className={classNames("chat-nav-toggle", styles)}
                  aria-expanded={historyOpen}
                  aria-controls="recent-conversations"
                  onClick={() => setExpandedMenu(historyOpen ? null : "chat")}
                >
                  <Icon name={icon} />
                  {label}
                </button>
                <button
                  type="button"
                  className={classNames("chat-nav-add", styles)}
                  aria-label="새 대화 생성"
                  title="새 대화"
                  onClick={createConversation}
                >
                  +
                </button>
              </div>
            ) : key === "clone" ? (
              <div
                className={classNames(
                  `chat-nav-row ${active === "clone" || active === "history" ? "is-active" : ""}`,
                  styles,
                )}
              >
                <button
                  type="button"
                  className={classNames("chat-nav-toggle", styles)}
                  aria-expanded={cloneOpen}
                  aria-controls="clone-navigation"
                  onClick={() => setExpandedMenu(cloneOpen ? null : "clone")}
                >
                  <Icon name={icon} />
                  {label}
                </button>
              </div>
            ) : (
              <Link
                className={classNames(
                  `nav-item ${active === key ? "is-active" : ""}`,
                  styles,
                )}
                href={href}
                onClick={() => setExpandedMenu(null)}
              >
                <Icon name={icon} />
                {label}
              </Link>
            )}
            {key === "chat" && historyOpen && (
              <div
                className={classNames("history", styles)}
                id="recent-conversations"
              >
                <div className={classNames("history-list", styles)}>
                  {recentConversations.map((conversation, i) => (
                    <div
                      className={classNames(
                        `history-item ${selectedConversation === conversation.id || (!selectedConversation && chat && i === 0) ? "selected" : ""}`,
                        styles,
                      )}
                      key={conversation.id}
                    >
                      <Link
                        href={
                          conversation.empty
                            ? base +
                              "/chat/empty?conversation=" +
                              conversation.id
                            : base +
                              "/chat?title=" +
                              encodeURIComponent(conversation.title)
                        }
                        onClick={() => setSelectedConversation(conversation.id)}
                      >
                        {conversation.title}
                      </Link>
                      <details>
                        <summary aria-label={conversation.title + " 메뉴"}>
                          ···
                        </summary>
                        <div>
                          <Link href={base + "/chat/edit"}>제목 수정</Link>
                          <Link
                            className={classNames("danger", styles)}
                            href={base + "/chat/delete"}
                          >
                            삭제
                          </Link>
                        </div>
                      </details>
                    </div>
                  ))}
                </div>
              </div>
            )}
            {key === "clone" && cloneOpen && (
              <div
                className={classNames("history", styles)}
                id="clone-navigation"
              >
                <Link
                  className={classNames(
                    `history-item ${active === "clone" ? "selected" : ""}`,
                    styles,
                  )}
                  href="/clone"
                  aria-current={active === "clone" ? "page" : undefined}
                >
                  클론 대시보드
                </Link>
                <Link
                  className={classNames(
                    `history-item ${active === "history" ? "selected" : ""}`,
                    styles,
                  )}
                  href="/clone/history"
                  aria-current={active === "history" ? "page" : undefined}
                >
                  접근 이력
                </Link>
              </div>
            )}
          </div>
        ))}
      </nav>
      <div className={classNames("profile", styles)} ref={profileRef}>
        {profileOpen && (
          <div
            className={classNames("profile-popup", styles)}
            id="profile-popup"
            role="dialog"
            aria-label="계정 메뉴"
          >
            <p className={classNames("profile-popup-title", styles)}>내 계정</p>
            <Link
              href={base + "/settings"}
              onClick={() => {
                setProfileOpen(false);
                setExpandedMenu(null);
              }}
            >
              <Icon name="settings" size={18} />
              계정 설정
            </Link>
            <Link
              href="/"
              onClick={() => {
                setProfileOpen(false);
                setExpandedMenu(null);
              }}
            >
              <Icon name="logout" size={18} />
              로그아웃
            </Link>
          </div>
        )}
        <button
          className={classNames("profile-trigger", styles)}
          type="button"
          ref={profileButtonRef}
          aria-expanded={profileOpen}
          aria-controls={profileOpen ? "profile-popup" : undefined}
          aria-haspopup="dialog"
          onClick={() => setProfileOpen(!profileOpen)}
        >
          <span className={classNames("avatar", styles)}>영</span>
          <div>
            <strong>김영희</strong>
            <small>younghee@email.com</small>
          </div>
          <span aria-hidden="true">{profileOpen ? "⌄" : "⌃"}</span>
        </button>
      </div>
    </aside>
  );
}
