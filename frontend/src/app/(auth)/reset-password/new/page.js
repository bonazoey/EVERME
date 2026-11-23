import Button from "@/components/Button";
import Field from "@/components/Field";
export default function NewPasswordPage() {
  return (
    <>
      <h2>새 비밀번호 설정</h2>
      <p className="ui-muted text-muted">
        안전한 시작을 위해 필요한 정보를 확인해 주세요.
      </p>
      <div className="mt-11 [&_>_.ui-button]:flex [&_>_.ui-button]:w-full [&_>_.ui-button]:m-[10px_0] [&_.ui-field]:m-[0_0_26px]">
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
        <p className="ui-muted text-muted">
          이메일 본인 확인을 완료한 단계입니다.
        </p>
      </div>
    </>
  );
}
