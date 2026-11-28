# EVERME Clone Lab

별도 실험 프로필에서 대화 생성 → 근거 분석 → 프로필 UPDATE → 기억 검색 → 클론 답변을 검증한다. 기존 `/api/me`, owner/viewer 엔드포인트와 기존 5종 지식 설계는 수정하지 않는다.

Java 소스는 `backend/src/main/java/com/example/everme/lab/` 아래에서 역할별로 구분한다.

- `controller`: 실험 REST API, HTML·정적 자원 응답, API 예외 응답 처리.
- `service`: 대화·분석·저장·클론·자동 실행 처리와 AI 모델 설정·호출, 검증·카탈로그 등 지원 코드.
- `dao`: DB 접근, MyBatis 매퍼와 UUID 타입 핸들러.

실험 테스트는 대응하는 `backend/src/test/java/com/example/everme/lab/service/` 패키지에 둔다.

코드 작성은 학습 프로젝트 `ch10-rag`, `ch11-tool-calling-practice`를 기준으로 2칸 들여쓰기, 명시적 import·지역 변수 타입, 중괄호가 있는 제어문, 필드·생성자·주요 메소드 구획 주석을 사용한다. ChatClient는 `prompt → system/user → options/advisors → call` 체인으로 작성한다. 도구는 `@Tool`, `@ToolParam`을 선언하고 서버가 만든 `ToolContext`로 소유자를 전달한다. 도구 실행 순서와 인자 검증은 기존 콜백 경계에서 유지한다. LLM 요청·응답은 SimpleLoggerAdvisor로 기록하고 운영 진단 로그는 Lombok `@Slf4j`를 사용한다.

## 실행

저장소 루트(`EVERME`) 또는 `backend` 폴더에서 아래 명령 하나로 실행한다.

```powershell
.\clone-lab.cmd
```

Docker Desktop, Java 21, `OPENAI_API_KEY`가 준비되어 있어야 한다. DB 컨테이너가 없으면 생성하고, 중지되어 있으면 시작하며, DB 준비 후 스키마 초기화와 서버 실행을 진행한다. 이미 실험 서버가 실행 중이면 주소만 안내하고 중복 실행하지 않는다. 종료는 **Ctrl+C**이며 DB와 데이터는 유지한다.

- 사전 확인만: `.\clone-lab.cmd -CheckOnly`
- 서버 포트 변경: `.\clone-lab.cmd -Port 8088`
- 실행 완료 기준은 `Started BackendApplication` 로그다. 서버 실행 중 Gradle 진행률이 80%에 머무르는 것은 정상이다.
- 한글 로그는 UTF-8로 출력한다. 실행 스크립트가 콘솔 인코딩을 맞추며 Gradle 컴파일·실행 JVM과 로그 인코딩도 UTF-8을 명시한다. 변경 후 서버를 완전히 종료하고 다시 실행한다. IDE에서 직접 실행하면 IDE 콘솔 인코딩도 UTF-8로 맞춘다.

### 수동 실행

Java 21, Docker, `OPENAI_API_KEY`가 필요하다. 기존 DB 대신 별도 PostgreSQL+pgvector를 사용한다.

```powershell
docker run --detach --name everme-clone-lab --publish 127.0.0.1:55439:5432 --env POSTGRES_DB=everme_lab --env POSTGRES_USER=everme_lab --env POSTGRES_PASSWORD=everme_lab --volume everme-clone-lab-data:/var/lib/postgresql/data pgvector/pgvector:pg17

# OPENAI_API_KEY는 현재 셸 환경변수로 설정한다. 저장소나 HTML에 키를 넣지 않는다.
cd backend
.\gradlew.bat bootRun --args="--spring.profiles.active=clone-lab --server.port=8087 --lab.initialize-schema=true"
```

