package com.example.everme.lab.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.UpdateProvider;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("clone-lab")
public interface LabMapper {
  @Select("SELECT id,name,profile_version,created_at FROM clone_lab.users ORDER BY created_at DESC")
  List<Map<String, Object>> users();

  @Select("SELECT id,name,persona::text,profile_version FROM clone_lab.users WHERE" + " id=#{id}")
  Map<String, Object> userRow(Long id);

  default Map<String, Object> user(Long id) {
    return withRun(userRow(id), id);
  }

  default Map<String, Object> lockUser(Long id) {
    return withRun(lockUserRow(id), id);
  }

  private Map<String, Object> withRun(Map<String, Object> row, Long id) {
    if (row != null) {
      String state = latestRun(id);
      row.put("run_state", state == null ? "{}" : state);
    }
    return row;
  }

  @Select(
      "SELECT id,name,persona::text,profile_version FROM clone_lab.users WHERE"
          + " id=#{id} FOR UPDATE")
  Map<String, Object> lockUserRow(Long id);

  @Insert(
      "INSERT INTO clone_lab.users(id,name,persona) VALUES(#{id},#{name},CAST(#{persona} AS"
          + " jsonb))")
  void createUser(
      @Param("id") Long id, @Param("name") String name, @Param("persona") String persona);

  @Update(
      "UPDATE clone_lab.users SET profile_version=profile_version+1 WHERE id=#{id} AND"
          + " profile_version=#{version}")
  int incrementVersion(@Param("id") Long id, @Param("version") long version);

  // 실행 컬럼을 원본으로 사용하고 state JSON에는 단계별 부가 정보만 저장한다.
  @Select(
      "SELECT state ||"
          + " jsonb_build_object('runId',run_id::text,'batchId',session_id::text,'mode',mode,'topic',topic,'status',status,'error',error,'targetPairs',target_pairs,'generatedPairs',generated_pairs,'analyzedPairs',analyzed_pairs,'savedPairs',saved_pairs,'inputTokens',input_tokens,'outputTokens',output_tokens,'cachedTokens',cached_tokens,'llmElapsedMs',llm_elapsed_ms,'elapsedMs',llm_elapsed_ms,'wallElapsedMs',wall_elapsed_ms,'retryCount',retry_count)"
          + " || CASE WHEN active_since IS NULL THEN '{}'::jsonb ELSE"
          + " jsonb_build_object('activeSince',(extract(epoch from active_since)*1000)::bigint) END"
          + " || CASE WHEN heartbeat_at IS NULL THEN '{}'::jsonb ELSE"
          + " jsonb_build_object('heartbeatAt',(extract(epoch from heartbeat_at)*1000)::bigint) END"
          + " FROM clone_lab.runs WHERE clone_owner_id=#{id} ORDER BY run_id DESC LIMIT 1")
  String latestRun(Long id);

  @UpdateProvider(type = LabRunSql.class, method = "upsert")
  void runState(@Param("id") Long id, @Param("state") String state);

  @Select("SELECT clone_owner_id FROM clone_lab.runs WHERE status IN ('running','stopping')")
  List<Long> interruptedUsers();

  @SelectProvider(type = LabRunSql.class, method = "nextId")
  Long nextId(@Param("table") String table, @Param("column") String column);

  @Select(
      "SELECT count(*)+1 FROM clone_lab.sessions WHERE owner_id=#{id} AND"
          + " conversation_type='owner'")
  int nextSequence(Long id);

  @Insert(
      "INSERT INTO"
          + " clone_lab.sessions(session_id,owner_id,viewer_id,conversation_type,topic)"
          + " VALUES(#{id},#{user},#{user},'owner',#{topic})")
  void createBatch(
      @Param("id") Long id,
      @Param("user") Long user,
      @Param("topic") String topic,
      @Param("sequence") int sequence);

  // 기존 화면의 id/status 별칭은 유지하되 세션 전체 상태 컬럼은 만들지 않는다.
  String SESSION_COLUMNS =
      "session_id AS id,owner_id AS"
          + " user_id,viewer_id,conversation_type,topic,created_at,analysis::text,"
          + " COALESCE((analysis->>'turnCount')::int,0) AS turn_count, CASE WHEN EXISTS(SELECT 1"
          + " FROM clone_lab.session_messages c WHERE c.session_id=s.session_id AND"
          + " c.status='pending') THEN 'generated' WHEN EXISTS(SELECT 1 FROM"
          + " clone_lab.session_messages c WHERE c.metadata->>'session_id'=s.session_id::text AND"
          + " c.status='analyzed') THEN 'analyzed' WHEN COALESCE((analysis->>'turnCount')::int,0)>0"
          + " THEN 'saved' ELSE 'generated' END AS status";

