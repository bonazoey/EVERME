# 백엔드 엔드포인트 정의

기준: 2026-11-26. Notion 요구사항·화면·ETL/RAG 아키텍처를 반영한다. X·구현 범위 밖·관리자는 제외하며 검토 중 항목은 초안으로 포함한다.

설계 변경: 실행 코드 57개, 지식 5대 분류 목표 계약은 58개다. 상세는 문서 끝의 변경 설계를 참조한다.

## 구현 경계

컨트롤러 8개, 엔드포인트 57개. 모든 엔드포인트 메서드 본문은 `return null;` 한 줄만 포함한다. 반환형은 향후 응답 계약 확정을 위한 `ResponseEntity<?>`이며 501 응답·예외·상태 처리 코드는 없다. 현재 정상 바인딩 요청은 기본 빈 응답이므로 실제 기능 성공을 의미하지 않는다. Spring MVC의 기본 입력 바인딩은 동작한다.

Service·Dao는 XxxService·XxxDao 이름의 빈 클래스만 유지한다. 업무 메서드·주입·SQL·인증·인가·검증·ETL·RAG·음성 처리·DB 변경은 후속 구현한다. 기존 루트 HomeController는 유지한다.

## 기능 분리

- OwnerConversation: /api/owner/conversations. 본인 원문 기록·정정·삭제·AI 대화·추천 주제. 세션 생성 요청 본문 없음.
- ViewerConversation: /api/viewer/conversations. cloneId로 세션 생성·질문·답변·근거. 대변인 대화는 사용자 정보 추출 대상에서 제외. 발언 정정은 사용자 대화에만 제공한다.
- 공통 테이블은 conversation_sessions/messages/message_revisions다. API owner/viewer를 DB kind=personal/spokesperson으로 서버가 결정한다. 참여자는 인증 주체이며 요청에 ownerId·consumerId·대화 mode를 받지 않는다.
- Analysis는 본인 분석 목록·상세, Clone은 클론 현황·이용 가능한 클론, Access는 정보이용자·그룹·권한·접근 이력, Invitation은 초대 발송·목록·수락/거절을 담당한다.

## API 목록

