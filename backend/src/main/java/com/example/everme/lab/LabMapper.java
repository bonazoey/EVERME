package com.example.everme.lab;

import org.apache.ibatis.annotations.*;
import org.springframework.context.annotation.Profile;
import java.util.*;

@Mapper
@Profile("clone-lab")
public interface LabMapper {
    @Select("SELECT id,name,profile_version,created_at FROM clone_lab.lab_users ORDER BY created_at DESC") List<Map<String,Object>> users();
    @Select("SELECT id,name,persona::text,profile_version,run_state::text,viewer_context::text FROM clone_lab.lab_users WHERE id=#{id}") Map<String,Object> user(UUID id);
    @Select("SELECT id,name,persona::text,profile_version,run_state::text,viewer_context::text FROM clone_lab.lab_users WHERE id=#{id} FOR UPDATE") Map<String,Object> lockUser(UUID id);
    @Insert("INSERT INTO clone_lab.lab_users(id,name,persona) VALUES(#{id},#{name},CAST(#{persona} AS jsonb))") void createUser(@Param("id") UUID id,@Param("name") String name,@Param("persona") String persona);
    @Update("UPDATE clone_lab.lab_users SET profile_version=profile_version+1 WHERE id=#{id} AND profile_version=#{version}") int incrementVersion(@Param("id") UUID id,@Param("version") long version);
    @Update("UPDATE clone_lab.lab_users SET run_state=CAST(#{state} AS jsonb) WHERE id=#{id}") void runState(@Param("id") UUID id,@Param("state") String state);
    @Update("UPDATE clone_lab.lab_users SET viewer_context=CAST(#{context} AS jsonb) WHERE id=#{id}") void viewerContext(@Param("id") UUID id,@Param("context") String context);
    @Select("SELECT id FROM clone_lab.lab_users WHERE run_state->>'status' IN ('running','stopping')") List<UUID> interruptedUsers();
    @Select("SELECT COALESCE(MAX(sequence_no),0)+1 FROM clone_lab.batches WHERE user_id=#{id}") int nextSequence(UUID id);
    @Insert("INSERT INTO clone_lab.batches(id,user_id,topic,sequence_no) VALUES(#{id},#{user},#{topic},#{sequence})") void createBatch(@Param("id") UUID id,@Param("user") UUID user,@Param("topic") String topic,@Param("sequence") int sequence);
    @Insert("INSERT INTO clone_lab.turns(id,batch_id,ordinal,user_text,assistant_text) VALUES(#{id},#{batch},#{ordinal},#{userText},#{assistantText})") void createTurn(@Param("id") UUID id,@Param("batch") UUID batch,@Param("ordinal") int ordinal,@Param("userText") String userText,@Param("assistantText") String assistantText);
    @Select("SELECT id,user_id,topic,sequence_no,status,analysis::text FROM clone_lab.batches WHERE user_id=#{user} ORDER BY sequence_no DESC") List<Map<String,Object>> batches(UUID user);
    @Select("SELECT id,user_id,topic,sequence_no,status,analysis::text FROM clone_lab.batches WHERE id=#{id} AND user_id=#{user}") Map<String,Object> batch(@Param("user") UUID user,@Param("id") UUID id);
    @Select("SELECT id,ordinal,user_text,assistant_text FROM clone_lab.turns WHERE batch_id=#{batch} ORDER BY ordinal") List<Map<String,Object>> turns(UUID batch);
    @Update("UPDATE clone_lab.batches SET analysis=CAST(#{analysis} AS jsonb),status='analyzed' WHERE id=#{id} AND status<>'saved'") int analysis(@Param("id") UUID id,@Param("analysis") String analysis);
    @Update("UPDATE clone_lab.batches SET status='saved' WHERE id=#{id} AND status='analyzed'") int saved(UUID id);
    @Update("UPDATE clone_lab.batches SET analysis=CAST(#{analysis} AS jsonb) WHERE id=#{id} AND status='analyzed'") int saveDetails(@Param("id") UUID id,@Param("analysis") String analysis);
    @Select("SELECT t.id,t.user_text FROM clone_lab.turns t JOIN clone_lab.batches b ON b.id=t.batch_id WHERE b.user_id=#{user} AND b.status='saved'") List<Map<String,Object>> savedSources(UUID user);
    @Insert("INSERT INTO clone_lab.memories(id,user_id,batch_id,kind,content,source_ids,occurred_at,fingerprint,embedding) VALUES(#{id},#{user},#{batch},#{kind},#{content},CAST(#{sources} AS jsonb),#{occurredAt},#{fingerprint},CAST(#{vector} AS vector)) ON CONFLICT(user_id,fingerprint) DO NOTHING")
    void memory(@Param("id") UUID id,@Param("user") UUID user,@Param("batch") UUID batch,@Param("kind") String kind,@Param("content") String content,@Param("sources") String sources,@Param("occurredAt") String occurredAt,@Param("fingerprint") String fingerprint,@Param("vector") String vector);
    @Select("SELECT m.id,m.kind,m.content,m.source_ids::text,m.occurred_at,(m.embedding <=> CAST(#{vector} AS vector)) AS distance FROM clone_lab.memories m WHERE m.user_id=#{user} ORDER BY (m.embedding <=> CAST(#{vector} AS vector)) LIMIT 8")
    List<Map<String,Object>> search(@Param("user") UUID user,@Param("vector") String vector);
    @Select("SELECT id,kind,content,source_ids::text,occurred_at FROM clone_lab.memories WHERE user_id=#{user} AND content ILIKE '%' || #{query} || '%' LIMIT 8")
    List<Map<String,Object>> keyword(@Param("user") UUID user,@Param("query") String query);
    @Select("SELECT id,kind,content,source_ids::text,occurred_at FROM clone_lab.memories WHERE user_id=#{user} ORDER BY id") List<Map<String,Object>> memories(UUID user);
    @InsertProvider(type=ProfileSql.class,method="initialize") void initialize(@Param("table") String table,@Param("user") UUID user);
    @SelectProvider(type=ProfileSql.class,method="read") String profile(@Param("table") String table,@Param("user") UUID user);
    @UpdateProvider(type=ProfileSql.class,method="update") void profileUpdate(@Param("table") String table,@Param("user") UUID user,@Param("scores") Map<String,Object> scores,@Param("metadata") String metadata);

    /** Identifiers never come from SQL supplied by a request or a model. */
    class ProfileSql {
        private static final Set<String> TABLES=Set.of("emotion","values","interests","decision","habits","relations","expression");
        private static String table(Map<String,Object> p) {
            String t=String.valueOf(p.get("table"));if(!TABLES.contains(t))throw new IllegalArgumentException("Unknown profile table");return "clone_lab.profile_"+t;
        }
        public static String initialize(Map<String,Object> p){return "INSERT INTO "+table(p)+"(user_id) VALUES(#{user})";}
        public static String read(Map<String,Object> p){return "SELECT (to_jsonb(p)-'user_id')::text FROM "+table(p)+" p WHERE user_id=#{user}";}
        public static String update(Map<String,Object> p){
            @SuppressWarnings("unchecked") var scores=(Map<String,Object>)p.get("scores");
            var assignments=new ArrayList<String>();for(var col:scores.keySet()){
                if(!col.matches("[a-z][a-z_]*"))throw new IllegalArgumentException("Invalid column");
                assignments.add(col+"=#{scores."+col+"}");
            }
            assignments.add("metadata=CAST(#{metadata} AS jsonb)");return "UPDATE "+table(p)+" SET "+String.join(",",assignments)+" WHERE user_id=#{user}";
        }
    }
}
