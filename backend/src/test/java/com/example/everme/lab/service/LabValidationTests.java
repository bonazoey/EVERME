package com.example.everme.lab.service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.everme.lab.dao.LabMapper;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.*;

class LabValidationTests {
  private final ObjectMapper json = new ObjectMapper();
  private LabCatalog catalog;
  private LabValidation validation;

  @BeforeEach
  void setup() throws Exception {
    catalog = new LabCatalog(json);
    validation = new LabValidation(catalog);
  }

  @Test
  void catalogHas140DistinctElementsAndExplicitAnchors() {
    assertEquals(140, catalog.codes().size());
    assertEquals(7, catalog.domains().size());
    for (var code : catalog.codes()) {
      var e = catalog.element(code);
      assertEquals(5, e.path("anchors").size());
      assertFalse(e.path("boundary").asText().isBlank());
    }
    assertTrue(
        catalog
            .element("relations.social_orientation")
            .path("anchors")
            .path("0")
            .asText()
            .contains("내향"));
    assertTrue(
        catalog
            .element("relations.social_orientation")
            .path("anchors")
            .path("100")
            .asText()
            .contains("외향"));
  }

  @Test
  void rejectsOutOfRangeAndNonNumericScores() {
    for (var node :
        List.of(
            json.valueToTree(-1),
            json.valueToTree(101),
            json.valueToTree("50"),
            json.valueToTree(50.001),
            json.nullNode())) assertThrows(LabException.class, () -> LabValidation.score(node));
    assertEquals(50, LabValidation.score(json.valueToTree(50)).intValue());
  }

  @Test
  void compactCatalogPreservesEveryCodeAnchorAndExample() {
    var compact = catalog.analysisDocument();
    var codes = new HashSet<String>();
    for (var domain : compact.path("domains")) {
      assertFalse(domain.path("exclude").asText().isBlank());
      assertFalse(domain.path("counterExample").asText().isBlank());
      for (var element : domain.path("elements")) {
        String code = element.path("code").asText();
        assertTrue(codes.add(code));
        assertFalse(element.has("anchors"));
        assertEquals(
            catalog.element(code).path("anchors"),
            compact.path("anchorDefinitions").path(element.path("anchorRef").asText()));
        assertEquals(
            catalog.element(code).path("positiveExample"), element.path("positiveExample"));
        assertFalse(element.has("updatePolicy"));
        assertFalse(element.has("column"));
      }
    }
    var uniqueAnchors = new HashSet<JsonNode>();
    for (var code : catalog.codes()) uniqueAnchors.add(catalog.element(code).path("anchors"));
    assertEquals(uniqueAnchors.size(), compact.path("anchorDefinitions").size());
    var expanded = compact.deepCopy();
    ((ObjectNode) expanded).remove(List.of("anchorRule", "anchorDefinitions"));
    for (var domain : expanded.path("domains"))
      for (var element : domain.path("elements")) {
        var item = (ObjectNode) element;
        item.set(
            "anchors", compact.path("anchorDefinitions").path(item.path("anchorRef").asText()));
        item.remove("anchorRef");
      }
    assertTrue(compact.toString().length() < expanded.toString().length());
    System.out.printf(
        "Analysis catalog: before=%d after=%d anchorDefinitions=%d%n",
        expanded.toString().length(), compact.toString().length(), uniqueAnchors.size());
    assertEquals(catalog.codes(), codes);
    assertTrue(compact.toString().length() < catalog.document().toString().length() / 2);
  }

  @Test
  void catalogRejectsMissingInvalidDuplicateAndUnknownPolicies() {
    for (int mode = 0; mode < 5; mode++) {
      var doc = catalog.document();
      var elements = doc.path("domains").get(0).path("elements");
      var first = (ObjectNode) elements.get(0);
      switch (mode) {
        case 0 -> first.remove("updatePolicy");
        case 1 -> first.withObject("/updatePolicy").put("maxWeight", 15);
        case 2 -> first.put("code", elements.get(1).path("code").asText());
        case 3 -> ((com.fasterxml.jackson.databind.node.ArrayNode) elements).remove(0);
        case 4 -> ((ObjectNode) doc).put("updatePolicyVersion", "unknown");
      }
      assertThrows(IllegalStateException.class, () -> new LabCatalog(doc));
    }
  }

