-- 사용자 요청에 따른 clone_lab 전체 재생성. 이 스키마의 기존 데이터는 모두 삭제된다.
-- 다른 스키마는 삭제하지 않는다. 오류 발생 시 전체 트랜잭션을 롤백한다.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout = '10s';
SELECT pg_advisory_xact_lock(hashtext('clone-lab-schema-recreate'));
DO $$
BEGIN
  IF current_database() <> 'everme_lab' THEN
    RAISE EXCEPTION 'This script is scoped to everme_lab';
  END IF;
END $$;
DROP SCHEMA IF EXISTS clone_lab CASCADE;
\ir ../clone-lab-design.sql
DO $$
BEGIN
  IF (SELECT count(*) FROM pg_tables WHERE schemaname='clone_lab') <> 11 THEN
    RAISE EXCEPTION 'Expected exactly 11 tables';
  END IF;
  IF to_regclass('clone_lab.batches') IS NOT NULL
      OR to_regclass('clone_lab.turns') IS NOT NULL
      OR to_regclass('clone_lab.memories') IS NOT NULL THEN
    RAISE EXCEPTION 'Obsolete tables found';
  END IF;
END $$;
COMMIT;