- 대화·분석: <http://127.0.0.1:8087/lab>
- 정보이용자: <http://127.0.0.1:8087/lab/viewer>
- 첫 실행에서만 스키마 생성이 필요하다. 이후 `--lab.initialize-schema=true`를 생략할 수 있다. 기존 테이블에 대한 자동 데이터 마이그레이션은 하지 않는다.
- 컨테이너 생성 명령은 최초 한 번만 실행한다. 이후에는 `docker start everme-clone-lab`으로 기존 DB를 시작한다. 백엔드도 한 번만 실행하고, 재실행 전 기존 터미널에서 Ctrl+C로 중지한다.
- `clone-lab` 미활성화 시 실험 Controller·Service·Dao와 HTML 경로가 등록되지 않는다. 리소스는 public/static 디렉터리에 두지 않는다.
- 기본 바인딩은 `127.0.0.1`이다. 인증·초대·공개 권한을 구현한 운영 서비스가 아니므로 이 실험 서버를 외부에 배포하지 않는다.
- 단일 서버 프로세스용이다. 같은 실험 DB에 여러 서버를 띄우지 않는다. 서버 재시작 시 진행 중 실행은 paused로 복구한다.

## 모델과 환경변수

| 변수 | 기본값 |
| --- | --- |
| LAB_DATABASE_URL | jdbc:postgresql://localhost:55439/everme_lab |
| LAB_DATABASE_USER / LAB_DATABASE_PASSWORD | everme_lab / everme_lab |
| LAB_GENERATION_MODEL | gpt-6.1-sol |
| LAB_ANALYSIS_MODEL | gpt-6.1-sol |
| LAB_MERGE_MODEL | gpt-6.1-sol |
| LAB_ANSWER_MODEL | gpt-6.1-terra |
| LAB_EMBEDDING_MODEL | text-embedding-3-small |
| LAB_OPENAI_BASE_URL | https://api.openai.com/v1 |
| LAB_INITIALIZE_SCHEMA | false |

모델은 자동 대체하지 않는다. `POST /api/lab/models/check`로 계정의 모델 접근 권한을 확인할 수 있다. 생성·분석·요약·사용자 대화 응답은 Spring AI `ChatClient → OpenAiChatModel`의 Chat Completions strict JSON Schema를 사용한다. 클론 답변은 `ChatClient → LabResponsesChatModel`에서 Responses API의 function_call/function_call_output과 추론 항목을 보존한다. 임베딩은 Spring AI `OpenAiEmbeddingModel`로 1536차원을 명시한다. 모델을 바꾸어 벡터 공간이 바뀌면 기존 기억을 그대로 혼용하지 말고 새 실험 DB에서 시작한다.

## 사용 순서

1. 이름·배경으로 고정 가상 인물을 만든다. 정답 점수·인물 설정은 서버에만 보존한다.
2. 주제를 넣고 **10개 대화 생성**을 누른다. 원문만 저장되고 분석은 하지 않는다.
   - **추가 대화 10개**는 선택한 대화의 주제로 10쌍을 이어 붙인다(10→20→30쌍). 추가 후에는 다시 분석해야 한다. 이전에 저장한 점수는 유지하고 이미 반영한 발언은 중복 반영하지 않는다.
   - 대화 내역 아래 입력창에서 직접 메시지를 보내면 AI가 공감·경청하며 답한다. Enter로 전송, Shift+Enter로 줄바꿈한다. 선택한 대화에 1쌍씩 추가되며, 대화가 없으면 새로 시작한다. 직접 쓴 발언도 이후 분석 대상이다. 대화 전송 자체는 수치 분석·프로필 갱신을 실행하지 않는다.
3. **분석**을 누르면 사용자 발언만으로 관찰된 요소와 기억 후보를 표시한다. 이 시점에도 프로필은 바뀌지 않는다.
   - `DB 저장값`은 기존 누적값, `이번 분석 (저장 전)`은 이번 배치의 분석값이다. 위 초록 막대는 DB 저장값(미관찰은 초기 기준 50), 아래 파란 막대는 저장 전 관찰값이다. 저장하면 파란 막대는 사라진다.
