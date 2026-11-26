# 사용자 지식 조회 API 설계

2026-11-26 목표 계약. 약관 조회 Controller/Service/Dao 제거와 가입 DTO 변경을 반영했다. 분석 API의 추가 구현은 하지 않는다. 실행 코드와 EndpointContractTests는 8개 도메인/57개 경로의 `return null;` 골격이다. 목표는 **8개 도메인/58개 경로**이며 추가 경로는 summary 한 개뿐이다. 아래 200/400/401/404 등의 응답은 구현 목표이지 현재 빈 응답 코드의 동작을 주장하는 것이 아니다.

## GET /api/me/analysis

기존 경로를 재사용하고 `kind` 선택 Query Parameter를 추가한다. `kind=FACT`, `MEMORY`, `VALUE`, `PREFERENCE`, `BEHAVIOR`는 다섯 개 API가 아니라 하나의 경로에서 필터 값만 다른 요청이다. 미지정 시 다섯 분류 전체를 조회한다. 소문자·알 수 없는 값은 목표 계약에서 400이다. 현재 Java 메서드는 kind를 바인딩하지 않는다.

| 입력 | 타입·규칙 | 기본값 |
| --- | --- | --- |
| kind | 위 5개 대문자 enum, 선택 | 전체 |
| page | integer ≥ 0 | 0 |
| size | integer 1~100 | 20 |

owner_id는 인증 주체에서 결정하며 요청 필드로 받지 않는다. 활성 knowledge_items와 활성 knowledge_versions를 기준으로 항목별 최신 활성 버전 한 개와 해당 종류 상세를 조회한다. 정렬은 대표 버전 recorded_at DESC, item.id DESC다. 충돌로 복수 활성 버전이 있으면 최신 대표와 hasConflict를 반환하고 상세에서 모든 이력을 볼 수 있다. 최신 대표를 선택했다고 다른 버전을 삭제하거나 대변인 검색에서 배제하지 않는다.

```json
{
  "items": [
    {
      "informationId": "00000000-0000-0000-0000-000000000101",
      "kind": "PREFERENCE",
      "latestVersionId": "00000000-0000-0000-0000-000000000201",
      "versionNo": 2,
      "content": "조용한 장소에서 가족과 산책하는 것을 선호한다",
      "sensitivity": "L1",
      "recordedAt": "2026-11-26T01:00:00Z",
      "hasConflict": false
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

공개 API에서 ciphertext·복호화 키·벡터·lease token·외부 모델 원문을 반환하지 않는다. content는 소유자 인증·활성·유효 출처 검증 후 필요한 부분만 복호화한 표시용 값이다.

## GET /api/me/analysis/{informationId}

기존 경로와 UUID 타입을 유지한다. informationId는 knowledge_items.id이며 version ID가 아니다. 항목 공통 정보와 활성 버전 이력, 분류별 상세, 허용된 출처, 충돌 상태를 반환하는 목표 계약이다. 잘못된 UUID 400, 없거나 다른 소유자의 자원은 노출 방지를 위해 404를 사용한다.

```json
{
  "informationId": "00000000-0000-0000-0000-000000000101",
  "kind": "PREFERENCE",
  "occurrenceCount": 2,
  "changeCount": 1,
  "versions": [
    {
      "versionId": "00000000-0000-0000-0000-000000000201",
      "versionNo": 2,
      "content": "조용한 장소에서 가족과 산책하는 것을 선호한다",
      "sensitivity": "L1",
      "occurredFrom": null,
      "occurredUntil": null,
      "recordedAt": "2026-11-26T01:00:00Z",
      "supersededAt": null,
      "detail": {
        "targetType": "ACTIVITY",
        "target": "가족과의 산책",
        "polarity": "LIKE",
        "strength": null,
        "context": "조용한 장소"
      },
      "sources": [
        {
          "messageRevisionId": "00000000-0000-0000-0000-000000000301",
          "excerpt": "가족과 조용한 곳을 산책하는 게 좋아요"
        }
      ],
      "hasConflict": false
    }
  ]
}
```

다른 분류의 detail은 DB의 정확한 1:1 상세에서 매핑한다. 목록에서 detail을 항상 포함해 N+1 조회를 유발하지 않으며 상세 응답은 버전별 배치 JOIN을 사용한다. API에는 원자 속성의 복호화한 값만 포함하고 DB의 암호화 표현은 숨긴다.

## GET /api/me/analysis/summary

신규 설계 경로 1개. 같은 AnalysisController 책임이며 신규 HTTP 도메인을 만들지 않는다. 구체적인 `/summary` 매핑은 UUID 경로 `/{informationId}`와 분리한다. 현재 소스에는 별도 summary 메서드가 없으므로 코드 반영 전에는 호출 가능하다고 간주하지 않는다.

```json
{
  "totalItems": 10,
  "countsByKind": {
    "FACT": 2,
    "MEMORY": 2,
    "VALUE": 2,
    "PREFERENCE": 2,
    "BEHAVIOR": 2
  },
  "openConflictCount": 1,
  "analysisPendingCount": 0,
  "embeddingPendingCount": 1,
  "lastAnalyzedAt": "2026-11-26T01:00:00Z"
}
```

예시는 설명용 가상 값이다. totalItems/countsByKind는 버전 수가 아니라 같은 인증 owner의 활성 지식 항목 수다. 유효 원문 근거가 있는 활성 버전이 하나 이상 있는 항목만 집계한다. 건수가 0인 분류도 0으로 반환한다. 분석이 전혀 없으면 lastAnalyzedAt=null이다. pending은 queued/running만 포함하고 failed/completed/cancelled는 제외한다. 서로 다른 소유자의 작업 상태를 합산하지 않는다. 여러 집계 쿼리는 같은 읽기 전용 트랜잭션의 일관된 스냅샷에서 읽는다.

## 계층 호출·테이블

| 계약 | 호출 | 조회 테이블 |
| --- | --- | --- |
| 목록 | AnalysisController.analysis → AnalysisService.list → AnalysisDao.listByOwner | knowledge_items + 대표 knowledge_versions + 유효 knowledge_sources 확인 |
| 상세 | AnalysisController.analysisDetail → AnalysisService.get → KnowledgeDao.loadVersionWithDetail | items/versions + 정확한 5종 상세 중 하나 + sources + conflicts |
| 요약 | AnalysisController.summary → AnalysisService.summarize → AnalysisDao.summaryByKind | items/versions/sources + conflicts + analysis_jobs + embedding_jobs |

## 구현 단계의 계약 테스트 추가안

약관 조회 제거를 반영해 EndpointContractTests는 57개 경로와 null 반환을 검증한다. 후속 구현 시 summary 추가와 함께 기대 경로 수를 58로 변경하고 다음을 추가한다.

1. kind 5개 값 각각 바인딩, 미지정 전체 조회, 잘못된 값 400.
2. summary가 `analysisDetail(UUID)`에 걸리지 않고 summary 핸들러로 매핑.
3. 다른 owner 자원 차단과 summary/분류별 집계 격리.
4. 필터가 카테고리 공개 설정이나 민감도 직접 수정으로 변질되지 않는지 확인.
5. count 합계와 항목/버전 기준, 0개 분류, 마지막 분석 시각 null 처리.

동일한 목표 계약은 [openapi.json](openapi.json)에 정의한다. `x-implementation-status`로 기존 null 골격, kind 설계 변경, 신규 summary를 구분한다.
