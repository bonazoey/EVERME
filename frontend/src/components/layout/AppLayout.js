import Link from "next/link";
import Icon from "@/components/ui/Icon";
import Badge from "@/components/ui/Badge";
export default function AppLayout({
  children,
  active,
  title,
  viewer = false,
  chat = false,
  modeOpen = false,
}) {
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
    <div className="app-layout">
      <aside className="sidebar">
        <Link className="brand" href={viewer ? "/viewer/clones" : "/chat"}>
          everme
        </Link>
        <p className="brand-caption">나를 이어가는 대화</p>
        <details className="mode-switch" open={modeOpen || undefined}>
          <summary>
            {viewer ? "정보이용자 모드" : "사용자 모드"}
            <span>⌄</span>
          </summary>
          <div className="mode-options">
            <Link href="/chat">사용자 모드</Link>
            <Link href="/viewer/clones">정보이용자 모드</Link>
          </div>
        </details>
        <nav aria-label="주 메뉴">
          {links.map(([key, label, icon, href]) => (
            <div key={key}>
              <Link
                className={`nav-item ${active === key ? "is-active" : ""}`}
                href={href}
              >
                <Icon name={icon} />
                {label}
              </Link>
              {key === "chat" && chat && (
                <div className="history">
                  <Link className="new-chat" href={base + "/chat/empty"}>
                    ＋ 새 대화
                  </Link>
                  <small>최근 대화</small>
                  {[
                    "가족과 함께한 저녁",
                    "좋아하는 음식 이야기",
                    "오래 기억하고 싶은 여행",
                    "나에게 소중한 것들",
                  ].map((t, i) => (
                    <div
                      className={`history-item ${i === 0 ? "selected" : ""}`}
                      key={t}
                    >
                      <Link href={base + "/chat"}>{t}</Link>
                      <details>
                        <summary aria-label={t + " 메뉴"}>···</summary>
                        <div>
                          <Link href={base + "/chat/edit"}>제목 수정</Link>
                          <Link className="danger" href={base + "/chat/delete"}>
                            삭제
                          </Link>
                        </div>
                      </details>
                    </div>
                  ))}
                </div>
              )}
              {key === "clone" && (
                <div className="subnav">
                  <Link href="/clone">클론 현황</Link>
                  <Link
                    className={active === "history" ? "is-active" : ""}
                    href="/clone/history"
                  >
                    접근 이력
                  </Link>
                </div>
              )}
            </div>
          ))}
          <Link
            className={`nav-item ${active === "settings" ? "is-active" : ""}`}
            href={base + "/settings"}
          >
            <Icon name="settings" />
            계정 설정
          </Link>
        </nav>
        <details className="profile">
          <summary>
            <span className="avatar">영</span>
            <div>
              <strong>김영희</strong>
              <small>younghee@email.com</small>
            </div>
            <span>⌃</span>
          </summary>
          <Link href="/">로그아웃</Link>
          <Link href="/screens">전체 목업 화면</Link>
        </details>
      </aside>
      <div className="app-main">
        <header className="topbar">
          <span>
            {viewer ? "정보이용자 공간" : "사용자 공간"} / {title}
          </span>
          <Badge>{viewer ? "허용된 정보만 제공" : "나의 기록"}</Badge>
        </header>
        {children}
      </div>
    </div>
  );
}
