# EVERME Clone Lab

별도 실험 프로필에서 대화 생성 → 근거 분석 → 프로필 UPDATE → 기억 검색 → 클론 답변을 검증한다. 기존 `/api/me`, owner/viewer 엔드포인트와 기존 5종 지식 설계는 수정하지 않는다.

## 실행

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
3. **분석**을 누르면 사용자 발언만으로 관찰된 요소와 기억 후보를 표시한다. 이 시점에도 프로필은 바뀌지 않는다.
4. **저장**을 누르면 LLM 병합 계산 → 서버 검증 → 7개 프로필 UPDATE·기억 INSERT·배치 완료를 한 트랜잭션으로 처리한다.
5. **1000개 테스트**는 10쌍씩 100회 실행한다. 현재 배치를 끝낸 후 중단하며 실패·서버 재시작 후 같은 배치부터 재개한다. paused/failed 실행은 재개하고, 별도 새 실험은 새 사용자를 만든다.
6. 정보이용자 화면에서 클론과 저장된 컨텍스트 배치를 선택한다. 배치 없이 장기 기억부터 찾을 수도 있다. 클론 변경 시 사용자별 대화 컨텍스트를 분리하며 새 대화는 이전 컨텍스트를 교체한다.
7. 결과 JSON에는 현재 프로필·원문·분석·기억·자동 실행 상태와 관찰된 요소의 합성 정답 대비 오차가 들어 있다. 원본 생성 설정은 일반 프로필·답변 API에 반환하지 않는다.

## 요소·수치·저장 규칙

- [ELEMENTS.md](ELEMENTS.md): 7개 영역 × 20개 요소의 코드·수치 기준·근거 예·오판 예·경계.
- [clone-lab.dbml](clone-lab.dbml): 11개 테이블. 운영의 DBML과 독립적이다.
- 기준 생성기: `node backend/clone-lab/tools/generate-catalog.cjs` (저장소 루트에서 실행).
- 기준 JSON: `backend/src/main/resources/clone-lab/catalog.json`. 생성기는 같은 기준으로 SQL·DBML·요소 문서를 만든다.
- 모든 점수는 소수 둘째 자리까지 `0~100`, 미확인은 `NULL`이다. 50은 중간 성향이다. 내향/외향은 `relations.social_orientation` 하나로 표현한다. 합계 100인 요소 그룹은 없다.
- 프로필은 사용자당 한 행, 최초만 INSERT한다. 기존 가중치 `W`, 새 근거 강도 `w=1~3`일 때 LLM이 `(old×W + observed×w)/(W+w)`를 소수 둘째 자리 HALF_UP으로 계산한다. 최초 값은 새 관찰 값이다.
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