4. **저장**은 서버가 요소별 가중평균을 계산하고 검증한 뒤 7개 프로필 UPDATE·기억 INSERT·완료 상태를 한 트랜잭션으로 처리한다. 병합 LLM 호출은 없다.
5. **1000개 테스트**는 같은 주제의 대화 하나에 1000쌍을 이어 생성하며, 10쌍마다 분석·저장한다. 생성 작업 1개와 분석·저장 작업 1개를 병행하고, 생성 예약을 포함한 미저장 구간은 최대 2개(20쌍)다. 다음 생성은 직전 대화를 이어받는다. 저장은 구간 순서대로 진행한다. 중단 시 이미 시작된 작업만 완료하고 초안·생성 결과를 보존한다. 재개 시 저장된 구간은 건너뛰고 분석 초안이 있으면 저장부터 진행한다. 서버 재시작 시에도 같은 대화에서 재개한다. 미완료 새 파이프라인에서는 수동 변경을 차단한다.
6. 정보이용자 화면에서 클론과 저장된 컨텍스트 배치를 선택한다. 배치 없이 장기 기억부터 찾을 수도 있다. 클론 변경 시 사용자별 대화 컨텍스트를 분리하며 새 대화는 이전 컨텍스트를 교체한다.
7. 결과 JSON에는 현재 프로필·원문·분석·기억·자동 실행 상태와 관찰된 요소의 합성 정답 대비 오차가 들어 있다. 원본 생성 설정은 일반 프로필·답변 API에 반환하지 않는다.

## 새 DB 구조와 최근 기억 (2026-11-28)

- 물리 테이블은 lab_users / sessions / context / vector_store / profile_* 7개다. 기존 실험 데이터는 사용자 승인으로 초기화했다. 과거 스키마의 데이터도 비웠으며 재이관하지 않는다.
- context와 vector_store는 각각 Spring AI PgVectorStore로 검색한다. 저장은 외부 임베딩 호출을 먼저 완료하고, MyBatis로 준비된 벡터·metadata를 프로필·처리 완료 기록과 같은 트랜잭션에 반영한다. PgVectorStore.add()로 트랜잭션 안에서 임베딩 API를 호출하지 않는다.
- owner 원문은 사용자별 최근 100쌍, viewer 원문은 clone_owner_id+actor_user_id별 최근 100쌍을 보관한다. pending/analyzed는 100쌍 초과 시에도 삭제하지 않는다.
- 저장 완료 원문은 범위를 초과하면 원래 content·embedding과 source_ids·source_excerpts를 vector_store에 원자적으로 이관한 뒤 삭제한다. 이관 실패 시 프로필·기억·완료 표시도 함께 롤백된다. 원문 이관 기록은 metadata.record_type=context_archive다.
- 질문 수신 시각 user_received_at과 AI 응답 수신 시각 assistant_received_at은 metadata의 UTC ISO 시각이다. created_at은 DB 저장 시각이다. 합성 10쌍은 생성 요청·응답 시각을 공유하고 synthetic=true로 표시한다. 대화 순서는 sequence_no로 판단한다.
- 세션 전체 status 컬럼은 없다. 기존 화면 응답의 status는 개별 context 상태에서 계산하는 호환 필드다. batchId/id/ordinal도 기존 화면 호환 이름이며 실제 DB에서는 session_id와 sequence_no를 사용한다. metadata.ordinal은 세션 안의 논리 순번이며 원문 배열 위치를 구간 식별자로 사용하지 않는다.
- 세션 JSON에는 turnCount, 고정 구간 sourceIds, 요약 캐시, processedEvidence를 보존한다. 원문이 삭제된 뒤에도 같은 발언을 프로필에 다시 반영하지 않는다.
- 정보이용자 화면에서 이용자를 별도 선택할 수 있다. 세션 생성·질문 요청에 actorUserId, 현재 이력 조회에 actorUserId 쿼리를 사용한다. 생략하면 자기 클론 테스트로 처리한다. 이용자 기록은 owner 기억·프로필 근거로 사용하지 않는다. 실험용 선택 기능이며 인증 기능을 구현한 것은 아니다.
- 클론은 owner 범위의 context 벡터 검색 후 부족하면 searchLongTermMemory, 그 다음 getAnalysisProfile을 사용한다. viewer 대화 컨텍스트는 별도로 분리한다.
- 새 DB 초기화 기준은 src/main/resources/clone-lab/schema.sql이다. 카탈로그 생성기는 DDL/DBML을 다시 생성하지 않는다. 기존 구형 DB의 자동 업그레이드는 하지 않으며 이관 SQL은 migrations 폴더에서 수동 관리한다.
- 잔액 소진(credit_balance_exhausted) 및 insufficient_quota 유형은 재시도하지 않는다. 실제 호출에는 API 크레딧 충전이 필요하다.

