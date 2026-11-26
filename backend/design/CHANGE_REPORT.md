# 지식 5대 분류 상세 설계 변경 보고

기준일: 2026-11-26. 실제 기능 구현이 아닌 설계 변경이다. 기존 Java·프론트엔드·빌드 의존성·실제 DB는 변경하지 않았다. 실행 코드 9개 도메인/58개 경로와 기존 테스트의 null 골격을 유지한다.

## 주요 결과와 요청 대비 판단

1. 기존 24개 기반에 `fact_details`, `memory_details`, `value_details`, `preference_details`, `behavior_details`를 추가해 **핵심 29개**로 설계했다.
2. 기존 그룹 API는 있었으나 저장 테이블이 없어 접근권한 요구와 모순됐다. `member_groups`와 clone_permissions.group_id를 보완해 **전체 설계는 30개**다. 기존 테이블을 임의로 제거하지 않았다.
3. 기존 목록 경로에 kind를 설계하고 summary GET 한 개를 추가해 **목표 API는 59개**다. 현재 소스는 58개이며 kind 미바인딩/summary 미등록 차이를 계약에 명시했다. Java 변경 금지 지시에 따라 이를 숨기거나 기존 테스트 숫자만 바꾸지 않았다.
4. 상세는 항목이 아닌 지식 버전과 1:1 연결한다. kind 복합 FK/상수 CHECK로 종류 혼합을 막고, 커밋 시점 지연 제약 트리거로 상세 누락을 막는다.
5. lease_token/lease_until과 최종 CAS를 도입했다. DB 행을 장시간 잠근 채 LLM/API를 호출하지 않는다. 분류 실패는 완료 결과 개수만 남기며 지식 재시도는 하지 않는다.

## 파일별 변경

| 파일 | 변경 |
| --- | --- |
| backend/design/everme.dbml | 신규 추적 가능한 기준 DBML. 전체 30개, 5종 상세·복합 관계·CHECK·인덱스·lease |
| docs/database/everme.dbml | 기존 DBML을 기준본과 동일하게 갱신 |
| output/architecture/everme-rag-proposal.dbml | 구버전 벡터 제안 사본을 동일 기준본으로 갱신 |
| docs/database/everme.dbdiagram | 기존 darkMode/레이아웃 유지, 신규 테이블 배치 추가 |
| output/architecture/everme-rag-proposal.dbdiagram | 기존 형식/표현 유지, 신규 테이블 배치 추가 |
| docs/database/README.md | 기존 7종·22개/보류 설명을 5종·29+1 설계와 최신 정책으로 정정 |
| backend/design/BACKEND_ARCHITECTURE.md | 계층/호출/동기·비동기/TX/병합/임베딩/RAG/권한/전환 상세 |
| backend/API_ENDPOINTS.md | 기존 58개 인벤토리 유지, kind/summary 목표 계약 및 구현 상태 추가 |
| backend/design/ANALYSIS_API.md | 목록·상세·summary의 요청/응답·집계·DTO·후속 테스트 |
| backend/design/openapi.json | 9개 도메인/59개 목표 API, 현재/계획 상태 구분 |
| backend/design/endpoint-contract.json | 실제 58개와 목표 59개 경로 인벤토리 |
| backend/design/architecture-model.json | DBML에서 추출한 30개 테이블·FK·9개 보기·호출 계약 |
| output/architecture/everme-data-architecture.html | 기존 색상·범례·테이블 선택 방식 유지. 동기 대화·5종·병합/임베딩·클래스 보기 추가 |
| backend/design/constraints-design.sql | 상세 누락·불변 지식·원문 소유자·충돌 항목 검사 SQL 설계. 자동 실행 migration 아님 |
| backend/design/knowledge-kinds.json | 분류별 상세 속성·제약의 기계 판독 정의 |
| backend/design/README.md | 기준 자료·사본·재생성·검증 방법 안내 |
| backend/design/package.json / package-lock.json | DBML 파서·격리 PostgreSQL 엔진의 설계 검증 전용 고정 의존성. 런타임 빌드와 분리 |
| backend/design/update-design.cjs | DBML 사본 동기화 도구. 애플리케이션 코드/DB 실행 없음 |
| backend/design/sync-artifacts.cjs | OpenAPI/모델/기존 HTML/레이아웃 동기화 도구 |
| backend/design/validate-design.cjs | DBML 파싱·문서/계약 일치·격리 관계형 제약 검증 |
| backend/design/validation-results.json | 검증 결과와 한계 기록 |
| output/architecture/check-architecture.cjs | 9개 보기·선택·모바일/데스크톱 화면 검증으로 확장 |

기존 docs/output은 gitignore 대상이다. 기준 설계를 backend/design에 두었으며 ignore 정책을 임의로 수정하지 않았다. 이 작업에서 commit/push는 하지 않는다.

