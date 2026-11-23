import AppLayout from "@/components/layout/AppLayout";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Notice from "@/components/ui/Notice";
export default function ClonePage() {
  return (
    <AppLayout active="clone" title="AI 클론 / 클론 현황">
      <main className="page-content">
        <h1>클론 현황</h1>
        <div className="tabs">
          <a className="selected" href="#overview">
            개요
          </a>
          <a href="#analysis">데이터 분석</a>
          <a href="#traits">주요 특징</a>
        </div>
        <div className="clone-grid" id="overview">
          <Card>
            <div className="section-heading">
              <h2>◎ AI 클론 상태</h2>
              <Badge>활성화</Badge>
            </div>
            <p className="muted">마지막 업데이트 2026.11.23 09:20</p>
          </Card>
          <Card>
            <h2>데이터 완성도</h2>
            <div className="completion">
              <strong>
                87<span>%</span>
              </strong>
              <div>
                <p className="muted">대화를 통해 클론의 정보가 완성됩니다.</p>
                <progress value="87" max="100">
                  87%
                </progress>
              </div>
            </div>
          </Card>
          <Card className="information-card">
            <h2 id="analysis">수집된 정보 현황</h2>
            {[
              ["기본 정보", 100],
              ["경험 / 기억", 86],
              ["선호 / 취향", 82],
              ["가치관 / 신념", 79],
              ["관계 / 주변", 85],
            ].map(([t, n]) => (
              <div className="progress-row" key={t}>
                <span>{t}</span>
                <progress value={n} max="100">
                  {n}%
                </progress>
                <strong>{n}%</strong>
              </div>
            ))}
          </Card>
          <Card>
            <h2 id="traits">요약 프로필</h2>
            <p className="profile-summary">
              가족과 함께하는 일상을 소중히 여기며,
              <br />
              익숙한 경험과 새로운 이야기를 나누는 것을 좋아해요.
            </p>
            <p className="muted">
              대화에서 드러난 기억과 선호를 바탕으로
              <br />
              김영희님을 이해하는 프로필을 만들어가고 있어요.
            </p>
            <div className="tag-list">
              {["가족 중심", "기억과 경험", "일상의 대화", "따뜻함"].map(
                (t) => (
                  <Badge key={t}>{t}</Badge>
                ),
              )}
            </div>
            <small className="muted">
              새로운 대화가 쌓이면 요약 프로필도 갱신됩니다.
            </small>
          </Card>
        </div>
        <Notice>
          대화로 쌓인 정보가 클론의 이해도를 높여요. 접근 등급과 이용 이력은
          사이드바에서 관리할 수 있어요.
        </Notice>
      </main>
    </AppLayout>
  );
}
