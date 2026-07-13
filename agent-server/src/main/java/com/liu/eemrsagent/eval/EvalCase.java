package com.liu.eemrsagent.eval;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EvalCase {
    @JsonProperty("case_id")
    private String caseId;
    private String source;
    private String scenario;
    private String category;
    @JsonProperty("user_profile")
    private Map<String, Object> userProfile = new LinkedHashMap<>();
    private List<EvalTurn> turns = new ArrayList<>();
    private EvalExpectedResult expected;
    private List<String> tags = new ArrayList<>();
    @JsonProperty("duplicate_group")
    private String duplicateGroup;

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getScenario() {
        return scenario;
    }

    public void setScenario(String scenario) {
        this.scenario = scenario;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Map<String, Object> getUserProfile() {
        return userProfile;
    }

    public void setUserProfile(Map<String, Object> userProfile) {
        this.userProfile = userProfile == null ? new LinkedHashMap<>() : userProfile;
    }

    public List<EvalTurn> getTurns() {
        return turns;
    }

    public void setTurns(List<EvalTurn> turns) {
        this.turns = turns == null ? new ArrayList<>() : turns;
    }

    public EvalExpectedResult getExpected() {
        return expected;
    }

    public void setExpected(EvalExpectedResult expected) {
        this.expected = expected;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags == null ? new ArrayList<>() : tags;
    }

    public String getDuplicateGroup() {
        return duplicateGroup;
    }

    public void setDuplicateGroup(String duplicateGroup) {
        this.duplicateGroup = duplicateGroup;
    }
}
