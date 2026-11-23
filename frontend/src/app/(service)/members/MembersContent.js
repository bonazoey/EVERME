import fieldStyles from "../../../components/Field.module.css";
import modalStyles from "../../../components/Modal.module.css";
import membersContentStyles from "./MembersContent.module.css";
import commonStyles from "../../../components/Common.module.css";
import pageStyles from "../../../components/Page.module.css";
import classNames from "../../../lib/classNames.js";
import Link from "next/link";
import Button from "@/components/Button";
import Badge from "@/components/Badge";
import Card from "@/components/Card";
import Notice from "@/components/Notice";
import Modal from "@/components/Modal";
import Field from "@/components/Field";
const styles = {
  ...fieldStyles,
  ...modalStyles,
  ...membersContentStyles,
  ...commonStyles,
  ...pageStyles,
};
const people = [
  ["김민수 · 가족", "minsu@email.com", "수락 완료", "L2 개인"],
  ["이수진 · 가족", "sujin@email.com", "초대 대기", "L2 개인"],
  ["박지은 · 친구", "jieun@email.com", "수락 완료", "L1 일반"],
];
export default function MembersContent({ variant = "" }) {
  const close = "/members";
  return (
    <>
      <main className={classNames("page-content", styles)}>
        <div className={classNames("page-heading", styles)}>
          <div>
            <h1>내 이야기를 나눌 사람들</h1>
            <p className={classNames("muted", styles)}>
              그룹별로 정보이용자와 접근 등급을 함께 관리하세요.
            </p>
          </div>
          <Button href="/members/invite">정보이용자 초대</Button>
        </div>
        <Card>
          <div className={classNames("section-heading", styles)}>
            <div>
              <h2>전체 3명 · 그룹 2개</h2>
              <p className={classNames("muted", styles)}>
                그룹을 펼쳐 구성원을 확인하고 권한을 변경하세요.
              </p>
            </div>
            <Button variant="secondary" href="/members/group">
              ＋ 그룹 만들기
            </Button>
          </div>
          <div className={classNames("table-scroll", styles)}>
            <table>
              <thead>
                <tr>
                  {[
                    "이름 / 관계",
                    "이메일",
                    "초대 상태",
                    "접근 등급",
                    "관리",
                  ].map((t) => (
                    <th key={t}>{t}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {["가족", "친구"].map((group, i) => (
                  <tr className={classNames("group-row", styles)} key={group}>
                    <td colSpan="4">
                      <Link
                        href={
                          variant === "expanded"
                            ? "/members"
                            : "/members/expanded"
                        }
                      >
                        ⌄ {group} <Badge>{i === 0 ? "2명" : "1명"}</Badge>
                      </Link>
                    </td>
                    <td>
                      <Link
                        href="/members/group"
                        aria-label={group + " 그룹 관리"}
                      >
                        ···
                      </Link>
                    </td>
                  </tr>
                ))}
                {people.map(([n, e, s, l]) => (
                  <tr key={e}>
                    <td>{n}</td>
                    <td className={classNames("muted", styles)}>{e}</td>
                    <td>
                      <Badge tone={s === "초대 대기" ? "amber" : ""}>{s}</Badge>
                    </td>
                    <td>
                      <Badge>{l}</Badge>
                    </td>
                    <td>
                      <Link
                        className={classNames("text-link", styles)}
                        href="/members/access"
                      >
                        {s === "초대 대기" ? "초대 상세" : "권한 변경"}
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
        <Notice>
          정보이용자는 부여받은 최대 접근 등급 이하의 정보만 확인할 수 있어요.
        </Notice>
      </main>
      {variant === "invite" && (
        <Modal title="내 이야기를 나눌 사람 초대" closeHref={close}>
          <Field
            label="초대할 이메일"
            type="email"
            placeholder="이메일 주소를 입력해 주세요"
          />
          <label className={classNames("field", styles)}>
            <span>최대 접근 등급</span>
            <select defaultValue="L1 일반">
              {["L1 일반", "L2 개인", "L3 고민감", "L4 최고민감"].map((t) => (
                <option key={t}>{t}</option>
              ))}
            </select>
          </label>
          <Notice title="접근 등급을 확인해 주세요">
            선택한 등급 이하의 정보만 이용할 수 있어요.
          </Notice>
          <div className={classNames("modal-actions", styles)}>
            <Button href={close} variant="secondary">
              취소
            </Button>
            <Button href={close}>초대 보내기</Button>
          </div>
        </Modal>
      )}
      {variant === "group" && (
        <Modal title="그룹 관리" closeHref={close}>
          <Field label="그룹 이름" defaultValue="가족" />
          <h3>구성원</h3>
          {["김민수", "이수진"].map((t) => (
            <label className={classNames("consent-row", styles)} key={t}>
              <input type="checkbox" defaultChecked />
              {t}
            </label>
          ))}
          <Notice title="그룹 접근 등급 · 검토 중">
            L2 개인 · 확정 전 적용할 수 없어요
          </Notice>
          <Link
            className={classNames("danger", styles)}
            href="/members/group-delete"
          >
            그룹 삭제
          </Link>
          <div className={classNames("modal-actions", styles)}>
            <Button href={close} variant="secondary">
              취소
            </Button>
            <Button href={close}>그룹 저장</Button>
          </div>
        </Modal>
      )}
      {variant === "access" && (
        <Modal title="접근 등급 설정" closeHref={close} drawer>
          <div className={classNames("person", styles)}>
            <span className={classNames("avatar", styles)}>민</span>
            <div>
              <strong>김민수</strong>
              <small>minsu@email.com</small>
            </div>
          </div>
          <p className={classNames("muted", styles)}>
            선택한 등급 이하의 정보에 접근할 수 있어요.
          </p>
          {[
            ["L1 일반", "취미, 일반적인 관심사"],
            ["L2 개인", "가족관계 등 개인적인 정보"],
            ["L3 고민감", "건강, 종교 등 민감한 정보"],
            ["L4 최고민감", "인증정보 등 매우 민감한 정보"],
          ].map(([t, d], i) => (
            <label className={classNames("radio-card", styles)} key={t}>
              <input type="radio" name="level" defaultChecked={i === 1} />
              <span>
                <strong>{t}</strong>
                <small>{d}</small>
              </span>
            </label>
          ))}
          <Link className={classNames("danger", styles)} href="/members/revoke">
            이용 권한 철회
          </Link>
          <div className={classNames("modal-actions", styles)}>
            <Button href={close} variant="secondary">
              취소
            </Button>
            <Button href={close}>변경사항 저장</Button>
          </div>
        </Modal>
      )}
      {["group-delete", "revoke"].includes(variant) && (
        <Modal
          title={
            variant === "revoke"
              ? "이용 권한을 철회하시겠어요?"
              : "그룹을 삭제하시겠어요?"
          }
          closeHref={close}
        >
          <p className={classNames("muted", styles)}>
            {variant === "revoke"
              ? "김민수님은 더 이상 나의 AI 클론을 이용할 수 없어요."
              : "그룹을 삭제해도 정보이용자는 유지됩니다."}
          </p>
          <div className={classNames("modal-actions", styles)}>
            <Button href={close} variant="secondary">
              취소
            </Button>
            <Button href={close} variant="danger">
              {variant === "revoke" ? "권한 철회" : "그룹 삭제"}
            </Button>
          </div>
        </Modal>
      )}
    </>
  );
}
