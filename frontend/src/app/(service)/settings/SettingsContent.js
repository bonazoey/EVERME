import modalStyles from "../../../components/Modal.module.css";
import settingsContentStyles from "./SettingsContent.module.css";
import commonStyles from "../../../components/Common.module.css";
import pageStyles from "../../../components/Page.module.css";
import classNames from "../../../lib/classNames.js";
import Link from "next/link";
import Card from "@/components/Card";
import Badge from "@/components/Badge";
import Button from "@/components/Button";
import Field from "@/components/Field";
import Notice from "@/components/Notice";
import Modal from "@/components/Modal";
const styles = {
  ...modalStyles,
  ...settingsContentStyles,
  ...commonStyles,
  ...pageStyles,
};
export default function SettingsContent({ viewer = false, variant = "" }) {
  const base = viewer ? "/viewer/settings" : "/settings",
    voice = variant.startsWith("voice");
  return (
    <>
      <main className={classNames("page-content", styles)}>
        <h1>나에게 맞는 계정 설정</h1>
        <p className={classNames("muted", styles)}>
          내 정보와 개인정보 활용, 음성 환경을 관리하세요.
        </p>
        <div className={classNames("tabs", styles)}>
          <Link
            className={classNames(!voice ? "selected" : "", styles)}
            href={base}
          >
            내 정보
          </Link>
          <Link
            className={classNames(voice ? "selected" : "", styles)}
            href={base + "/voice"}
          >
            음성 설정
          </Link>
        </div>
        {voice ? (
          <>
            <Card>
              <h2>AI 음성</h2>
              <p className={classNames("muted", styles)}>
                대화할 때 듣고 싶은 AI 목소리를 선택하세요.
              </p>
              {["차분한 목소리", "밝은 목소리"].map((t, i) => (
                <label className={classNames("radio-card", styles)} key={t}>
                  <input type="radio" name="voice" defaultChecked={i === 0} />
                  <span>
                    <strong>{t}</strong>
                    <small>
                      {i ? "또렷하고 따뜻한 음성" : "편안하고 안정적인 음성"}
                    </small>
                  </span>
                  <span className={classNames("muted", styles)}>미리 듣기</span>
                </label>
              ))}
              <Button href={base + "/voice-saved"}>음성 설정 저장</Button>
            </Card>
            <Notice title="대화 방식은 대화 중에도 바꿀 수 있어요">
              텍스트와 음성 대화는 같은 세션에서 이어져요. 응답 재생은 언제든
              중단할 수 있어요.
            </Notice>
          </>
        ) : (
          <div className={classNames("settings-grid", styles)}>
            <Card>
              <h2>내 정보</h2>
              <Field label="이름" defaultValue="김영희" />
              <Field
                label="이메일"
                type="email"
                defaultValue="younghee@email.com"
              />
              <Button href={base + "/saved"}>변경사항 저장</Button>
              <hr />
              <h3>비밀번호 및 계정</h3>
              <p>
                <Link
                  className={classNames("text-link", styles)}
                  href="/reset-password"
                >
                  비밀번호 재설정 →
                </Link>
              </p>
              <Link
                className={classNames("muted", styles)}
                href={base + "/delete"}
              >
                회원탈퇴
              </Link>
            </Card>
            <Card>
              <h2>개인정보 및 동의</h2>
              {[
                [
                  "개인정보 수집 및 이용",
                  "계정과 서비스 이용에 필요한 개인정보를 관리해요.",
                ],
                [
                  "대화 기록 및 AI 분석 활용",
                  "대화를 통해 기억과 선호를 기록하고 분석해요.",
                ],
                [
                  "AI 클론 활용 및 정보 제공",
                  "허용한 정보이용자에게 접근 등급에 맞는 정보를 제공해요.",
                ],
              ].map(([t, d]) => (
                <div className={classNames("privacy-row", styles)} key={t}>
                  <div>
                    <h3>{t}</h3>
                    <p className={classNames("muted", styles)}>{d}</p>
                  </div>
                  <div>
                    <Badge>동의 중</Badge>
                    <Link
                      className={classNames("muted", styles)}
                      href={base + "/withdraw"}
                    >
                      동의 철회
                    </Link>
                  </div>
                </div>
              ))}
              <small className={classNames("muted", styles)}>
                동의 철회 시 연결된 서비스 이용이 제한될 수 있어요.
              </small>
            </Card>
          </div>
        )}
      </main>
      {variant.includes("saved") && (
        <div className={classNames("toast", styles)} role="status">
          ✓ 변경사항이 저장되었어요.{" "}
          <Link href={voice ? base + "/voice" : base} aria-label="알림 닫기">
            ×
          </Link>
        </div>
      )}
      {["withdraw", "delete"].includes(variant) && (
        <Modal
          title={
            variant === "delete"
              ? "EVERME를 떠나시겠어요?"
              : "동의를 철회하시겠어요?"
          }
          closeHref={base}
        >
          <p className={classNames("muted", styles)}>
            {variant === "delete"
              ? "탈퇴 요청 후 계정 이용과 개인정보 활용이 중단됩니다. 관련 데이터는 개인정보 처리 정책에 따라 처리됩니다."
              : "동의 철회 시 연결된 서비스 이용이 제한될 수 있어요."}
          </p>
          <div className={classNames("modal-actions", styles)}>
            <Button variant="secondary" href={base}>
              취소
            </Button>
            <Button variant="danger" href={variant === "delete" ? "/" : base}>
              {variant === "delete" ? "탈퇴 요청" : "동의 철회"}
            </Button>
          </div>
        </Modal>
      )}
    </>
  );
}