  @Select(
      "SELECT "
          + SESSION_COLUMNS
          + " FROM clone_lab.sessions s WHERE owner_id=#{user} AND conversation_type='owner'"
          + " ORDER BY created_at DESC,session_id DESC")
  List<Map<String, Object>> batches(Long user);

  @Select(
      "SELECT "
          + SESSION_COLUMNS
          + " FROM clone_lab.sessions s WHERE session_id=#{id} AND owner_id=#{user} AND"
          + " conversation_type='owner'")
  Map<String, Object> batch(@Param("user") Long user, @Param("id") Long id);

  String CONTEXT_COLUMNS =
      "id,session_id,sequence_no,created_at,status,metadata::text,pair_no::int AS"
          + " ordinal,user_text,assistant_text";

  @Select(
      "SELECT "
          + CONTEXT_COLUMNS
          + " FROM clone_lab.session_messages WHERE session_id=#{batch} ORDER BY sequence_no")
  List<Map<String, Object>> turns(Long batch);

  @Select(
      "SELECT * FROM (SELECT m.* FROM clone_lab.session_messages m JOIN clone_lab.sessions s ON"
          + " s.session_id=m.session_id WHERE s.owner_id=#{user} AND s.conversation_type='owner'"
          + " ORDER BY m.sequence_no DESC LIMIT 100) r ORDER BY sequence_no")
  List<Map<String, Object>> recent(Long user);

  @Select(
      "SELECT m.id,m.user_text FROM clone_lab.session_messages m JOIN clone_lab.sessions s ON"
          + " s.session_id=m.session_id WHERE s.owner_id=#{user} AND s.conversation_type='owner'"
          + " AND m.embedding IS NULL ORDER BY m.sequence_no")
  List<Map<String, Object>> missingEmbeddings(Long user);

  @Update(
      "UPDATE clone_lab.session_messages SET embedding=CAST(#{vector} AS vector) WHERE id=#{id} AND"
          + " embedding IS NULL")
  void embedding(@Param("id") Long id, @Param("vector") String vector);

  @Insert(
      "INSERT INTO"
          + " clone_lab.session_messages(id,session_id,content,embedding,metadata,user_text,assistant_text,user_received_at,assistant_received_at,pair_no,status)"
          + " VALUES(#{id},(CAST(#{metadata} AS"
          + " jsonb)->>'session_id')::bigint,#{content},CAST(#{vector} AS vector),CAST(#{metadata}"
          + " AS jsonb),CAST(#{metadata} AS jsonb)->>'user_text',CAST(#{metadata} AS"
          + " jsonb)->>'assistant_text',(CAST(#{metadata} AS"
          + " jsonb)->>'user_received_at')::timestamptz,(CAST(#{metadata} AS"
          + " jsonb)->>'assistant_received_at')::timestamptz,(CAST(#{metadata} AS"
          + " jsonb)->>'ordinal')::bigint,CAST(#{status} AS clone_lab.message_status))")
  void context(
      @Param("id") Long id,
      @Param("content") String content,
      @Param("vector") String vector,
      @Param("metadata") String metadata,
      @Param("status") String status);

  @Update(
      "UPDATE clone_lab.sessions SET analysis=CAST(#{analysis} AS jsonb) WHERE session_id=#{id}")
  int analysis(@Param("id") Long id, @Param("analysis") String analysis);

  @Update(
      "UPDATE clone_lab.session_messages SET status='analyzed' WHERE"
          + " metadata->>'session_id'=CAST(#{id} AS text) AND status='pending'")
  void analyzed(Long id);

  @Update(
      "UPDATE clone_lab.session_messages SET status='saved' WHERE"
          + " metadata->>'session_id'=CAST(#{id} AS text) AND status='analyzed'")
  int saved(Long id);

  @Update(
      "UPDATE clone_lab.sessions SET analysis=CAST(#{analysis} AS jsonb) WHERE session_id=#{id}")
  int saveDetails(@Param("id") Long id, @Param("analysis") String analysis);

