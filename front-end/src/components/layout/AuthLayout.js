import Link from "next/link";
export default function AuthLayout({ children }) {
  return (
    <main className="auth-layout">
      <section className="auth-story">
        <Link href="/" className="brand">
          everme
        </Link>
        <div>
          <span className="eyebrow">일상의 대화로 이어지는 나</span>
          <h1>
            오늘의 이야기가,
            <br />
            내일의 나를 이어줍니다.
          </h1>
          <p>
            AI와 편하게 대화하며 나의 기억과 생각을 남기세요.
            <br />
            내가 허용한 사람에게만, 나의 이야기가 전해집니다.
          </p>
          <div className="story-card">
            <div className="person">
              <span className="avatar">영</span>
              <div>
                <strong>김영희님의 이야기</strong>
                <small>오늘 · 일상의 기억</small>
              </div>
            </div>
            <blockquote>
              “가족과 함께 식사하는 시간이
              <br />
              저에게는 가장 소중해요.”
            </blockquote>
          </div>
        </div>
        <footer>나의 기록 · 나의 선택 · 권한으로 보호되는 연결</footer>
      </section>
      <section className="auth-form">{children}</section>
    </main>
  );
}
