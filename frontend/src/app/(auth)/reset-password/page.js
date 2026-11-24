"use client";

import { useRef, useState } from "react";
import Link from "next/link";
import Button from "@/components/Button";
import Field from "@/components/Field";
import Notice from "@/components/Notice";
export default function ResetPasswordPage() {
  const [sent, setSent] = useState(false);
  const headingRef = useRef(null);
  return (
    <>
      <h2 ref={headingRef} tabIndex={-1}>
        비밀번호 재설정
      </h2>
      <p className="ui-muted text-muted">
        안전한 시작을 위해 필요한 정보를 확인해 주세요.
      </p>
      <form
        hidden={sent}
        onSubmit={(event) => {
          event.preventDefault();
          setSent(true);
          headingRef.current?.focus();
        }}
        className="mt-11 [&_>_.ui-button]:flex [&_>_.ui-button]:w-full [&_>_.ui-button]:m-[10px_0] [&_.ui-field]:m-[0_0_26px]"
      >
        <Field
          label="가입한 이메일"
          placeholder="이메일 주소를 입력해 주세요"
          type="email"
          name="email"
          autoComplete="email"
          required
        />
        <Notice title="이메일로 본인 확인">
          가입한 이메일로 재설정 링크를 보내드려요.
        </Notice>
        <Button type="submit">재설정 링크 보내기</Button>
        <Button variant="secondary" href="/">
          로그인으로 돌아가기
        </Button>
      </form>
      {sent && (
        <div
          role="status"
          className="mt-11 [&_>_.ui-button]:flex [&_>_.ui-button]:w-full [&_>_.ui-button]:m-[10px_0] [&_.ui-field]:m-[0_0_26px]"
        >
          <Notice title="이메일을 확인해 주세요">
            재설정 링크를 보냈어요. 받은 메일에서 본인 확인을 이어가 주세요.
          </Notice>
          <Button href="/">로그인으로 돌아가기</Button>
          <Link
            className="block text-center text-green text-[14px] mt-5.5"
            href="/reset-password/new"
          >
            본인 확인 후 · 새 비밀번호 설정 →
          </Link>
        </div>
      )}
    </>
  );
}
