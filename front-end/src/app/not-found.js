import Link from "next/link";
export default function NotFound() {
  return (
    <main className="screen-index">
      <h1>화면을 찾을 수 없어요</h1>
      <Link href="/screens" className="text-link">
        전체 목업 화면 보기 →
      </Link>
    </main>
  );
}
