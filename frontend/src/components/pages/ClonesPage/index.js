import AppLayout from "@/components/layout/AppLayout";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Notice from "@/components/ui/Notice";
export default function ClonesPage({ variant = "" }) {
  return (
    <AppLayout
      active="clones"
      title="이용 가능 클론"
      viewer
      modeOpen={variant === "mode"}
    >
      <main className="page-content">
        <h1>나에게 허용된 AI 클론</h1>
        <p className="muted">
          초대를 수락한 클론만 표시됩니다. 실제 사용자와의 대화가 아닙니다.
        </p>
        <div className="two-columns">
          {[
            ["김영희", "가족", "L2 개인", "23"],
            ["박정호", "친구", "L1 일반", "21"],
          ].map(([n, r, l, d]) => (
            <Card key={n}>
              <span className="avatar large">{n[0]}</span>
              <h2>{n}님의 AI 클론</h2>
              <p className="muted">{r} · 초대 수락 완료</p>
              <Badge>{l}</Badge>
              <p className="muted">최근 데이터 갱신 11월 {d}일</p>
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
    </AppLayout>
  );
}
