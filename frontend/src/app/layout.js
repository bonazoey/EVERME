import "./globals.css";
import AuthContextProvider from "./context/AuthContext";
export const metadata = {
  title: "EVERME · 나를 이어가는 대화",
  description: "일상의 대화로 나를 기록하고, 나의 이야기를 이어갑니다.",
  icons: {
    icon: { url: "/everme_logo.svg", type: "image/svg+xml", sizes: "any" },
  },
};
export default function RootLayout({ children }) {
  return (
    <html lang="ko">
      <body><AuthContextProvider>{children}</AuthContextProvider></body>
    </html>
  );
}
