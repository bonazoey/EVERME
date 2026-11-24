import Card from "@/components/Card";
import Badge from "@/components/Badge";
import Button from "@/components/Button";

export default function AccessHistory() {
  return (
    <div id="access-history" className="mt-5 scroll-mt-6 [&_>_.ui-card]:m-0">
      <Card>
        <div className="flex items-center justify-between gap-4 mb-5 [@media(max-width:_760px)]:flex-col [@media(max-width:_760px)]:items-start [&_h2]:m-0">
          <div>
            <h2>접근 이력</h2>
            <p className="text-[13px] text-muted mt-2 mb-0">
              AI 클론에 대한 정보이용자의 접근과 이용 내역입니다.
            </p>
          </div>
          <Button variant="secondary" href="/owner/members">
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
  );
}