  @Select(
      "SELECT e->>'user_text' AS user_text FROM clone_lab.sessions s CROSS JOIN LATERAL"
          + " jsonb_array_elements(COALESCE(s.analysis->'processedEvidence','[]'::jsonb)) e WHERE"
          + " owner_id=#{user} AND conversation_type='owner'")
  List<Map<String, Object>> savedSources(Long user);

  @Update(
      "UPDATE clone_lab.sessions SET analysis=CAST(#{analysis} AS jsonb) WHERE session_id=#{id}")
  void appended(@Param("id") Long id, @Param("analysis") String analysis);

  @Update(
      "UPDATE clone_lab.session_messages SET status='pending' WHERE"
          + " metadata->>'session_id'=CAST(#{id} AS text) AND status='analyzed'")
  void resetDraft(Long id);

  @Update("UPDATE clone_lab.sessions SET topic=#{topic} WHERE session_id=#{id}")
  void topic(@Param("id") Long id, @Param("topic") String topic);

  @Update(
      "UPDATE clone_lab.sessions SET analysis=CAST(#{analysis} AS jsonb) WHERE session_id=#{id}")
  void pipelineDetails(
      @Param("id") Long id, @Param("analysis") String analysis, @Param("status") String status);

  @Insert(
      "INSERT INTO"
          + " clone_lab.vector_store(owner_id,source_session_record_id,content,embedding,metadata)"
          + " SELECT s.owner_id,m.id,m.content,m.embedding,m.metadata ||"
          + " jsonb_build_object('record_type','conversation_archive','source_ids',jsonb_build_array(m.id::text),'source_excerpts',jsonb_build_array(jsonb_build_object('source_id',m.id::text,'user_text',m.user_text)))"
          + " FROM clone_lab.session_messages m JOIN clone_lab.sessions s ON"
          + " s.session_id=m.session_id WHERE m.id=#{id} AND m.status='saved' AND m.embedding IS"
          + " NOT NULL AND s.conversation_type='owner' ON CONFLICT(source_session_record_id) DO"
          + " NOTHING")
  int archive(Long id);

  @Select(
      "SELECT m.id FROM clone_lab.session_messages m JOIN clone_lab.sessions s ON"
          + " s.session_id=m.session_id WHERE s.owner_id=#{owner} AND s.conversation_type='owner'"
          + " ORDER BY m.sequence_no DESC OFFSET 100")
  List<Map<String, Object>> overflow(
      @Param("owner") Long owner, @Param("actor") Long actor, @Param("type") String type);

  @Select(
      "SELECT id,content,metadata::text FROM clone_lab.vector_store WHERE owner_id=#{user} ORDER BY"
          + " created_at,id")
  List<Map<String, Object>> memories(Long user);

  @Update(
      "UPDATE clone_lab.session_messages SET status=CAST(#{status} AS clone_lab.message_status)"
          + " WHERE id=#{id}")
  void contextStatus(@Param("id") Long id, @Param("status") String status);

  @Select(
      "SELECT status='saved' AND embedding IS NOT NULL FROM clone_lab.session_messages WHERE"
          + " id=#{id}")
  boolean archiveReady(Long id);

  @Insert(
      "INSERT INTO"
          + " clone_lab.sessions(session_id,owner_id,viewer_id,conversation_type,analysis)"
          + " VALUES(#{id},#{owner},#{actor},'viewer',CAST(#{analysis} AS jsonb))")
  void viewerSession(
      @Param("id") Long id,
      @Param("owner") Long owner,
      @Param("actor") Long actor,
      @Param("analysis") String analysis);

  @Select(
      "SELECT session_id,analysis::text FROM clone_lab.sessions WHERE owner_id=#{owner} AND"
          + " viewer_id=#{actor} AND conversation_type='viewer' ORDER BY created_at"
          + " DESC,session_id DESC LIMIT 1")
  Map<String, Object> currentViewer(@Param("owner") Long owner, @Param("actor") Long actor);

  @Select(
      "SELECT m.id,m.session_id,m.sequence_no,m.created_at,m.status,m.metadata::text,m.pair_no::int"
          + " AS ordinal,m.user_text,m.assistant_text FROM clone_lab.session_messages m JOIN"
          + " clone_lab.sessions s ON s.session_id=m.session_id WHERE s.owner_id=#{owner} AND"
          + " s.viewer_id=#{actor} AND s.session_id=#{session} AND s.conversation_type='viewer'"
          + " ORDER BY m.sequence_no")
  List<Map<String, Object>> viewerTurns(
      @Param("owner") Long owner, @Param("actor") Long actor, @Param("session") Long session);

