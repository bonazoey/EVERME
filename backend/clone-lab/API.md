# Clone Lab API

`clone-lab` 프로필에서만 등록된다. 기존 서비스 API와 분리된 실험 계약이다. UUID는 경로·본문에 사용한다. 성공 응답은 JSON이며 오류는 `{ "message": "..." }`이다. 입력 오류400, 미존재404, 상태·버전 충돌409, 공급자 제한429, 모델·검증 실패502, 키 미설정503이다.

| 메서드 | 경로 (`/api/lab` 기준) | 본문·응답 |
| --- | --- | --- |
| GET | /catalog | 7개 영역·140개 요소 명세 |
| GET | /config | 단계별 모델 ID·추론 강도·`framework: "spring-ai"`·키 설정 여부. 키 자체는 반환하지 않음 |
| POST | /models/check | 본문 없음. 모델별 GET 접근 확인 |
| GET | /users | 테스트 사용자 ID·이름·버전·생성시각 |
| POST | /users | `{name,seed}` → ID·이름·생성 사용량. 인물 설정은 반환하지 않음 |
| GET | /users/{user}/profile | `{userId,name,version,elements}`. elements는 코드별 score/weight/sourceIds/reason |
| GET | /users/{user}/export | 현재 프로필·카탈로그·원문·배치 분석·기억·실행 상태·평가 |
| GET | /users/{user}/batches | 최신 순 배치 목록 |
| POST | /users/{user}/batches | `{topic}` → 정확히 10쌍 생성, 분석하지 않음 |
| GET | /users/{user}/batches/{batch} | 배치 상태·원문·현재 분석 초안·저장 변경값 |
| POST | /users/{user}/batches/{batch}/turns | 본문 없음. 해당 배치의 주제로 10쌍 추가. 기존 대화 유지, 분석 초안 초기화. 이미 저장된 발언은 재반영하지 않음 |
| POST | /users/{user}/messages | `{batchId:uuid 또는 null,topic:string 또는 null,content}` → 직접 쓴 발언과 AI 답변 1쌍 추가. batchId가 없으면 새 대화 생성. 본문 최대8000자 |
| POST | /users/{user}/batches/{batch}/analysis | 분석 초안 저장. 프로필 변경 없음 |
| POST | /users/{user}/batches/{batch}/save | LLM 계산 검증 후 원자 저장. 이미 저장된 배치는 현재 프로필 반환 |
| POST | /users/{user}/runs | `{topic}` → 새 대화 하나에 1000쌍을 이어 생성하며 10쌍마다 분석·저장 |
| GET | /users/{user}/runs/current | 상태·현재 배치·완료 수·사용량·실패 이유 |
| POST | /users/{user}/runs/current/stop | 진행 중인 생성·분석·저장 작업만 완료한 뒤 paused |
| POST | /users/{user}/runs/current/resume | paused/failed 실행을 마지막 배치부터 재개 |
| POST | /users/{user}/sessions | `{batchId:uuid 또는 null}` → 새 sessionId·메시지 목록 |
| GET | /users/{user}/sessions/current | 현재 클론 대화와 버전 |
| POST | /users/{user}/questions | `{sessionId,question}` → answer/stage/inferred/evidenceIds/trace/metrics |

주제 최대1000자, 이름80자, 인물 배경3000자, 질문4000자다. 자동 실행 중 수동 생성·분석·저장은409다. 사용자마다 현재 정보이용자 대화 한 개를 유지하며 새 대화 시작은 기존 컨텍스트를 교체한다. 질문을 동시에 보내면 대화 버전 충돌을409로 반환한다.

분석 JSON은 `observations:[{code,score,strength,sourceIds,reason}]`, `memories:[{kind,content,sourceIds,occurredAt}]`다. 미확인 요소는 observations에서 생략한다. sourceIds는 제공된 사용자 원문 ID만 허용하며 기억 후보는 최대40개다.

저장 후 analysis.changes는 요소별 `previous/observed/stored/delta`를 제공한다. 최초 관찰의 delta는null이다. 현재 프로필을 덮어쓴 뒤에도 선택한 배치가 어떻게 반영됐는지 표시하기 위한 배치 결과이며 별도 이력 테이블은 두지 않는다.
`GET /api/lab/catalog`은 `updatePolicyVersion: "element-cap-v1"`과 요소별 `updatePolicy: {maxWeight,rationale}`를 제공한다. maxWeight는 10·20·40 중 하나다. 저장 시 서버 정책만 적용하며 클라이언트가 상한을 지정할 수 없다. 저장 완료 배치의 analysis에는 적용한 `updatePolicyVersion`과 관찰 요소별 `maxWeights`가 기록된다. 정책은 실험용 반영 속도이며 신뢰도 확률을 뜻하지 않는다.

새 자동 실행은 `mode=single-session-pipeline-v1`이다. 상태 응답은 batchId(단일 대화), generatedPairs/analyzedPairs/savedPairs, generationPhase(generating/waiting/completed), analysisPhase(analyzing/ready_to_save/saving/waiting/completed), wallElapsedMs(활성 실제 시간), llmElapsedMs(성공한 텍스트 모델 호출 시간 합계), inputTokens/outputTokens/cachedTokens, retryCount를 포함한다. completedBatches는 저장 완료 10쌍 구간 수이며 completedPairs는 savedPairs와 같다. 최종 진행률은 savedPairs/1000이다. 생성 예약을 포함한 미저장 범위는 최대 20쌍이다.

배치 조회의 analysis.segments에는 number/from/to/sourceIds/status(generated/analyzed/saved), analysis 초안, 필요 시 summary·summaryMetrics, 저장 후 changes·maxWeights·updatePolicyVersion·profileVersion이 포함된다. 분석 대상 ID는 불변이다. 배치 전체 status=saved는 1000쌍 전부 저장된 경우에만 설정한다. 미완료 파이프라인의 running/stopping/paused/failed 상태에서는 수동 생성·직접 대화·분석·저장을 409로 차단하고 조회·중단·재개는 유지한다. 이전 모드 실행은 기존 처리 순서로 재개한다.

설정 조회 `/config`은 단계별 모델과 reasoningEfforts를 제공하며 merge 모델은 제거한다. 수동·자동 저장은 서버 산술로 병합한다. 과거 분석의 mergeMetrics는 기존 데이터에 남아 있을 수 있지만 새 저장은 생성하지 않는다. 구조화 호출 metrics에는 cachedTokens·retries도 포함된다.
