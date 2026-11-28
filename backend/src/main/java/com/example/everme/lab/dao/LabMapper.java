package com.example.everme.lab.dao;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Profile;

// Clone Lab MyBatis 매퍼. SQL은 resources/mybatis/mapper/LabMapper.xml에 있다.
// SQL 식별자(테이블·컬럼)가 들어가는 호출은 default 메소드에서 허용 목록을 검사한 뒤 XML 문장을 부른다.
@Mapper
@Profile("clone-lab")
public interface LabMapper {
  // ##### 필드 #####
  Set<String> SEQUENCES =
      Set.of("users.id", "sessions.session_id", "session_messages.id", "runs.run_id");
  Set<String> PROFILE_TABLES =
      Set.of("emotion", "values", "interests", "decision", "habits", "relations", "expression");

  // ##### context: Spring AI ChatMemory 저장소 #####
  void preserveContext(Long session);

  void slideContext(Long session);

  void deleteSummarizedContext(Long session);

  // ##### context_summaries: 세션 문맥 요약 #####
  void discardPrematureSummaries(@Param("session") Long session, @Param("blocks") int blocks);

  List<Map<String, Object>> sessionPairs(Long id);

  List<Map<String, Object>> summaries(Long id);

  int summary(
      @Param("session") Long session,
      @Param("segment") int segment,
      @Param("start") Long start,
      @Param("end") Long end,
      @Param("content") String content);

  void trimSummaries(Long id);

  // ##### users: 테스트 사용자 #####
  List<Map<String, Object>> users();

  Map<String, Object> userRow(Long id);

  Map<String, Object> lockUserRow(Long id);

  // 사용자 + 최신 실행 상태(run_state). LabDao.requireUser가 사용한다.
  default Map<String, Object> user(Long id) {
    return withRun(userRow(id), id);
  }

  // 사용자 행을 FOR UPDATE로 잠그고 run_state를 붙인다.
  default Map<String, Object> lockUser(Long id) {
    return withRun(lockUserRow(id), id);
  }

  // runs 테이블의 최신 실행을 JSON 문자열로 붙인다. 실행이 없으면 "{}".
  private Map<String, Object> withRun(Map<String, Object> row, Long id) {
    if (row != null) {
      String state = latestRun(id);
      row.put("run_state", state == null ? "{}" : state);
    }
    return row;
  }

  void createUser(
      @Param("id") Long id, @Param("name") String name, @Param("persona") String persona);

  int incrementVersion(@Param("id") Long id, @Param("version") long version);

  // ##### runs: 자동 실행 #####
  String latestRun(Long id);

  void runState(@Param("id") Long id, @Param("state") String state);

  List<Long> interruptedUsers();

  void runResult(@Param("owner") Long owner, @Param("result") String result);

  // ##### 시퀀스 다음 값을 구하는 메소드 #####
  // 허용 목록에 있는 테이블·컬럼만 받는다. 값은 바인딩 파라미터로 넘긴다.
  default Long nextId(String table, String column) {
    if (!SEQUENCES.contains(table + "." + column)) {
      throw new IllegalArgumentException("허용되지 않은 시퀀스");
    }
    return nextSerialValue(table, column);
  }

  Long nextSerialValue(@Param("table") String table, @Param("column") String column);

  // ##### sessions: 대화방(배치) #####
  int nextSequence(Long id);

  void createBatch(
      @Param("id") Long id,
      @Param("user") Long user,
      @Param("topic") String topic,
      @Param("sequence") int sequence);

  List<Map<String, Object>> batches(Long user);

  Map<String, Object> batch(@Param("user") Long user, @Param("id") Long id);

  int analysis(@Param("id") Long id, @Param("analysis") String analysis);

  int saveDetails(@Param("id") Long id, @Param("analysis") String analysis);

  void appended(@Param("id") Long id, @Param("analysis") String analysis);

  void pipelineDetails(
      @Param("id") Long id, @Param("analysis") String analysis, @Param("status") String status);

  void topic(@Param("id") Long id, @Param("topic") String topic);

  List<Map<String, Object>> savedSources(Long user);

  Map<String, Object> lockSession(Long id);

  Long sessionOwner(Long id);

  Map<String, Object> lockFreeSession(Long id);

  // ##### viewer 세션: 클론 대화 #####
  void viewerSession(
      @Param("id") Long id,
      @Param("owner") Long owner,
      @Param("actor") Long actor,
      @Param("analysis") String analysis);

  Map<String, Object> currentViewer(@Param("owner") Long owner, @Param("actor") Long actor);

  List<Map<String, Object>> viewerTurns(
      @Param("owner") Long owner, @Param("actor") Long actor, @Param("session") Long session);

  List<Map<String, Object>> viewerMissingEmbeddings(
      @Param("owner") Long owner, @Param("viewer") Long viewer, @Param("session") Long session);

  // ##### session_messages: 원문 턴 #####
  List<Map<String, Object>> turns(Long batch);

  List<Map<String, Object>> recent(Long user);

  List<Map<String, Object>> missingEmbeddings(Long user);

  void embedding(@Param("id") Long id, @Param("vector") String vector);

  void context(
      @Param("id") Long id,
      @Param("content") String content,
      @Param("vector") String vector,
      @Param("metadata") String metadata,
      @Param("status") String status);

  void analyzed(Long id);

  int saved(Long id);

  void resetDraft(Long id);

  void contextStatus(@Param("id") Long id, @Param("status") String status);

  // ##### vector_store: 장기 기억 #####
  int archive(Long id);

  List<Map<String, Object>> overflow(
      @Param("owner") Long owner, @Param("actor") Long actor, @Param("type") String type);

  List<Map<String, Object>> memories(Long user);

  boolean archiveReady(Long id);

  // ##### profile_*: 7개 영역 점수 #####
  // 영역 코드를 검사해 실제 테이블명으로 바꾼다. 요청이나 모델이 준 값은 여기서 거부된다.
  static String profileTable(String domain) {
    if (!PROFILE_TABLES.contains(domain)) {
      throw new IllegalArgumentException("Unknown profile table");
    }
    return "clone_lab.profile_" + domain;
  }

  // 영역 테이블에 빈 행 만들기
  default void initialize(String table, Long user) {
    initializeProfileRow(profileTable(table), user);
  }

  // 영역 행 전체를 JSON 문자열로
  default String profile(String table, Long user) {
    return profileRow(profileTable(table), user);
  }

  // 영역 점수·metadata 저장. 컬럼명은 소문자·밑줄만 허용한다.
  default void profileUpdate(
      String table, Long user, Map<String, Object> scores, String metadata) {
    for (String column : scores.keySet()) {
      if (!column.matches("[a-z][a-z_]*")) {
        throw new IllegalArgumentException("Invalid column");
      }
    }
    updateProfileRow(profileTable(table), user, scores, metadata);
  }

  void initializeProfileRow(@Param("table") String table, @Param("user") Long user);

  String profileRow(@Param("table") String table, @Param("user") Long user);

  void updateProfileRow(
      @Param("table") String table,
      @Param("user") Long user,
      @Param("scores") Map<String, Object> scores,
      @Param("metadata") String metadata);
}