  @Select(
      "SELECT session_id,owner_id,viewer_id,conversation_type,memory_key,analysis::text FROM"
          + " clone_lab.sessions WHERE session_id=#{id} FOR UPDATE")
  Map<String, Object> lockSession(Long id);

  @Select("SELECT owner_id FROM clone_lab.sessions WHERE session_id=#{id}")
  Long sessionOwner(Long id);

  @Update(
      "UPDATE clone_lab.runs SET result=CAST(#{result} AS jsonb) WHERE clone_owner_id=#{owner} AND"
          + " run_id=(SELECT max(run_id) FROM clone_lab.runs WHERE clone_owner_id=#{owner}) AND"
          + " status='completed'")
  void runResult(@Param("owner") Long owner, @Param("result") String result);

  @Select(
      "SELECT id,pair_no,user_text,assistant_text FROM clone_lab.session_messages WHERE"
          + " session_id=#{id} ORDER BY pair_no")
  List<Map<String, Object>> sessionPairs(Long id);

  @Select(
      "SELECT segment_no,content FROM clone_lab.context_summaries WHERE session_id=#{id} ORDER BY"
          + " segment_no")
  List<Map<String, Object>> summaries(Long id);

  @Insert(
      "INSERT INTO"
          + " clone_lab.context_summaries(session_id,segment_no,start_message_id,end_message_id,content)"
          + " VALUES(#{session},#{segment},#{start},#{end},#{content}) ON"
          + " CONFLICT(session_id,segment_no) DO NOTHING")
  int summary(
      @Param("session") Long session,
      @Param("segment") int segment,
      @Param("start") Long start,
      @Param("end") Long end,
      @Param("content") String content);

  @org.apache.ibatis.annotations.Delete(
      "DELETE FROM clone_lab.context_summaries WHERE session_id=#{id} AND segment_no NOT IN (SELECT"
          + " segment_no FROM clone_lab.context_summaries WHERE session_id=#{id} ORDER BY"
          + " segment_no DESC LIMIT 5)")
  void trimSummaries(Long id);

  @InsertProvider(type = ProfileSql.class, method = "initialize")
  void initialize(@Param("table") String table, @Param("user") Long user);

  @SelectProvider(type = ProfileSql.class, method = "read")
  String profile(@Param("table") String table, @Param("user") Long user);

  @UpdateProvider(type = ProfileSql.class, method = "update")
  void profileUpdate(
      @Param("table") String table,
      @Param("user") Long user,
      @Param("scores") Map<String, Object> scores,
      @Param("metadata") String metadata);

  /** Identifiers never come from SQL supplied by a request or a model. */
  class ProfileSql {
    private static final Set<String> TABLES =
        Set.of("emotion", "values", "interests", "decision", "habits", "relations", "expression");

    private static String table(Map<String, Object> p) {
      String t = String.valueOf(p.get("table"));
      if (!TABLES.contains(t)) {
        throw new IllegalArgumentException("Unknown profile table");
      }
      return "clone_lab.profile_" + t;
    }

    public static String initialize(Map<String, Object> p) {
      return "INSERT INTO " + table(p) + "(user_id) VALUES(#{user})";
    }

    public static String read(Map<String, Object> p) {
      return "SELECT (to_jsonb(p)-'user_id'-'id')::text FROM "
          + table(p)
          + " p WHERE user_id=#{user}";
    }

    public static String update(Map<String, Object> p) {
      @SuppressWarnings("unchecked")
      Map<String, Object> scores = (Map<String, Object>) p.get("scores");
      ArrayList<String> assignments = new ArrayList<String>();
      for (String col : scores.keySet()) {
        if (!col.matches("[a-z][a-z_]*")) {
          throw new IllegalArgumentException("Invalid column");
        }
        assignments.add(col + "=#{scores." + col + "}");
      }
      assignments.add("metadata=CAST(#{metadata} AS jsonb)");
      return "UPDATE "
          + table(p)
          + " SET "
          + String.join(",", assignments)
          + " WHERE user_id=#{user}";
    }
  }
}
