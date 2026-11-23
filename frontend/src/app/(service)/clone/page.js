"use client";

import { useSyncExternalStore } from "react";
import Card from "@/components/Card";
import Badge from "@/components/Badge";
import Button from "@/components/Button";
import Notice from "@/components/Notice";

const updatedAt = new Date("2026-11-23T09:20:00+09:00");
const updateDate = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  year: "numeric",
  month: "long",
  day: "numeric",
}).format(updatedAt);

function subscribeToClock(onChange) {
  const timer = setInterval(onChange, 60_000);
  return () => clearInterval(timer);
}
function getCurrentMinute() {
  return Math.floor(Date.now() / 60_000);
}
function getServerMinute() {
  return null;
}

function formatUpdatedTime(currentMinute) {
  if (currentMinute === null) return updateDate;
  const now = currentMinute * 60_000;
  const minutes = Math.max(0, Math.floor((now - updatedAt.getTime()) / 60_000));
  const seoulOffset = 9 * 60 * 60 * 1000;
  const days =
    Math.floor((now + seoulOffset) / 86_400_000) -
    Math.floor((updatedAt.getTime() + seoulOffset) / 86_400_000);
  if (minutes < 1) return "방금 전";
  if (minutes < 60) return `${minutes}분 전`;
  if (days === 0) return `${Math.floor(minutes / 60)}시간 전`;
  if (days === 1) return "어제";
  if (days < 7) return `${days}일 전`;
  return updateDate;
}

