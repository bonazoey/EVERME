# EVERME Clone Lab

별도 실험 프로필에서 대화 생성 → 근거 분석 → 프로필 UPDATE → 기억 검색 → 클론 답변을 검증한다. 기존 `/api/me`, owner/viewer 엔드포인트와 기존 5종 지식 설계는 수정하지 않는다.

## 실행

저장소 루트(`EVERME`) 또는 `backend` 폴더에서 아래 명령 하나로 실행한다.

```powershell
.\clone-lab.cmd
```

Docker Desktop, Java 21, `OPENAI_API_KEY`가 준비되어 있어야 한다. DB 컨테이너가 없으면 생성하고, 중지되어 있으면 시작하며, DB 준비 후 스키마 초기화와 서버 실행을 진행한다. 이미 실험 서버가 실행 중이면 주소만 안내하고 중복 실행하지 않는다. 종료는 **Ctrl+C**이며 DB와 데이터는 유지한다.

- 사전 확인만: `.\clone-lab.cmd -CheckOnly`
- 서버 포트 변경: `.\clone-lab.cmd -Port 8088`
- 실행 완료 기준은 `Started BackendApplication` 로그다. 서버 실행 중 Gradle 진행률이 80%에 머무르는 것은 정상이다.

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

모델은 자동 대체하지 않는다. `POST /api/lab/models/check`로 계정의 모델 접근 권한을 확인할 수 있다. 텍스트 생성은 Responses API의 strict JSON Schema, 클론 검색은 function_call/function_call_output으로 구현했다. 임베딩은 1536차원을 명시한다. 모델을 바꾸어 벡터 공간이 바뀌면 기존 기억을 그대로 혼용하지 말고 새 실험 DB에서 시작한다.

## 사용 순서

1. 이름·배경으로 고정 가상 인물을 만든다. 정답 점수·인물 설정은 서버에만 보존한다.
2. 주제를 넣고 **10개 대화 생성**을 누른다. 원문만 저장되고 분석은 하지 않는다.
   - **추가 대화 10개**는 선택한 대화의 주제로 10쌍을 이어 붙인다(10→20→30쌍). 추가 후에는 다시 분석해야 한다. 이전에 저장한 점수는 유지하고 이미 반영한 발언은 중복 반영하지 않는다.
   - 대화 내역 아래 입력창에서 직접 메시지를 보내면 AI가 공감·경청하며 답한다. Enter로 전송, Shift+Enter로 줄바꿈한다. 선택한 대화에 1쌍씩 추가되며, 대화가 없으면 새로 시작한다. 직접 쓴 발언도 이후 분석 대상이다. 대화 전송 자체는 수치 분석·프로필 갱신을 실행하지 않는다.
3. **분석**을 누르면 사용자 발언만으로 관찰된 요소와 기억 후보를 표시한다. 이 시점에도 프로필은 바뀌지 않는다.
   - `DB 저장값`은 기존 누적값, `이번 분석 (저장 전)`은 이번 배치의 분석값이다. 분석 직후 막대는 이번 분석값을 미리 보여주며, 저장 후에는 DB 누적값으로 바뀐다. 미관찰 요소는 기존 DB 값 또는 미확인을 표시한다.
4. **저장**을 누르면 LLM 병합 계산 → 서버 검증 → 7개 프로필 UPDATE·기억 INSERT·배치 완료를 한 트랜잭션으로 처리한다.
5. **1000개 테스트**는 같은 주제의 새 대화 배치 하나에 10쌍씩 100회 이어서 생성한다. 1000쌍 생성이 끝난 후 전체를 분석하고 프로필을 한 번 저장한다. 내부 분석은 10쌍씩 수행하고 이전 요약을 최대 5개 붙여 결과를 합친다. 화면은 생성·전체 분석·최종 저장 단계를 구분한다. 중단 요청은 현재 생성 호출 또는 전체 분석·저장 단계가 끝난 뒤 반영한다. 실패·서버 재시작 후 같은 대화에서 재개하며, 분석 도중 실패한 경우 전체 분석을 다시 시도한다. paused/failed 실행은 재개하고, 별도 새 실험은 새 사용자를 만든다.
6. 정보이용자 화면에서 클론과 저장된 컨텍스트 배치를 선택한다. 배치 없이 장기 기억부터 찾을 수도 있다. 클론 변경 시 사용자별 대화 컨텍스트를 분리하며 새 대화는 이전 컨텍스트를 교체한다.
7. 결과 JSON에는 현재 프로필·원문·분석·기억·자동 실행 상태와 관찰된 요소의 합성 정답 대비 오차가 들어 있다. 원본 생성 설정은 일반 프로필·답변 API에 반환하지 않는다.

