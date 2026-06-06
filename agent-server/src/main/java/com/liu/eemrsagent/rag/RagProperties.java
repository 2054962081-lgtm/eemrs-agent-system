package com.liu.eemrsagent.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private boolean enabled = true;
    private String serviceUrl = "http://localhost:18080";
    private String retrievePath = "/rag/retrieve";
    private int timeoutMs = 3000;
    private int topK = 8;
    private int maxContextChars = 3000;
    private List<String> includeDocTypes = new ArrayList<>(List.of(
            "red_flag",
            "symptom_inquiry",
            "special_population",
            "department_triage",
            "medical_record_template"
    ));
    private boolean failOpen = true;
    private boolean debugLog = true;
    private QuestionPlan questionPlan = new QuestionPlan();
    private PostProcess postProcess = new PostProcess();
    private QueryExpansion queryExpansion = new QueryExpansion();
    private Retrieval retrieval = new Retrieval();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public String getRetrievePath() {
        return retrievePath;
    }

    public void setRetrievePath(String retrievePath) {
        this.retrievePath = retrievePath;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getMaxContextChars() {
        return maxContextChars;
    }

    public void setMaxContextChars(int maxContextChars) {
        this.maxContextChars = maxContextChars;
    }

    public List<String> getIncludeDocTypes() {
        return includeDocTypes;
    }

    public void setIncludeDocTypes(List<String> includeDocTypes) {
        this.includeDocTypes = includeDocTypes == null ? new ArrayList<>() : includeDocTypes;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    public boolean isDebugLog() {
        return debugLog;
    }

    public void setDebugLog(boolean debugLog) {
        this.debugLog = debugLog;
    }

    public QuestionPlan getQuestionPlan() {
        return questionPlan;
    }

    public void setQuestionPlan(QuestionPlan questionPlan) {
        this.questionPlan = questionPlan == null ? new QuestionPlan() : questionPlan;
    }

    public PostProcess getPostProcess() {
        return postProcess;
    }

    public void setPostProcess(PostProcess postProcess) {
        this.postProcess = postProcess == null ? new PostProcess() : postProcess;
    }

    public QueryExpansion getQueryExpansion() {
        return queryExpansion;
    }

    public void setQueryExpansion(QueryExpansion queryExpansion) {
        this.queryExpansion = queryExpansion == null ? new QueryExpansion() : queryExpansion;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public void setRetrieval(Retrieval retrieval) {
        this.retrieval = retrieval == null ? new Retrieval() : retrieval;
    }

    public static class QuestionPlan {
        private boolean enabled = true;
        private int maxKeyQuestionsQuick = 5;
        private int maxKeyQuestionsDeep = 8;
        private double mustAskTargetCoverage = 0.6;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxKeyQuestionsQuick() {
            return maxKeyQuestionsQuick;
        }

        public void setMaxKeyQuestionsQuick(int maxKeyQuestionsQuick) {
            this.maxKeyQuestionsQuick = maxKeyQuestionsQuick;
        }

        public int getMaxKeyQuestionsDeep() {
            return maxKeyQuestionsDeep;
        }

        public void setMaxKeyQuestionsDeep(int maxKeyQuestionsDeep) {
            this.maxKeyQuestionsDeep = maxKeyQuestionsDeep;
        }

        public double getMustAskTargetCoverage() {
            return mustAskTargetCoverage;
        }

        public void setMustAskTargetCoverage(double mustAskTargetCoverage) {
            this.mustAskTargetCoverage = mustAskTargetCoverage;
        }
    }

    public static class PostProcess {
        private boolean enabled = true;
        private int maxAddedQuestions = 3;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxAddedQuestions() {
            return maxAddedQuestions;
        }

        public void setMaxAddedQuestions(int maxAddedQuestions) {
            this.maxAddedQuestions = maxAddedQuestions;
        }
    }

    public static class QueryExpansion {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Retrieval {
        private boolean ensureDocTypeBalance = true;

        public boolean isEnsureDocTypeBalance() {
            return ensureDocTypeBalance;
        }

        public void setEnsureDocTypeBalance(boolean ensureDocTypeBalance) {
            this.ensureDocTypeBalance = ensureDocTypeBalance;
        }
    }
}
