package com.example.everme.lab.service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.everme.lab.dao.LabMapper;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.*;

class LabValidationTests {
  private final ObjectMapper json = new ObjectMapper();
  private LabCatalog catalog;
  private LabValidation validation;

  @BeforeEach
  void setup() throws Exception {
    catalog = LabCatalog.load(json);
    validation = new LabValidation(catalog);
  }

  private Map<String, Object> readMap(String text) {
    return LabJson.readMap(json, text);
  }

  private List<Object> readList(String text) {
    return LabJson.list(LabJson.read(json, text));
  }

  private int length(Object value) {
    return LabJson.write(json, value).length();
  }

  private static Map<String, Object> observation(String code, int score, int strength) {
    Map<String, Object> item = LabJson.object();
    item.put("code", code);
    item.put("score", score);
    item.put("strength", strength);
    item.put("reason", "evidence");
    item.put("sourceIds", new ArrayList<Object>(List.of("a")));
    return item;
  }

  @Test
  void profileToolDescriptorLimitsCodeCountToOneThroughTwenty() throws Exception {
    LabCloneTools tools = new LabCloneTools(null, catalog, new LabSchemas(), null);
    Object codes =
        LabJson.path(
            readMap(tools.profile(1L).getToolDefinition().inputSchema()), "properties", "codes");
    assertEquals(1, LabJson.integer(LabJson.path(codes, "minItems")));
    assertEquals(20, LabJson.integer(LabJson.path(codes, "maxItems")));
    assertEquals(140, LabJson.list(LabJson.path(codes, "items", "enum")).size());
  }

  @Test
  void catalogHas140DistinctElementsAndExplicitAnchors() {
    assertEquals(140, catalog.codes().size());
    assertEquals(7, catalog.domains().size());
    for (String code : catalog.codes()) {
      Map<String, Object> e = catalog.element(code);
      assertEquals(5, LabJson.map(e.get("anchors")).size());
      assertFalse(LabJson.text(e.get("boundary")).isBlank());
    }
    assertTrue(
        LabJson.text(
                LabJson.path(catalog.element("relations.social_orientation"), "anchors", "0"))
            .contains("내향"));
    assertTrue(
        LabJson.text(
                LabJson.path(catalog.element("relations.social_orientation"), "anchors", "100"))
            .contains("외향"));
  }

  @Test
  void rejectsOutOfRangeAndNonNumericScores() {
    for (Object value : Arrays.asList(-1, 101, "50", 50.001, null))
      assertThrows(LabException.class, () -> LabValidation.score(value));
    assertEquals(50, LabValidation.score(50).intValue());
  }

  @Test
  void compactCatalogPreservesEveryCodeAnchorAndExample() {
    Map<String, Object> compact = catalog.analysisDocument();
    Set<String> codes = new HashSet<String>();
    Map<String, Object> anchorDefinitions = LabJson.map(compact.get("anchorDefinitions"));
    for (Object domainValue : LabJson.list(compact.get("domains"))) {
      Map<String, Object> domain = LabJson.map(domainValue);
      assertFalse(LabJson.text(domain.get("exclude")).isBlank());
      assertFalse(LabJson.text(domain.get("counterExample")).isBlank());
      for (Object elementValue : LabJson.list(domain.get("elements"))) {
        Map<String, Object> element = LabJson.map(elementValue);
        String code = LabJson.text(element.get("code"));
        assertTrue(codes.add(code));
        assertFalse(element.containsKey("anchors"));
        assertEquals(
            catalog.element(code).get("anchors"),
            anchorDefinitions.get(LabJson.text(element.get("anchorRef"))));
        assertEquals(catalog.element(code).get("positiveExample"), element.get("positiveExample"));
        assertFalse(element.containsKey("updatePolicy"));
        assertFalse(element.containsKey("column"));
      }
    }
    Set<Object> uniqueAnchors = new HashSet<Object>();
    for (String code : catalog.codes()) uniqueAnchors.add(catalog.element(code).get("anchors"));
    assertEquals(uniqueAnchors.size(), anchorDefinitions.size());
    Map<String, Object> expanded = LabJson.copy(compact);
    expanded.remove("anchorRule");
    expanded.remove("anchorDefinitions");
    for (Object domain : LabJson.list(expanded.get("domains")))
      for (Object element : LabJson.list(LabJson.path(domain, "elements"))) {
        Map<String, Object> item = LabJson.map(element);
        item.put("anchors", anchorDefinitions.get(LabJson.text(item.get("anchorRef"))));
        item.remove("anchorRef");
      }
    assertTrue(length(compact) < length(expanded));
    System.out.printf(
        "Analysis catalog: before=%d after=%d anchorDefinitions=%d%n",
        length(expanded), length(compact), uniqueAnchors.size());
    assertEquals(catalog.codes(), codes);
    assertTrue(length(compact) < length(catalog.document()) / 2);
  }

  @Test
  void catalogRejectsMissingInvalidDuplicateAndUnknownPolicies() {
    for (int mode = 0; mode < 5; mode++) {
      Map<String, Object> doc = catalog.document();
      List<Object> elements = LabJson.list(LabJson.path(LabJson.list(doc.get("domains")).get(0), "elements"));
      Map<String, Object> first = LabJson.map(elements.get(0));
      switch (mode) {
        case 0 -> first.remove("updatePolicy");
        case 1 -> LabJson.child(first, "updatePolicy").put("maxWeight", 15);
        case 2 -> first.put("code", LabJson.text(LabJson.path(elements.get(1), "code")));
        case 3 -> elements.remove(0);
        case 4 -> doc.put("updatePolicyVersion", "unknown");
      }
      assertThrows(IllegalStateException.class, () -> new LabCatalog(doc));
    }
  }

