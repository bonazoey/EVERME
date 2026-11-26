package com.example.everme.lab;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
@Profile("clone-lab")
public class LabDao {
    private final LabMapper mapper;
    private final ObjectMapper json;
    private final LabCatalog catalog;
    public LabDao(LabMapper mapper,ObjectMapper json,LabCatalog catalog){this.mapper=mapper;this.json=json;this.catalog=catalog;}
    public LabMapper mapper(){return mapper;}
    public JsonNode parse(Object value){if(value==null)return json.nullNode();try{return json.readTree(value.toString());}catch(Exception e){throw new IllegalStateException("Invalid lab JSON",e);}}
    public Map<String,Object> requireUser(UUID user){var row=mapper.user(user);if(row==null)throw new LabException(404,"테스트 사용자를 찾을 수 없습니다.");return row;}
    public Map<String,Object> requireBatch(UUID user,UUID batch){var row=mapper.batch(user,batch);if(row==null)throw new LabException(404,"대화 배치를 찾을 수 없습니다.");return row;}
    public ObjectNode profile(UUID user){
        var profile=json.createObjectNode();
        for(var d:catalog.domains().entrySet()){
            var row=parse(mapper.profile(d.getKey(),user));
            for(var code:d.getValue()){
                var point=json.createObjectNode();point.set("score",row.path(catalog.column(code)));var meta=row.path("metadata").path(code);
                point.put("weight",meta.path("weight").asInt(0));point.set("sourceIds",meta.path("sourceIds").isArray()?meta.path("sourceIds"):json.createArrayNode());point.put("reason",meta.path("reason").asText(""));
                profile.set(code,point);
            }
        }return profile;
    }
    public void updateProfile(UUID user,JsonNode profile){
        for(var d:catalog.domains().entrySet()){
            var scores=new LinkedHashMap<String,Object>();var metadata=json.createObjectNode();
            for(var code:d.getValue()){var point=profile.path(code);scores.put(catalog.column(code),point.path("score").isNumber()?point.path("score").decimalValue():null);metadata.set(code,point);}
            mapper.profileUpdate(d.getKey(),user,scores,metadata.toString());
        }
    }
    public static String vector(float[] vector){return Arrays.toString(vector);}
}
