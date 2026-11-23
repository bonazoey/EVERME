import Link from "next/link";
export default function NotFound() {
  return (
    <main className="max-w-250 m-auto p-[48px_24px] [&_h1]:mt-8">
      <h1>화면을 찾을 수 없어요</h1>
      <Link href="/" className="ui-text-link text-green font-medium">
        홈으로 돌아가기 →
      </Link>
    </main>
  );
}
