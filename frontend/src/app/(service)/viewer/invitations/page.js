"use client";

import { useCallback, useState } from "react";
import Card from "@/components/Card";
import Badge from "@/components/Badge";
import Button from "@/components/Button";
import Modal from "@/components/Modal";
export default function InvitationsPage() {
  const [declining, setDeclining] = useState(false);
  const closeModal = useCallback(() => setDeclining(false), []);
  return (
    <>
      <main className="p-[34px_40px_48px] max-w-400 m-[0_auto] [&_>_p.ui-muted]:mb-8 [@media(max-width:_1100px)]:p-[28px_24px] [@media(max-width:_760px)]:p-[24px_20px] [@media(max-width:_760px)]:[&_h1]:text-[23px]">
        <h1>함께 이야기를 이어갈 초대</h1>
        <p className="ui-muted text-muted">
          초대를 수락하면 허용된 접근 등급의 AI 클론과 대화할 수 있어요.
        </p>
        <Card>
          <div className="flex items-center gap-5 [&_small]:block [&_small]:text-muted [&_small]:mt-2 [&_h2]:m-0 [&_.ui-badge]:ml-auto">
            <span className="ui-avatar w-10 h-10 bg-green-light text-green rounded-full inline-flex items-center justify-center shrink-0 font-semibold [&.ui-common-large]:w-16 [&.ui-common-large]:h-16 [&.ui-common-large]:text-[22px] ui-common-large">
              수
            </span>
            <div>
              <h2>이수진님이 초대했어요</h2>
              <p className="ui-muted text-muted">
                2026.11.23 · sujin@email.com
              </p>
            </div>
            <Badge>L2 개인</Badge>
          </div>
          <p>최대 L2 개인 등급의 정보까지 접근할 수 있어요.</p>
          <div className="flex gap-3">
            <Button href="/viewer/clones">초대 수락</Button>
            <Button variant="secondary" onClick={() => setDeclining(true)}>
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
      {declining && (
        <Modal title="초대를 거절하시겠어요?" onClose={closeModal}>
          <p className="ui-muted text-muted">
            이수진님의 AI 클론을 이용하려면 다시 초대받아야 해요.
          </p>
          <div className="flex gap-3 mt-9 [&_.ui-button]:flex-1">
            <Button onClick={closeModal} variant="secondary">
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
