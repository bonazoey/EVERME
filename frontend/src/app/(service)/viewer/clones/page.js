import Card from "@/components/Card";
import Badge from "@/components/Badge";
import Button from "@/components/Button";
import Notice from "@/components/Notice";
export default function ClonesPage() {
  return (
    <>
      <main className="p-[34px_40px_48px] max-w-400 m-[0_auto] [&_>_p.ui-muted]:mb-8 [@media(max-width:_1100px)]:p-[28px_24px] [@media(max-width:_760px)]:p-[24px_20px] [@media(max-width:_760px)]:[&_h1]:text-[23px]">
        <h1>나에게 허용된 AI 클론</h1>
        <p className="ui-muted text-muted">
          초대를 수락한 클론만 표시됩니다. 실제 사용자와의 대화가 아닙니다.
        </p>
        <div className="grid [grid-template-columns:1fr_1fr] gap-6 [&_.ui-card_>_.ui-avatar]:mb-6 [&_.ui-button]:mt-3 [@media(max-width:_760px)]:[grid-template-columns:1fr] [@media(max-width:_760px)]:gap-0">
          {[
            ["김영희", "가족", "L2 개인", "23"],
            ["박정호", "친구", "L1 일반", "21"],
          ].map(([n, r, l, d]) => (
            <Card key={n}>
              <span className="ui-avatar w-10 h-10 bg-green-light text-green rounded-full inline-flex items-center justify-center shrink-0 font-semibold [&.ui-common-large]:w-16 [&.ui-common-large]:h-16 [&.ui-common-large]:text-[22px] ui-common-large">
                {n[0]}
              </span>
              <h2>{n}님의 AI 클론</h2>
              <p className="ui-muted text-muted">{r} · 초대 수락 완료</p>
              <Badge>{l}</Badge>
              <p className="ui-muted text-muted">최근 데이터 갱신 11월 {d}일</p>
              <Button
                href={n === "박정호" ? "/viewer/chat/park" : "/viewer/chat"}
              >
                AI 대변인과 대화
              </Button>
            </Card>
          ))}
        </div>
        <Notice title="권한 범위 안에서 안전하게 연결돼요">
          기록된 사실과 AI 추론을 구분해 확인하고, 응답에 연결된 근거를
          살펴보세요.
        </Notice>
      </main>
    </>
  );
}
