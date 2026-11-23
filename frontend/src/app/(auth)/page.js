import Link from "next/link";
import Button from "@/components/Button";
import Field from "@/components/Field";
export default function LoginPage() {
  return (
    <>
      <h2>서비스 소개 및 로그인</h2>
      <p className="ui-muted text-muted">나의 이야기를 이어가 볼까요?</p>
      <div className="mt-11 [&_>_.ui-button]:flex [&_>_.ui-button]:w-full [&_>_.ui-button]:m-[10px_0] [&_.ui-field]:m-[0_0_26px]">
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
          className="block text-right text-muted text-[14px] m-[0_0_24px]"
          href="/reset-password"
        >
          비밀번호를 잊으셨나요?
        </Link>
        <Button href="/chat">로그인</Button>
        <div
          className={
            'text-center text-muted text-[13px] flex items-center gap-3.5 m-[36px_0_24px] [&:before]:[content:""] [&:before]:h-[1px] [&:before]:bg-[var(--line)] [&:before]:flex-1 [&:after]:[content:""] [&:after]:h-[1px] [&:after]:bg-[var(--line)] [&:after]:flex-1'
          }
        >
          또는 소셜 계정으로 시작하기
        </div>
        <Button variant="secondary" href="/chat">
          카카오로 계속하기
        </Button>
        <Button variant="secondary" href="/chat">
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
}
