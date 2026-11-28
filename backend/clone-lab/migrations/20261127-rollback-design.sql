-- 원복 시에만 수동 실행한다. 애플리케이션 변경 요청을 중지한 뒤 실행한다.
-- 새 설계에 쌓인 데이터도 삭제하지 않고 별도 스키마에 보존한다.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout = '10s';
SELECT pg_advisory_xact_lock(hashtext('clone-lab-design-20261127'));
DO $$
DECLARE r record;
BEGIN
  IF to_regnamespace('clone_lab_design_20261127') IS NOT NULL THEN
    RAISE EXCEPTION 'Design archive already exists; inspect before rollback';
  END IF;
  IF to_regnamespace('clone_lab_legacy_20261127') IS NULL THEN
    RAISE EXCEPTION 'Legacy schema is missing';
  END IF;
  FOR r IN SELECT schemaname,tablename FROM pg_tables
    WHERE schemaname IN ('clone_lab','clone_lab_legacy_20261127') ORDER BY schemaname,tablename LOOP
    EXECUTE format('LOCK TABLE %I.%I IN ACCESS EXCLUSIVE MODE',r.schemaname,r.tablename);
  END LOOP;
END $$;
ALTER SCHEMA clone_lab RENAME TO clone_lab_design_20261127;
ALTER SCHEMA clone_lab_legacy_20261127 RENAME TO clone_lab;
COMMIT;
