"use client";

import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import Button from "@/components/Button";
import Field from "@/components/Field";
export default function SignupPage() {
  const [step, setStep] = useState("account");
  const router = useRouter();
  const headingRef = useRef(null);
  function goToStep(step) {
    setStep(step);
    headingRef.current?.focus();
  }
  function confirmAccount(event) {
    event.preventDefault();
    const fields = event.currentTarget.elements;
    const password = fields.namedItem("password");
    const confirmation = fields.namedItem("confirmation");
    if (password.value !== confirmation.value) {
      confirmation.setCustomValidity("비밀번호가 일치하지 않습니다.");
      confirmation.reportValidity();
      return;
    }
    goToStep("consent");
  }
  return (
    <>
      <h2 ref={headingRef} tabIndex={-1}>
        {step === "account" ? "회원가입" : "개인정보 및 AI 활용 동의"}
      </h2>
      <p className="ui-muted text-muted">
        안전한 시작을 위해 필요한 정보를 확인해 주세요.
      </p>
      <form
        hidden={step !== "account"}
        onSubmit={confirmAccount}
        className="mt-11 [&_>_.ui-button]:flex [&_>_.ui-button]:w-full [&_>_.ui-button]:m-[10px_0] [&_.ui-field]:m-[0_0_26px]"
      >
        <Field
          label="이름"
          name="name"
          autoComplete="name"
          placeholder="이름을 입력해 주세요"
          required
        />
        <Field
          label="이메일"
          placeholder="이메일 주소를 입력해 주세요"
          type="email"
          name="email"
          autoComplete="email"
          required
        />
        <Field
          label="비밀번호"
          name="password"
          autoComplete="new-password"
          required
          placeholder="안전한 비밀번호를 입력해 주세요"
          type="password"
        />
        <Field
          label="비밀번호 확인"
          name="confirmation"
          autoComplete="new-password"
          required
          onChange={(event) => event.currentTarget.setCustomValidity("")}
          placeholder="비밀번호를 다시 입력해 주세요"
          type="password"
        />
        <Button type="submit">다음 · 동의 확인</Button>
        <Link
          className="block text-center text-green text-[14px] mt-5.5"
          href="/"
        >
          이미 계정이 있어요 · 로그인
        </Link>
      </form>
      <form
        hidden={step !== "consent"}
        onSubmit={(event) => {
          event.preventDefault();
          router.push("/chat");
        }}
        className="mt-11 [&_>_.ui-button]:flex [&_>_.ui-button]:w-full [&_>_.ui-button]:m-[10px_0] [&_.ui-field]:m-[0_0_26px]"
      >
        {[
          "서비스 이용약관",
          "개인정보 수집 및 이용",
          "대화 기록 및 AI 분석 활용",
          "AI 클론 활용 및 권한 기반 제공",
        ].map((t, i) => (
          <label
            className="flex items-start gap-3.5 m-[22px_0] p-[8px_0] [&_small]:block [&_small]:text-muted [&_small]:mt-2"
            key={t}
          >
            <input type="checkbox" required={i < 3} defaultChecked={i < 3} />
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
        <Button type="submit">동의하고 시작하기</Button>
        <Button variant="secondary" onClick={() => goToStep("account")}>
          이전 · 가입 정보 수정
        </Button>
        <p className="ui-muted text-muted">
          동의 상태는 계정 설정에서 언제든 확인할 수 있어요.
        </p>
      </form>
    </>
  );
}
