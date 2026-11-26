package com.example.everme.lab;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.math.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("clone-lab")
@TestPropertySource(properties="lab.initialize-schema=true")
@EnabledIfEnvironmentVariable(named="LAB_TEST_DATABASE_URL",matches=".+")
class LabIntegrationTests {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",()->System.getenv("LAB_TEST_DATABASE_URL"));r.add("spring.datasource.username",()->"everme_lab");r.add("spring.datasource.password",()->"everme_lab");}
    @Autowired LabService lab;
    @Autowired LabCloneService clone;
    @Autowired LabRunService runs;
    @Autowired ObjectMapper json;
    @Autowired LabCatalog catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean OpenAiLabClient ai;

    @BeforeEach void fixture(){
        reset(ai);
        when(ai.embed(anyList())).thenAnswer(call->{List<?> list=call.getArgument(0);return list.stream().map(x->{float[] v=new float[1536];v[0]=1;return v;}).toList();});
        when(ai.structured(anyString(),anyString(),any(JsonNode.class),any(JsonNode.class))).thenAnswer(call->{
            String stage=call.getArgument(0);JsonNode input=call.getArgument(2);ObjectNode data=json.createObjectNode();
            switch(stage){
                case "generation" -> {
                    if(input.has("seed")){data.put("biography","고정 테스트 인물");data.putArray("facts").add("매주 달린다");var scores=data.putObject("scores");catalog.codes().forEach(c->scores.put(c,70));}
                    else {var turns=data.putArray("turns");String topic=input.path("topic").asText();for(int i=0;i<10;i++)turns.addObject().put("user",topic+" 나는 매주 "+i+"일 달린다").put("assistant","어떤 기분인가요?");}
                }
                case "analysis" -> {var source=input.path("sources").get(0);data.putArray("observations").addObject().put("code","habits.exercise").put("score",80).put("strength",2).put("reason","사용자가 직접 반복 운동을 설명했다").putArray("sourceIds").add(source.path("id").asText());data.putArray("memories").addObject().put("kind","FACT").put("content","이 사용자는 달리기를 한다").putNull("occurredAt").putArray("sourceIds").add(source.path("id").asText());}
                case "merge" -> {var updates=data.putArray("updates");for(var o:input.path("observations")){String code=o.path("code").asText();var old=input.path("previous").path(code);int w=old.path("weight").asInt(),add=o.path("strength").asInt();double score=(old.path("score").asDouble()*w+o.path("score").asDouble()*add)/(w+add);updates.addObject().put("code",code).put("score",BigDecimal.valueOf(score).setScale(2,RoundingMode.HALF_UP)).put("weight",w+add);}}
                default -> throw new IllegalArgumentException(stage);
            }
            return new LabAi.Result(data,"test-model",10,10,1);
        });
    }
    UUID user(){return UUID.fromString(lab.createUser("통합 테스트","운동하는 사람").path("id").asText());}
    UUID generated(UUID user,String topic){return UUID.fromString(lab.generate(user,topic,false).path("id").asText());}
    @Test void generationAnalysisAndSaveAreSeparateAndIdempotent(){
        UUID user=user(),batch=generated(user,"운동 "+UUID.randomUUID());
        assertTrue(lab.profile(user).path("elements").path("habits.exercise").path("score").isNull());
        lab.analyze(user,batch,false);assertEquals(0,lab.profile(user).path("version").asInt());
        lab.save(user,batch,false);assertEquals(80,lab.profile(user).path("elements").path("habits.exercise").path("score").asInt());
        lab.save(user,batch,false);assertEquals(1,lab.profile(user).path("version").asInt());assertEquals(1,lab.dao.mapper().memories(user).size());
        assertTrue(lab.profile(user).path("elements").path("values.family").path("score").isNull());
        UUID duplicate=generated(user,lab.batch(user,batch).path("topic").asText());lab.analyze(user,duplicate,false);lab.save(user,duplicate,false);
        assertEquals(2,lab.profile(user).path("elements").path("habits.exercise").path("weight").asInt());
    }
    @Test void invalidMergeAndEmbeddingFailureNeverPartiallySave(){
        UUID user=user(),batch=generated(user,"rollback "+UUID.randomUUID());lab.analyze(user,batch,false);
        when(ai.embed(anyList())).thenThrow(new LabException(502,"embedding failed"));assertThrows(LabException.class,()->lab.save(user,batch,false));
        assertEquals(0,lab.profile(user).path("version").asInt());assertEquals("analyzed",lab.batch(user,batch).path("status").asText());assertTrue(lab.dao.mapper().memories(user).isEmpty());
    }
    @Test void userIsolationAndCurrentContextAnswer() {
        UUID owner=user(),other=user(),batch=generated(owner,"context "+UUID.randomUUID());lab.analyze(owner,batch,false);lab.save(owner,batch,false);
        assertThrows(LabException.class,()->lab.batch(other,batch));assertThrows(LabException.class,()->clone.start(other,batch));
        var session=clone.start(owner,batch);String source=lab.batch(owner,batch).path("turns").get(0).path("id").asText();
        when(ai.respond(anyString(),any(JsonNode.class),any(JsonNode.class),anyList())).thenReturn(response("CONTEXT",false,List.of(source)));
        var answer=clone.answer(owner,UUID.fromString(session.path("sessionId").asText()),"운동하나요?");assertEquals("CONTEXT",answer.path("stage").asText());assertEquals(1,clone.history(owner).path("messages").size());
        var next=clone.start(owner,null);assertNotEquals(session.path("sessionId").asText(),next.path("sessionId").asText());assertThrows(LabException.class,()->clone.answer(owner,UUID.fromString(session.path("sessionId").asText()),"이전 대화?"));
    }
    JsonNode response(String stage,boolean inferred,List<String> ids){var r=json.createObjectNode();r.put("status","completed");var content=r.putArray("output").addObject().put("type","message").putArray("content").addObject().put("type","output_text");var answer=json.createObjectNode();answer.put("answer","근거에 따른 테스트 답변");answer.put("stage",stage);answer.put("inferred",inferred);answer.set("evidenceIds",json.valueToTree(ids));content.put("text",answer.toString());return r;}
    JsonNode call(String name,JsonNode args){var r=json.createObjectNode();r.put("status","completed");r.putArray("output").addObject().put("type","function_call").put("name",name).put("call_id",UUID.randomUUID().toString()).put("arguments",args.toString());return r;}
    @Test void memoryThenProfileToolsAndInsufficientRoute(){
        UUID user=user(),batch=generated(user,"tools "+UUID.randomUUID());lab.analyze(user,batch,false);lab.save(user,batch,false);var session=clone.start(user,null);
        var query=json.createObjectNode().put("query","운동");var codes=json.createObjectNode();codes.putArray("codes").add("habits.exercise");
        when(ai.respond(anyString(),any(JsonNode.class),any(JsonNode.class),anyList())).thenReturn(call("searchLongTermMemory",query),call("getAnalysisProfile",codes),response("PROFILE",true,List.of("habits.exercise")));
        var answer=clone.answer(user,UUID.fromString(session.path("sessionId").asText()),"오늘도 운동할까요?");assertEquals(2,answer.path("trace").size());assertTrue(answer.path("inferred").asBoolean());
        session=clone.start(user,null);String memory=lab.dao.mapper().memories(user).getFirst().get("id").toString();
        when(ai.respond(anyString(),any(JsonNode.class),any(JsonNode.class),anyList())).thenReturn(call("searchLongTermMemory",query),response("MEMORY",false,List.of(memory)));
        assertEquals("MEMORY",clone.answer(user,UUID.fromString(session.path("sessionId").asText()),"무슨 운동?").path("stage").asText());
        session=clone.start(user,null);when(ai.respond(anyString(),any(JsonNode.class),any(JsonNode.class),anyList())).thenReturn(call("searchLongTermMemory",query),response("INSUFFICIENT",false,List.of()));
        assertEquals("INSUFFICIENT",clone.answer(user,UUID.fromString(session.path("sessionId").asText()),"생일은?").path("stage").asText());
    }
    @Test void profileToolCannotBeCalledBeforeMemory(){UUID user=user();var session=clone.start(user,null);var args=json.createObjectNode();args.putArray("codes").add("habits.exercise");when(ai.respond(anyString(),any(JsonNode.class),any(JsonNode.class),anyList())).thenReturn(call("getAnalysisProfile",args));assertThrows(LabException.class,()->clone.answer(user,UUID.fromString(session.path("sessionId").asText()),"질문"));}
    @Test void databaseChecksAndSchemaAreRealPostgres(){assertEquals(11,jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='clone_lab'",Integer.class));UUID user=user();assertThrows(Exception.class,()->jdbc.update("UPDATE clone_lab.profile_relations SET social_orientation=101 WHERE user_id=?",user));}
    @Test void realHttpViewsCatalogAndValidation() throws Exception {
        mvc.perform(get("/lab")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/html"));
        mvc.perform(get("/lab/viewer")).andExpect(status().isOk());
        mvc.perform(get("/lab/assets/schema.sql")).andExpect(status().isNotFound());
        mvc.perform(get("/api/lab/catalog")).andExpect(jsonPath("$.domains.length()").value(7));
        mvc.perform(post("/api/lab/users").contentType("application/json").content("{\"name\":\"\",\"seed\":\"\"}")).andExpect(status().isBadRequest());
    }
    @Test void failedLlmArithmeticDoesNotWriteAnyProfileOrMemory(){
        UUID user=user(),batch=generated(user,"arithmetic "+UUID.randomUUID());lab.analyze(user,batch,false);
        var bad=json.createObjectNode();bad.putArray("updates").addObject().put("code","habits.exercise").put("score",81).put("weight",2);
        when(ai.structured(eq("merge"),anyString(),any(JsonNode.class),any(JsonNode.class))).thenReturn(new LabAi.Result(bad,"test",0,0,0));
        assertThrows(LabException.class,()->lab.save(user,batch,false));assertEquals(0,lab.profile(user).path("version").asInt());assertTrue(lab.dao.mapper().memories(user).isEmpty());
    }
    @Test void databaseFailureRollsBackAllSevenProfilesAndBatch(){
        UUID user=user(),batch=generated(user,"DB rollback "+UUID.randomUUID());lab.analyze(user,batch,false);
        String constraint="lab_test_"+user.toString().replace("-","");
        jdbc.execute("ALTER TABLE clone_lab.memories ADD CONSTRAINT "+constraint+" CHECK(user_id <> '"+user+"'::uuid)");
        try{assertThrows(Exception.class,()->lab.save(user,batch,false));assertEquals(0,lab.profile(user).path("version").asInt());assertEquals("analyzed",lab.batch(user,batch).path("status").asText());assertTrue(lab.profile(user).path("elements").path("habits.exercise").path("score").isNull());}
        finally{jdbc.execute("ALTER TABLE clone_lab.memories DROP CONSTRAINT "+constraint);}
    }
    @Test void simultaneousSavesApplyTheBatchOnlyOnce() throws Exception {
        UUID user=user(),batch=generated(user,"concurrent "+UUID.randomUUID());lab.analyze(user,batch,false);
        var ready=new java.util.concurrent.CountDownLatch(2);
        when(ai.embed(anyList())).thenAnswer(call->{ready.countDown();assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS));List<?> list=call.getArgument(0);return list.stream().map(x->{var v=new float[1536];v[0]=1;return v;}).toList();});
        try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
            var a=executor.submit(()->lab.save(user,batch,false));var b=executor.submit(()->lab.save(user,batch,false));
            assertEquals(1,a.get(15,java.util.concurrent.TimeUnit.SECONDS).path("version").asInt());assertEquals(1,b.get(15,java.util.concurrent.TimeUnit.SECONDS).path("version").asInt());
        }
        assertEquals(1,lab.dao.mapper().memories(user).size());assertEquals(2,lab.profile(user).path("elements").path("habits.exercise").path("weight").asInt());
    }
    @Test void automaticRunProcessesExactly100BatchesAnd1000Pairs() throws Exception {
        UUID user=user();runs.start(user,"자동 테스트 "+UUID.randomUUID(),false);
        long deadline=System.nanoTime()+java.time.Duration.ofSeconds(45).toNanos();JsonNode state;
        do{Thread.sleep(30);state=runs.state(user);}while("running".equals(state.path("status").asText())&&System.nanoTime()<deadline);
        assertEquals("completed",state.path("status").asText(),state.toString());assertEquals(100,state.path("completedBatches").asInt());
        assertEquals(100,lab.dao.mapper().batches(user).size());assertEquals(1000,jdbc.queryForObject("SELECT count(*) FROM clone_lab.turns t JOIN clone_lab.batches b ON b.id=t.batch_id WHERE b.user_id=?",Integer.class,user));
        assertTrue(lab.dao.mapper().batches(user).stream().allMatch(b->"saved".equals(b.get("status"))));
    }
    @Test void failedRunResumesExistingBatchWithoutGeneratingItAgain() throws Exception {
        UUID user=user();when(ai.embed(anyList())).thenThrow(new LabException(502,"temporary failure"));runs.start(user,"resume "+UUID.randomUUID(),false);
        long deadline=System.nanoTime()+java.time.Duration.ofSeconds(10).toNanos();while("running".equals(runs.state(user).path("status").asText())&&System.nanoTime()<deadline)Thread.sleep(20);
        assertEquals("failed",runs.state(user).path("status").asText());assertEquals(1,lab.dao.mapper().batches(user).size());String batch=runs.state(user).path("batchId").asText();
        fixture();runs.start(user,null,true);runs.stop(user);
        deadline=System.nanoTime()+java.time.Duration.ofSeconds(10).toNanos();while(Set.of("running","stopping").contains(runs.state(user).path("status").asText())&&System.nanoTime()<deadline)Thread.sleep(20);
        assertEquals("paused",runs.state(user).path("status").asText());assertEquals(1,lab.dao.mapper().batches(user).size());
        assertTrue(runs.state(user).path("batchId").asText().isEmpty()||batch.equals(runs.state(user).path("batchId").asText()));
    }
}
