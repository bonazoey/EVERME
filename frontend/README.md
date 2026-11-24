# EVERME 프론트엔드 목업

Penpot 디자인을 바탕으로 만든 Next.js App Router 목업입니다.

## 실행

```powershell
cd frontend
npm install
npm run dev
```

시작 화면: http://localhost:3000
사용자 대화: /owner/chat
정보이용자 모드: /viewer/clones

## 구조

```text
src/
  app/
    layout.js                    # HTML·메타데이터·공통 스타일
    globals.css
    (auth)/                      # 인증 화면 그룹 (URL에는 포함되지 않음)
      layout.js                  # 서비스 소개와 인증 폼 공통 틀
      page.js                    # /
      AuthForm.js                # 인증 화면이 공유하는 폼
      signup/page.js
      consent/page.js
      reset-password/
        page.js
        new/page.js
        sent/page.js
    (service)/                   # 서비스 화면 그룹
      layout.js                  # 공통 사이드바·헤더 및 현재 메뉴 표시
      AppSidebar.js
      owner/
        chat/
          page.js                # /owner/chat
          ChatContent.js
          ChatInput.js
        clone/
          page.js                # /owner/clone
          CloneDashboard.js
          AccessHistory.js
        members/page.js          # /owner/members
      settings/
        page.js                  # /settings
        BasicSettings.js
        EvermeSettings.js
        PrivacySettings.js
      viewer/
        chat/page.js
        chat/park/page.js
        clones/page.js
        invitations/page.js
    context/
      ChatSessions.js
      ServiceMode.js
  components/                    # 여러 기능에서 공유하는 단일 파일 UI
    Button.js
    Field.js
    Modal.js
    ...
```

- 페이지는 경로별 폴더의 page.js로 정의합니다.
- 화면 전용 컴포넌트는 해당 화면 폴더 근처에 둡니다.
- 여러 화면이 공유하는 UI만 components 아래에 둡니다.
- 레이아웃은 Next.js의 layout.js에서 한 번 적용합니다.
- 사용자·정보이용자 대화와 설정은 같은 내용 컴포넌트를 재사용합니다.
- 변형 화면은 기존 URL을 유지합니다. 예: /owner/members/invite, /viewer/chat/evidence.
- 전체 화면 목록과 catch-all 라우트, 화면 대응표는 사용하지 않습니다.

## 스타일 관리

- globals.css에는 색상 변수, 폰트, 기본 태그 스타일만 둡니다.
- 컴포넌트 스타일과 반응형 규칙은 같은 폴더의 .module.css에서 관리합니다.
- 예: ChatInput.js → ChatInput.module.css, AppSidebar.js → AppSidebar.module.css.
- 여러 화면이 공유하는 표현은 components/Common.module.css와 Page.module.css에 둡니다.
- 부모가 공용 UI의 배치를 조정하는 일부 규칙은 :global 선택자로 한정합니다.
- classNames 유틸리티는 동적 클래스와 모듈 클래스를 연결합니다.
- Tailwind 로더는 globals.css에만 적용하며, CSS Modules는 Next.js 기본 처리를 사용합니다.

## 목업 범위

Link로 화면을 전환하고, 모달·드로어·음성 화면·저장 알림도 URL로 재현합니다.
입력값은 저장하지 않으며 실제 인증, 이메일 발송, AI 응답, 녹음·재생, 저장·삭제는 구현하지 않습니다.

## 확인

```powershell
npm run lint
npm run build
```
