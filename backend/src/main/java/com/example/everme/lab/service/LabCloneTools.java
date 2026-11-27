package com.example.everme.lab.service;

import com.example.everme.lab.dao.LabDao;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.Function;

/** Tool callbacks capture the server-selected owner; tool input cannot change that owner. */
@Component
@Profile("clone-lab")
public class LabCloneTools {
    private final LabService lab;
    private final LabAi ai;
    private final LabCatalog catalog;
    private final LabSchemas schemas;
    public LabCloneTools(LabService lab,LabAi ai,LabCatalog catalog,LabSchemas schemas){this.lab=lab;this.ai=ai;this.catalog=catalog;this.schemas=schemas;}

    public ToolCallback memory(UUID owner) {
        return callback(schemas.tool("searchLongTermMemory","현재 컨텍스트의 근거가 부족할 때 소유자 장기 기억 검색",Map.of("query",schemas.string())),args->{
            String query=args.path("query").asText();LabValidation.text(query,1000,"검색어");
            var vector=ai.embed(List.of(query)).getFirst();var found=new LinkedHashMap<String,Map<String,Object>>();
            for(var item:lab.dao.mapper().search(owner,LabDao.vector(vector)))found.put(item.get("id").toString(),item);
            for(var item:lab.dao.mapper().keyword(owner,query))found.putIfAbsent(item.get("id").toString(),item);
            var items=lab.json.createArrayNode();
            for(var memory:found.values()) {
                var item=lab.object();item.put("id",memory.get("id").toString());item.put("kind",memory.get("kind").toString());item.put("content",memory.get("content").toString());
                item.set("sourceIds",lab.dao.parse(memory.get("source_ids")));if(memory.get("occurred_at")==null)item.putNull("occurredAt");else item.put("occurredAt",memory.get("occurred_at").toString());items.add(item);
            }
            return items;
        });
    }
    public ToolCallback profile(UUID owner) {
        return callback(schemas.tool("getAnalysisProfile","기억으로 판단 불가능한 성향·선택을 추정할 때 관련 요소 조회",Map.of("codes",schemas.array(schemas.enumeration(catalog.codes())))),args->{
            if(!args.path("codes").isArray()||args.path("codes").isEmpty()||args.path("codes").size()>20)throw LabException.invalid("관련 프로필 요소는 1~20개만 조회할 수 있습니다.");
            var profile=lab.profile(owner).path("elements");var points=lab.object();
            for(var codeNode:args.path("codes")){String code=codeNode.asText();var definition=catalog.element(code);var point=profile.path(code);if(point.path("score").isNumber()){var detail=lab.object();detail.set("definition",definition);detail.set("value",point);points.set(code,detail);}}
            return points;
        });
    }
    public JsonNode descriptor(ToolCallback callback) {
        var definition=callback.getToolDefinition();var descriptor=lab.object();descriptor.put("type","function");descriptor.put("name",definition.name());descriptor.put("description",definition.description());descriptor.put("strict",true);
        try{descriptor.set("parameters",lab.json.readTree(definition.inputSchema()));return descriptor;}catch(Exception failure){throw new IllegalStateException("Invalid tool schema",failure);}
    }
    private ToolCallback callback(JsonNode schema,Function<JsonNode,JsonNode> operation) {
        var definition=ToolDefinition.builder().name(schema.path("name").asText()).description(schema.path("description").asText()).inputSchema(schema.path("parameters").toString()).build();
        return new ToolCallback() {
            public ToolDefinition getToolDefinition(){return definition;}
            public String call(String input) {
                JsonNode args;try{args=lab.json.readTree(input);}catch(Exception failure){throw LabException.invalid("도구 인자 JSON 오류입니다.");}
                if(args==null||!args.isObject())throw LabException.invalid("도구 인자는 객체여야 합니다.");
                var fields=args.fieldNames();while(fields.hasNext())if(!schema.path("parameters").path("properties").has(fields.next()))throw LabException.invalid("허용되지 않은 도구 인자입니다.");
                return operation.apply(args).toString();
            }
        };
    }
}
