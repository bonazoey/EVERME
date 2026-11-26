package com.example.everme.lab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.*;

/** One server process; checkpoints live on the user, not in an extra jobs table. */
@Service
@Profile("clone-lab")
public class LabRunService {
    private final LabService lab;
    private final Set<UUID> active=ConcurrentHashMap.newKeySet();
    private final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
    public LabRunService(LabService lab){this.lab=lab;}
    public void recoverInterrupted(){for(var user:lab.dao.mapper().interruptedUsers())lab.tx.executeWithoutResult(s->{var row=lab.dao.mapper().lockUser(user);var state=(ObjectNode)lab.dao.parse(row.get("run_state"));state.put("status","paused");state.put("error","서버가 재시작되었습니다. 재개하면 현재 배치부터 이어집니다.");lab.dao.mapper().runState(user,state.toString());});}
    public JsonNode state(UUID user){return lab.dao.parse(lab.dao.requireUser(user).get("run_state"));}
    public synchronized JsonNode start(UUID user,String topic,boolean resume){
        if(active.contains(user))throw LabException.conflict("이미 자동 실행 중입니다.");
        lab.tx.executeWithoutResult(s->{var row=lab.dao.mapper().lockUser(user);if(row==null)throw new LabException(404,"테스트 사용자를 찾을 수 없습니다.");var old=lab.dao.parse(row.get("run_state"));ObjectNode state;
            if(Set.of("running","stopping").contains(old.path("status").asText()))throw LabException.conflict("이미 자동 실행 중입니다.");
            if(resume){if(!Set.of("paused","failed").contains(old.path("status").asText()))throw LabException.conflict("재개할 실행이 없습니다.");state=(ObjectNode)old.deepCopy();}
            else {if(Set.of("paused","failed").contains(old.path("status").asText()))throw LabException.conflict("이전 실행을 먼저 재개해 주세요. 새 실험은 다른 테스트 사용자를 만드세요.");LabValidation.text(topic,1000,"주제");state=lab.object();state.put("runId",UUID.randomUUID().toString());state.put("topic",topic);state.put("targetPairs",1000);state.put("completedBatches",0);state.put("inputTokens",0);state.put("outputTokens",0);state.put("elapsedMs",0);}
            state.put("status","running");state.putNull("error");lab.dao.mapper().runState(user,state.toString());});
        active.add(user);executor.submit(()->execute(user));return state(user);
    }
    public JsonNode stop(UUID user){lab.tx.executeWithoutResult(s->{var row=lab.dao.mapper().lockUser(user);if(row==null)throw new LabException(404,"사용자를 찾을 수 없습니다.");var state=(ObjectNode)lab.dao.parse(row.get("run_state"));if("running".equals(state.path("status").asText()))state.put("status","stopping");lab.dao.mapper().runState(user,state.toString());});return state(user);}
    private void execute(UUID user){
        try {
            while(!Thread.currentThread().isInterrupted()){
                var state=state(user);if("stopping".equals(state.path("status").asText())){finish(user,"paused",null);return;}
                if(state.path("completedBatches").asInt()>=100){finish(user,"completed",null);return;}
                UUID batch=state.path("batchId").isTextual()?UUID.fromString(state.path("batchId").asText()):null;
                if(batch==null)batch=UUID.fromString(lab.generate(user,state.path("topic").asText(),true).path("id").asText());
                var current=lab.batch(user,batch);if("generated".equals(current.path("status").asText()))current=lab.analyze(user,batch,true);
                JsonNode saved="saved".equals(current.path("status").asText())?null:lab.save(user,batch,true);
                var analyzed=current;var done=saved;lab.tx.executeWithoutResult(s->{var row=lab.dao.mapper().lockUser(user);var checkpoint=(ObjectNode)lab.dao.parse(row.get("run_state"));checkpoint.put("completedBatches",checkpoint.path("completedBatches").asInt()+1);checkpoint.put("completedPairs",checkpoint.path("completedBatches").asInt()*10);
                    for(var metric:List.of("inputTokens","outputTokens","elapsedMs")){long total=checkpoint.path(metric).asLong()+checkpoint.path("generationMetrics").path(metric).asLong()+analyzed.path("analysis").path("metrics").path(metric).asLong()+(done==null?analyzed.path("analysis").path("mergeMetrics").path(metric).asLong():done.path("metrics").path(metric).asLong());checkpoint.put(metric,total);}
                    checkpoint.remove(List.of("batchId","generationMetrics"));lab.dao.mapper().runState(user,checkpoint.toString());});
            }
            finish(user,"paused","실행이 중단되었습니다.");
        }catch(Exception e){finish(user,"failed",e instanceof LabException?e.getMessage():"자동 실행에 실패했습니다. 서버 로그와 설정을 확인한 후 재개해 주세요.");}
        finally{active.remove(user);}
    }
    private void finish(UUID user,String status,String error){lab.tx.executeWithoutResult(s->{var row=lab.dao.mapper().lockUser(user);var state=(ObjectNode)lab.dao.parse(row.get("run_state"));state.put("status",status);if(error==null)state.putNull("error");else state.put("error",error);lab.dao.mapper().runState(user,state.toString());});}
    @PreDestroy public void close(){executor.shutdownNow();}
}
