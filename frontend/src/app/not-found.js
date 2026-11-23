import commonStyles from "../components/Common.module.css";
import notFoundStyles from "./not-found.module.css";
import classNames from "../lib/classNames.js";
import Link from "next/link";
const styles = {
  ...commonStyles,
  ...notFoundStyles,
};
export default function NotFound() {
  return (
    <main className={classNames("screen-index", styles)}>
      <h1>화면을 찾을 수 없어요</h1>
      <Link href="/" className={classNames("text-link", styles)}>
        홈으로 돌아가기 →
      </Link>
    </main>
  );
}