## 요소·수치·저장 규칙

새 주제의 대화 10쌍 생성에는 주제만 전달하고 이전 주제의 원문은 섞지 않는다. 이어 생성과 사용자 직접 입력 응답에는 소유자 기준 최근 100쌍을 전달한다. 인물 배경·사실·140개 정답 점수는 비교 전용이며 대화 생성·분석·클론 답변에 전달하지 않는다. AI는 공감·경청·맞장구로 반응하며 사용자가 대화를 주도한다. 분석에는 140개 요소 명세를 사용한다.

수동 대화의 주제는 선택 사항이다. 빈 주제로 시작하면 9쌍까지 주제 미정으로 표시하고, 10번째 쌍이 저장될 때 최초 10쌍을 분석 모델로 읽어 주제를 한 번 확정한다. 10쌍 생성 버튼도 빈 주제를 허용한다. 확정한 주제는 이후 대화에 적용하고 직접 지정한 주제는 유지한다. 주제 추출은 성향 분석·프로필 저장과 별개다. 추출 오류 시 해당 요청의 대화와 주제는 함께 저장되지 않아 재시도할 수 있다. 1000쌍 자동 실행은 시작 주제가 필요하다.

분석은 미반영 사용자 발언을 최대 10쌍씩 처리한다. 문맥 요약은 같은 대화의 앞선 10쌍 단위만 최근 최대 5개를 사용한다. 다른 대화 주제는 섞지 않는다. 요약은 최대 1000자이며 저장해 재사용한다. 분석 대상 원문 ID는 구간별로 고정하고 요약·다음 구간을 새 점수 근거로 삼지 않는다. 미분석 43쌍은 10+10+10+10+3으로 모두 처리한다. 수동 분석 결과는 analysis.windows·summaryCache, 자동 실행 결과는 analysis.segments에서 확인한다.

여러 분석 창의 같은 요소는 근거 강도 가중 평균으로 합치고 배치 근거 강도는 최대 3으로 제한한다. DB 누적은 기존 분석→저장 절차를 유지한다. 인물 설정과 무관하게 생성한 대화의 점수 오차는 해당 인물의 재현 정확도로 해석할 수 없다.

프로필 저장은 정책 `element-cap-v1`의 요소별 상한 `C=10·20·40`을 적용한다. 빠름 38개·중간 48개·느림 54개이며, 초기 실험 가설이다. 기존 점수가 NULL이면 기존 가중치를 0으로, 아니면 `min(기존 가중치, C)`로 계산한다. 새 점수는 `(기존 점수 × 인정 가중치 + 관찰 점수 × 근거 강도) / (인정 가중치 + 근거 강도)`이며 소수 둘째 자리 HALF_UP 반올림한다. 저장할 가중치는 `min(인정 가중치 + 근거 강도, C)`이다. 분모에는 상한 적용 전 합을 사용한다. 기존 40·가중치 200에 관찰 60·강도 2를 반영하면 상한 10은 43.33·가중치 10, 상한 20은 41.82·가중치 20, 상한 40은 40.95·가중치 40이다. 서버가 직접 계산하고 검증하며 관찰이 없는 요소는 유지한다. 기존 데이터는 일괄 수정하지 않고 해당 요소를 다음 저장할 때 상한을 적용한다. 새 상한이 40이더라도 기존 가중치 20을 부풀리지 않는다. 요소별 배정과 이유는 ELEMENTS.md에 명시한다. 저장 결과의 updatePolicyVersion·maxWeights로 적용 정책을 확인할 수 있다.

