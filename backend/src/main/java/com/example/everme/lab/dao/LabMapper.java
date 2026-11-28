package com.example.everme.lab.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
  @Select(
      "SELECT id,name,profile_version,created_at FROM clone_lab.lab_users ORDER BY created_at DESC")
  List<Map<String, Object>> users();

  @Select(
      "SELECT id,name,persona::text,profile_version,run_state::text FROM clone_lab.lab_users WHERE"
          + " id=#{id}")
  Map<String, Object> user(UUID id);

  @Select(
      "SELECT id,name,persona::text,profile_version,run_state::text FROM clone_lab.lab_users WHERE"
          + " id=#{id} FOR UPDATE")
  Map<String, Object> lockUser(UUID id);

  @Insert(
      "INSERT INTO clone_lab.lab_users(id,name,persona) VALUES(#{id},#{name},CAST(#{persona} AS"
          + " jsonb))")
  void createUser(
      @Param("id") UUID id, @Param("name") String name, @Param("persona") String persona);

  @Update(
      "UPDATE clone_lab.lab_users SET profile_version=profile_version+1 WHERE id=#{id} AND"
          + " profile_version=#{version}")
  int incrementVersion(@Param("id") UUID id, @Param("version") long version);

  @Update("UPDATE clone_lab.lab_users SET run_state=CAST(#{state} AS jsonb) WHERE id=#{id}")
  void runState(@Param("id") UUID id, @Param("state") String state);

  @Select("SELECT id FROM clone_lab.lab_users WHERE run_state->>'status' IN ('running','stopping')")
  List<UUID> interruptedUsers();

  @Select(
      "SELECT count(*)+1 FROM clone_lab.sessions WHERE clone_owner_id=#{id} AND"
          + " conversation_type='owner'")
  int nextSequence(UUID id);

  @Insert(
      "INSERT INTO"
          + " clone_lab.sessions(session_id,clone_owner_id,actor_user_id,conversation_type,topic)"
          + " VALUES(#{id},#{user},#{user},'owner',#{topic})")
  void createBatch(
      @Param("id") UUID id,
      @Param("user") UUID user,
      @Param("topic") String topic,
      @Param("sequence") int sequence);

  // 기존 화면의 id/status 별칭은 유지하되 세션 전체 상태 컬럼은 만들지 않는다.
  String SESSION_COLUMNS =
      "session_id AS id,clone_owner_id AS"
          + " user_id,actor_user_id,conversation_type,topic,created_at,analysis::text,"
          + " COALESCE((analysis->>'turnCount')::int,0) AS turn_count, CASE WHEN EXISTS(SELECT 1"
          + " FROM clone_lab.context c WHERE c.metadata->>'session_id'=s.session_id::text AND"
          + " c.status='pending') THEN 'generated' WHEN EXISTS(SELECT 1 FROM clone_lab.context c"
          + " WHERE c.metadata->>'session_id'=s.session_id::text AND c.status='analyzed') THEN"
          + " 'analyzed' WHEN COALESCE((analysis->>'turnCount')::int,0)>0 THEN 'saved' ELSE"
          + " 'generated' END AS status";

  @Select(
      "SELECT "
          + SESSION_COLUMNS
          + " FROM clone_lab.sessions s WHERE clone_owner_id=#{user} AND conversation_type='owner'"
          + " ORDER BY created_at DESC,session_id DESC")
  List<Map<String, Object>> batches(UUID user);

  @Select(
      "SELECT "
          + SESSION_COLUMNS
          + " FROM clone_lab.sessions s WHERE session_id=#{id} AND clone_owner_id=#{user} AND"
          + " conversation_type='owner'")
  Map<String, Object> batch(@Param("user") UUID user, @Param("id") UUID id);

  String CONTEXT_COLUMNS =
      "id,sequence_no,created_at,status,metadata::text,(metadata->>'ordinal')::int AS"
          + " ordinal,metadata->>'user_text' AS user_text,metadata->>'assistant_text' AS"
          + " assistant_text";

  @Select(
      "SELECT "
          + CONTEXT_COLUMNS
          + " FROM clone_lab.context WHERE metadata->>'session_id'=CAST(#{batch} AS text) ORDER BY"
          + " sequence_no")
  List<Map<String, Object>> turns(UUID batch);

  @Select(
      "SELECT * FROM (SELECT "
          + CONTEXT_COLUMNS
          + " FROM clone_lab.context WHERE metadata->>'clone_owner_id'=CAST(#{user} AS text) AND"
          + " conversation_type='owner' ORDER BY sequence_no DESC LIMIT 100) recent ORDER BY"
          + " sequence_no")
  List<Map<String, Object>> recent(UUID user);

  @Insert(
      "INSERT INTO clone_lab.context(id,content,embedding,metadata,status)"
          + " VALUES(#{id},#{content},CAST(#{vector} AS vector),CAST(#{metadata} AS"
          + " jsonb),CAST(#{status} AS clone_lab.context_status))")
  void context(
      @Param("id") UUID id,
      @Param("content") String content,
      @Param("vector") String vector,
      @Param("metadata") String metadata,
      @Param("status") String status);

  @Update(
      "UPDATE clone_lab.sessions SET analysis=CAST(#{analysis} AS jsonb) WHERE session_id=#{id}")
  int analysis(@Param("id") UUID id, @Param("analysis") String analysis);

  @Update(
      "UPDATE clone_lab.context SET status='analyzed' WHERE metadata->>'session_id'=CAST(#{id} AS"
          + " text) AND status='pending'")
  void analyzed(UUID id);

  @Update(
      "UPDATE clone_lab.context SET status='saved' WHERE metadata->>'session_id'=CAST(#{id} AS"
          + " text) AND status='analyzed'")
  int saved(UUID id);

  @Update(
      "UPDATE clone_lab.sessions SET analysis=CAST(#{analysis} AS jsonb) WHERE session_id=#{id}")
  int saveDetails(@Param("id") UUID id, @Param("analysis") String analysis);

  @Select(
      "SELECT e->>'user_text' AS user_text FROM clone_lab.sessions s CROSS JOIN LATERAL"
          + " jsonb_array_elements(COALESCE(s.analysis->'processedEvidence','[]'::jsonb)) e WHERE"
          + " clone_owner_id=#{user} AND conversation_type='owner'")
  List<Map<String, Object>> savedSources(UUID user);

  @Update(
      "UPDATE clone_lab.sessions SET analysis=CAST(#{analysis} AS jsonb) WHERE session_id=#{id}")
  void appended(@Param("id") UUID id, @Param("analysis") String analysis);

  @Update(
      "UPDATE clone_lab.context SET status='pending' WHERE metadata->>'session_id'=CAST(#{id} AS"
          + " text) AND status='analyzed'")
  void resetDraft(UUID id);

  @Update("UPDATE clone_lab.sessions SET topic=#{topic} WHERE session_id=#{id}")
  void topic(@Param("id") UUID id, @Param("topic") String topic);

  @Update(
      "UPDATE clone_lab.sessions SET analysis=CAST(#{analysis} AS jsonb) WHERE session_id=#{id}")
  void pipelineDetails(
      @Param("id") UUID id, @Param("analysis") String analysis, @Param("status") String status);

  @Insert(
      "INSERT INTO clone_lab.vector_store(id,content,embedding,metadata)"
          + " VALUES(#{id},#{content},CAST(#{vector} AS vector),CAST(#{metadata} AS jsonb)) ON"
          + " CONFLICT(id) DO NOTHING")
  void document(
      @Param("id") UUID id,
      @Param("content") String content,
      @Param("vector") String vector,
      @Param("metadata") String metadata);

  @Select(
      "SELECT id,content,embedding::text,metadata::text FROM clone_lab.context WHERE"
          + " metadata->>'clone_owner_id'=CAST(#{owner} AS text) AND"
          + " metadata->>'actor_user_id'=CAST(#{actor} AS text) AND conversation_type=#{type} ORDER"
          + " BY sequence_no DESC OFFSET 100")
  List<Map<String, Object>> overflow(
      @Param("owner") UUID owner, @Param("actor") UUID actor, @Param("type") String type);

  @Select(
      "SELECT id,content,metadata::text FROM clone_lab.vector_store WHERE"
          + " metadata->>'clone_owner_id'=CAST(#{user} AS text) AND conversation_type='owner' ORDER"
          + " BY created_at,id")
  List<Map<String, Object>> memories(UUID user);

  @Update(
      "UPDATE clone_lab.context SET status=CAST(#{status} AS clone_lab.context_status) WHERE"
          + " id=#{id}")
  void contextStatus(@Param("id") UUID id, @Param("status") String status);

  @Select("SELECT status IN ('saved','archived') FROM clone_lab.context WHERE id=#{id}")
  boolean archiveReady(UUID id);

  @org.apache.ibatis.annotations.Delete(
      "DELETE FROM clone_lab.context WHERE id=#{id} AND status='archived'")
  int deleteArchived(UUID id);

  @Insert(
      "INSERT INTO"
          + " clone_lab.sessions(session_id,clone_owner_id,actor_user_id,conversation_type,analysis)"
          + " VALUES(#{id},#{owner},#{actor},'viewer',CAST(#{analysis} AS jsonb))")
  void viewerSession(
      @Param("id") UUID id,
      @Param("owner") UUID owner,
      @Param("actor") UUID actor,
      @Param("analysis") String analysis);

  @Select(
      "SELECT session_id,analysis::text FROM clone_lab.sessions WHERE clone_owner_id=#{owner} AND"
          + " actor_user_id=#{actor} AND conversation_type='viewer' ORDER BY created_at"
          + " DESC,session_id DESC LIMIT 1")
  Map<String, Object> currentViewer(@Param("owner") UUID owner, @Param("actor") UUID actor);

  @Select(
      "SELECT "
          + CONTEXT_COLUMNS
          + " FROM clone_lab.context WHERE metadata->>'clone_owner_id'=CAST(#{owner} AS text) AND"
          + " metadata->>'actor_user_id'=CAST(#{actor} AS text) AND"
          + " metadata->>'session_id'=CAST(#{session} AS text) AND conversation_type='viewer' ORDER"
          + " BY sequence_no")
  List<Map<String, Object>> viewerTurns(
      @Param("owner") UUID owner, @Param("actor") UUID actor, @Param("session") UUID session);

  @InsertProvider(type = ProfileSql.class, method = "initialize")
  void initialize(@Param("table") String table, @Param("user") UUID user);

  @SelectProvider(type = ProfileSql.class, method = "read")
  String profile(@Param("table") String table, @Param("user") UUID user);

  @UpdateProvider(type = ProfileSql.class, method = "update")
  void profileUpdate(
      @Param("table") String table,
      @Param("user") UUID user,
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
      return "SELECT (to_jsonb(p)-'user_id')::text FROM " + table(p) + " p WHERE user_id=#{user}";
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
