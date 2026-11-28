package com.example.everme.lab.dao;

import java.util.Map;
import java.util.Set;

/** 실행 JSON 인터페이스를 정규화된 runs 컬럼으로 변환한다. 식별자는 고정 허용 목록만 사용한다. */
public final class LabRunSql {
  private LabRunSql() {}

  public static String nextId(Map<String, Object> p) {
    String table = p.get("table").toString(), column = p.get("column").toString();
    if (!Set.of("users.id", "sessions.session_id", "session_messages.id", "runs.run_id")
        .contains(table + "." + column)) throw new IllegalArgumentException("허용되지 않은 시퀀스");
    return "SELECT nextval(pg_get_serial_sequence('clone_lab." + table + "','" + column + "'))";
  }

  public static String upsert(Map<String, Object> ignored) {
    return """
INSERT INTO clone_lab.runs(run_id,clone_owner_id,session_id,mode,topic,status,error,
  target_pairs,generated_pairs,analyzed_pairs,saved_pairs,input_tokens,output_tokens,cached_tokens,
  llm_elapsed_ms,wall_elapsed_ms,retry_count,config,state,active_since,heartbeat_at,finished_at)
SELECT (j->>'runId')::bigint,#{id},(j->>'batchId')::bigint,j->>'mode',j->>'topic',
  (j->>'status')::clone_lab.run_status,j->>'error',
  COALESCE((j->>'targetPairs')::int,1000),COALESCE((j->>'generatedPairs')::int,0),
  COALESCE((j->>'analyzedPairs')::int,0),COALESCE((j->>'savedPairs')::int,0),
  COALESCE((j->>'inputTokens')::bigint,0),COALESCE((j->>'outputTokens')::bigint,0),
  COALESCE((j->>'cachedTokens')::bigint,0),COALESCE((j->>'llmElapsedMs')::bigint,0),
  COALESCE((j->>'wallElapsedMs')::bigint,0),COALESCE((j->>'retryCount')::int,0),
  CASE WHEN jsonb_typeof(j->'config')='object' THEN j->'config' ELSE '{}'::jsonb END,
  j-ARRAY['runId','batchId','mode','topic','status','error','targetPairs','generatedPairs',
    'analyzedPairs','savedPairs','inputTokens','outputTokens','cachedTokens','llmElapsedMs',
    'elapsedMs','wallElapsedMs','retryCount','activeSince','heartbeatAt','config'],
  CASE WHEN j->>'status' IN ('running','stopping') THEN
    COALESCE(to_timestamp((j->>'activeSince')::double precision/1000),clock_timestamp()) END,
  COALESCE(to_timestamp((j->>'heartbeatAt')::double precision/1000),clock_timestamp()),
  CASE WHEN j->>'status' IN ('completed','cancelled') THEN clock_timestamp() END
FROM (SELECT CAST(#{state} AS jsonb) j) input
ON CONFLICT(run_id) DO UPDATE SET status=EXCLUDED.status,error=EXCLUDED.error,
  generated_pairs=EXCLUDED.generated_pairs,analyzed_pairs=EXCLUDED.analyzed_pairs,
  saved_pairs=EXCLUDED.saved_pairs,input_tokens=EXCLUDED.input_tokens,
  output_tokens=EXCLUDED.output_tokens,cached_tokens=EXCLUDED.cached_tokens,
  llm_elapsed_ms=EXCLUDED.llm_elapsed_ms,wall_elapsed_ms=EXCLUDED.wall_elapsed_ms,
  retry_count=EXCLUDED.retry_count,state=EXCLUDED.state,active_since=EXCLUDED.active_since,
  heartbeat_at=EXCLUDED.heartbeat_at,finished_at=EXCLUDED.finished_at
""";
  }
}