| 컨트롤러 | 메서드 | 경로 | 입력 | 기능 |
| --- | --- | --- | --- | --- |
| AccessController | GET | `/api/me/members` | 경로/쿼리 | FR-071,074,075: 정보이용자 목록. |
| AccessController | PATCH | `/api/me/members/{memberId}/access` | AccessRequest | FR-074,075,077-05: 개별 최대 접근 등급 변경. |
| AccessController | DELETE | `/api/me/members/{memberId}` | 경로/쿼리 | FR-076: 이용 권한 철회. |
| AccessController | GET | `/api/me/groups` | 경로/쿼리 | FR-074-01: 그룹 조회. |
| AccessController | POST | `/api/me/groups` | GroupRequest | FR-074-01: 그룹 생성. |
| AccessController | PATCH | `/api/me/groups/{groupId}` | GroupRequest | FR-074-01: 그룹 이름 변경. |
| AccessController | DELETE | `/api/me/groups/{groupId}` | 경로/쿼리 | FR-074-01: 그룹 해제. |
| AccessController | PUT | `/api/me/members/{memberId}/group` | MembershipRequest | FR-074-01: 그룹 이동 또는 해제. |
| AccessController | PATCH | `/api/me/groups/{groupId}/access` | AccessRequest | FR-074-01: 그룹 구성원 등급 일괄 변경. |
| AccessController | GET | `/api/me/clone/access-history` | 경로/쿼리 | FR-078: 클론 접근·이용 이력. |
| AccountController | GET | `/api/me` | 없음 | FR-006: 내 프로필. |
| AccountController | PATCH | `/api/me` | ProfileRequest | FR-006: 프로필 변경. |
| AccountController | POST | `/api/me/profile-image` | multipart file | FR-006: 프로필 이미지 업로드. |
| AccountController | DELETE | `/api/me` | 없음 | FR-007; NFR-013: 회원탈퇴 및 논리 비활성화.  |
| AccountController | GET | `/api/me/conversation-settings` | 없음 | FR-019: 대화 설정. |
| AccountController | PATCH | `/api/me/conversation-settings` | SettingsRequest | FR-019: 음성 종류 변경. |
| AuthController | POST | `/api/auth/signup` | SignupRequest | FR-001,008: 회원가입. |
| AuthController | POST | `/api/auth/login` | LoginRequest | FR-002: 로그인. |
| AuthController | POST | `/api/auth/logout` | 없음 | FR-003: 로그아웃. |
| AuthController | GET | `/api/auth/oauth/{provider}` | 경로/쿼리 | FR-004: 소셜 인증 시작. |
| AuthController | GET | `/api/auth/oauth/{provider}/callback` | 경로/쿼리 | FR-004: 소셜 인증 콜백. |
| AuthController | POST | `/api/auth/password-reset-requests` | PasswordResetRequest | FR-005: 재설정 이메일 요청. |
| AuthController | POST | `/api/auth/password-resets` | PasswordChangeRequest | FR-005: 본인 인증 토큰으로 비밀번호 재설정. |
| CloneController | GET | `/api/me/clone` | 없음 | FR-048,055: 클론 요약 프로필·충분성·최근 갱신. |
| CloneController | GET | `/api/viewer/clones` | 경로/쿼리 | FR-079: 이용 가능한 클론. |
| InvitationController | POST | `/api/me/invitations` | InvitationRequest | FR-071,072: 초대를 통한 정보이용자 등록. |
| InvitationController | GET | `/api/viewer/invitations` | 경로/쿼리 | FR-073: 받은 초대 목록. |
| InvitationController | PATCH | `/api/viewer/invitations/{invitationId}` | InvitationResponseRequest | FR-073: 초대 수락 또는 거절. |
| AnalysisController | GET | `/api/me/analysis` | 경로/쿼리 | FR-030~043: 분석 정보 목록. |
| AnalysisController | GET | `/api/me/analysis/{informationId}` | 경로/쿼리 | FR-039,042,043: 분석 정보 상세·출처·변경 이력. |
| OwnerConversationController | GET | `/api/owner/conversations` | 경로/쿼리 | FR-023: 본인 사용자 대화 조회. |
| OwnerConversationController | POST | `/api/owner/conversations` | 없음 | FR-022: 본인 대화 생성. 서버가 kind=personal과 인증 참여자를 결정한다. |
| OwnerConversationController | GET | `/api/owner/conversations/{conversationId}` | 경로/쿼리 | FR-023: 본인 사용자 대화 조회. |
| OwnerConversationController | PATCH | `/api/owner/conversations/{conversationId}` | TitleRequest | FR-022; 화면: 대화 제목 변경. |
| OwnerConversationController | DELETE | `/api/owner/conversations/{conversationId}` | 경로/쿼리 | FR-027; NFR-013: 세션 논리 비활성화. |
| OwnerConversationController | POST | `/api/owner/conversations/{conversationId}/messages` | MessageRequest | FR-009,014~016,021: 사용자 메시지 저장·AI 대화. 정보 추출·ETL 예약은 후속 서비스 내부 작업. |
| OwnerConversationController | GET | `/api/owner/conversations/{conversationId}/messages/{messageId}/events` | 경로/쿼리 | FR-013: 응답 스트림 구독 (향후 SSE). |
| OwnerConversationController | POST | `/api/owner/conversations/{conversationId}/messages/{messageId}/stop` | 경로/쿼리 | FR-018: 응답 생성·음성 출력 중단. |
| OwnerConversationController | PATCH | `/api/owner/conversations/{conversationId}/messages/{messageId}` | MessageRequest | FR-026: 자신의 원문 발언 정정. |
| OwnerConversationController | DELETE | `/api/owner/conversations/{conversationId}/messages/{messageId}` | 경로/쿼리 | FR-027; NFR-013: 메시지 논리 비활성화. |
| OwnerConversationController | GET | `/api/owner/conversations/{conversationId}/summary` | 경로/쿼리 | FR-029: 세션 요약. |
| OwnerConversationController | POST | `/api/owner/conversations/{conversationId}/speech-recognitions` | multipart file | FR-010,012,020: 사용자 음성 인식 요청. |
| OwnerConversationController | GET | `/api/owner/conversations/{conversationId}/messages/{messageId}/audio` | 경로/쿼리 | FR-011: 사용자 대화 AI 응답 음성 조회. |
| OwnerConversationController | GET | `/api/owner/conversations/topics` | 없음 | FR-017: 대화 주제 추천. |
| ViewerConversationController | GET | `/api/viewer/conversations` | 경로/쿼리 | FR-070: 본인 대변인 대화 조회. 현재 클론 권한도 재검증 예정. |
| ViewerConversationController | POST | `/api/viewer/conversations` | ViewerConversationRequest | FR-058,070: 대상 cloneId로 대변인 세션 생성. 현재 권한 검사 후 kind=spokesperson 저장 예정. |
| ViewerConversationController | GET | `/api/viewer/conversations/{conversationId}` | 경로/쿼리 | FR-070: 본인 대변인 대화 조회. 현재 클론 권한도 재검증 예정. |
| ViewerConversationController | PATCH | `/api/viewer/conversations/{conversationId}` | TitleRequest | FR-022; 화면: 대화 제목 변경. |
| ViewerConversationController | DELETE | `/api/viewer/conversations/{conversationId}` | 경로/쿼리 | FR-027; NFR-013: 세션 논리 비활성화. |
| ViewerConversationController | POST | `/api/viewer/conversations/{conversationId}/messages` | MessageRequest | FR-058,060~068,070: 대변인 질문 저장·RAG·응답 생성. 권한 검색·노출 검사·근거 저장은 후속 서비스 내부 작업. |
| ViewerConversationController | GET | `/api/viewer/conversations/{conversationId}/messages/{messageId}/events` | 경로/쿼리 | FR-013: 응답 스트림 구독 (향후 SSE). |
| ViewerConversationController | POST | `/api/viewer/conversations/{conversationId}/messages/{messageId}/stop` | 경로/쿼리 | FR-018: 응답 생성·음성 출력 중단. |
| ViewerConversationController | DELETE | `/api/viewer/conversations/{conversationId}/messages/{messageId}` | 경로/쿼리 | FR-027; NFR-013: 메시지 논리 비활성화. |
| ViewerConversationController | GET | `/api/viewer/conversations/{conversationId}/summary` | 경로/쿼리 | FR-029,070: 대변인 세션 요약. 현재 권한으로 조회 범위를 검사 예정. |
| ViewerConversationController | GET | `/api/viewer/conversations/{conversationId}/messages/{messageId}/evidence` | 경로/쿼리 | FR-064,069; NFR-023: 응답 지식 버전·출처 조회. 원문은 현재 권한 검사와 마스킹 후 제공 예정. |
| ViewerConversationController | POST | `/api/viewer/conversations/{conversationId}/speech-recognitions` | multipart file | FR-059: 대변인 질문 음성 인식 요청. |
| ViewerConversationController | GET | `/api/viewer/conversations/{conversationId}/messages/{messageId}/audio` | 경로/쿼리 | FR-059: 현재 권한 내 검증된 대변인 답변 음성 조회. |

