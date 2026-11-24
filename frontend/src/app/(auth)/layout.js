import Link from "next/link";

export default function AuthLayout({ children }) {
  return (
    <main className="grid [grid-template-columns:58%_42%] min-h-screen [@media(max-width:_760px)]:[grid-template-columns:1fr]">
      <section className="relative isolate flex min-h-225 flex-col justify-between overflow-hidden bg-[#f0f8f3] p-[44px_56px] [&_>_.ui-brand]:self-start [&_.ui-auth-layout-eyebrow]:rounded-[20px] [&_.ui-auth-layout-eyebrow]:bg-[#e3f1e8] [&_.ui-auth-layout-eyebrow]:p-[8px_16px] [&_.ui-auth-layout-eyebrow]:text-[13px] [&_.ui-auth-layout-eyebrow]:text-green [&_h1]:m-[38px_0_26px] [&_h1]:text-[40px] [&_h1]:leading-[1.55] [&_h1]:tracking-[-1.3px] [&_p]:text-[18px] [&_p]:leading-[1.9] [&_p]:text-muted [&_footer]:mt-11 [&_footer]:text-[14px] [&_footer]:text-green [@media(max-width:_1100px)]:p-[40px_32px] [@media(max-width:_1100px)]:[&_h1]:text-[34px] [@media(max-width:_1100px)]:[&_p]:text-[16px] [@media(max-width:_760px)]:min-h-auto [@media(max-width:_760px)]:p-7 [@media(max-width:_760px)]:[&_>_div]:m-[36px_0_0] [@media(max-width:_760px)]:[&_h1]:m-[24px_0_16px] [@media(max-width:_760px)]:[&_h1]:text-[30px] [@media(max-width:_760px)]:[&_p]:text-[14px] [@media(max-width:_760px)]:[&_footer]:hidden">
        <Link
          href="/"
          className="ui-brand relative z-20 text-[32px] [font-weight:750] tracking-[-0.5px] text-green leading-[1.2]"
        >
          EVERME
        </Link>
        <div className="relative z-20">
          <span className="ui-auth-layout-eyebrow">
            일상의 대화로 이어지는 나
          </span>
          <h1>
            오늘의 이야기가,
            <br />
            내일의 나를 이어줍니다.
          </h1>
          <p>
            AI와 편하게 대화하며 나의 기억과 생각을 남기세요.
            <br />
            내가 허용한 사람에게만, 나의 이야기가 전해집니다.
          </p>
        </div>
        <div className="max-w-155 p-7 bg-white [border:1px_solid_#dcebe1] rounded-2xl mt-12 [&_blockquote]:text-[24px] [&_blockquote]:leading-[1.65] [&_blockquote]:m-[28px_0_0] [@media(max-width:_760px)]:hidden">
          <div className="flex items-center gap-5 [&_small]:block [&_small]:text-muted [&_small]:mt-2 [&_h2]:m-0 [&_.ui-badge]:ml-auto">
            <span className="ui-avatar w-10 h-10 bg-green-light text-green rounded-full inline-flex items-center justify-center shrink-0 font-semibold [&.ui-common-large]:w-16 [&.ui-common-large]:h-16 [&.ui-common-large]:text-[22px]">
              영
            </span>
            <div>
              <strong>김영희님의 이야기</strong>
              <small>오늘 · 일상의 기억</small>
            </div>
          </div>
          <blockquote>
            “가족과 함께 식사하는 시간이
            <br />
            저에게는 가장 소중해요.”
          </blockquote>
        </div>
        <footer>나의 기록 · 나의 선택 · 권한으로 보호되는 연결</footer>
      </section>
      <section className="w-full max-w-132 p-[120px_36px_60px] m-auto [&_>_h2]:text-[28px] [&_>_h2]:mb-4 [@media(min-width:_1600px)]:max-w-140 [@media(max-width:_1100px)]:p-[70px_28px] [@media(max-width:_760px)]:p-[36px_24px_50px] [@media(max-width:_760px)]:max-w-130 [@media(max-width:_760px)]:[&_>_h2]:text-[24px]">
        {children}
      </section>
    </main>
  );
}