- [ELEMENTS.md](ELEMENTS.md): 7개 영역 × 20개 요소의 코드·수치 기준·근거 예·오판 예·경계.
- [clone-lab-design.dbml](clone-lab-design.dbml): 현재 Clone Lab의 기준 DBML. 11개 테이블이며 운영 DBML과 독립적이다.
- [clone-lab-design.dbml](clone-lab-design.dbml): 합의한 DB 설계. sessions.status만 제거하고 analysis·profile_version은 유지한다. context는 created_at·status·sequence_no 컬럼을 사용한다. context/vector_store는 owner·viewer가 공유하되 클론 소유자와 실제 참여자를 구분한다. 사용자·세션·기억 종류·출처·사건 시점은 metadata에 보존하며 conversation_type은 metadata에서 계산하는 생성 컬럼이다. 최근 100쌍을 초과한 원문은 분석·장기 기억·출처 보존을 완료한 뒤 삭제한다. 카탈로그 생성기가 덮어쓰지 않는다. 2026-11-28 기준 Java와 로컬 DB에 반영했다.
- [clone-lab-design.sql](clone-lab-design.sql): DBML을 PostgreSQL로 변환하고 생성 컬럼·범위 검색·멱등성·벡터 인덱스를 보완한 검토용 DDL. 현재 DB에 실행하는 마이그레이션이 아니다. 반복 초기화용 schema.sql과 별개인 최초 생성 검토본이다. PgVectorStore의 자동 스키마 생성은 꺼 두었다.
- 기준 생성기: `node backend/clone-lab/tools/generate-catalog.cjs` (저장소 루트에서 실행).
- 기준 JSON: `backend/src/main/resources/clone-lab/catalog.json`. 생성기는 카탈로그 JSON과 요소 설명 문서를 만든다. SQL·DBML은 별도 관리해 구형 구조로 덮어쓰지 않는다.
- 모든 점수는 소수 둘째 자리까지 `0~100`, 미확인은 `NULL`이다. 50은 중간 성향이다. 내향/외향은 `relations.social_orientation` 하나로 표현한다. 합계 100인 요소 그룹은 없다.
- 프로필은 사용자당 한 행, 최초만 INSERT한다. 인정 가중치 `W=min(기존 가중치,C)`, 새 근거 강도 `w=1~3`일 때 서버가 `(old×W + observed×w)/(W+w)`를 소수 둘째 자리 HALF_UP으로 계산하고 가중치는 `min(W+w,C)`으로 저장한다. 최초 값은 새 관찰 값이다.
- 서버가 계산·범위·근거·버전을 검증한다. 결과가 틀리면 임의로 고치지 않고 저장하지 않는다. 프로필 충돌은 409이며 다시 저장하면 최신 버전으로 재계산한다.
- 근거가 없는 요소는 유지한다. 동일 수동 배치 또는 자동 처리 구간은 한 번만 반영한다. 이미 저장된 사용자 발언과 공백 정규화 후 동일한 발언은 새 근거에서 제외한다. 의미가 유사한 표현은 LLM에게 중복 근거로 과대 반영하지 않도록 지시하지만 완벽한 의미 중복 판정을 보장하지 않는다.
- 배치에는 현재 분석 결과와 해당 저장의 변경값·모델 사용량을 보존한다. 별도 점수 이력·스냅샷·작업 큐 테이블은 없다. 원문·새 기억의 INSERT는 프로필 UPDATE 정책과 구분한다.
- 표시되는 토큰·호출 시간은 텍스트 LLM 단계의 사용량이다. 임베딩 사용량과 실패한 외부 호출의 과금은 이 합계에 포함하지 않는다. 실패 후 재개로 외부 호출 자체가 반복될 수 있어 공급자 청구서와 동일한 비용 집계가 아니다.
- 기억은 FACT·EXPERIENCE·PREFERENCE·CONDITIONAL_PREFERENCE·VALUE·HABIT·GOAL·RELATIONSHIP의 8종류를 허용한다. 고정 문서 ID와 extraction_key로 같은 작업의 재시도 중복 INSERT를 방지한다. 서로 다른 작업의 의미 중복까지 자동 제거하는 것은 아니다. 상충 내용은 시간·근거와 함께 별도 보존하고 모델에 불확실성을 밝히도록 한다.

