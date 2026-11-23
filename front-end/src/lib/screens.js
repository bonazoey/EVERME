// Penpot PC 화면과 목업 경로의 대응표. URL만으로 화면 상태를 재현합니다.
export const screens = [
  {
    path: "/",
    component: "AuthPage",
    props: {
      screen: "login",
    },
    name: "PC-001 / 서비스 소개 및 로그인",
    penpotId: "5c4bd28d-3951-8018-8008-fd04f05cd98d",
  },
  {
    path: "/signup",
    component: "AuthPage",
    props: {
      screen: "signup",
    },
    name: "PC-002 / 회원가입",
    penpotId: "5c4bd28d-3951-8018-8008-fd050db3add6",
  },
  {
    path: "/reset-password",
    component: "AuthPage",
    props: {
      screen: "reset",
    },
    name: "PC-003 / 비밀번호 재설정",
    penpotId: "5c4bd28d-3951-8018-8008-fd05107dd507",
  },
  {
    path: "/reset-password/new",
    component: "AuthPage",
    props: {
      screen: "password",
    },
    name: "PC-003-new / 새 비밀번호 설정",
    penpotId: "5c4bd28d-3951-8018-8008-fd06a83362fc",
  },
  {
    path: "/reset-password/sent",
    component: "AuthPage",
    props: {
      screen: "sent",
    },
    name: "PC-003-sent / 재설정 링크 발송 완료",
    penpotId: "5c4bd28d-3951-8018-8008-fd06a40ab0cd",
  },
  {
    path: "/consent",
    component: "AuthPage",
    props: {
      screen: "consent",
    },
    name: "PC-004 / 개인정보 및 AI 활용 동의",
    penpotId: "5c4bd28d-3951-8018-8008-fd0523193c8b",
  },
  {
    path: "/chat/mode",
    component: "ChatPage",
    props: {
      variant: "mode",
    },
    name: "PC-102-mode / 이용 모드 전환 / 드롭다운",
    penpotId: "5c4bd28d-3951-8018-8008-fd06970717d7",
  },
  {
    path: "/chat",
    component: "ChatPage",
    props: {
      variant: "",
    },
    name: "PC-102 / AI 대화",
    penpotId: "5c4bd28d-3951-8018-8008-fd052ce0d6a7",
  },
  {
    path: "/chat/delete",
    component: "ChatPage",
    props: {
      variant: "delete",
    },
    name: "PC-102-delete / 대화 삭제 확인",
    penpotId: "5c4bd28d-3951-8018-8008-fd065ae5b1eb",
  },
  {
    path: "/chat/edit",
    component: "ChatPage",
    props: {
      variant: "edit",
    },
    name: "PC-102-edit / 대화 제목 수정",
    penpotId: "5c4bd28d-3951-8018-8008-fd065846856b",
  },
  {
    path: "/chat/empty",
    component: "ChatPage",
    props: {
      variant: "empty",
    },
    name: "PC-102-empty / AI 대화",
    penpotId: "5c4bd28d-3951-8018-8008-fd065d6b5edf",
  },
  {
    path: "/chat/error",
    component: "ChatPage",
    props: {
      variant: "error",
    },
    name: "PC-102-error / AI 대화",
    penpotId: "5c4bd28d-3951-8018-8008-fd06681f502b",
  },
  {
    path: "/chat/streaming",
    component: "ChatPage",
    props: {
      variant: "streaming",
    },
    name: "PC-102-streaming / 응답 생성 중 / 스트리밍",
    penpotId: "5c4bd28d-3951-8018-8008-fd06b83a98b5",
  },
  {
    path: "/chat/voice",
    component: "ChatPage",
    props: {
      variant: "voice",
    },
    name: "PC-102-voice / AI 대화",
    penpotId: "5c4bd28d-3951-8018-8008-fd055da607de",
  },
  {
    path: "/clone",
    component: "ClonePage",
    props: {},
    name: "PC-103 / 클론 현황",
    penpotId: "5c4bd28d-3951-8018-8008-fd0533f0582e",
  },
  {
    path: "/members",
    component: "MembersPage",
    props: {
      variant: "",
    },
    name: "PC-201 / 정보이용자 관리",
    penpotId: "5c4bd28d-3951-8018-8008-fd0564771609",
  },
  {
    path: "/members/expanded",
    component: "MembersPage",
    props: {
      variant: "expanded",
    },
    name: "PC-202 / 정보이용자 관리 · 그룹 펼침",
    penpotId: "5c4bd28d-3951-8018-8008-fd056ca292fc",
  },
  {
    path: "/members/group",
    component: "MembersPage",
    props: {
      variant: "group",
    },
    name: "PC-202-create / 그룹 생성·수정",
    penpotId: "5c4bd28d-3951-8018-8008-fd0674f8a476",
  },
  {
    path: "/members/group-delete",
    component: "MembersPage",
    props: {
      variant: "group-delete",
    },
    name: "PC-202-delete / 그룹 삭제 확인",
    penpotId: "5c4bd28d-3951-8018-8008-fd06794811fe",
  },
  {
    path: "/members/invite",
    component: "MembersPage",
    props: {
      variant: "invite",
    },
    name: "PC-203 / 정보이용자 초대 / 모달",
    penpotId: "5c4bd28d-3951-8018-8008-fd0582e96705",
  },
  {
    path: "/members/access",
    component: "MembersPage",
    props: {
      variant: "access",
    },
    name: "PC-204 / 접근 등급 설정 / 드로어",
    penpotId: "5c4bd28d-3951-8018-8008-fd0585c1a017",
  },
  {
    path: "/members/revoke",
    component: "MembersPage",
    props: {
      variant: "revoke",
    },
    name: "PC-204-revoke / 이용 권한 철회",
    penpotId: "5c4bd28d-3951-8018-8008-fd067bd39ac8",
  },
  {
    path: "/clone/history",
    component: "AccessHistoryPage",
    props: {},
    name: "PC-205 / 클론 현황 · 접근 이력",
    penpotId: "5c4bd28d-3951-8018-8008-fd058a24063f",
  },
  {
    path: "/viewer/clones/mode",
    component: "ClonesPage",
    props: {
      variant: "mode",
    },
    name: "PC-302-mode / 이용 모드 전환 / 드롭다운",
    penpotId: "5c4bd28d-3951-8018-8008-fd0699e69833",
  },
  {
    path: "/viewer/clones",
    component: "ClonesPage",
    props: {},
    name: "PC-302 / 이용 가능 클론",
    penpotId: "5c4bd28d-3951-8018-8008-fd059c5f8e34",
  },
  {
    path: "/viewer/invitations",
    component: "InvitationsPage",
    props: {},
    name: "PC-303 / 받은 초대",
    penpotId: "5c4bd28d-3951-8018-8008-fd05a3fe9c69",
  },
  {
    path: "/viewer/invitations/decline",
    component: "InvitationsPage",
    props: {
      variant: "decline",
    },
    name: "PC-303-decline / 초대 거절 확인",
    penpotId: "5c4bd28d-3951-8018-8008-fd069166b48f",
  },
  {
    path: "/viewer/chat",
    component: "ChatPage",
    props: {
      variant: "",
      viewer: true,
    },
    name: "PC-304 / AI 대변인",
    penpotId: "5c4bd28d-3951-8018-8008-fd05ad33b090",
  },
  {
    path: "/viewer/chat/delete",
    component: "ChatPage",
    props: {
      variant: "delete",
      viewer: true,
    },
    name: "PC-304-delete / 대변인 대화 삭제",
    penpotId: "5c4bd28d-3951-8018-8008-fd068e93caca",
  },
  {
    path: "/viewer/chat/voice",
    component: "ChatPage",
    props: {
      variant: "voice",
      viewer: true,
    },
    name: "PC-304-voice / AI 대변인",
    penpotId: "5c4bd28d-3951-8018-8008-fd067e717993",
  },
  {
    path: "/viewer/chat/evidence",
    component: "ChatPage",
    props: {
      variant: "evidence",
      viewer: true,
    },
    name: "PC-305 / 응답 근거 상세 / 우측 패널",
    penpotId: "5c4bd28d-3951-8018-8008-fd05b8704850",
  },
  {
    path: "/settings",
    component: "SettingsPage",
    props: {
      variant: "",
      viewer: false,
    },
    name: "PC-401 / 계정 설정 · 내 정보",
    penpotId: "5c4bd28d-3951-8018-8008-fd05bec12161",
  },
  {
    path: "/settings/saved",
    component: "SettingsPage",
    props: {
      variant: "saved",
      viewer: false,
    },
    name: "PC-401-saved / 저장 완료 / 토스트",
    penpotId: "5c4bd28d-3951-8018-8008-fd06a04f7c0c",
  },
  {
    path: "/viewer/settings/saved",
    component: "SettingsPage",
    props: {
      variant: "saved",
      viewer: true,
    },
    name: "PC-401-saved-viewer / 저장 완료 / 토스트 · 정보이용자 모드",
    penpotId: "5c4bd28d-3951-8018-8008-fd088f817553",
  },
  {
    path: "/viewer/settings",
    component: "SettingsPage",
    props: {
      variant: "",
      viewer: true,
    },
    name: "PC-401-viewer / 계정 설정 · 내 정보 · 정보이용자 모드",
    penpotId: "5c4bd28d-3951-8018-8008-fd088461d8a6",
  },
  {
    path: "/settings/privacy",
    component: "SettingsPage",
    props: {
      variant: "privacy",
      viewer: false,
    },
    name: "PC-402 / 계정 설정 · 개인정보 및 동의",
    penpotId: "5c4bd28d-3951-8018-8008-fd05cab7e23a",
  },
  {
    path: "/viewer/settings/privacy",
    component: "SettingsPage",
    props: {
      variant: "privacy",
      viewer: true,
    },
    name: "PC-402-viewer / 계정 설정 · 개인정보 및 동의 · 정보이용자 모드",
    penpotId: "5c4bd28d-3951-8018-8008-fd08864e51d9",
  },
  {
    path: "/settings/withdraw",
    component: "SettingsPage",
    props: {
      variant: "withdraw",
      viewer: false,
    },
    name: "PC-402-withdraw / 동의 철회 확인",
    penpotId: "5c4bd28d-3951-8018-8008-fd069cb15b27",
  },
  {
    path: "/viewer/settings/withdraw",
    component: "SettingsPage",
    props: {
      variant: "withdraw",
      viewer: true,
    },
    name: "PC-402-withdraw-viewer / 동의 철회 확인 · 정보이용자 모드",
    penpotId: "5c4bd28d-3951-8018-8008-fd088cf4917c",
  },
  {
    path: "/settings/voice",
    component: "SettingsPage",
    props: {
      variant: "voice",
      viewer: false,
    },
    name: "PC-403 / 계정 설정 · 음성 설정",
    penpotId: "5c4bd28d-3951-8018-8008-fd05d96685bb",
  },
  {
    path: "/settings/voice-saved",
    component: "SettingsPage",
    props: {
      variant: "voice-saved",
      viewer: false,
    },
    name: "PC-403-saved / 저장 완료 / 토스트",
    penpotId: "5c4bd28d-3951-8018-8008-fd06a201a863",
  },
  {
    path: "/viewer/settings/voice-saved",
    component: "SettingsPage",
    props: {
      variant: "voice-saved",
      viewer: true,
    },
    name: "PC-403-saved-viewer / 저장 완료 / 토스트 · 정보이용자 모드",
    penpotId: "5c4bd28d-3951-8018-8008-fd0891836f06",
  },
  {
    path: "/viewer/settings/voice",
    component: "SettingsPage",
    props: {
      variant: "voice",
      viewer: true,
    },
    name: "PC-403-viewer / 계정 설정 · 음성 설정 · 정보이용자 모드",
    penpotId: "5c4bd28d-3951-8018-8008-fd088857135d",
  },
  {
    path: "/settings/delete",
    component: "SettingsPage",
    props: {
      variant: "delete",
      viewer: false,
    },
    name: "PC-404 / 회원탈퇴 확인",
    penpotId: "5c4bd28d-3951-8018-8008-fd069438b14b",
  },
  {
    path: "/viewer/settings/delete",
    component: "SettingsPage",
    props: {
      variant: "delete",
      viewer: true,
    },
    name: "PC-404-viewer / 회원탈퇴 확인 · 정보이용자 모드",
    penpotId: "5c4bd28d-3951-8018-8008-fd088a63a0f5",
  },
  {
    path: "/viewer/chat/empty",
    component: "ChatPage",
    props: { viewer: true, variant: "empty" },
    name: "AI 대변인 · 새 대화",
  },
  {
    path: "/viewer/chat/edit",
    component: "ChatPage",
    props: { viewer: true, variant: "edit" },
    name: "AI 대변인 · 대화 제목 수정",
  },
  {
    path: "/viewer/chat/park",
    component: "ChatPage",
    props: {
      viewer: true,
      variant: "empty",
      owner: "박정호",
      level: "L1 일반",
    },
    name: "박정호님의 AI 대변인",
  },
  {
    path: "/viewer/chat/park/empty",
    component: "ChatPage",
    props: {
      viewer: true,
      owner: "박정호",
      level: "L1 일반",
      variant: "empty",
    },
    name: "박정호님의 AI 대변인 · empty",
  },
  {
    path: "/viewer/chat/park/voice",
    component: "ChatPage",
    props: {
      viewer: true,
      owner: "박정호",
      level: "L1 일반",
      variant: "voice",
    },
    name: "박정호님의 AI 대변인 · voice",
  },
  {
    path: "/viewer/chat/park/edit",
    component: "ChatPage",
    props: { viewer: true, owner: "박정호", level: "L1 일반", variant: "edit" },
    name: "박정호님의 AI 대변인 · edit",
  },
  {
    path: "/viewer/chat/park/delete",
    component: "ChatPage",
    props: {
      viewer: true,
      owner: "박정호",
      level: "L1 일반",
      variant: "delete",
    },
    name: "박정호님의 AI 대변인 · delete",
  },
];
