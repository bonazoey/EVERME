import authFormStyles from "./AuthForm.module.css";
import commonStyles from "../../components/Common.module.css";
import classNames from "../../lib/classNames.js";
import Link from "next/link";
import Button from "@/components/Button";
import Field from "@/components/Field";
import Notice from "@/components/Notice";
const styles = {
  ...authFormStyles,
  ...commonStyles,
};
const titles = {
  login: "서비스 소개 및 로그인",
  signup: "회원가입",
  reset: "비밀번호 재설정",
  sent: "비밀번호 재설정",
  password: "새 비밀번호 설정",
  consent: "개인정보 및 AI 활용 동의",
};
export default function AuthForm({ screen = "login" }) {
  return (
    <>
      <h2>{titles[screen]}</h2>
      <p className={classNames("muted", styles)}>
        {screen === "login"
          ? "나의 이야기를 이어가 볼까요?"
          : "안전한 시작을 위해 필요한 정보를 확인해 주세요."}
      </p>
      <div className={classNames("auth-fields", styles)}>
        {screen === "login" && (
          <>
            <Field
              label="이메일"
              placeholder="이메일 주소를 입력해 주세요"
              type="email"
            />
            <Field
              label="비밀번호"
              placeholder="비밀번호를 입력해 주세요"
              type="password"
            />
            <Link
              className={classNames("forgot", styles)}
              href="/reset-password"
            >
              비밀번호를 잊으셨나요?
            </Link>
            <Button href="/chat">로그인</Button>
            <div className={classNames("divider", styles)}>
              또는 소셜 계정으로 시작하기
            </div>
            <Button variant="secondary" href="/chat">
              카카오로 계속하기
            </Button>
            <Button variant="secondary" href="/chat">
              Google로 계속하기
            </Button>
            <Link className={classNames("center-link", styles)} href="/signup">
              처음이신가요? 회원가입
            </Link>
          </>
        )}
        {screen === "signup" && (
          <>
            <Field label="이름" placeholder="이름을 입력해 주세요" />
            <Field
              label="이메일"
              placeholder="이메일 주소를 입력해 주세요"
              type="email"
            />
            <Field
              label="비밀번호"
              placeholder="안전한 비밀번호를 입력해 주세요"
              type="password"
            />
            <Field
              label="비밀번호 확인"
              placeholder="비밀번호를 다시 입력해 주세요"
              type="password"
            />
            <Button href="/consent">다음 · 동의 확인</Button>
            <Link className={classNames("center-link", styles)} href="/">
              이미 계정이 있어요 · 로그인
            </Link>
          </>
        )}
        {screen === "reset" && (
          <>
            <Field
              label="가입한 이메일"
              placeholder="이메일 주소를 입력해 주세요"
              type="email"
            />
            <Notice title="이메일로 본인 확인">
              가입한 이메일로 재설정 링크를 보내드려요.
            </Notice>
            <Button href="/reset-password/sent">재설정 링크 보내기</Button>
            <Button variant="secondary" href="/">
              로그인으로 돌아가기
            </Button>
          </>
        )}
        {screen === "sent" && (
          <>
            <Notice title="이메일을 확인해 주세요">
              재설정 링크를 보냈어요. 받은 메일에서 본인 확인을 이어가 주세요.
            </Notice>
            <Button href="/">로그인으로 돌아가기</Button>
            <Link
              className={classNames("center-link", styles)}
              href="/reset-password/new"
            >
              본인 확인 후 · 새 비밀번호 설정 →
            </Link>
          </>
        )}
        {screen === "password" && (
          <>
            <Field
              label="새 비밀번호"
              placeholder="새 비밀번호를 입력해 주세요"
              type="password"
            />
            <Field
              label="새 비밀번호 확인"
              placeholder="새 비밀번호를 다시 입력해 주세요"
              type="password"
            />
            <Button href="/">비밀번호 변경</Button>
            <p className={classNames("muted", styles)}>
              이메일 본인 확인을 완료한 단계입니다.
            </p>
          </>
        )}
        {screen === "consent" && (
          <>
            {[
              "서비스 이용약관",
              "개인정보 수집 및 이용",
              "대화 기록 및 AI 분석 활용",
              "AI 클론 활용 및 권한 기반 제공",
            ].map((t, i) => (
              <label className={classNames("consent-row", styles)} key={t}>
                <input type="checkbox" defaultChecked={i < 3} />
                <span>
                  <strong>{t}</strong>
                  <small>
                    {i < 3 ? "필수" : "선택"} ·{" "}
                    {
                      [
                        "서비스 이용 조건을 확인해 주세요.",
                        "계정과 대화 기록을 안전하게 관리해요.",
                        "대화에서 나의 기억과 선호를 기록해요.",
                        "허용한 정보이용자가 클론과 대화할 수 있어요.",
                      ][i]
                    }
                  </small>
                </span>
              </label>
            ))}
            <Button href="/chat/empty">동의하고 시작하기</Button>
            <p className={classNames("muted", styles)}>
              동의 상태는 계정 설정에서 언제든 확인할 수 있어요.
            </p>
          </>
        )}
      </div>
    </>
  );
}