## 클론의 근거 순서

1. 선택한 저장 배치의 사용자 원문과 현재 정보이용자 대화 맥락 확인.
2. 부족하면 `searchLongTermMemory(query)` 호출. 서버가 소유자를 고정한 정확 벡터 검색과 키워드 검색을 실행.
3. 기억으로 판단할 수 없는 성향·선택 질문이면 `getAnalysisProfile(codes)` 호출. 관련 요소 최대 20개만 제공.
4. 근거 부족이면 정보 부족을 답한다. 프로필을 사용한 응답은 추정으로 표시한다.

반환되는 단계는 CONTEXT/MEMORY/PROFILE/INSUFFICIENT다. 근거 ID·도구 호출 순서를 검증하고, 사실·날짜·인물은 프로필로 만들어내지 않도록 지시한다. 정보이용자 발언과 AI 답변은 원문 근거 또는 소유자 기억으로 적재하지 않는다. 언어 모델의 의미적 충실성·모든 사실 날조를 형식 검증만으로 보장할 수는 없으므로 원문과 함께 평가해야 한다.

## 검증

별도 **테스트 DB**를 준비해 실행한다. 통합 테스트는 `LAB_TEST_DATABASE_URL`이 없으면 건너뛰며 실제 OpenAI 대신 가짜 응답을 사용한다. URL은 폐기 가능한 로컬 DB만 지정한다.

```powershell
# 로컬 Docker 컨테이너 이름이 everme-clone-lab인 경우
docker exec everme-clone-lab createdb -U everme_lab everme_lab_test
$env:LAB_TEST_DATABASE_URL="jdbc:postgresql://127.0.0.1:55439/everme_lab_test"
$env:SPRING_DATASOURCE_URL=$env:LAB_TEST_DATABASE_URL
$env:SPRING_DATASOURCE_USERNAME="everme_lab"
$env:SPRING_DATASOURCE_PASSWORD="everme_lab"
cd backend
.\gradlew.bat test
```

실험 서버를 실행한 상태에서 아래 검증을 저장소 루트에서 실행할 수 있다.

```powershell
node backend/clone-lab/tools/check-ui.cjs
# 실제 모델 호출 비용이 드는 작은 검증: 사용자1명·대화10쌍·분석/저장1회·답변1회
node backend/clone-lab/tools/smoke-live.cjs
```

UI 검증은 API 응답을 브라우저에서 가짜 데이터로 대체하여 비용 없이 버튼 단계·질문·새 대화·1440/390/320px을 확인한다. 화면과 실제 모델 검증 결과는 무시되는 `backend/build/`에 저장한다. 실제 모델로 1000쌍을 자동 실행하지는 않는다. 실행은 사용자가 테스트 화면에서 시작한다.

점수 오차는 대화에서 근거가 확보된 요소만 평가한다. 장기간 인물 변화에 대한 가중치 감쇠, 다중 서버 워커, 실사용자 인증·공유 권한, 대규모 ANN 최적화, 독립적인 의미·심리측정 타당성 검증은 이번 실험 범위 밖이다.

## 공식 연동 자료

