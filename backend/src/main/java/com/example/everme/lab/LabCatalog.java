package com.example.everme.lab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;

@Component
@Profile("clone-lab")
public class LabCatalog {
    private final JsonNode document;
    private final Map<String, JsonNode> elements = new LinkedHashMap<>();
    private final Map<String, List<String>> domains = new LinkedHashMap<>();
    @org.springframework.beans.factory.annotation.Autowired
    public LabCatalog(ObjectMapper json) throws IOException { this(read(json)); }
    private static JsonNode read(ObjectMapper json) throws IOException {
        try (var in = new ClassPathResource("clone-lab/catalog.json").getInputStream()) { return json.readTree(in); }
    }
    LabCatalog(JsonNode source) {
        document=source.deepCopy();
        if(!"element-cap-v1".equals(document.path("updatePolicyVersion").asText()))throw new IllegalStateException("Invalid update policy version");
        for (var domain : document.path("domains")) {
            var codes = new ArrayList<String>();
            for (var element : domain.path("elements")) {
                String code = element.path("code").asText();
                var policy=element.path("updatePolicy");
                if(!code.equals(domain.path("code").asText()+"."+element.path("column").asText())||!policy.path("maxWeight").isIntegralNumber()||!Set.of(10,20,40).contains(policy.path("maxWeight").asInt())||policy.path("rationale").asText().isBlank())throw new IllegalStateException("Invalid update policy: "+code);
                if (elements.put(code, element) != null) throw new IllegalStateException("Duplicate element");
                codes.add(code);
            }
            if (codes.size() != 20) throw new IllegalStateException("20 elements required per domain");
            if(domains.put(domain.path("code").asText(), List.copyOf(codes))!=null)throw new IllegalStateException("Duplicate domain");
        }
        if (domains.size() != 7 || elements.size() != 140) throw new IllegalStateException("Invalid catalog");
    }
    public JsonNode document() { return document.deepCopy(); }
    public Map<String, List<String>> domains() { return Collections.unmodifiableMap(domains); }
    public Set<String> codes() { return Collections.unmodifiableSet(elements.keySet()); }
    public JsonNode element(String code) {
        if (!elements.containsKey(code)) throw LabException.bad("알 수 없는 분석 요소입니다.");
        return elements.get(code).deepCopy();
    }
    public String column(String code) { return element(code).path("column").asText(); }
    public int maxWeight(String code) { return element(code).path("updatePolicy").path("maxWeight").asInt(); }
    public String policyVersion() { return document.path("updatePolicyVersion").asText(); }
    public void requireDomain(String code) { if (!domains.containsKey(code)) throw LabException.bad("알 수 없는 분석 영역입니다."); }
}
