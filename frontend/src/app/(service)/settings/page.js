"use client";

import { useCallback, useState } from "react";
import { useRouter } from "next/navigation";
import Badge from "@/components/Badge";
import Button from "@/components/Button";
import Modal from "@/components/Modal";
import BasicSettings from "./BasicSettings";
import EvermeSettings from "./EvermeSettings";
import PrivacySettings from "./PrivacySettings";

export default function Page() {
  const router = useRouter();
  const [modal, setModal] = useState(null);
  const [saved, setSaved] = useState(false);
  const [personality, setPersonality] = useState("warm");
  const [voice, setVoice] = useState("male-calm");
  const [otherDevice, setOtherDevice] = useState(true);
  const [twoFactor, setTwoFactor] = useState(false);
  const [googleConnected, setGoogleConnected] = useState(false);
  const closeModal = useCallback(() => setModal(null), []);
  return (
    <>
      <main className="p-[34px_40px_48px] max-w-400 m-[0_auto] [&_>_p.ui-muted]:mb-8 [@media(max-width:_1100px)]:p-[28px_24px] [@media(max-width:_760px)]:p-[24px_20px] [@media(max-width:_760px)]:[&_h1]:text-[23px]">
        <h1>나에게 맞는 계정 설정</h1>
        <p className="ui-muted text-muted">
          내 정보와 개인정보 활용, 음성 환경을 관리하세요.
        </p>
        <BasicSettings onSave={() => setSaved(true)} onOpenModal={setModal} />
        <EvermeSettings
          personality={personality}
          voice={voice}
          onPersonalityChange={setPersonality}
          onVoiceChange={setVoice}
          onSave={() => setSaved(true)}
          onSettingsChange={() => setSaved(false)}
        />
        <PrivacySettings />
      </main>
      {saved && (
        <div
          className="fixed bottom-8 left-[calc(50%_+_118px)] [transform:translateX(-50%)] bg-green text-white rounded-[10px] p-[16px_24px] shadow-[0_8px_24px_#17212b25] z-50 [&_button]:ml-6 [@media(max-width:_760px)]:left-[50%] [@media(max-width:_760px)]:w-[max-content] [@media(max-width:_760px)]:max-w-[90%] [@media(max-width:_760px)]:text-[13px]"
          role="status"
        >
          ✓ 변경사항이 저장되었어요.{" "}
          <button
            type="button"
            onClick={() => setSaved(false)}
            aria-label="알림 닫기"
          >
            ×
          </button>
        </div>
      )}
      {modal === "devices" && (
        <Modal title="로그인 기기 관리" onClose={closeModal}>
          <p className="text-[13px] text-muted">
            예시 기기 목록입니다. 실제 로그인 세션과 연결되지 않습니다.
          </p>
          <div className="divide-y divide-line">
            <div className="flex flex-wrap items-center justify-between gap-3 py-4">
              <div>
                <strong>Windows · Chrome</strong>
                <small className="block text-muted">
                  현재 기기 · 대한민국 서울
                </small>
              </div>
              <Badge>현재 기기</Badge>
            </div>
            {otherDevice && (
              <div className="flex flex-wrap items-center justify-between gap-3 py-4">
                <div>
                  <strong>iPhone · Safari</strong>
                  <small className="block text-muted">
                    예시 기기 · 최근 접속
                  </small>
                </div>
                <Button
                  variant="secondary"
                  onClick={() => setOtherDevice(false)}
                >
                  로그아웃
                </Button>
              </div>
            )}
          </div>
        </Modal>
      )}
      {modal === "logout-all" && (
        <Modal title="전체 기기 로그아웃" onClose={closeModal}>
          <p className="text-muted">
            모든 로그인 세션 종료 화면의 예시입니다. 실제 계정의 로그인 상태는
            변경되지 않습니다.
          </p>
          <div className="mt-6 flex justify-end gap-3">
            <Button variant="secondary" onClick={closeModal}>
              취소
            </Button>
            <Button
              onClick={() => {
                setOtherDevice(false);
                setModal("logout-preview");
              }}
            >
              전체 기기 로그아웃
            </Button>
          </div>
        </Modal>
      )}
      {modal === "logout-preview" && (
        <Modal title="로그아웃 예시" onClose={closeModal}>
          <p>
            예시 기기 목록에서 다른 기기를 제거했습니다. 실제 세션 종료는 서버
            연동 후 적용됩니다.
          </p>
          <div className="flex justify-end">
            <Button onClick={closeModal}>확인</Button>
          </div>
        </Modal>
      )}
      {modal === "two-factor" && (
        <Modal title="2단계 인증" onClose={closeModal}>
          <p className="text-muted">
            비밀번호 확인 후 추가 인증을 진행하는 기능입니다. 현재는 설정 화면
            예시이며 실제 인증이 활성화되지는 않습니다.
          </p>
          <label className="my-6 flex items-center justify-between gap-4">
            <span>2단계 인증 사용 · 예시</span>
            <input
              type="checkbox"
              checked={twoFactor}
              onChange={(event) => setTwoFactor(event.target.checked)}
            />
          </label>
          <div className="flex justify-end">
            <Button onClick={closeModal}>확인</Button>
          </div>
        </Modal>
      )}
      {modal === "connections" && (
        <Modal title="연결된 계정" onClose={closeModal}>
          <p className="text-[13px] text-muted">
            소셜 로그인 연결 화면의 예시입니다. 실제 계정 연동은 진행하지
            않습니다.
          </p>
          <div className="my-6 flex items-center justify-between gap-4">
            <div>
              <strong>Google</strong>
              <small className="block text-muted">
                {googleConnected ? "연결됨 · 예시" : "연결되지 않음"}
              </small>
            </div>
            <Button
              variant="secondary"
              onClick={() => setGoogleConnected((current) => !current)}
            >
              {googleConnected ? "연결 해제" : "연결"}
            </Button>
          </div>
        </Modal>
      )}
      {modal === "export" && (
        <Modal title="내 데이터 내려받기" onClose={closeModal}>
          <p className="text-muted">
            대화와 기억을 JSON 파일로 내려받는 화면입니다. 현재는 실제 개인
            기록이 포함되지 않은 예시 파일만 제공합니다.
          </p>
          <div className="mt-6 flex justify-end">
            <Button
              onClick={() => {
                const file = new Blob(
                  [
                    JSON.stringify(
                      {
                        example: true,
                        conversations: [
                          {
                            title: "가족과 함께한 저녁",
                            messages: [
                              {
                                role: "user",
                                content: "가족과 함께 저녁을 먹었어.",
                              },
                            ],
                          },
                        ],
                        memories: [
                          { content: "가족과의 식사 시간을 소중히 여김" },
                        ],
                      },
                      null,
                      2,
                    ),
                  ],
                  { type: "application/json;charset=utf-8" },
                );
                const url = URL.createObjectURL(file);
                const link = document.createElement("a");
                link.href = url;
                link.download = "everme-data-example.json";
                document.body.appendChild(link);
                link.click();
                link.remove();
                window.setTimeout(() => URL.revokeObjectURL(url), 1000);
              }}
            >
              예시 데이터 내려받기
            </Button>
          </div>
        </Modal>
      )}
      {modal === "delete" && (
        <Modal title="EVERME를 떠나시겠어요?" onClose={closeModal}>
          <p className="text-muted">
            탈퇴 요청 후 계정 이용과 개인정보 활용이 중단됩니다. 관련 데이터는
            개인정보 처리 정책에 따라 처리됩니다.
          </p>
          <div className="mt-9 flex gap-3 [&_.ui-button]:flex-1">
            <Button variant="secondary" onClick={closeModal}>
              취소
            </Button>
            <Button variant="danger" onClick={() => router.push("/")}>
              탈퇴 요청
            </Button>
          </div>
        </Modal>
      )}
    </>
  );
}
