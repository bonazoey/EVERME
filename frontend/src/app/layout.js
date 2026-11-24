import "./globals.css";
export const metadata = {
  title: "EVERME · 나를 이어가는 대화",
  description: "일상의 대화로 나를 기록하고, 나의 이야기를 이어갑니다.",
};
export default function RootLayout({ children }) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
