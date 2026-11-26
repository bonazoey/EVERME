# EVERME 설계 자료

현재 구현은 기존 8개 도메인/57개 HTTP 경로의 null 골격이다. 이 디렉터리는 실제 기능을 구현하지 않고 5대 지식 분류와 장기 기억 구축을 구체화한 설계 기준이다.

- [BACKEND_ARCHITECTURE.md](BACKEND_ARCHITECTURE.md): 시스템·클래스·TX·5종 분석·병합·임베딩·RAG·그룹 권한.
- [everme.dbml](everme.dbml): 핵심 27 + 그룹 저장 보완 1 = 전체 28 테이블.
- [constraints-design.sql](constraints-design.sql): DBML로 표현하지 못하는 지연 상세 존재·불변성·출처 소유자 제약 설계. 운영 실행용 migration 아님.
- [ANALYSIS_API.md](ANALYSIS_API.md), [openapi.json](openapi.json): kind 필터와 summary를 포함한 58개 목표 계약. 현재 코드와의 차이 명시.
- [CHANGE_REPORT.md](CHANGE_REPORT.md): 파일·테이블·컬럼·관계·클래스·API·충돌·전환 과제.
- [validation-results.json](validation-results.json): 격리 관계형 제약 검증 결과와 한계.

기존 아키텍처 HTML은 output/architecture/everme-data-architecture.html이며 색상·범례·테이블 선택 방식을 유지한다. docs/database와 output/architecture의 DBML 사본은 동일하다. 이 두 디렉터리는 기존 gitignore 대상이어서 추적 가능한 기준본을 여기에 둔다.

재생성 및 관계형 설계 검증:

```powershell
npm ci --prefix backend/design --ignore-scripts
node backend/design/update-design.cjs
node backend/design/sync-artifacts.cjs
node backend/design/validate-design.cjs
node output/architecture/check-architecture.cjs
```

Node 22 이상과 로컬 Chrome이 화면 검사에 필요하다. 도구 의존성은 설계 검증 전용이며 Spring 애플리케이션 런타임 의존성을 바꾸지 않는다. SQL 검증은 PGlite 격리 엔진과 vector 대체 타입을 사용하므로 실제 pgvector 검색·다중 DB 연결 경합 테스트를 대신하지 않는다.

update-design은 기존 DBML을 바탕으로 이번 변경 대상 블록만 동기화한다. sync-artifacts는 Java를 읽어 실제 57개 인벤토리를 확인하고 목표 58개와 모델을 생성한다. 어느 도구도 애플리케이션 Java를 수정하거나 운영 DB에 접속하지 않는다.