## 테이블·컬럼·관계 변경

| 대상 | 변경 컬럼/제약 |
| --- | --- |
| information_kind enum | 5개 대문자 FACT/MEMORY/VALUE/PREFERENCE/BEHAVIOR. 기존 소문자·성향/언어 스타일은 전환 계획 필요 |
| knowledge_items | semantic_key, lock_version 추가. UNIQUE(owner_id,kind,semantic_key), UNIQUE(id,kind), 카운터 CHECK |
| knowledge_versions | kind 타입 가드 추가. item_id+kind 복합 FK, UNIQUE(id,kind), 버전/시간 CHECK |
| 5종 상세 | version_id PK, 상수 kind CHECK, version_id+kind 복합 1:1 FK. 고유 코드·암호화 값·극성/강도/빈도 등 개별 칼럼. 공통 owner/등급/시점/내용 중복 없음 |
| analysis_jobs | owner_id, lease_token, lease_until, locked_by, attempt_count/max_attempts, analyzer_revision, 판정별 5개 개수, failure_code 추가. 상태/lease/완료/카운터 CHECK와 폴링 인덱스 |
| knowledge_sources | candidate_no 추가. UNIQUE(job,candidate,revision), 역방향 원문 활성 인덱스. 기존 N:M PK 유지 |
| knowledge_conflicts | 상태·서로 다른/정렬된 버전 쌍 CHECK. 같은 item의 충돌인지 보완 트리거 |
| knowledge_embeddings | 24개 기준의 기존 제안을 통합. source_fingerprint 추가, 버전/모델 UNIQUE·owner/등급/활성 인덱스 |
| embedding_jobs | lease·시도 횟수·fingerprint·실패/완료 시각 추가. 버전/모델 UNIQUE·상태/lease CHECK |
| member_groups | 그룹 식별·clone FK·이름·default_level·활성·시각. UNIQUE(clone_id,name), UNIQUE(id,clone_id) |
| clone_permissions | group_id 추가 및 (group_id,clone_id) 복합 FK. max_level을 그룹 일괄 변경 시 같은 TX에서 갱신 |
| consent_events | 스키마 유지, 가입/탈퇴 외 개별 철회 API가 없다는 정책 설명 정정 |

기존 회원·대화·클론·초대·감사 테이블을 삭제하지 않았다. 상세의 version_id가 PK이므로 같은 분류 상세 2행은 불가능하고 다른 kind 상세는 복합 FK/상수 CHECK로 차단한다. DBML은 정확히 한 상세의 존재를 단독 보장하지 못하므로 SQL 보완안이 필요하다. 상세 UPDATE/DELETE는 차단하며 새 버전으로 이력을 보존한다.

## 트랜잭션·비동기 변경

- TX-1: 사용자 messages + message_revisions + analysis_jobs queued.
- TX 밖: 최근 대화·기존 지식 검색과 AI 응답 LLM.
- TX-2: assistant 메시지·본문 저장. 분석 작업을 생성하지 않음.
- TX-3: SKIP LOCKED로 점유·lease 갱신 후 즉시 커밋.
- TX 밖: 원자 추출 → 5종/민감도 판정 → 기존 지식 비교 → Analyzer → Merge 계획.
- TX-4: owner 행 → 작업 행 → 지식 순서 잠금, token·lease·원문 활성 재검증, items/versions/상세/sources/conflicts/embedding_jobs/작업 완료 원자 저장.
- 임베딩 별도 점유 TX → API는 TX 밖 → 저장 TX에서 fencing·활성·fingerprint 확인 후 UPSERT+완료.
- 분류 실패는 completed + classification_failed_count이며 재시도 없음. 프로세스 사망으로 만료된 running만 제한적으로 재점유한다. 외부 API exactly-once를 보장한다고 표현하지 않는다.

## 검증과 실제 적용 전 과제

검증 결과의 구체적 목록은 validation-results.json을 따른다. DBML 현대 파서로 30개 및 복합 FK/CHECK를 파싱하고, 격리된 PostgreSQL 엔진에서 5종 정상 저장과 잘못된 상세/누락/덮어쓰기/그룹/lease/소유자 등을 검증했다. pgvector는 관계형 검증에서 대체 타입을 사용했으므로 실제 ANN·암호화·성능을 검증한 것은 아니다. 운영 DB에는 접속하지 않는다.

후속 과제: 모델/차원·키 관리·L4 상세 메타데이터 보호·HMAC 정규화/키 교체, 7→5종 데이터 전환, 그룹 정책 최종 합의, 다중 연결 SKIP LOCKED 경합/lease heartbeat 테스트, 실제 벡터 인덱스 실행 계획·회수율, summary/kind 코드·DTO·테스트 59 반영. Java 소스는 이 설계 작업에서 변경하지 않았다.