## 요청과 정책

- ViewerConversationRequest는 cloneId만 포함한다. 사용자 세션 생성에는 요청 DTO가 없다. 목록은 page=0, size=20 기본값이며 대변인 목록에만 선택 cloneId 필터가 있다.
- 가입 요청은 agreedToTerms=true를 요구하는 계약이다. 동의 문서·이력 저장 및 개별 동의 API는 없다. 약관은 프론트에 하드코딩하며 문서 조회 API와 동의 저장 테이블은 없다.
- text/voice 전환은 프론트엔드 상태이며 /mode API는 없다. 음성 종류는 계정 설정으로 유지한다.
- DELETE는 외부 자원 제거 계약이며 DB에서는 논리 비활성화로 구현할 예정이다.
- 메시지는 content를 받는다. 향후 messageId로 스트림·중단·음성·근거 경로를 연결한다. SSE·TTS·STT 전달 프로토콜과 성공 응답 DTO는 후속 확정한다.
- UUID 경로·JSON·multipart file 바인딩만 정의했다. 입력 범위·파일·문서 버전·초대·권한 검증은 후속 구현 대상이다.
- maxAccessLevel=1~4, 초대 응답 decision=accepted/declined 제안. 그룹 이동은 groupId=null로 해제하는 초안이다. 그룹 정책과 저장 구조는 후속 결정한다.

## 요청과 테이블

| 요청 | 후속 내부 처리·테이블 |
| --- | --- |
| 사용자 메시지 | messages/message_revisions → analysis_jobs → knowledge_items/versions/sources/conflicts → embedding_jobs/knowledge_embeddings → clones/clone_assessments |
| 대변인 질문 | clones/clone_permissions → 소유자·등급 필터 검색 → knowledge_versions/items와 유효 출처 재검증 → 허용 컨텍스트 → 답변 노출 검사 → response_segments/evidence 및 대화·감사 저장 |
| 근거 조회 | response_evidence → knowledge_versions → knowledge_sources → message_revisions. 현재 권한 검사·원문 마스킹 후 제공 |
| 정정·삭제 | revision과 출처를 추적. 파생 지식·벡터 비활성화 및 재분석 세부 정책은 구현 전 확정 |

추출·벡터 검색·임베딩·분류·클론 생성/갱신·감사마다 공개 CRUD API를 만들지 않는다. 지식 내용/민감도 직접 수정·개별 분석 제외도 제공하지 않는다. 권한 철회 후 대화 재조회·LLM 맥락·스트리밍·TTS·근거 조회에도 현재 권한을 적용해야 한다.

## 경로 변경

/api/conversations는 제거하고 /api/owner/conversations와 /api/viewer/conversations로 분리했다. /api/conversation-topics는 /api/owner/conversations/topics로 이동했다. 분석·접근 이력·초대 발송은 경로를 유지하고 담당 컨트롤러만 변경했다. ConversationRequest와 EndpointStatus는 제거했다.

## 전체 요구사항 추적

아래 '후속 내부/UI/운영'은 누락이 아니라 공개 엔드포인트와 별도로 구현할 항목이다. 엔드포인트가 있어도 관련 기능이 구현된 것은 아니다.

