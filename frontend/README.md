# EVERME 프론트엔드 목업

Penpot의 `02. PC` 페이지를 바탕으로 만든 Next.js App Router 목업입니다.
인증, 대화, AI 클론, 정보이용자 관리, AI 대변인, 계정 설정 화면을 연결합니다.

## 실행

```powershell
cd front-end
npm install
npm run dev
```

- 서비스 시작: http://localhost:3000
- 전체 화면 목록: http://localhost:3000/screens
- 사용자 대화: http://localhost:3000/chat
- 정보이용자 모드: http://localhost:3000/viewer/clones

## 디렉터리

```text
src/
  app/                      # Next.js 라우트, 루트 레이아웃, 공통 CSS
    [...slug]/page.js        # 화면 대응표에서 페이지 컴포넌트 선택
    screens/page.js          # 전체 목업 화면 목록
  components/
    layout/                 # AuthLayout, AppLayout (사이드바·헤더)
    pages/                  # 폴더형 페이지 컴포넌트
      AuthPage/index.js
      ChatPage/index.js
      ClonePage/index.js
      MembersPage/index.js
      AccessHistoryPage/index.js
      ClonesPage/index.js
      InvitationsPage/index.js
      SettingsPage/index.js
      ScreenIndexPage/index.js
    ui/                     # Button.js, Field.js 등 단일 파일 UI 컴포넌트
  lib/screens.js            # Penpot 화면 ID ↔ URL ↔ 페이지 컴포넌트
design/penpot-screens.json   # MCP로 읽은 원본 화면명·치수·문구·텍스트 배치
```

페이지별 폴더에 전용 컴포넌트나 CSS 모듈을 추가할 수 있습니다.
공용 UI는 `components/ui` 아래 단일 파일로 유지합니다.

## 목업 동작

- 화면 전환은 Next.js Link와 URL 기반으로 처리합니다.
- 모달, 드로어, 음성 화면, 저장 알림도 별도 URL로 재현합니다.
- 모드 선택과 대화 메뉴는 HTML details 요소를 사용합니다.
- 실제 로그인, 소셜 인증, 이메일 발송, AI 응답, 녹음·재생, 저장·삭제는 구현하지 않습니다.
- 입력값·선택값은 서버나 전역 상태에 저장하지 않습니다. 동작 버튼은 샘플 결과 화면으로 이동합니다.
- Penpot의 PC 화면 45개에 더해 새 대화 등 이동에 필요한 보조 경로를 제공합니다.
- PC 레이아웃을 기준으로 좁은 화면에서도 탐색할 수 있도록 반응형 스타일을 적용했습니다.

## 확인

```powershell
npm run lint
npm run build
```

[Penpot 원본](https://design.penpot.app/?screen=workspace&team-id=7b7d55f9-965b-8000-8008-fd2808e132b4&file-id=42288968-6e5e-808d-8008-fccfdd4cbaeb&page-id=5c4bd28d-3951-8018-8008-fd03c6143459&layout=layers)
