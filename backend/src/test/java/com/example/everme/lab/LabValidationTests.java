package com.example.everme.lab;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LabValidationTests {
    private final ObjectMapper json=new ObjectMapper();
    private LabCatalog catalog;
    private LabValidation validation;
    @BeforeEach void setup() throws Exception {catalog=new LabCatalog(json);validation=new LabValidation(catalog);}
    @Test void catalogHas140DistinctElementsAndExplicitAnchors(){
        assertEquals(140,catalog.codes().size());assertEquals(7,catalog.domains().size());
        for(var code:catalog.codes()){var e=catalog.element(code);assertEquals(5,e.path("anchors").size());assertFalse(e.path("boundary").asText().isBlank());}
        assertTrue(catalog.element("relations.social_orientation").path("anchors").path("0").asText().contains("내향"));
        assertTrue(catalog.element("relations.social_orientation").path("anchors").path("100").asText().contains("외향"));
    }
    @Test void rejectsOutOfRangeAndNonNumericScores(){
        for(var node:List.of(json.valueToTree(-1),json.valueToTree(101),json.valueToTree("50"),json.valueToTree(50.001),json.nullNode()))assertThrows(LabException.class,()->LabValidation.score(node));
        assertEquals(50,LabValidation.score(json.valueToTree(50)).intValue());
    }
    @Test void analysisRejectsAssistantOrForgedSourcesAndDuplicateDimensions() throws Exception {
        var analysis=json.readTree("""
          {"observations":[{"code":"habits.exercise","score":80,"strength":2,"sourceIds":["assistant-id"],"reason":"운동한다"}],"memories":[]}
          """);
        assertThrows(LabException.class,()->validation.analysis(analysis,Set.of("owner-id")));
        ((ObjectNode)analysis.path("observations").get(0)).putArray("sourceIds").add("owner-id");
        validation.analysis(analysis,Set.of("owner-id"));
        ((com.fasterxml.jackson.databind.node.ArrayNode)analysis.path("observations")).add(analysis.path("observations").get(0).deepCopy());
        assertThrows(LabException.class,()->validation.analysis(analysis,Set.of("owner-id")));
    }
    @Test void mergeChecksLlmArithmeticAndPreservesUnknowns() throws Exception {
        ObjectNode old=(ObjectNode)json.readTree("""
          {"habits.exercise":{"score":20,"weight":2},"values.family":{"score":null,"weight":0},"interests.music":{"score":50,"weight":2}}
          """);
        var observations=json.readTree("""
          [{"code":"habits.exercise","score":80,"strength":1,"sourceIds":["a"],"reason":"actual practice"}]
          """);
        var valid=json.readTree("""
          {"updates":[{"code":"habits.exercise","score":40,"weight":3}]}
          """);
        var merged=validation.merge(old,observations,valid);assertEquals(40,merged.path("habits.exercise").path("score").asInt());
        assertTrue(merged.path("values.family").path("score").isNull());assertEquals(50,merged.path("interests.music").path("score").asInt());
        ((ObjectNode)valid.path("updates").get(0)).put("score",41);assertThrows(LabException.class,()->validation.merge(old,observations,valid));
    }
    @Test void refusesProfileIdentifierInjection(){assertThrows(IllegalArgumentException.class,()->LabMapper.ProfileSql.read(Map.of("table","emotion; DROP TABLE accounts")));}
}
