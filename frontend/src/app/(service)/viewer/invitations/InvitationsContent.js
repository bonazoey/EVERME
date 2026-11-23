import modalStyles from "../../../../components/Modal.module.css";
import commonStyles from "../../../../components/Common.module.css";
import pageStyles from "../../../../components/Page.module.css";
import classNames from "../../../../lib/classNames.js";
import Card from "@/components/Card";
import Badge from "@/components/Badge";
import Button from "@/components/Button";
import Modal from "@/components/Modal";
const styles = {
  ...modalStyles,
  ...commonStyles,
  ...pageStyles,
};
export default function InvitationsContent({ variant = "" }) {
  return (
    <>
      <main className={classNames("page-content", styles)}>
        <h1>함께 이야기를 이어갈 초대</h1>
        <p className={classNames("muted", styles)}>
          초대를 수락하면 허용된 접근 등급의 AI 클론과 대화할 수 있어요.
        </p>
        <Card>
          <div className={classNames("person", styles)}>
            <span className={classNames("avatar large", styles)}>수</span>
            <div>
              <h2>이수진님이 초대했어요</h2>
              <p className={classNames("muted", styles)}>
                2026.11.23 · sujin@email.com
              </p>
            </div>
            <Badge>L2 개인</Badge>
          </div>
          <p>최대 L2 개인 등급의 정보까지 접근할 수 있어요.</p>
          <div className={classNames("inline-actions", styles)}>
            <Button href="/viewer/clones">초대 수락</Button>
            <Button variant="secondary" href="/viewer/invitations/decline">
              거절
            </Button>
          </div>
        </Card>
        <Card>
          <h2>이전 초대</h2>
          <table>
            <thead>
              <tr>
                {["초대한 사람", "초대 일시", "접근 등급", "상태"].map((t) => (
                  <th key={t}>{t}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {["김영희", "박정호"].map((t, i) => (
                <tr key={t}>
                  <td>{t}</td>
                  <td>2026.11.{i ? "18" : "20"}</td>
                  <td>L{i ? "1 일반" : "2 개인"}</td>
                  <td>
                    <Badge>수락 완료</Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      </main>
      {variant === "decline" && (
        <Modal title="초대를 거절하시겠어요?" closeHref="/viewer/invitations">
          <p className={classNames("muted", styles)}>
            이수진님의 AI 클론을 이용하려면 다시 초대받아야 해요.
          </p>
          <div className={classNames("modal-actions", styles)}>
            <Button href="/viewer/invitations" variant="secondary">
              취소
            </Button>
            <Button href="/viewer/clones" variant="danger">
              초대 거절
            </Button>
          </div>
        </Modal>
      )}
    </>
  );
}
