package com.example.everme.lab.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.math.*;
import java.util.*;

@Component
@Profile("clone-lab")
public class LabValidation {
    private final LabCatalog catalog;
    public LabValidation(LabCatalog catalog){this.catalog=catalog;}
    public static void text(String text,int max,String name){if(text==null||text.isBlank()||text.length()>max)throw LabException.bad(name+"을 확인해 주세요.");}
    public static BigDecimal score(JsonNode value){if(!value.isNumber()||value.decimalValue().compareTo(BigDecimal.ZERO)<0||value.decimalValue().compareTo(new BigDecimal("100"))>0||value.decimalValue().stripTrailingZeros().scale()>2)throw LabException.invalid("점수는 소수 둘째 자리 이내의 0~100이어야 합니다.");return value.decimalValue();}
    private static void sources(JsonNode ids,Set<String> allowed){
        if(!ids.isArray()||ids.isEmpty())throw LabException.invalid("근거 발언이 필요합니다.");
        var used=new HashSet<String>();for(var id:ids)if(!id.isTextual()||!allowed.contains(id.asText())||!used.add(id.asText()))throw LabException.invalid("유효하지 않거나 중복된 사용자 근거입니다.");
    }
    public void analysis(JsonNode data,Set<String> allowed){
        analysis(data,allowed,40);
    }
    public void analysis(JsonNode data,Set<String> allowed,int maxMemories){
        if(!data.path("observations").isArray()||!data.path("memories").isArray())throw LabException.invalid("분석 응답 형식 오류입니다.");
        if(data.path("observations").size()>140||data.path("memories").size()>maxMemories)throw LabException.invalid("분석 결과가 너무 많습니다.");
        var seen=new HashSet<String>();
        for(var point:data.path("observations")){
            String code=point.path("code").asText();catalog.element(code);if(!seen.add(code))throw LabException.invalid("분석 요소가 중복되었습니다.");
            score(point.path("score"));var weight=point.path("strength");if(!weight.isIntegralNumber()||weight.asInt()<1||weight.asInt()>3)throw LabException.invalid("근거 강도 오류입니다.");
            sources(point.path("sourceIds"),allowed);if(point.path("reason").asText().isBlank()||point.path("reason").asText().length()>2000)throw LabException.invalid("분석 근거 설명 오류입니다.");
        }
        for(var memory:data.path("memories")){
            if(!Set.of("FACT","EXPERIENCE","CONDITIONAL_PREFERENCE").contains(memory.path("kind").asText()))throw LabException.invalid("기억 유형 오류입니다.");
            sources(memory.path("sourceIds"),allowed);if(memory.path("content").asText().isBlank()||memory.path("content").asText().length()>3000)throw LabException.invalid("기억 내용 오류입니다.");
        }
    }
    /** Deterministic server-side accumulation, with per-element caps. */
    public ObjectNode merge(ObjectNode old,JsonNode observations){
        var next=old.deepCopy();var seen=new HashSet<String>();
        for(var observation:observations){
            String code=observation.path("code").asText();int cap=catalog.maxWeight(code);
            if(!seen.add(code))throw LabException.invalid("Duplicate merge element");
            var previous=old.path(code);int weight=previous.path("score").isNull()?0:Math.min(previous.path("weight").asInt(),cap);
            int addition=observation.path("strength").asInt();if(addition<1||addition>3)throw LabException.invalid("Invalid evidence strength");
            int total=Math.addExact(weight,addition);var observed=score(observation.path("score"));
            var numerator=previous.path("score").isNull()?observed.multiply(BigDecimal.valueOf(addition)):score(previous.path("score")).multiply(BigDecimal.valueOf(weight)).add(observed.multiply(BigDecimal.valueOf(addition)));
            var point=next.withObject("/"+code);point.put("score",numerator.divide(BigDecimal.valueOf(total),2,RoundingMode.HALF_UP));point.put("weight",Math.min(total,cap));point.set("sourceIds",observation.path("sourceIds"));point.put("reason",observation.path("reason").asText());
        }return next;
    }
}
