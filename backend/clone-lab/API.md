# Clone Lab API

`clone-lab` 프로필에서만 등록된다. 기존 서비스 API와 분리된 실험 계약이다. UUID는 경로·본문에 사용한다. 성공 응답은 JSON이며 오류는 `{ "message": "..." }`이다. 입력 오류400, 미존재404, 상태·버전 충돌409, 공급자 제한429, 모델·검증 실패502, 키 미설정503이다.

| 메서드 | 경로 (`/api/lab` 기준) | 본문·응답 |
| --- | --- | --- |
| GET | /catalog | 7개 영역·140개 요소 명세 |
| GET | /config | 단계별 모델 ID·키 설정 여부. 키 자체는 반환하지 않음 |
| POST | /models/check | 본문 없음. 모델별 GET 접근 확인 |
| GET | /users | 테스트 사용자 ID·이름·버전·생성시각 |
| POST | /users | `{name,seed}` → ID·이름·생성 사용량. 인물 설정은 반환하지 않음 |
| GET | /users/{user}/profile | `{userId,name,version,elements}`. elements는 코드별 score/weight/sourceIds/reason |
| GET | /users/{user}/export | 현재 프로필·카탈로그·원문·배치 분석·기억·실행 상태·평가 |
| GET | /users/{user}/batches | 최신 순 배치 목록 |
| POST | /users/{user}/batches | `{topic}` → 정확히 10쌍 생성, 분석하지 않음 |
| GET | /users/{user}/batches/{batch} | 배치 상태·원문·현재 분석 초안·저장 변경값 |
| POST | /users/{user}/batches/{batch}/analysis | 분석 초안 저장. 프로필 변경 없음 |
| POST | /users/{user}/batches/{batch}/save | LLM 계산 검증 후 원자 저장. 이미 저장된 배치는 현재 프로필 반환 |
| POST | /users/{user}/runs | `{topic}` → 1000쌍 자동 실행 상태 |
| GET | /users/{user}/runs/current | 상태·현재 배치·완료 수·사용량·실패 이유 |
| POST | /users/{user}/runs/current/stop | 현재 배치 완료 후 paused |
| POST | /users/{user}/runs/current/resume | paused/failed 실행을 마지막 배치부터 재개 |
| POST | /users/{user}/sessions | `{batchId:uuid 또는 null}` → 새 sessionId·메시지 목록 |
| GET | /users/{user}/sessions/current | 현재 클론 대화와 버전 |
| POST | /users/{user}/questions | `{sessionId,question}` → answer/stage/inferred/evidenceIds/trace/metrics |

주제 최대1000자, 이름80자, 인물 배경3000자, 질문4000자다. 자동 실행 중 수동 생성·분석·저장은409다. 사용자마다 현재 정보이용자 대화 한 개를 유지하며 새 대화 시작은 기존 컨텍스트를 교체한다. 질문을 동시에 보내면 대화 버전 충돌을409로 반환한다.

분석 JSON은 `observations:[{code,score,strength,sourceIds,reason}]`, `memories:[{kind,content,sourceIds,occurredAt}]`다. 미확인 요소는 observations에서 생략한다. sourceIds는 제공된 사용자 원문 ID만 허용하며 기억 후보는 최대40개다.

저장 후 analysis.changes는 요소별 `previous/observed/stored/delta`를 제공한다. 최초 관찰의 delta는null이다. 현재 프로필을 덮어쓴 뒤에도 선택한 배치가 어떻게 반영됐는지 표시하기 위한 배치 결과이며 별도 이력 테이블은 두지 않는다.