## 요소·수치·저장 규칙

대화 10쌍 생성에는 주제·최근 4쌍만 전달한다. 인물 배경·사실·140개 정답 점수는 비교 전용이며 대화 생성·분석·클론 답변에 전달하지 않는다. AI는 공감·경청·맞장구로 반응하며 사용자가 대화를 주도한다. 분석에는 140개 요소 명세를 사용한다.

수동 대화의 주제는 선택 사항이다. 빈 주제로 시작하면 9쌍까지 주제 미정으로 표시하고, 10번째 쌍이 저장될 때 최초 10쌍을 분석 모델로 읽어 주제를 한 번 확정한다. 10쌍 생성 버튼도 빈 주제를 허용한다. 확정한 주제는 이후 대화에 적용하고 직접 지정한 주제는 유지한다. 주제 추출은 성향 분석·프로필 저장과 별개다. 추출 오류 시 해당 요청의 대화와 주제는 함께 저장되지 않아 재시도할 수 있다. 1000쌍 자동 실행은 시작 주제가 필요하다.

분석은 미반영 사용자 발언을 최대 10쌍씩 처리한다. 각 분석에 앞선 대화는 시간순 10쌍 단위로 요약하고 최근 요약 최대 5개를 문맥으로 전달한다. 요약 하나는 최대 1000자이며 분석 모델을 사용한다. 이전 33쌍 뒤의 새 10쌍을 분석하면 완성된 30쌍의 요약 3개를 첨부한다. 미완성 요약 구간은 포함하지 않는다. 분석 대상이 43쌍이면 10+10+10+10+3으로 처리해 발언을 누락하지 않는다. 요약은 문맥 전용이고 점수·기억의 새 근거가 될 수 없다. 동일 요청 안에서는 요약을 재사용하며 호출 비용과 시간을 분석 metrics에 합산한다. 창별 요약은 analysis.windows에서 확인할 수 있다.

여러 분석 창의 같은 요소는 근거 강도 가중 평균으로 합치고 배치 근거 강도는 최대 3으로 제한한다. DB 누적은 기존 분석→저장 절차를 유지한다. 인물 설정과 무관하게 생성한 대화의 점수 오차는 해당 인물의 재현 정확도로 해석할 수 없다.

프로필 저장은 정책 `element-cap-v1`의 요소별 상한 `C=10·20·40`을 적용한다. 빠름 38개·중간 48개·느림 54개이며, 초기 실험 가설이다. 기존 점수가 NULL이면 기존 가중치를 0으로, 아니면 `min(기존 가중치, C)`로 계산한다. 새 점수는 `(기존 점수 × 인정 가중치 + 관찰 점수 × 근거 강도) / (인정 가중치 + 근거 강도)`이며 소수 둘째 자리 HALF_UP 반올림한다. 저장할 가중치는 `min(인정 가중치 + 근거 강도, C)`이다. 분모에는 상한 적용 전 합을 사용한다. 기존 40·가중치 200에 관찰 60·강도 2를 반영하면 상한 10은 43.33·가중치 10, 상한 20은 41.82·가중치 20, 상한 40은 40.95·가중치 40이다. LLM 계산을 서버가 검증하며 관찰이 없는 요소는 유지한다. 기존 데이터는 일괄 수정하지 않고 해당 요소를 다음 저장할 때 상한을 적용한다. 새 상한이 40이더라도 기존 가중치 20을 부풀리지 않는다. 요소별 배정과 이유는 ELEMENTS.md에 명시한다. 저장 결과의 updatePolicyVersion·maxWeights로 적용 정책을 확인할 수 있다.