  @Test
  void allElementsUseTheirOwnCapsAndRetainLowExistingWeights() {
    assertEquals("element-cap-v1", catalog.policyVersion());
    var counts = new HashMap<Integer, Integer>();
    for (String code : catalog.codes()) {
      int cap = catalog.maxWeight(code);
      counts.merge(cap, 1, Integer::sum);
      assertFalse(catalog.element(code).path("updatePolicy").path("rationale").asText().isBlank());
      ObjectNode old = json.createObjectNode();
      old.putObject(code).put("score", 40).put("weight", 200);
      var observations = json.createArrayNode();
      observations
          .addObject()
          .put("code", code)
          .put("score", 60)
          .put("strength", 2)
          .put("reason", "evidence")
          .putArray("sourceIds")
          .add("a");
      String expected =
          switch (cap) {
            case 10 -> "43.33";
            case 20 -> "41.82";
            case 40 -> "40.95";
            default -> throw new AssertionError();
          };
      var result = json.createObjectNode();
      result
          .putArray("updates")
          .addObject()
          .put("code", code)
          .put("score", new java.math.BigDecimal(expected))
          .put("weight", cap);
      var point = validation.merge(old, observations).path(code);
      assertEquals(expected, point.path("score").asText());
      assertEquals(cap, point.path("weight").asInt());
    }
    assertEquals(Map.of(10, 38, 20, 48, 40, 54), counts);
    ObjectNode old = json.createObjectNode();
    old.putObject("values.family").put("score", 40).put("weight", 20);
    var observations = json.createArrayNode();
    observations
        .addObject()
        .put("code", "values.family")
        .put("score", 60)
        .put("strength", 2)
        .put("reason", "evidence")
        .putArray("sourceIds")
        .add("a");
    var result = json.createObjectNode();
    result
        .putArray("updates")
        .addObject()
        .put("code", "values.family")
        .put("score", 41.82)
        .put("weight", 22);
    assertEquals(
        22, validation.merge(old, observations).path("values.family").path("weight").asInt());
  }

  @Test
  void analysisRejectsAssistantOrForgedSourcesAndDuplicateDimensions() throws Exception {
    var analysis =
        json.readTree(
            """
{"observations":[{"code":"habits.exercise","score":80,"strength":2,"sourceIds":["assistant-id"],"reason":"운동한다"}],"memories":[]}
""");
    assertThrows(LabException.class, () -> validation.analysis(analysis, Set.of("owner-id")));
    ((ObjectNode) analysis.path("observations").get(0)).putArray("sourceIds").add("owner-id");
    validation.analysis(analysis, Set.of("owner-id"));
    ((com.fasterxml.jackson.databind.node.ArrayNode) analysis.path("observations"))
        .add(analysis.path("observations").get(0).deepCopy());
    assertThrows(LabException.class, () -> validation.analysis(analysis, Set.of("owner-id")));
  }

  @Test
  void mergeChecksLlmArithmeticAndPreservesUnknowns() throws Exception {
    ObjectNode old =
        (ObjectNode)
            json.readTree(
                """
{"habits.exercise":{"score":20,"weight":2},"values.family":{"score":null,"weight":0},"interests.music":{"score":50,"weight":2}}
""");
    var observations =
        json.readTree(
            """
[{"code":"habits.exercise","score":80,"strength":1,"sourceIds":["a"],"reason":"actual practice"}]
""");
    var valid =
        json.readTree(
            """
            {"updates":[{"code":"habits.exercise","score":40,"weight":3}]}
            """);
    var merged = validation.merge(old, observations);
    assertEquals(40, merged.path("habits.exercise").path("score").asInt());
    assertTrue(merged.path("values.family").path("score").isNull());
    assertEquals(50, merged.path("interests.music").path("score").asInt());
  }

  @Test
  void capsLegacyWeightAndRejectsUncappedOrSimpleAverageResults() throws Exception {
    ObjectNode old =
        (ObjectNode) json.readTree("{\"habits.plan_execution\":{\"score\":40,\"weight\":200}}");
    var observations =
        json.readTree(
            "[{\"code\":\"habits.plan_execution\",\"score\":60,\"strength\":2,\"sourceIds\":[\"a\"],\"reason\":\"new"
                + " evidence\"}]");
    var result =
        json.readTree(
            "{\"updates\":[{\"code\":\"habits.plan_execution\",\"score\":41.82,\"weight\":20}]}");
    var merged = validation.merge(old, observations);
    assertEquals("41.82", merged.path("habits.plan_execution").path("score").asText());
    assertEquals(20, merged.path("habits.plan_execution").path("weight").asInt());
  }

  @Test
  void crossingCapUsesUncappedDenominatorAndNullHasNoPriorWeight() throws Exception {
    ObjectNode old =
        (ObjectNode) json.readTree("{\"habits.plan_execution\":{\"score\":40,\"weight\":19}}");
    var observations =
        json.readTree(
            "[{\"code\":\"habits.plan_execution\",\"score\":60,\"strength\":3,\"sourceIds\":[\"a\"],\"reason\":\"new"
                + " evidence\"}]");
    var result =
        json.readTree(
            "{\"updates\":[{\"code\":\"habits.plan_execution\",\"score\":42.73,\"weight\":20}]}");
    assertEquals(
        "42.73",
        validation.merge(old, observations).path("habits.plan_execution").path("score").asText());
    old.withObject("/habits.plan_execution").putNull("score").put("weight", 200);
    ((ObjectNode) result.path("updates").get(0)).put("score", 60).put("weight", 3);
    assertEquals(
        3,
        validation.merge(old, observations).path("habits.plan_execution").path("weight").asInt());
  }

  @Test
  void refusesProfileIdentifierInjection() {
    assertThrows(
        IllegalArgumentException.class,
        () -> LabMapper.ProfileSql.read(Map.of("table", "emotion; DROP TABLE accounts")));
  }
}
