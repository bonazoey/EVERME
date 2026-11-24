"use client";

import Card from "@/components/Card";
import Button from "@/components/Button";
import Notice from "@/components/Notice";

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
export default function EvermeSettings({ personality, voice, onPersonalityChange, onVoiceChange, onSave, onSettingsChange }) {
  return (
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
                          onVoiceChange(option.id);
                          onSettingsChange();
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
          <Button onClick={onSave}>음성 설정 저장</Button>
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
                    onPersonalityChange(template.id);
                    onSettingsChange();
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
          <Button onClick={onSave}>성격 설정 저장</Button>
        </div>
      </Card>
    </section>
  );
}