- [ELEMENTS.md](ELEMENTS.md): 7개 영역 × 20개 요소의 코드·수치 기준·근거 예·오판 예·경계.
- [clone-lab.dbml](clone-lab.dbml): 11개 테이블. 운영의 DBML과 독립적이다.
- 기준 생성기: `node backend/clone-lab/tools/generate-catalog.cjs` (저장소 루트에서 실행).
- 기준 JSON: `backend/src/main/resources/clone-lab/catalog.json`. 생성기는 같은 기준으로 SQL·DBML·요소 문서를 만든다.
- 모든 점수는 소수 둘째 자리까지 `0~100`, 미확인은 `NULL`이다. 50은 중간 성향이다. 내향/외향은 `relations.social_orientation` 하나로 표현한다. 합계 100인 요소 그룹은 없다.
- 프로필은 사용자당 한 행, 최초만 INSERT한다. 인정 가중치 `W=min(기존 가중치,C)`, 새 근거 강도 `w=1~3`일 때 LLM이 `(old×W + observed×w)/(W+w)`를 소수 둘째 자리 HALF_UP으로 계산하고 가중치는 `min(W+w,C)`으로 저장한다. 최초 값은 새 관찰 값이다.
- 서버가 계산·범위·근거·버전을 검증한다. 결과가 틀리면 임의로 고치지 않고 저장하지 않는다. 프로필 충돌은 409이며 다시 저장하면 최신 버전으로 재계산한다.
- 근거가 없는 요소는 유지한다. 동일 배치는 한 번만 반영한다. 이미 저장된 사용자 발언과 공백 정규화 후 동일한 발언은 새 근거에서 제외한다. 의미가 유사한 표현은 LLM에게 중복 근거로 과대 반영하지 않도록 지시하지만 완벽한 의미 중복 판정을 보장하지 않는다.
- 배치에는 현재 분석 결과와 해당 저장의 변경값·모델 사용량을 보존한다. 별도 점수 이력·스냅샷·작업 큐 테이블은 없다. 원문·새 기억의 INSERT는 프로필 UPDATE 정책과 구분한다.
- 표시되는 토큰·호출 시간은 텍스트 LLM 단계의 사용량이다. 임베딩 사용량과 실패한 외부 호출의 과금은 이 합계에 포함하지 않는다. 실패 후 재개로 외부 호출 자체가 반복될 수 있어 공급자 청구서와 동일한 비용 집계가 아니다.
- 기억은 사실·경험·조건부 선호의 내용과 시점·원문 근거를 저장한다. 내용이 같은 기억은 중복 INSERT하지 않는다. 상충 내용은 시간·근거와 함께 별도 보존하고 모델에 불확실성을 밝히도록 한다.

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

새 자동 실행은 `mode=single-session-v1`이며 `batchId`를 완료 후에도 유지한다. `generatedPairs`는 생성된 쌍 수, `phase`는 generating/analyzing/saving/completed다. 기존 `completedBatches`는 호환을 위해 완료된 10쌍 생성 묶음 수(최대 100)를 나타낸다. 이전 버전에서 중단된 실행은 기존 다중 배치 방식으로 재개한다. 전체 분석의 기억은 창마다 최대 40개이고 최종 배치에서는 창 개수에 비례해 허용하며, 임베딩은 최대 256개씩 나누어 호출한 뒤 한 트랜잭션으로 저장한다.

대화 생성 단계(`generation`)의 Responses API 요청은 `reasoning.effort=low`로 지정한다. 같은 단계를 사용하는 가상 인물 생성에도 적용된다. 분석·요약·병합·답변은 추론 강도를 별도로 지정하지 않는다. 실제 지연 개선 폭은 모델 호출을 통해 비교해야 한다.

지연 진단용 SLF4J INFO 로그는 실행 콘솔에서 `lab_timing`, `lab_llm`, `lab_http`, `lab_usage`로 확인한다. 같은 처리 안의 중첩 단계는 동일한 `requestId`를 사용한다. `elapsedMs`는 직전 단계 이후 시간, `totalMs`는 해당 operation 전체 경과 시간이다. 중첩 operation의 시간은 서로 포함되므로 단순 합산하지 않는다. 성공·실패 종료 모두 기록한다.

대화 생성 로그는 `load_db_and_prepare_input` → `generate_llm` → `validate_turns` → `store_turns_including_topic` → `read_response_from_db`로 나뉜다. 저장 내부는 `infer_topic_if_required`와 `db_transaction`으로 구분한다. LLM 호출 내부에는 요청 직렬화, `api_wait`, 응답 JSON 파싱, 구조화 JSON 해석, 모델·추론 설정·입출력/추론 토큰 수를 기록한다. `api_wait`는 외부 API 응답 전체 대기 시간으로 네트워크·공급자 대기·모델 생성 시간을 개별 분리한 값이 아니다. UI 렌더링 시간은 포함하지 않는다. 분석·프로필 저장에도 입력 준비, 모델 호출, 검증, 임베딩, DB 저장 시간을 기록한다. API 키·주제·대화 원문·프롬프트·응답 본문은 이 진단 로그에 기록하지 않는다.

프로젝트에는 Spring AI 의존성이 있으나 현재 Clone Lab의 모델 통신은 `OpenAiLabClient`의 Java `HttpClient`가 OpenAI Responses/Embeddings API를 직접 호출한다. Clone Lab의 호출 경로에 Spring AI ChatClient/ChatModel은 사용하지 않는다.