  @Test
  void allElementsUseTheirOwnCapsAndRetainLowExistingWeights() {
    assertEquals("element-cap-v1", catalog.policyVersion());
    Map<Integer, Integer> counts = new HashMap<Integer, Integer>();
    for (String code : catalog.codes()) {
      int cap = catalog.maxWeight(code);
      counts.merge(cap, 1, Integer::sum);
      assertFalse(
          LabJson.text(LabJson.path(catalog.element(code), "updatePolicy", "rationale")).isBlank());
      Map<String, Object> old = LabJson.object();
      Map<String, Object> point = LabJson.child(old, code);
      point.put("score", 40);
      point.put("weight", 200);
      List<Object> observations = new ArrayList<Object>(List.of(observation(code, 60, 2)));
      String expected =
          switch (cap) {
            case 10 -> "43.33";
            case 20 -> "41.82";
            case 40 -> "40.95";
            default -> throw new AssertionError();
          };
      Map<String, Object> merged = LabJson.map(validation.merge(old, observations).get(code));
      assertEquals(expected, LabJson.text(merged.get("score")));
      assertEquals(cap, LabJson.integer(merged.get("weight")));
    }
    assertEquals(Map.of(10, 38, 20, 48, 40, 54), counts);
    Map<String, Object> old = LabJson.object();
    Map<String, Object> family = LabJson.child(old, "values.family");
    family.put("score", 40);
    family.put("weight", 20);
    List<Object> observations =
        new ArrayList<Object>(List.of(observation("values.family", 60, 2)));
    assertEquals(
        22,
        LabJson.integer(
            LabJson.path(validation.merge(old, observations), "values.family", "weight")));
  }

  @Test
  void analysisRejectsAssistantOrForgedSourcesAndDuplicateDimensions() throws Exception {
    Map<String, Object> analysis =
        readMap(
            """
{"observations":[{"code":"habits.exercise","score":80,"strength":2,"sourceIds":["assistant-id"],"reason":"운동한다"}],"memories":[]}
""");
    assertThrows(LabException.class, () -> validation.analysis(analysis, Set.of("owner-id")));
    List<Object> observations = LabJson.list(analysis.get("observations"));
    LabJson.map(observations.get(0)).put("sourceIds", new ArrayList<Object>(List.of("owner-id")));
    validation.analysis(analysis, Set.of("owner-id"));
    observations.add(LabJson.copy(observations.get(0)));
    assertThrows(LabException.class, () -> validation.analysis(analysis, Set.of("owner-id")));
  }

  @Test
  void mergeChecksLlmArithmeticAndPreservesUnknowns() throws Exception {
    Map<String, Object> old =
        readMap(
            """
{"habits.exercise":{"score":20,"weight":2},"values.family":{"score":null,"weight":0},"interests.music":{"score":50,"weight":2}}
""");
    List<Object> observations =
        readList(
            """
[{"code":"habits.exercise","score":80,"strength":1,"sourceIds":["a"],"reason":"actual practice"}]
""");
    Map<String, Object> merged = validation.merge(old, observations);
    assertEquals(40, LabJson.integer(LabJson.path(merged, "habits.exercise", "score")));
    assertNull(LabJson.path(merged, "values.family", "score"));
    assertTrue(LabJson.map(merged.get("values.family")).containsKey("score"));
    assertEquals(50, LabJson.integer(LabJson.path(merged, "interests.music", "score")));
  }

  @Test
  void capsLegacyWeightAndRejectsUncappedOrSimpleAverageResults() throws Exception {
    Map<String, Object> old = readMap("{\"habits.plan_execution\":{\"score\":40,\"weight\":200}}");
    List<Object> observations =
        readList(
            "[{\"code\":\"habits.plan_execution\",\"score\":60,\"strength\":2,\"sourceIds\":[\"a\"],\"reason\":\"new"
                + " evidence\"}]");
    Map<String, Object> merged = validation.merge(old, observations);
    assertEquals("41.82", LabJson.text(LabJson.path(merged, "habits.plan_execution", "score")));
    assertEquals(20, LabJson.integer(LabJson.path(merged, "habits.plan_execution", "weight")));
  }

  @Test
  void crossingCapUsesUncappedDenominatorAndNullHasNoPriorWeight() throws Exception {
    Map<String, Object> old = readMap("{\"habits.plan_execution\":{\"score\":40,\"weight\":19}}");
    List<Object> observations =
        readList(
            "[{\"code\":\"habits.plan_execution\",\"score\":60,\"strength\":3,\"sourceIds\":[\"a\"],\"reason\":\"new"
                + " evidence\"}]");
    assertEquals(
        "42.73",
        LabJson.text(
            LabJson.path(validation.merge(old, observations), "habits.plan_execution", "score")));
    Map<String, Object> previous = LabJson.child(old, "habits.plan_execution");
    previous.put("score", null);
    previous.put("weight", 200);
    assertEquals(
        3,
        LabJson.integer(
            LabJson.path(validation.merge(old, observations), "habits.plan_execution", "weight")));
  }

  @Test
  void refusesProfileIdentifierInjection() {
    assertThrows(
        IllegalArgumentException.class,
        () -> LabMapper.profileTable("emotion; DROP TABLE accounts"));
  }
}
