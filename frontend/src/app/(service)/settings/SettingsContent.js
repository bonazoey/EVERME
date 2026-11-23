"use client";

import { useCallback, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import Card from "@/components/Card";
import Badge from "@/components/Badge";
import Button from "@/components/Button";
import Field from "@/components/Field";
import Notice from "@/components/Notice";
import Modal from "@/components/Modal";
import Icon from "@/components/Icon";
const personalities = [
  {
    id: "warm",
    name: "따뜻한 공감형",
    description: "마음을 먼저 살피고 다정하게 이야기를 들어줘요.",
    example: "그런 하루였군요. 가장 마음에 남은 순간을 들려주시겠어요?",
  },
  {
    id: "bright",
    name: "밝은 친구형",
    description: "친근하고 경쾌하게 일상의 즐거움을 함께 찾아요.",
    example: "작지만 기분 좋은 발견이네요! 오늘 또 즐거웠던 일이 있나요?",
  },
  {
    id: "calm",
    name: "차분한 경청형",
    description: "서두르지 않고 담백하게, 나의 속도에 맞춰 대화해요.",
    example: "천천히 이야기해 주세요. 그때 어떤 생각이 드셨나요?",
  },
  {
    id: "curious",
    name: "호기심 많은 탐구형",
    description: "구체적인 질문으로 기억과 생각을 더 깊이 들여다봐요.",
    example: "그 장면이 특별하게 남은 이유는 무엇일까요?",
  },
  {
    id: "clear",
    name: "명료한 정리형",
    description: "이야기의 핵심을 짚고 생각을 차근차근 정리해 줘요.",
    example: "말씀을 모아보면 가족과 보내는 시간이 가장 소중하셨군요.",
  },
];
const voiceGroups = [
  {
    title: "남성 목소리",
    voices: [
      {
        id: "male-calm",
        name: "차분한 민준",
        description: "낮고 안정적인 톤으로 편안하게 이야기해요.",
      },
      {
        id: "male-warm",
        name: "다정한 도윤",
        description: "부드럽고 따뜻한 톤으로 마음을 전해요.",
      },
      {
        id: "male-bright",
        name: "밝은 지호",
        description: "경쾌하고 친근한 톤으로 활기를 더해요.",
      },
      {
        id: "male-clear",
        name: "또렷한 서준",
        description: "명료하고 단정한 톤으로 내용을 전달해요.",
      },
    ],
  },
  {
    title: "여성 목소리",
    voices: [
      {
        id: "female-calm",
        name: "차분한 서연",
        description: "잔잔하고 안정적인 톤으로 이야기를 이어가요.",
      },
      {
        id: "female-warm",
        name: "포근한 하은",
        description: "온화하고 다정한 톤으로 편안함을 전해요.",
      },
      {
        id: "female-bright",
        name: "밝은 지우",
        description: "생기 있고 맑은 톤으로 즐겁게 대화해요.",
      },
    ],
  },
];
const consentDetails = [
  {
    title: "개인정보 수집 및 이용",
    description: "계정을 식별하고 기본적인 서비스를 제공하기 위한 정보입니다.",
    items: [
      ["기본 항목", "이름, 이메일 주소 등 계정에 등록한 정보를 확인합니다."],
      [
        "계정 관리",
        "로그인과 계정 식별, 계정 정보 변경을 위한 용도로 안내합니다.",
      ],
      [
        "서비스 안내",
        "초대와 계정 관련 알림 등 서비스 이용에 필요한 안내에 활용하는 항목입니다.",
      ],
      [
        "정보 확인",
        "등록된 이름과 이메일은 이 화면의 기본 설정에서 확인할 수 있습니다.",
      ],
      [
        "처리 기준",
        "실제 수집 항목과 보유 기간은 서비스에서 고지하는 개인정보 처리방침을 기준으로 합니다.",
      ],
    ],
  },
  {
    title: "대화 기록 및 AI 분석 활용",
    description:
      "내가 남긴 이야기가 기억과 선호를 정리하는 데 어떻게 쓰이는지 확인합니다.",
    items: [
      [
        "대상 정보",
        "대화에 직접 입력한 일상, 경험, 관심사와 기억 등의 내용을 다룹니다.",
      ],
      [
        "활용 목적",
        "대화의 맥락을 이어가고, 기록에서 반복되는 주제와 주요 특징을 정리하는 항목입니다.",
      ],
      [
        "분석 예시",
        "좋아하는 음식, 소중한 사람, 기억에 남는 여행처럼 대화에서 드러난 내용을 정리합니다.",
      ],
      [
        "사실과 추론",
        "직접 남긴 기록과 AI가 해석한 내용은 서로 구분해서 확인해야 합니다.",
      ],
      [
        "확인 시 유의점",
        "AI의 해석은 실제 생각이나 성격에 대한 확정적인 판단이 아니며, 답변에는 오류가 있을 수 있습니다.",
      ],
      ["기록 정정", "대화 화면에서 내 발언을 확인하고 정정할 수 있습니다."],
    ],
  },
  {
    title: "AI 클론 활용 및 정보 제공",
    description:
      "정보이용자와 나누는 정보의 범위와 AI 클론의 역할을 확인합니다.",
    items: [
      [
        "이용 대상",
        "정보이용자 관리 화면에서 초대한 사람과 그룹을 확인합니다.",
      ],
      [
        "공유 범위",
        "이용자에게 설정한 최대 접근 범위 이하의 정보를 제공하는 구조입니다.",
      ],
      [
        "범위 예시",
        "취미·관심사, 가족관계·개인적인 경험, 건강·종교, 매우 민감한 기록 등으로 나뉩니다.",
      ],
      [
        "제공 방식",
        "정보이용자는 AI 대변인과의 대화를 통해 허용된 기록에 관한 답변을 확인합니다.",
      ],
      [
        "응답 확인",
        "답변에 표시된 기록된 사실, AI 추론과 응답 근거를 함께 확인합니다.",
      ],
      [
        "역할의 한계",
        "AI 클론의 답변은 본인의 현재 의사나 동의를 대신하지 않습니다.",
      ],
      [
        "접근 관리",
        "이용자와 그룹의 접근 범위는 정보이용자 관리 화면에서 확인하고 조절합니다.",
      ],
    ],
  },
];
export default function SettingsContent() {
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
        <section aria-labelledby="basic-settings" className="mt-10">
          <h2 id="basic-settings" className="mb-5 text-[22px] font-bold">
            기본 설정
          </h2>
          <div className="grid grid-cols-2 items-stretch gap-6 [&_>_.ui-card]:m-0 [&_>_.ui-card]:min-w-0 [@media(max-width:_1100px)]:grid-cols-1">
            <Card>
              <h3>내 정보</h3>
              <Field label="이름" defaultValue="김영희" />
              <Field
                label="이메일"
                type="email"
                defaultValue="younghee@email.com"
                disabled
                className="disabled:cursor-not-allowed disabled:bg-surface disabled:text-muted"
              />
              <div className="grid grid-cols-2 gap-x-6 [@media(max-width:_760px)]:grid-cols-1">
                <Field
                  label="생년월일"
                  type="date"
                  name="birthDate"
                  autoComplete="bday"
                />
                <Field
                  label="국적"
                  name="nationality"
                  placeholder="예: 대한민국"
                />
                <Field
                  label="전화번호"
                  type="tel"
                  name="phone"
                  autoComplete="tel"
                  placeholder="010-1234-5678"
                />
                <label className="my-[22px] flex flex-col gap-3 text-[14px]">
                  <span>성별</span>
                  <select
                    name="gender"
                    defaultValue=""
                    className="h-12.5 w-full rounded-lg border border-line bg-white px-4 text-ink"
                  >
                    <option value="" disabled>
                      선택해 주세요
                    </option>
                    <option value="male">남성</option>
                    <option value="female">여성</option>
                    <option value="other">기타</option>
                    <option value="unspecified">선택하지 않음</option>
                  </select>
                </label>
              </div>
              <div className="flex justify-end">
                <Button onClick={() => setSaved(true)}>변경사항 저장</Button>
              </div>
            </Card>
            <Card>
              <h3>비밀번호 및 계정</h3>
              <p>
                <Link
                  className="ui-text-link flex items-center justify-between gap-3 text-green font-medium"
                  href="/reset-password"
                >
                  비밀번호 재설정
                  <span className="inline-flex size-8 shrink-0 items-center justify-center rounded-full bg-green text-white">
                    <Icon name="arrow-right" size={18} />
                  </span>
                </Link>
              </p>
              <div className="my-6 divide-y divide-line">
                {[
                  [
                    "devices",
                    "로그인 기기 관리",
                    "접속 중인 기기를 확인하고 개별 로그아웃해요.",
                  ],
                  [
                    "logout-all",
                    "전체 기기 로그아웃",
                    "현재 기기를 포함한 모든 로그인 세션을 종료해요.",
                  ],
                  [
                    "two-factor",
                    "2단계 인증",
                    "로그인 시 추가 인증을 사용하는 설정이에요.",
                  ],
                  [
                    "connections",
                    "연결된 계정",
                    "소셜 로그인 계정의 연결 상태를 관리해요.",
                  ],
                  [
                    "export",
                    "내 데이터 내려받기",
                    "대화와 기억 기록을 파일로 보관해요.",
                  ],
                ].map(([id, title, description]) => (
                  <button
                    key={id}
                    type="button"
                    onClick={() => setModal(id)}
                    className="flex w-full items-center justify-between gap-4 py-5 text-left hover:text-green"
                  >
                    <span className="min-w-0">
                      <strong className="block text-[14px]">{title}</strong>
                      <span className="mt-1 block text-[13px] text-muted">
                        {description}
                      </span>
                    </span>
                    <span aria-hidden="true" className="shrink-0">
                      →
                    </span>
                  </button>
                ))}
              </div>
              <button
                type="button"
                className="ui-muted text-muted"
                onClick={() => setModal("delete")}
              >
                회원탈퇴
              </button>
            </Card>
          </div>
        </section>
        <section aria-labelledby="everme-settings" className="mt-12">
          <h2 id="everme-settings" className="mb-5 text-[22px] font-bold">
            EVERME 설정
          </h2>
          <Card>
            <h3>EVERME 음성</h3>
            <p className="ui-muted text-muted">
              대화할 때 듣고 싶은 AI 목소리를 선택하세요.
            </p>
            <fieldset className="m-0 mb-6 border-0 p-0">
              <legend className="sr-only">AI 목소리 선택</legend>
              <div className="grid grid-cols-2 gap-6 [@media(max-width:_760px)]:grid-cols-1">
                {voiceGroups.map((group) => (
                  <div key={group.title} className="min-w-0">
                    <h4 className="mb-3 font-semibold text-green">
                      {group.title}
                    </h4>
                    <div className="space-y-3">
                      {group.voices.map((option) => (
                        <label
                          key={option.id}
                          className="flex cursor-pointer items-start gap-3 rounded-xl border border-line p-4 has-[:checked]:border-green has-[:checked]:bg-green-light"
                        >
                          <input
                            type="radio"
                            name="voice"
                            value={option.id}
                            checked={voice === option.id}
                            onChange={() => {
                              setVoice(option.id);
                              setSaved(false);
                            }}
                            className="mt-1"
                          />
                          <span className="min-w-0">
                            <strong className="block">{option.name}</strong>
                            <span className="mt-1 block text-[13px] text-muted">
                              {option.description}
                            </span>
                          </span>
                        </label>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </fieldset>
            <div className="flex justify-end">
              <Button onClick={() => setSaved(true)}>음성 설정 저장</Button>
            </div>
          </Card>
          <Notice title="대화 방식은 대화 중에도 바꿀 수 있어요">
            텍스트와 음성 대화는 같은 세션에서 이어져요. 응답 재생은 언제든
            중단할 수 있어요.
          </Notice>
          <Card>
            <h3>EVERME 성격 설정</h3>
            <p className="text-muted">
              나와 대화할 EVERME의 성격을 골라 주세요.
            </p>
            <fieldset className="m-0 border-0 p-0">
              <legend className="sr-only">EVERME 성격 템플릿</legend>
              <div className="grid grid-cols-2 gap-4 [@media(max-width:_760px)]:grid-cols-1">
                {personalities.map((template) => (
                  <label
                    key={template.id}
                    className="flex cursor-pointer items-start gap-3 rounded-xl border border-line p-5 has-[:checked]:border-green has-[:checked]:bg-green-light"
                  >
                    <input
                      type="radio"
                      name="personality"
                      value={template.id}
                      checked={personality === template.id}
                      onChange={() => {
                        setPersonality(template.id);
                        setSaved(false);
                      }}
                      className="mt-1"
                    />
                    <span className="min-w-0">
                      <strong className="block">{template.name}</strong>
                      <span className="mt-1 block text-[13px] text-muted">
                        {template.description}
                      </span>
                      <span className="mt-3 block text-[13px]">
                        “{template.example}”
                      </span>
                    </span>
                  </label>
                ))}
              </div>
            </fieldset>
            <div className="mt-6 flex justify-end">
              <Button onClick={() => setSaved(true)}>성격 설정 저장</Button>
            </div>
          </Card>
        </section>
        <section aria-labelledby="privacy-settings" className="mt-12">
          <h2 id="privacy-settings" className="mb-5 text-[22px] font-bold">
            개인 정보 및 동의
          </h2>
          <p className="text-[14px] text-muted">
            동의 항목과 활용 범위를 확인하는 안내 예시입니다. 실제 적용 내용은
            서비스의 약관과 개인정보 처리방침에서 확인해 주세요.
          </p>
          {consentDetails.map((consent) => (
            <Card key={consent.title}>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <h3 className="m-0 text-[18px]">{consent.title}</h3>
                <Badge>동의 중</Badge>
              </div>
              <p className="text-[14px] text-muted">{consent.description}</p>
              <dl className="m-0 divide-y divide-line">
                {consent.items.map(([title, description]) => (
                  <div
                    key={title}
                    className="grid grid-cols-[140px_minmax(0,1fr)] gap-4 py-4 text-[14px] [@media(max-width:_760px)]:grid-cols-1 [@media(max-width:_760px)]:gap-1"
                  >
                    <dt className="font-semibold">{title}</dt>
                    <dd className="m-0 text-muted">{description}</dd>
                  </div>
                ))}
              </dl>
            </Card>
          ))}
        </section>
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