- [Responses API](https://developers.openai.com/api/reference/resources/responses/methods/create)
- [Function calling](https://developers.openai.com/api/docs/guides/function-calling)
- [Embeddings](https://developers.openai.com/api/docs/guides/embeddings)

요소별 반영 정책의 고정 시퀀스 평가: `node backend/clone-lab/tools/evaluate-update-policies.cjs` (저장소 루트). 안정적 관찰·지속적 변화·일회성 반대 관찰을 일괄 20과 비교하며, 오차·변화 추종 횟수는 `backend/build/clone-lab-evaluation/update-policies.json`에 기록한다. 이는 합성 점수 수열 검증이며 실제 인물 복제 정확도 평가가 아니다.

새 자동 실행은 mode=single-session-pipeline-v1이며 batchId 하나를 유지한다. generatedPairs/analyzedPairs/savedPairs는 각 처리 완료 쌍 수, completedBatches는 저장 완료 구간 수다. generationPhase와 analysisPhase는 동시에 진행되는 작업 상태다. wallElapsedMs는 중단 시간을 뺀 실제 활성 시간, llmElapsedMs는 텍스트 모델 호출 시간 합계다. 병행 처리 때문에 호출 합계가 실제 시간보다 클 수 있다. 입력·출력·캐시 토큰과 retryCount도 표시한다. 기존 single-session-v1 및 이전 실행 상태는 원래 순서로 재개하되 병합 계산은 서버에서 수행한다.

추론 강도 기본값은 생성 low, 분석·요약 medium, 답변 미지정이다. LAB_GENERATION_REASONING_EFFORT / LAB_ANALYSIS_REASONING_EFFORT / LAB_SUMMARY_REASONING_EFFORT / LAB_ANSWER_REASONING_EFFORT로 변경할 수 있다. 빈 문자열이면 reasoning 파라미터를 생략한다. gpt-6.1-sol에는 none·minimal을 지정하지 않는다. 모델 기본값은 변경하지 않았다.

LLM 요청·응답은 두 ChatClient에 등록한 Spring AI `SimpleLoggerAdvisor`가 DEBUG 레벨로 출력한다. `clone-lab` 프로필에서 해당 로거만 DEBUG로 활성화한다. 요청에는 단계(`labStage`), 고정·가변 입력 글자 수와 프롬프트·모델 옵션, 응답에는 본문·도구 호출·사용량 메타데이터가 포함된다. `OpenAiLabClient`의 직접 SLF4J 로그는 제거했다. 출력이 필요 없으면 `logging.level.org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor=OFF`로 설정한다.

서비스 단계별 시간(`lab_timing`)과 HTTP 상태·재시도(`lab_http`, `lab_retry`)는 운영 진단 로그로 유지한다. 임베딩은 ChatClient Advisor의 적용 대상이 아니며 벡터 본문은 출력하지 않는다. 이 운영 로그에는 프롬프트·응답 본문을 남기지 않지만, SimpleLoggerAdvisor 로그에는 대화 원문과 분석 내용이 포함된다. API 인증 키는 Advisor 입력에 전달하지 않는다. 화면·실행 상태 응답의 시간·토큰·캐시·재시도 집계는 유지한다.

Clone Lab은 Spring AI 1.1.8을 사용한다. `LabAiConfiguration`에서 실험 전용 ChatModel·EmbeddingModel을 구성하고, 기존 이름을 유지한 `OpenAiLabClient`는 서비스와 Spring AI 사이의 파사드 역할만 수행한다. 생성·분석은 기본 `OpenAiChatModel`, 임베딩은 `OpenAiEmbeddingModel`, 클론 답변은 Responses 호환 `LabResponsesChatModel`을 사용한다. 현재 버전에는 기본 Responses ChatModel이 없어 어댑터를 두었으며, Spring Boot/Spring AI 버전은 올리지 않았다.

기억 검색과 프로필 조회는 `LabCloneTools`의 Spring AI `ToolCallback`으로 실행한다. 소유자 ID는 서버에서 콜백에 고정한다. 검색 → 프로필의 허용 순서, 근거 검증, 추론 항목 재전송은 `LabCloneService`의 명시적 루프에서 유지하며 모델의 자동 도구 실행에 맡기지 않는다. 채팅 이력은 기존 DB에 보존하고, 별도 ChatMemory·VectorStore 테이블을 추가하지 않는다.

HTTP는 `LabAiTransport`의 Spring `RestClient` 인터셉터로 통합했다. 기존 일시 오류 재시도와 중단 대기를 유지하고 Spring AI 내부 재시도는 1회로 설정해 중복 재시도를 막는다. 모델·추론 강도·키·기본 URL의 기존 `LAB_*` 환경변수를 그대로 사용한다. 네이티브 텍스트 호출의 고정 지침은 첫 system 메시지에 두며 가변 원문과 요약은 다음 user 메시지에 둔다. 프로필 계산·10쌍 파이프라인·REST 경로·DB 구조는 유지한다.

## 10쌍 구간 파이프라인과 비용 진단

- `sessions.analysis.segments`에 number/from/to/sourceIds/status와 analysis 초안, summary 캐시, changes, generationMetrics/summaryMetrics, 적용 정책·프로필 버전을 보존한다. 상태는 generated → analyzed → saved다. `run_state.generationReservation`은 생성 중인 고정 범위이며 재시작 후 동일 범위를 재시도한다. 생성 결과·구간 등록은 같은 트랜잭션이고, 프로필·기억·처리 완료 원문 ID·저장 진행 위치도 함께 커밋한다. 새 테이블·컬럼은 없다.
- `savedPairs`가 1000이 되기 전에는 배치 전체를 saved로 표시하지 않는다. 원문이 뒤에 추가되어도 고정된 구간 분석은 무효화하지 않는다. 관찰이 없는 구간도 완료로 기록해 정상 실행의 프로필 버전은 100회 증가한다. 첫 구간 저장 전후에 대화 생성이 계속 진행될 수 있다.
- 분석 카탈로그는 공통 영역 규칙과 반복 오판 예시를 한 번만 전달하고 코드·이름·5개 앵커·실제 예시·특수 경계를 유지한다. 실제 축소 비율은 테스트와 Advisor 요청의 `fixedCharacters`, `variableCharacters`로 측정한다. 원본 화면/내보내기 JSON은 유지한다. 고정 지침·카탈로그는 첫 system 메시지, 가변 원문·요약은 뒤 user 메시지에 둔다. 캐시 적중은 보장하지 않는다.
- 외부 호출의 일시적 429·5xx·연결/타임아웃 오류는 최대 4회 재시도한다(최초 포함 5회). Retry-After를 우선하고 없으면 1·2·4·8초 + 0~250ms 지연을 적용한다. 인증·잘못된 요청·잔액 부족·TLS 오류·응답 JSON/분석 검증 오류는 재시도하지 않는다. 재시도 대기는 중단 가능하고 DB 트랜잭션은 반복하지 않는다. 취소·연결 단절 시 공급자가 이미 처리한 요청이 다시 호출될 가능성이 있으므로 청구 수준의 exactly-once를 보장하지 않는다.
- 중단 시 생성 호출/분석 단계/저장 단계 중 이미 시작한 작업만 마친다. 미처리 생성 구간과 저장 전 초안은 남겨 재개한다. 한 작업 실패 시 다른 작업은 진행 중인 결과만 정리한 뒤 멈춘다. 요약은 재사용하지만 실패한 분석 호출 자체는 다시 시도한다.
- 재시작 시 활성 시간은 마지막 DB 체크포인트까지 합산하고 서버가 꺼져 있던 시간은 제외한다. 실패한 요청의 알려지지 않은 과금·토큰은 집계할 수 없으며, 화면의 토큰·호출 합계는 성공한 텍스트 모델 결과의 사용량이다. 임베딩과 모델 접근 점검은 별도다.
