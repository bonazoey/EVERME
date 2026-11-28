"use client";

import { useContext, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import Button from "@/components/Button";
import Field from "@/components/Field";
import { AuthContext } from "@/app/context/AuthContext";
import { memberLogin } from "../../../apis/memberApi";
const LoginPage = () => {
  const { login } = useContext(AuthContext);

  const [loginForm, setLoginForm] = useState({ email: "", password: "" });
  const [errors, setErrors] = useState({});
  const [submitted, setSubmitted] = useState(false);
  const [loginError, setLoginError] = useState("");
  const [isLoggingIn, setIsLoggingIn] = useState(false);
  const fieldsRef = useRef(null);
  const router = useRouter();

  const validateLogin = (values) => {
    const result = {};
    if (!values.email.trim()) {
      result.email = "이메일을 입력해 주세요.";
    } else if (fieldsRef.current.querySelector('[name="email"]').validity.typeMismatch) {
      result.email = "올바른 이메일 주소를 입력해 주세요.";
    }
    if (!values.password) result.password = "비밀번호를 입력해 주세요.";
    return result;
  };

  const handleLoginChange = (event) => {
    const { name, value } = event.currentTarget;
    const nextForm = { ...loginForm, [name]: value };
    setLoginError("");
    setLoginForm(nextForm);
    if (submitted) setErrors(validateLogin(nextForm));
  };

  const handleLogin = async () => {
    if (isLoggingIn) return;
    const nextErrors = validateLogin(loginForm);
    setSubmitted(true);
    setErrors(nextErrors);
    const firstError = Object.keys(nextErrors)[0];
    if (firstError) {
      fieldsRef.current.querySelector(`[name="${firstError}"]`)?.focus();
      return;
    }
    setLoginError("");
    setIsLoggingIn(true);
    try {
      const email = loginForm.email.trim();
      const response = await memberLogin({ ...loginForm, email });
      const token = response.data?.token;
      if (typeof token !== "string" || !token) {
        setLoginError("로그인 응답을 확인할 수 없습니다. 다시 시도해 주세요.");
        return;
      }
      login(token);
      router.push("/owner/chat");
    } catch (error) {
      setLoginError(error.response?.status === 401
        ? "이메일 또는 비밀번호가 올바르지 않습니다."
        : "로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setIsLoggingIn(false);
    }
  };
  return (
    <>
      <h2>서비스 소개 및 로그인</h2>
      <p className="ui-muted text-muted">나의 이야기를 이어가 볼까요?</p>
      <div ref={fieldsRef} className="mt-11 [&_>_.ui-button]:flex [&_>_.ui-button]:w-full [&_>_.ui-button]:m-[10px_0] [&_.ui-field]:m-[0_0_26px]">
        <Field
          label="이메일"
          placeholder="이메일 주소를 입력해 주세요"
          type="email"
          name="email"
          value={loginForm.email}
          onChange={handleLoginChange}
          error={errors.email}
          autoComplete="email"
          required
        />
        <Field
          label="비밀번호"
          placeholder="비밀번호를 입력해 주세요"
          type="password"
          name="password"
          value={loginForm.password}
          onChange={handleLoginChange}
          error={errors.password}
          autoComplete="current-password"
          required
        />
        <Link
          className="block text-right text-muted text-[14px] m-[0_0_24px]"
          href="/reset-password"
        >
          비밀번호를 잊으셨나요?
        </Link>
        {loginError && <p role="alert" className="text-sm text-red-600">{loginError}</p>}
        <Button onClick={handleLogin} disabled={isLoggingIn}>
          {isLoggingIn ? "로그인 중…" : "로그인"}
        </Button>
        <div
          className={
            'text-center text-muted text-[13px] flex items-center gap-3.5 m-[36px_0_24px] [&:before]:[content:""] [&:before]:h-[1px] [&:before]:bg-[var(--line)] [&:before]:flex-1 [&:after]:[content:""] [&:after]:h-[1px] [&:after]:bg-[var(--line)] [&:after]:flex-1'
          }
        >
          또는 소셜 계정으로 시작하기
        </div>
        <Button variant="secondary" href="/owner/chat">
          카카오로 계속하기
        </Button>
        <Button variant="secondary" href="/owner/chat">
          Google로 계속하기
        </Button>
        <Link
          className="block text-center text-green text-[14px] mt-5.5"
          href="/signup"
        >
          처음이신가요? 회원가입
        </Link>
      </div>
    </>
  );
};

export default LoginPage;
