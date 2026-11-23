import membersContentStyles from "../../members/MembersContent.module.css";
import commonStyles from "../../../../components/Common.module.css";
import pageStyles from "../../../../components/Page.module.css";
import classNames from "../../../../lib/classNames.js";
import Card from "@/components/Card";
import Badge from "@/components/Badge";
import Notice from "@/components/Notice";
import Button from "@/components/Button";
const styles = {
  ...membersContentStyles,
  ...commonStyles,
  ...pageStyles,
};
export default function AccessHistoryPage() {
  return (
    <>
      <main className={classNames("page-content", styles)}>
        <h1>누가 내 클론을 이용했는지 확인하세요</h1>
        <p className={classNames("muted", styles)}>
          AI 클론에 대한 정보이용자의 접근과 이용 내역입니다.
        </p>
        <Card>
          <div className={classNames("table-scroll", styles)}>
            <table>
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
        </Card>
        <Notice title="접근 권한은 언제든 바꿀 수 있어요">
          원하지 않는 이용이 있다면 정보이용자 관리에서 권한을 변경하거나
          철회하세요.
        </Notice>
        <Button variant="secondary" href="/members">
          정보이용자 관리
        </Button>
      </main>
    </>
  );
}