export default function ClonePage() {
  const currentMinute = useSyncExternalStore(
    subscribeToClock,
    getCurrentMinute,
    getServerMinute,
  );
  const completeness = 87;
  const information = [
    ["기본 정보", 100],
    ["경험 / 기억", 86],
    ["선호 / 취향", 82],
    ["가치관 / 신념", 79],
    ["관계 / 주변", 85],
  ];
  const highlights = [
    [
      "가족 중심의 일상",
      "가족과 함께하는 시간을 소중히 여기며, 함께한 일상의 경험을 나누는 것을 좋아해요.",
    ],
    [
      "기억과 경험의 연결",
      "익숙한 경험과 오래 간직한 기억을 바탕으로 나의 이야기를 이어가요.",
    ],
    [
      "따뜻한 대화",
      "소소한 일상과 새로운 이야기를 편안하게 주고받는 모습을 보여요.",
    ],
  ];

  return (
    <main className="p-[34px_40px_48px] max-w-400 mx-auto [@media(max-width:_1100px)]:p-[28px_24px] [@media(max-width:_760px)]:p-[24px_20px]">
      <header className="flex items-center justify-between gap-6 mb-8 [@media(max-width:_760px)]:flex-col [@media(max-width:_760px)]:items-start">
        <div>
          <p className="text-[12px] font-bold tracking-[0.12em] text-green mb-3">
            MY AI CLONE
          </p>
          <h1 className="mb-3 [@media(max-width:_760px)]:text-[24px]">
            대화 속 나를 이해하는 AI 클론
          </h1>
          <p className="text-muted m-0">
            쌓여가는 기억과 선호를 통해, 나를 닮은 클론을 만들어가요.
          </p>
        </div>
        <Button href="/chat" className="shrink-0">
          ＋ 대화 이어가기
        </Button>
      </header>

      <div className="grid grid-cols-4 gap-4 mb-5 [&_>_.ui-card]:m-0 [@media(max-width:_1100px)]:grid-cols-2 [@media(max-width:_760px)]:gap-3">
        {[
          ["누적 대화 수", "128건", "지금까지 쌓아온 나의 이야기"],
          ["기록한 기억", "42개", "대화에서 정리한 소중한 경험"],
          ["발견한 키워드", "가족 중심", "기억과 경험 · 따뜻함"],
          [
            "최근 업데이트",
            formatUpdatedTime(currentMinute),
            "2026.11.23 09:20",
          ],
        ].map(([label, value, detail]) => (
          <Card key={label}>
            <p className="text-[13px] text-muted mb-4">{label}</p>
            <strong className="block text-[28px] leading-tight tabular-nums mb-3 [@media(max-width:_760px)]:text-[24px]">
              {value}
            </strong>
            <p className="text-[12px] text-green m-0">{detail}</p>
          </Card>
        ))}
      </div>

      <div className="grid grid-cols-[1.4fr_1fr] gap-5 [&_>_.ui-card]:m-0 [@media(max-width:_1100px)]:grid-cols-1">
        <Card>
          <div className="flex items-center justify-between gap-4 mb-7 [&_h2]:m-0">
            <h2>AI가 이해한 나의 모습</h2>
            <Badge>요약 프로필</Badge>
          </div>
          <div className="flex items-center gap-7 [@media(max-width:_760px)]:flex-col [@media(max-width:_760px)]:items-start">
            <div
              className="relative size-40 shrink-0"
              role="img"
              aria-label={`데이터 완성도 ${completeness}%`}
            >
              <svg
                viewBox="0 0 128 128"
                className="size-full -rotate-90"
                aria-hidden="true"
              >
                <circle
                  cx="64"
                  cy="64"
                  r="52"
                  fill="none"
                  strokeWidth="12"
                  className="stroke-green-light"
                />
                <circle
                  cx="64"
                  cy="64"
                  r="52"
                  fill="none"
                  strokeWidth="12"
                  strokeLinecap="round"
                  pathLength="100"
                  strokeDasharray={`${completeness} ${100 - completeness}`}
                  className="stroke-green"
                />
              </svg>
              <div
                className="absolute inset-0 flex flex-col items-center justify-center gap-2"
                aria-hidden="true"
              >
                <strong className="text-[34px] leading-none text-green tabular-nums">
                  {completeness}
                  <span className="text-[17px]">%</span>
                </strong>
                <span className="text-[11px] text-muted">데이터 완성도</span>
              </div>
            </div>
            <div className="min-w-0">
              <p className="text-[12px] text-muted mb-2">
                김영희님의 대화에서 발견한 모습
              </p>
              <h3 className="text-[24px] mt-0 mb-3 tracking-[-0.5px]">
                일상의 기억을 나누는 사람
              </h3>
              <p className="text-[14px] leading-relaxed text-muted mb-4">
                가족과 함께하는 일상을 소중히 여기며, 익숙한 경험과 새로운
                이야기를 나누는 것을 좋아해요.
              </p>
              <div className="flex flex-wrap gap-2">
                {["가족 중심", "기억과 경험", "일상의 대화", "따뜻함"].map(
                  (tag) => (
                    <Badge key={tag}>{tag}</Badge>
                  ),
                )}
              </div>
            </div>
          </div>
          <p className="rounded-xl bg-surface p-4 text-[14px] leading-relaxed mt-6 mb-5">
            대화에서 드러난 기억과 선호를 바탕으로 김영희님을 이해하는 프로필을
            만들어가고 있어요.
          </p>
          <p className="text-[12px] text-muted m-0">
            새로운 대화가 쌓이면 요약 프로필도 함께 갱신됩니다.
          </p>
        </Card>

        <Card>
          <h2>수집된 정보 현황</h2>
          <div className="space-y-5">
            {information.map(([label, value]) => (
              <div key={label}>
                <div className="flex items-center justify-between gap-4 text-[13px] mb-2">
                  <span className="font-medium">{label}</span>
                  <span className="text-muted tabular-nums">
                    {value}
                    <span className="text-[11px]"> / 100</span>
                  </span>
                </div>
                <progress
                  value={value}
                  max="100"
                  aria-label={`${label} 완성도`}
                  className="block"
                >
                  {value}%
                </progress>
              </div>
            ))}
          </div>
          <p className="text-[12px] text-muted mt-6 mb-0">
            대화를 통해 각 항목의 정보가 조금씩 채워집니다.
          </p>
        </Card>
      </div>

      <div className="mt-5 [&_>_.ui-card]:m-0">
        <Card>
          <div className="flex items-center justify-between gap-4 mb-6 [&_h2]:m-0">
            <h2>대화에서 발견한 주요 특징</h2>
            <span className="text-[12px] text-muted">나를 이해하는 단서</span>
          </div>
          <div className="grid grid-cols-3 gap-6 [@media(max-width:_760px)]:grid-cols-1">
            {highlights.slice(0, 3).map(([title, description], index) => (
              <div key={title} className="border-t border-line pt-4">
                <span className="text-[12px] font-semibold text-green tabular-nums">
                  0{index + 1}
                </span>
                <h3 className="mt-3 mb-2 text-[15px]">{title}</h3>
                <p className="text-[13px] text-muted leading-relaxed m-0">
                  {description}
                </p>
              </div>
            ))}
          </div>
        </Card>
      </div>

      <div id="access-history" className="mt-5 scroll-mt-6 [&_>_.ui-card]:m-0">
        <Card>
          <div className="flex items-center justify-between gap-4 mb-5 [@media(max-width:_760px)]:flex-col [@media(max-width:_760px)]:items-start [&_h2]:m-0">
            <div>
              <h2>접근 이력</h2>
              <p className="text-[13px] text-muted mt-2 mb-0">
                AI 클론에 대한 정보이용자의 접근과 이용 내역입니다.
              </p>
            </div>
            <Button variant="secondary" href="/members">
              정보이용자 관리
            </Button>
          </div>
          <div className="overflow-x-auto">
            <table className="min-w-[680px]">
              <thead>
                <tr>
                  {[
                    "접근 일시",
                    "정보이용자",
                    "이용 대상",
                    "접근 유형",
                    "결과 / 상세",
                  ].map((t) => (
                    <th key={t}>{t}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {[
                  ["11.23 09:42", "김민수", "텍스트 대화", "허용됨"],
                  ["11.22 18:20", "박지은", "음성 대화", "허용됨"],
                  ["11.22 18:18", "박지은", "원본 근거 조회", "등급 초과 제한"],
                  ["11.21 12:10", "김민수", "원본 근거 조회", "허용됨"],
                ].map(([d, n, t, s]) => (
                  <tr key={d}>
                    <td>{d}</td>
                    <td>{n}</td>
                    <td>김영희 AI 클론</td>
                    <td>{t}</td>
                    <td>
                      <Badge tone={s === "허용됨" ? "" : "amber"}>{s}</Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="text-[12px] text-muted mt-5 mb-0">
            원하지 않는 이용이 있다면 정보이용자 관리에서 접근 권한을 변경하거나
            철회할 수 있어요.
          </p>
        </Card>
      </div>

      <Notice>
        대화로 쌓인 정보가 클론의 이해도를 높여요. 새로운 대화가 쌓이면 나를
        이해하는 정보도 함께 갱신됩니다.
      </Notice>
    </main>
  );
}
