-- 실행: psql -X -v ON_ERROR_STOP=1 -f 20261127-apply-design.sql
-- 사전 조건: 애플리케이션 변경 요청 중지, 전체 DB pg_dump 백업 완료.
-- 기존 원문에는 임베딩이 없으므로 context에 가짜 벡터를 넣지 않는다.
-- 모든 기존 테이블은 clone_lab_legacy_20261127에 보관한다.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout = '10s';
SELECT pg_advisory_xact_lock(hashtext('clone-lab-design-20261127'));
DO $$
DECLARE r record;
BEGIN
  IF to_regnamespace('clone_lab_legacy_20261127') IS NOT NULL THEN
    RAISE EXCEPTION 'Legacy archive already exists; migration must not run twice';
  END IF;
  FOR r IN SELECT tablename FROM pg_tables WHERE schemaname='clone_lab' ORDER BY tablename LOOP
    EXECUTE format('LOCK TABLE clone_lab.%I IN ACCESS EXCLUSIVE MODE', r.tablename);
  END LOOP;
  IF EXISTS (SELECT 1 FROM clone_lab.lab_users WHERE run_state->>'status' IN ('running','stopping')) THEN
    RAISE EXCEPTION 'Pause active runs before migration';
  END IF;
  IF EXISTS (
    SELECT 1 FROM clone_lab.memories m CROSS JOIN LATERAL jsonb_array_elements_text(m.source_ids) s(id)
    LEFT JOIN clone_lab.turns t ON t.id::text=s.id WHERE t.id IS NULL
  ) THEN
    RAISE EXCEPTION 'Memory sources missing; cannot preserve source excerpts';
  END IF;
END $$;
ALTER SCHEMA clone_lab RENAME TO clone_lab_legacy_20261127;
\ir ../clone-lab-design.sql

INSERT INTO clone_lab.lab_users(id,name,persona,profile_version,run_state,created_at)
SELECT id,name,persona,profile_version,run_state,created_at FROM clone_lab_legacy_20261127.lab_users;

INSERT INTO clone_lab.sessions(session_id,clone_owner_id,actor_user_id,conversation_type,topic,analysis,created_at)
SELECT id,user_id,user_id,'owner',topic,
  COALESCE(analysis,'{}'::jsonb) || jsonb_build_object('legacyMigration',jsonb_build_object(
    'batchStatus',status,'batchSequenceNo',sequence_no,'contextBackfill','pending_embedding',
    'sourceSchema','clone_lab_legacy_20261127')),created_at
FROM clone_lab_legacy_20261127.batches;

DO $$
DECLARE name text;
BEGIN
  FOREACH name IN ARRAY ARRAY['profile_emotion','profile_values','profile_interests','profile_decision','profile_habits','profile_relations','profile_expression'] LOOP
    EXECUTE format('INSERT INTO clone_lab.%I SELECT * FROM clone_lab_legacy_20261127.%I', name,name);
  END LOOP;
END $$;

INSERT INTO clone_lab.vector_store(id,content,embedding,metadata)
SELECT m.id,m.content,m.embedding,jsonb_build_object(
  'clone_owner_id',m.user_id::text,'actor_user_id',m.user_id::text,
  'session_id',m.batch_id::text,'conversation_type','owner','kind',m.kind,
  'source_ids',m.source_ids,'source_excerpts',(
    SELECT jsonb_agg(jsonb_build_object('source_id',t.id::text,'user_text',t.user_text) ORDER BY s.ordinality)
    FROM jsonb_array_elements_text(m.source_ids) WITH ORDINALITY s(id,ordinality)
    JOIN clone_lab_legacy_20261127.turns t ON t.id::text=s.id),
  'occurred_at',m.occurred_at,'extraction_key','legacy-memory:'||m.id::text,
  'legacy_fingerprint',m.fingerprint,'legacy_created_at_unknown',true)
FROM clone_lab_legacy_20261127.memories m;

-- 7개 프로필의 점수·가중치·근거 및 원래 profile_version이 그대로인지 검사한다.
DO $$
DECLARE name text; mismatch boolean;
BEGIN
  IF (SELECT count(*) FROM clone_lab.lab_users) <> (SELECT count(*) FROM clone_lab_legacy_20261127.lab_users)
    OR (SELECT count(*) FROM clone_lab.sessions) <> (SELECT count(*) FROM clone_lab_legacy_20261127.batches)
    OR (SELECT count(*) FROM clone_lab.vector_store) <> (SELECT count(*) FROM clone_lab_legacy_20261127.memories) THEN
    RAISE EXCEPTION 'Row count mismatch';
  END IF;
  IF EXISTS (SELECT 1 FROM clone_lab.lab_users n JOIN clone_lab_legacy_20261127.lab_users o USING(id)
    WHERE n.profile_version<>o.profile_version OR n.run_state<>o.run_state) THEN
    RAISE EXCEPTION 'User checkpoint mismatch';
  END IF;
  FOREACH name IN ARRAY ARRAY['profile_emotion','profile_values','profile_interests','profile_decision','profile_habits','profile_relations','profile_expression'] LOOP
    EXECUTE format('SELECT EXISTS((SELECT * FROM clone_lab.%I EXCEPT SELECT * FROM clone_lab_legacy_20261127.%I) UNION ALL (SELECT * FROM clone_lab_legacy_20261127.%I EXCEPT SELECT * FROM clone_lab.%I))',name,name,name,name) INTO mismatch;
    IF mismatch THEN RAISE EXCEPTION 'Profile mismatch: %',name; END IF;
  END LOOP;
END $$;
COMMIT;

SELECT 'users' item,count(*) FROM clone_lab.lab_users
UNION ALL SELECT 'sessions',count(*) FROM clone_lab.sessions
UNION ALL SELECT 'vector_store',count(*) FROM clone_lab.vector_store
UNION ALL SELECT 'context_pending_embedding_in_legacy',count(*) FROM clone_lab_legacy_20261127.turns;