| 요구사항 | 상태 | 분류/처리 |
| --- | --- | --- |
| [FR-001 회원가입](https://app.notion.com/60b1a5bdc5208246a74e81fa92e1bf31) | 확정 | `POST /api/auth/signup` |
| [FR-002 로그인](https://app.notion.com/ddd1a5bdc520835f9bcd81f5a2e47f81) | 확정 | `POST /api/auth/login` |
| [FR-003 로그아웃](https://app.notion.com/0ed1a5bdc52082d3949581015021f237) | 확정 | `POST /api/auth/logout` |
| [FR-004 소셜 로그인](https://app.notion.com/1e31a5bdc52082df932201dd32a916c2) | 확정 | `GET /api/auth/oauth/{provider}`<br>`GET /api/auth/oauth/{provider}/callback` |
| [FR-005 비밀번호 재설정](https://app.notion.com/b0d1a5bdc52082ad995e01914ec99d44) | 확정 | `POST /api/auth/password-reset-requests`<br>`POST /api/auth/password-resets` |
| [FR-006 프로필 관리](https://app.notion.com/0981a5bdc52082b0942d011b31ccec37) | 확정 | `GET /api/me`<br>`PATCH /api/me`<br>`POST /api/me/profile-image` |
| [FR-007 회원탈퇴](https://app.notion.com/5fe1a5bdc5208313a8fd01834e6b6b1e) | 확정 | `DELETE /api/me` |
| [FR-008 약관 및 동의 관리](https://app.notion.com/8071a5bdc52082ffab00014440d14a09) | 확정 | 프론트에 약관 하드코딩; `POST /api/auth/signup`에서 agreedToTerms 수신; 동의 테이블 없음 |
| [FR-009 텍스트 대화](https://app.notion.com/a851a5bdc52082c791d8011f1b7dacfe) | 확정 | `POST /api/owner/conversations/{conversationId}/messages`<br>`POST /api/viewer/conversations/{conversationId}/messages` |
| [FR-010 실시간 음성 인식](https://app.notion.com/6bb1a5bdc5208282bd050131fda8dcbf) | 확정 | `POST /api/owner/conversations/{conversationId}/speech-recognitions`<br>`POST /api/viewer/conversations/{conversationId}/speech-recognitions` |
| [FR-011 실시간 음성 출력](https://app.notion.com/a391a5bdc520831f9f0b81b533056688) | 확정 | `GET /api/owner/conversations/{conversationId}/messages/{messageId}/audio`<br>`GET /api/viewer/conversations/{conversationId}/messages/{messageId}/audio` |
| [FR-012 음성 대화 모드](https://app.notion.com/c4b1a5bdc520828e97ab010de3a5900a) | 확정 | 프론트엔드 모드 전환 및 음성 입출력 API |
| [FR-013 스트리밍 응답](https://app.notion.com/46b1a5bdc52082deb61681b17873af27) | 확정 | `GET /api/owner/conversations/{conversationId}/messages/{messageId}/events`<br>`GET /api/viewer/conversations/{conversationId}/messages/{messageId}/events` |
| [FR-014 대화 맥락 유지](https://app.notion.com/dc31a5bdc52082e885e4810a4894b497) | 확정 | 후속 내부/UI/운영; 메시지 응답 생성에 통합 |
| [FR-015 개인화 대화](https://app.notion.com/1eb1a5bdc52083cbb7640179ff5549ee) | 확정 | 후속 내부/UI/운영; 메시지 응답 생성에 통합 |
| [FR-016 능동적 질문](https://app.notion.com/2171a5bdc52083c1923781314cd1b6dd) | 확정 | 후속 내부/UI/운영; 메시지 응답 생성에 통합 |
| [FR-017 대화 주제 추천](https://app.notion.com/bf71a5bdc52083df814f01d2c5febc55) | 확정 | `GET /api/owner/conversations/topics` |
| [FR-018 대화 중단](https://app.notion.com/60a1a5bdc520838fb51501a58a8bdd44) | 확정 | `POST /api/owner/conversations/{conversationId}/messages/{messageId}/stop`<br>`POST /api/viewer/conversations/{conversationId}/messages/{messageId}/stop` |
| [FR-019 음성 설정](https://app.notion.com/11c1a5bdc52083b1af280199df44f1a3) | 확정 | `GET /api/me/conversation-settings`<br>`PATCH /api/me/conversation-settings` |
| [FR-020 대화 모드 전환](https://app.notion.com/9d01a5bdc520823c90ca812e6ac1461f) | 확정 | 후속 내부/UI/운영 |
| [FR-021 대화 기록 저장](https://app.notion.com/d131a5bdc52083e6b3fb811717c2364e) | 확정 | 후속 내부/UI/운영 |
| [FR-022 대화 세션 관리](https://app.notion.com/2121a5bdc52082f3a9ec01f23d2ed1db) | 확정 | `GET /api/owner/conversations`<br>`GET /api/viewer/conversations`<br>`POST /api/owner/conversations`<br>`POST /api/viewer/conversations`<br>`PATCH /api/owner/conversations/{conversationId}`<br>`PATCH /api/viewer/conversations/{conversationId}` |
| [FR-023 대화 기록 조회](https://app.notion.com/9b21a5bdc520832bb470011055702a38) | 확정 | `GET /api/owner/conversations/{conversationId}`<br>`GET /api/viewer/conversations/{conversationId}` |
| [FR-024 대화 기록 검색](https://app.notion.com/6dd1a5bdc52083d8ac22817ce998b2fd) | X | 제외 |
| [FR-025 기간별 기록 조회](https://app.notion.com/0b61a5bdc520835eadc781daf2ec9a48) | X | 제외 |
| [FR-026 대화 기록 수정](https://app.notion.com/b4b1a5bdc52082b1b8c3811f1afefd9c) | 확정 | `PATCH /api/owner/conversations/{conversationId}/messages/{messageId}` |
| [FR-027 대화 기록 삭제](https://app.notion.com/8641a5bdc520834b82dd810c12ef5cb0) | 확정 | `DELETE /api/owner/conversations/{conversationId}`<br>`DELETE /api/viewer/conversations/{conversationId}`<br>`DELETE /api/owner/conversations/{conversationId}/messages/{messageId}`<br>`DELETE /api/viewer/conversations/{conversationId}/messages/{messageId}` |
| [FR-028 대화 분석 제외](https://app.notion.com/4501a5bdc52082c29e5a8173e7dca68a) | X | 제외 |
| [FR-029 대화 요약](https://app.notion.com/a681a5bdc52082f892f0819fb1aeabfa) | 확정 | `GET /api/owner/conversations/{conversationId}/summary`<br>`GET /api/viewer/conversations/{conversationId}/summary` |
| [FR-030 사용자 정보 추출](https://app.notion.com/9231a5bdc52082148306811422dd58de) | 확정 | 후속 내부/UI/운영; 분석 조회 API와 내부 분석 파이프라인 |
| [FR-031 사실 정보 분석](https://app.notion.com/8d11a5bdc520828680360130ffea417b) | 확정 | 후속 내부/UI/운영; 분석 조회 API와 내부 분석 파이프라인 |
| [FR-032 경험 및 기억 분석](https://app.notion.com/1451a5bdc52083a8a43e01fdca3097ed) | 확정 | 후속 내부/UI/운영; 분석 조회 API와 내부 분석 파이프라인 |
| [FR-033 가치관 분석](https://app.notion.com/75a1a5bdc520835a963e011d03b9d7cc) | 확정 | 후속 내부/UI/운영; 분석 조회 API와 내부 분석 파이프라인 |
| [FR-034 선호도 분석](https://app.notion.com/abe1a5bdc52082b9aeea817f63121fd4) | 확정 | 후속 내부/UI/운영; 분석 조회 API와 내부 분석 파이프라인 |
| [FR-035 행동 패턴 분석](https://app.notion.com/8f21a5bdc520820f8950811cadc56641) | 확정 | 후속 내부/UI/운영; 분석 조회 API와 내부 분석 파이프라인 |
| [FR-036 성향 분석](https://app.notion.com/28f1a5bdc5208368946d015515dfcc56) | 검토 중 | 후속 내부/UI/운영; 분석 조회 API와 내부 분석 파이프라인 |
| [FR-037 언어적 특성 분석](https://app.notion.com/c6b1a5bdc52083378bc601339fff5e7d) | 검토 중 | 후속 내부/UI/운영; 분석 조회 API와 내부 분석 파이프라인 |
| [FR-038 데이터 분류](https://app.notion.com/70b1a5bdc52082199d0981dfcd0bfd82) | 확정 | 후속 내부/UI/운영; 분석 조회 API와 내부 분석 파이프라인 |
| [FR-039 출처 연결](https://app.notion.com/1ff1a5bdc52082fb8e1e8172fccf2b2c) | 확정 | `GET /api/me/analysis/{informationId}`; 분석 조회 API와 내부 분석 파이프라인 |
| [FR-040 중복 정보 처리](https://app.notion.com/26f1a5bdc52082dea64501800748d07a) | 확정 | 후속 내부/UI/운영; 분석 조회 API와 내부 분석 파이프라인 |
| [FR-041 정보 충돌 관리](https://app.notion.com/a281a5bdc52083959812016f494b7c4d) | 확정 | 후속 내부/UI/운영; 분석 조회 API와 내부 분석 파이프라인 |
| [FR-042 시간적 변화 관리](https://app.notion.com/7021a5bdc52082d09378812966dc535a) | 확정 | 후속 내부/UI/운영; 분석 조회 API와 내부 분석 파이프라인 |
| [FR-043 분석 결과 조회](https://app.notion.com/0d81a5bdc520838fbf5c818b8e392287) | 검토 중 | 후속 내부/UI/운영; 분석 조회 API와 내부 분석 파이프라인 |
| [FR-044 분석 결과 정정](https://app.notion.com/4fc1a5bdc52083b7882e019d8951d2f7) | 확정 | 직접 변경/제외 API 금지 |
| [FR-045 데이터 활용 제외](https://app.notion.com/d141a5bdc5208380ac9081fc701da7d2) | 확정 | 직접 변경/제외 API 금지 |
| [FR-046 AI 클론 생성](https://app.notion.com/f8c1a5bdc52082c7bfda01b4568c436a) | 확정 | 후속 내부/UI/운영 |
| [FR-047 AI 클론 갱신](https://app.notion.com/c3b1a5bdc520838487de818503a86d80) | 확정 | 후속 내부/UI/운영 |
| [FR-048 AI 클론 프로필 조회](https://app.notion.com/f571a5bdc52083ec807a010870e73ce6) | 검토 중 | `GET /api/me/clone` |
| [FR-049 AI 클론 테스트](https://app.notion.com/5d11a5bdc520839e873e8125d6b2cc33) | X | 제외 |
| [FR-050 AI 클론 피드백](https://app.notion.com/64d1a5bdc5208325980001c8d1db2ed6) | X | 제외 |
| [FR-051 피드백 반영](https://app.notion.com/0e41a5bdc520828d82468166904b0f66) | X | 제외 |
| [FR-052 기간별 클론 재현](https://app.notion.com/b231a5bdc52082ba87ab8136ded53651) | X | 제외 |
| [FR-053 AI 클론 상시 활성화](https://app.notion.com/a961a5bdc520832eab4a81b63e413ef6) | 확정 | 후속 내부/UI/운영 |
| [FR-055 데이터 충분성 평가](https://app.notion.com/e771a5bdc52082649644018c6fb424f0) | 확정 | 후속 내부/UI/운영 |
| [FR-058 대변인 텍스트 대화](https://app.notion.com/7cf1a5bdc5208317aada81d0fc817873) | 확정 | 후속 내부/UI/운영 |
| [FR-059 대변인 음성 대화](https://app.notion.com/3511a5bdc5208284aa8681214efa5e6f) | 확정 | 후속 내부/UI/운영 |
| [FR-060 관련 정보 검색](https://app.notion.com/07f1a5bdc520838c854901ee784b3f24) | 확정 | 후속 내부/UI/운영; 메시지 응답 생성에 통합 |
| [FR-061 사용자 정보 조회](https://app.notion.com/6061a5bdc520825496730120783699ea) | 확정 | 후속 내부/UI/운영; 메시지 응답 생성에 통합 |
| [FR-062 사실 기반 응답](https://app.notion.com/8651a5bdc5208342a4bc016b3b7f0495) | 확정 | 후속 내부/UI/운영; 메시지 응답 생성에 통합 |
| [FR-063 예상 의사 추론](https://app.notion.com/72d1a5bdc52083e39c25814a9d0d2761) | 확정 | 후속 내부/UI/운영; 메시지 응답 생성에 통합 |
| [FR-064 판단 근거 제공](https://app.notion.com/6351a5bdc52082d88b5b8138aa82c959) | 확정 | `GET /api/viewer/conversations/{conversationId}/messages/{messageId}/evidence`; 메시지 응답 생성에 통합 |
| [FR-065 불확실성 제공](https://app.notion.com/8b41a5bdc52082e08e7b81199d165646) | 확정 | 후속 내부/UI/운영; 메시지 응답 생성에 통합 |
| [FR-066 사실과 추론 구분](https://app.notion.com/6ca1a5bdc520823a90c081046c7ab80b) | 확정 | 후속 내부/UI/운영; 메시지 응답 생성에 통합 |
| [FR-067 정보 부족 처리](https://app.notion.com/6f61a5bdc52082d5b78e81ec4f613a9d) | 검토 중 | 후속 내부/UI/운영; 메시지 응답 생성에 통합 |
| [FR-068 응답 생성 데이터 범위](https://app.notion.com/9c01a5bdc5208379a2cd81d9f1e41b33) | 확정 | 후속 내부/UI/운영; 메시지 응답 생성에 통합 |
| [FR-069 응답 출처 조회](https://app.notion.com/1bd1a5bdc52083b7b31f014db3a1b163) | 확정 | 후속 내부/UI/운영 |
| [FR-070 대변인 대화 기록](https://app.notion.com/f151a5bdc520829e818e01731cac3596) | 확정 | 후속 내부/UI/운영 |
| [FR-071 정보 이용자 등록](https://app.notion.com/2a81a5bdc52082498ef6013de09755f3) | 확정 | `GET /api/me/members`<br>`POST /api/me/invitations` |
| [FR-072 정보 이용자 초대](https://app.notion.com/8ad1a5bdc520824ea24d018f15a1f302) | 확정 | 후속 내부/UI/운영 |
| [FR-073 이용 권한 수락](https://app.notion.com/ff41a5bdc52083719e7281c75b9624ae) | 확정 | `GET /api/viewer/invitations`<br>`PATCH /api/viewer/invitations/{invitationId}` |
| [FR-074 접근 범위 설정](https://app.notion.com/0aa1a5bdc5208310a6b0819f1728753b) | 확정 | `PATCH /api/me/members/{memberId}/access` |
| [FR-074-01 정보 이용자 그룹별 접근 권한 설정](https://app.notion.com/41e1a5bdc52081eb8e59c7becc0bfe65) | 검토 중 | `GET /api/me/groups`<br>`POST /api/me/groups`<br>`PATCH /api/me/groups/{groupId}`<br>`DELETE /api/me/groups/{groupId}`<br>`PUT /api/me/members/{memberId}/group`<br>`PATCH /api/me/groups/{groupId}/access` |
| [FR-075 이용 권한 변경](https://app.notion.com/0a81a5bdc5208374b63c81b7341be227) | 확정 | 후속 내부/UI/운영 |
| [FR-076 이용 권한 철회](https://app.notion.com/b081a5bdc52082d68bb181dacc402485) | 확정 | `DELETE /api/me/members/{memberId}` |
| [FR-077 민감정보 공개 제한](https://app.notion.com/66c1a5bdc5208226a33b818003c6bd5b) | 확정 | 후속 내부/UI/운영 |
| [FR-077-01 민감정보 등급 분류](https://app.notion.com/41e1a5bdc52081ee91b0c84d8c4c15fd) | 확정 | 후속 내부/UI/운영 |
| [FR-077-02 민감정보 자동 탐지](https://app.notion.com/41e1a5bdc5208147a064d450d38a958a) | 확정 | 후속 내부/UI/운영 |
| [FR-077-03 민감정보 분류 기준 및 기본 공개 정책](https://app.notion.com/41e1a5bdc52081358c75ca45cc8f01cb) | 구현 범위 밖 | 제외 |
| [FR-077-04 사용자 공개 권한 설정](https://app.notion.com/41e1a5bdc52081ecb66cc644e4ed9187) | 구현 범위 밖 | 제외 |
| [FR-077-05 이용자별 공개 설정](https://app.notion.com/41e1a5bdc52081edad7cc0b96f5946ba) | 검토 중 | 후속 내부/UI/운영 |
| [FR-077-06 민감정보 분류 정정](https://app.notion.com/41e1a5bdc5208133be8af22f8fe4422b) | 확정 | 직접 변경/제외 API 금지 |
| [FR-077-07 검색 권한 필터링](https://app.notion.com/41e1a5bdc52081b5b579c275ec96e76d) | 확정 | 후속 내부/UI/운영 |
| [FR-077-08 응답 필터링](https://app.notion.com/41e1a5bdc52081edbed2c05edb9a81ba) | 확정 | 후속 내부/UI/운영 |
| [FR-077-09 고위험 민감정보 접근 관리](https://app.notion.com/41e1a5bdc52081eeaa51fee9517185e0) | 확정 | 후속 내부/UI/운영 |
| [FR-077-10 공개 권한 변경 이력](https://app.notion.com/41e1a5bdc520817baf16d2d5f8838a05) | X | 제외 |
| [FR-077-11 미분류 정보 저장 제한](https://app.notion.com/41e1a5bdc52081b283c9c3b496011d62) | 검토 중 | 후속 내부/UI/운영 |
| [FR-078 접근 이력 조회](https://app.notion.com/8971a5bdc520833d84800130c1801717) | 확정 | `GET /api/me/clone/access-history` |
| [FR-079 이용 가능 클론 조회](https://app.notion.com/14c1a5bdc520829fa8000185aedc6a58) | 확정 | `GET /api/viewer/clones` |
| [FR-080 접근 제한 알림](https://app.notion.com/8ba1a5bdc52083d2ae0581ea9eaf39ba) | X | 제외 |
| [FR-081 회원 관리](https://app.notion.com/18e1a5bdc5208287bf34811952a62ff1) | 미정 | 제외 |
| [FR-084 신고 관리](https://app.notion.com/df21a5bdc5208263a734818fd19cdf31) | 미정 | 제외 |
| [FR-085 서비스 이용 통계](https://app.notion.com/4261a5bdc52082b986ef814903091fdf) | 미정 | 제외 |
| [FR-086 AI 사용량 관리](https://app.notion.com/f501a5bdc520831ab9c401ed7a13acef) | 미정 | 제외 |
| [FR-087 시스템 로그 관리](https://app.notion.com/5dd1a5bdc52082cea9b80142854ca21e) | 미정 | 제외 |
| [FR-088 공지사항 관리](https://app.notion.com/4bc1a5bdc520827e8f7c01a48fdd8e0d) | 미정 | 제외 |
| [NFR-001 AI 응답 속도](https://app.notion.com/2711a5bdc52082fd90e801ba716f3a89) | 확정 | 후속 내부/UI/운영 |
| [NFR-002 음성 인식 지연](https://app.notion.com/d371a5bdc520821e96188160a254b637) | 확정 | 후속 내부/UI/운영 |
| [NFR-003 음성 출력 지연](https://app.notion.com/ffc1a5bdc52082199f680175b2fc48a9) | 확정 | 후속 내부/UI/운영 |
| [NFR-004 동시 접속 처리](https://app.notion.com/6d81a5bdc52082c59c8381e0070f4e68) | 확정 | 후속 내부/UI/운영 |
| [NFR-005 비동기 데이터 분석](https://app.notion.com/0111a5bdc5208397ab9001f64b940f9c) | 확정 | 후속 내부/UI/운영 |
| [NFR-006 장애 대응](https://app.notion.com/6e21a5bdc520824c83a281cdf7b5375b) | 확정 | 후속 내부/UI/운영 |
| [NFR-007 데이터 복구](https://app.notion.com/1481a5bdc5208236ba6a81fa36a3eadc) | 검토 중 | 후속 내부/UI/운영 |
| [NFR-008 비밀번호 보호](https://app.notion.com/c851a5bdc52082a2b5bc81f7c807de75) | 확정 | 후속 내부/UI/운영 |
| [NFR-009 민감정보 암호화](https://app.notion.com/f171a5bdc52083f6933181d99ff01041) | 확정 | 후속 내부/UI/운영 |
| [NFR-010 통신 암호화](https://app.notion.com/9391a5bdc520830d810981f9642a28cd) | 확정 | 후속 내부/UI/운영 |
| [NFR-011 인증 및 인가](https://app.notion.com/e491a5bdc52083849938816b4e9491df) | 확정 | 후속 내부/UI/운영 |
| [NFR-012 사용자 데이터 격리](https://app.notion.com/4351a5bdc520820389188141c21ee7f0) | 확정 | 후속 내부/UI/운영 |
| [NFR-013 개인정보 논리적 비활성화](https://app.notion.com/80b1a5bdc52083f09a3a81785359e17a) | 확정 | `DELETE /api/me`<br>`DELETE /api/owner/conversations/{conversationId}`<br>`DELETE /api/viewer/conversations/{conversationId}`<br>`DELETE /api/owner/conversations/{conversationId}/messages/{messageId}`<br>`DELETE /api/viewer/conversations/{conversationId}/messages/{messageId}` |
| [NFR-014 접근 감사](https://app.notion.com/aa11a5bdc52082098b6f01e8ad614eb7) | 확정 | 후속 내부/UI/운영 |
| [NFR-015 프롬프트 인젝션 방어](https://app.notion.com/e631a5bdc520827a813c018e85b26215) | 확정 | 후속 내부/UI/운영 |
| [NFR-016 관리자 접근 제한](https://app.notion.com/a241a5bdc52083e9ac6d81c64b318f9a) | 구현 범위 밖 | 제외 |
| [NFR-017 응답 근거 확보](https://app.notion.com/9541a5bdc52083cc866d01b765f1f2af) | 확정 | 후속 내부/UI/운영 |
| [NFR-018 할루시네이션 최소화](https://app.notion.com/3c31a5bdc52082b59452813a77fde656) | 확정 | 후속 내부/UI/운영 |
| [NFR-019 AI 클론 식별](https://app.notion.com/97e1a5bdc5208216830c81f3dab9e2c9) | 확정 | 후속 내부/UI/운영 |
| [NFR-020 법적 효력 제한](https://app.notion.com/75a1a5bdc52082ec9f9c011b9d8023ce) | 확정 | 후속 내부/UI/운영 |
| [NFR-021 고위험 판단 안내](https://app.notion.com/cb01a5bdc52083e589f80100fdb93ae6) | 확정 | 후속 내부/UI/운영 |
| [NFR-022 데이터 최신성](https://app.notion.com/c821a5bdc52082ec80598155b74785ff) | 확정 | 후속 내부/UI/운영 |
| [NFR-023 정보 출처 추적성](https://app.notion.com/6b01a5bdc520832aa81a81383b1bb986) | 확정 | `GET /api/viewer/conversations/{conversationId}/messages/{messageId}/evidence` |
| [NFR-024 반응형 웹](https://app.notion.com/b211a5bdc520825e80b8818a2bd4086f) | 확정 | 후속 내부/UI/운영 |
| [NFR-025 접근성](https://app.notion.com/4031a5bdc520834cbd4381b6512a16c3) | 검토 중 | 후속 내부/UI/운영 |
| [NFR-026 직관적 인터페이스](https://app.notion.com/5db1a5bdc52082c8a92c815c1f38e789) | 확정 | 후속 내부/UI/운영 |
| [NFR-027 오류 메시지](https://app.notion.com/49b1a5bdc520831fa3af813d6a1d1159) | 확정 | 후속 내부/UI/운영 |
| [NFR-028 모듈 독립성](https://app.notion.com/0be1a5bdc5208330938d0147a0da35cb) | 확정 | 후속 내부/UI/운영 |
| [NFR-029 모델 확장성](https://app.notion.com/1ee1a5bdc52083859c5d813084cf4038) | 확정 | 후속 내부/UI/운영 |
| [NFR-030 API 문서화](https://app.notion.com/e151a5bdc520838c912a81c2195dd88d) | 확정 | 후속 내부/UI/운영 |

## 지식 5대 분류 변경 설계 (2026-11-26)

아래는 **설계 계약 변경**이며 약관 제거에 따라 Java/DTO/EndpointContractTests를 갱신했다. 위 목록은 실행 코드 57개 경로의 인벤토리다. 목표는 기존 8개 도메인 그대로 58개 경로다.

| 메서드 | 경로 | 변경 | 현재 코드 |
| --- | --- | --- | --- |
| GET | /api/me/analysis?kind=FACT | 기존 목록에 선택 필터 추가 | kind 미바인딩 |
| GET | /api/me/analysis?kind=MEMORY | 같은 목록의 필터 값 | kind 미바인딩 |
| GET | /api/me/analysis?kind=VALUE | 같은 목록의 필터 값 | kind 미바인딩 |
| GET | /api/me/analysis?kind=PREFERENCE | 같은 목록의 필터 값 | kind 미바인딩 |
| GET | /api/me/analysis?kind=BEHAVIOR | 같은 목록의 필터 값 | kind 미바인딩 |
| GET | /api/me/analysis/summary | 신규 경로 1개 | 전용 핸들러 없음 |

kind는 FACT/MEMORY/VALUE/PREFERENCE/BEHAVIOR 중 하나이며 미지정 전체다. informationId는 knowledge_items.id다. 상세는 해당 버전의 단일 분류 상세 테이블을 JOIN하고 이력·출처·충돌을 제공한다. summary는 버전 수가 아닌 유효한 활성 항목 수를 5종별 집계한다.

요청/응답과 후속 테스트는 [ANALYSIS_API.md](design/ANALYSIS_API.md), 기계 판독 계약은 [openapi.json](design/openapi.json), 호출·TX·병합·권한 설계는 [BACKEND_ARCHITECTURE.md](design/BACKEND_ARCHITECTURE.md)를 따른다. 조회 필터는 분류별 공개 권한 설정이 아니며 지식 수정/제외 API는 추가하지 않는다.
