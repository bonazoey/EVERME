"use client";

import { useState, useSyncExternalStore } from "react";
import Card from "@/components/Card";
import Badge from "@/components/Badge";

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

export default function CloneDashboard() {
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
  const keywordDetails = {
    "가족 중심": "가족과 함께하는 시간을 소중히 여기며, 함께한 일상의 경험을 나누는 것을 좋아해요. 가족과의 식사나 소소한 순간들이 대화 속에서 의미 있는 기억으로 나타나요.",
    "기억과 경험": "익숙한 경험과 오래 간직한 기억을 바탕으로 이야기를 이어가요. 과거의 추억을 오늘의 일상과 연결하며, 그때 느꼈던 감정과 의미를 함께 나눠요.",
    "일상의 대화": "특별한 사건뿐 아니라 식사, 산책, 취미처럼 소소한 일상도 편안하게 나눠요. 평범한 하루의 이야기가 쌓이며 관심사와 생활 속 선호가 드러나요.",
    "따뜻함": "가까운 사람들과 안부를 나누고 서로를 배려하는 마음이 대화에서 드러나요. 함께한 순간에 대한 고마움과 다정한 표현에서 관계를 소중히 여기는 모습을 발견할 수 있어요.",
  };
  const [selectedKeyword, setSelectedKeyword] = useState("가족 중심");

  return (
    <>
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
              <div className="flex flex-wrap gap-2" role="group" aria-label="프로필 키워드">
                {Object.keys(keywordDetails).map((tag) => (
                  <button
                    key={tag}
                    type="button"
                    aria-pressed={selectedKeyword === tag}
                    aria-controls="keyword-description"
                    onClick={() => setSelectedKeyword(tag)}
                    className={
                      "inline-flex min-h-11 items-center justify-center rounded-[7px] border px-3 py-2 text-[13px] font-medium cursor-pointer focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green " +
                      (selectedKeyword === tag
                        ? "border-green bg-green text-white"
                        : "border-transparent bg-green-light text-green hover:border-green")
                    }
                  >
                    {tag}
                  </button>
                ))}
              </div>
            </div>
          </div>
          <p
            id="keyword-description"
            aria-live="polite"
            aria-atomic="true"
            className="rounded-xl bg-surface p-4 text-[14px] leading-relaxed mt-6 mb-5"
          >
            <strong className="mb-2 block text-green">{selectedKeyword}</strong>
            {keywordDetails[selectedKeyword]}
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
    </>
  );
}
