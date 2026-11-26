# EVERME 데이터베이스 상세 설계

기준일: 2026-11-26. 최신 사용자 첨부의 5대 분류·비동기 작업·API 정책을 기존 설계에 반영했다. 운영 DB migration이나 실제 구현은 아니다.

## 기준 파일

- [everme.dbml](everme.dbml): 전체 28개. 기준본은 [backend/design/everme.dbml](../../backend/design/everme.dbml)이며 내용이 동일하다.
- [백엔드 상세 아키텍처](../../backend/design/BACKEND_ARCHITECTURE.md): 클래스/메서드, TX-1~4, ETL·병합·임베딩·RAG·권한.
- [API 설계](../../backend/design/ANALYSIS_API.md): 실행 57개 유지, 목표 58개. kind 5개 필터와 summary 1개 추가 설계.
- [변경 보고](../../backend/design/CHANGE_REPORT.md), [검증 결과](../../backend/design/validation-results.json).
- docs/output은 기존 gitignore 대상이며 추적 가능한 기준 자료는 backend/design에 있다.

## 개수와 기존 설계 대비

기존 원본에서 약관 2개 제거 후 20개 + 벡터 제안 2개 = 22개. 5종 상세를 추가한 핵심 27개에 그룹 API 저장 보완 member_groups 1개를 더해 총 28개다. 약관 문서·동의 이력 테이블 2개를 제거했다. 27개를 엄격히 유지하려면 그룹 저장·API 구현을 보류해야 하므로 이번안은 그룹 권한 요구를 반영해 28개를 선택했다.

분류는 FACT/MEMORY/VALUE/PREFERENCE/BEHAVIOR 5개다. 기존 7종 소문자 enum에서 personality/linguistic_style은 자동 변환하지 않는다. 원문과 기존 값을 보존한 별도 전환 계획이 필요하다.

## 테이블 역할

| 테이블 | 역할 | 상태 |
| --- | --- | --- |
| accounts | 계정·활성 상태 | 기존 |
| social_identities | 소셜 계정 연결 | 기존 |
| auth_sessions | 로그인 세션 | 기존 |
| password_reset_tokens | 비밀번호 재설정 토큰 | 기존 |
| deactivation_requests | 논리 비활성화 요청 | 기존 |
| clones | 클론 식별·소유자 | 기존 |
| clone_assessments | 정보 충분성 평가 | 기존 |
| clone_invitations | 초대 상태 | 기존 |
| clone_permissions | 개인별 현재 허용 등급 | 기존 수정 |
| conversation_sessions | personal/spokesperson 세션 | 기존 |
| messages | 메시지 역할·순서·상태 | 기존 |
| message_revisions | 원문과 정정 버전 | 기존 |
| analysis_jobs | 분석 작업·lease 점유 | 기존 수정 |
| knowledge_items | 지식 정체성·소유자·분류 | 기존 수정 |
| knowledge_versions | 내용·민감도·시간 이력 | 기존 수정 |
| knowledge_sources | 지식 버전 ↔ 원문 근거 | 기존 수정 |
| knowledge_conflicts | 상충하는 버전 | 기존 수정 |
| response_segments | 답변의 사실·추론·안내 | 기존 |
| response_evidence | 답변이 사용한 지식 버전 | 기존 |
| audit_events | 접근·권한 변경 감사 | 기존 |
| member_groups | 그룹과 일괄 등급 변경 | 추가 제안 |
| knowledge_embeddings | 지식 버전의 검색 벡터 | 추가 제안 |
| embedding_jobs | 인덱싱 작업·lease 점유 | 추가 제안 |
| fact_details | FACT: 주체·술어·값 형식 | 추가 제안 |
| memory_details | MEMORY: 사건·회상 특성 | 추가 제안 |
| value_details | VALUE: 원칙·판단 영역 | 추가 제안 |
| preference_details | PREFERENCE: 호불호·대상 | 추가 제안 |
| behavior_details | BEHAVIOR: 계기·빈도·습관 | 추가 제안 |

## 관계와 무결성

knowledge_items 1:N knowledge_versions, 각 version에는 정확히 한 분류 상세가 1:1 연결된다. 상세 PK는 version_id다. owner/내용/민감도/시점/활성은 공통 테이블을 JOIN한다. 고정 kind CHECK + 복합 FK는 서로 다른 상세 혼합을 차단한다. 상세 누락은 커밋 시 지연 constraint trigger로 보완한다. [SQL 보완 설계](../../backend/design/constraints-design.sql)는 자동 실행하지 않는다.

knowledge_sources는 지식 버전과 원문 revision을 N:M 연결한다. response_evidence는 답변 부분과 사용한 지식 버전을 연결한다. 단순 출처 링크는 원문 전체 공개 권한을 의미하지 않는다. 원문 혼합 민감도는 별도 검사·마스킹한다.

작업의 lease_token/lease_until과 최종 CAS는 이전 워커의 늦은 커밋을 막는다. 새 지식/버전/상세/출처/embedding_jobs/분석 완료는 한 TX-4에서 저장한다. 외부 LLM·임베딩 호출은 TX 밖이다. 분류 실패는 지식을 저장하거나 재시도하지 않는다.

그룹은 한 이용자당 최대 하나다. member_groups.default_level 변경과 소속 clone_permissions.max_level 변경은 같은 TX다. 검색은 현재 max_level을 사용한다. 이동은 새 default, 해제는 기존 개인 등급 유지, 철회는 별도다. 이 그룹 정책은 구현 전 확인할 설계 가정이다.

## 기존 정책 유지

- 사용자/대변인 대화는 공통 테이블, kind=personal/spokesperson. 참여 계정과 클론 소유자는 다르다.
- 사용자 personal/user 발언만 지식 추출 대상. AI 답변·정보이용자 질문은 사용자 사실로 축적하지 않는다.
- 약관은 프론트에 하드코딩한다. 가입 시 agreedToTerms=true를 요구하는 계약이며 동의 문서·이력은 DB에 저장하지 않는다.
- 삭제는 논리 비활성화. 지식 내용/민감도 직접 수정·개별 활용 제외 API 없음.
- 클론은 별도 활성화 신청 없이 이용하며 개인정보 비활성화·현재 접근 권한은 검사한다.
- 유효한 과거 정보도 전체 누적 기록의 일부로 검색. 기간별 페르소나 기능 없음.
- 벡터 검색 필터와 LLM 전달 전 2차 인가, 응답·근거·이력·TTS에도 현재 권한 적용.

## 검증 한계

현대 DBML 파서와 격리 PostgreSQL 엔진에서 관계형 제약을 검증한다. 테스트의 vector는 대체 타입이므로 실제 pgvector/ANN·모델·암호화·네트워크·다중 워커 경합은 별도 검증 대상이다. 실제 DB에는 접속하지 않는다. 모델/차원 결정 후 vector(d), 키 관리, HNSW 실행 계획·검색 정확도를 확인해야 한다.
