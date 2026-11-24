import Button from "@/components/Button";
import Notice from "@/components/Notice";
import CloneDashboard from "./CloneDashboard";
import AccessHistory from "./AccessHistory";

export default function ClonePage() {
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
        <Button href="/owner/chat" className="shrink-0">
          ＋ 대화 이어가기
        </Button>
      </header>

      <CloneDashboard />
      <AccessHistory />

      <Notice>
        대화로 쌓인 정보가 클론의 이해도를 높여요. 새로운 대화가 쌓이면 나를
        이해하는 정보도 함께 갱신됩니다.
      </Notice>
    </main>
  );
}
