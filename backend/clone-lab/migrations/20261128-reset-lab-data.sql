-- 사용자 승인에 따른 실험 데이터 초기화. 테이블 구조·원래 백업 파일은 유지한다.
-- 지정된 두 실험 스키마만 대상으로 하며 CASCADE는 사용하지 않는다.
\set ON_ERROR_STOP on
BEGIN;
DO $$
DECLARE targets text;
BEGIN
  IF current_database()<>'everme_lab' THEN
    RAISE EXCEPTION 'This reset script is scoped to everme_lab';
  END IF;
  SELECT string_agg(format('%I.%I',schemaname,tablename),',') INTO targets
  FROM pg_tables WHERE schemaname IN ('clone_lab','clone_lab_legacy_20261127');
  IF targets IS NULL THEN RAISE EXCEPTION 'No lab tables found'; END IF;
  EXECUTE 'TRUNCATE TABLE '||targets||' RESTART IDENTITY';
END $$;
COMMIT;
