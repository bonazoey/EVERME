"use client";

import Card from "@/components/Card";
import Badge from "@/components/Badge";

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
export default function PrivacySettings() {
  return (
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
  );
}
