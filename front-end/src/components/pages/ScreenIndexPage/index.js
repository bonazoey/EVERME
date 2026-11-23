import Link from "next/link";
import { screens } from "@/lib/screens";
export default function ScreenIndexPage() {
  return (
    <main className="screen-index">
      <Link href="/" className="brand">
        everme
      </Link>
      <h1>목업 화면 목록</h1>
      <p className="muted">
        Penpot의 PC 화면과 변형 상태를 URL로 확인할 수 있습니다. 입력값과
        변경사항은 저장되지 않습니다.
      </p>
      <div>
        {screens.map((s) => (
          <Link className="screen-index-item" key={s.path} href={s.path}>
            <span>{s.name}</span>
            <code>{s.path}</code>
            <span>→</span>
          </Link>
        ))}
      </div>
    </main>
  );
}
