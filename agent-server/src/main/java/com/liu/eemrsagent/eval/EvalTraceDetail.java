package com.liu.eemrsagent.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EvalTraceDetail {
    private Map<String, Object> run = new LinkedHashMap<>();
    private List<EvalTraceStep> steps = new ArrayList<>();
    private List<EvalTraceToolCall> toolCalls = new ArrayList<>();

    public Map<String, Object> getRun() {
        return run;
    }

    public void setRun(Map<String, Object> run) {
        this.run = run == null ? new LinkedHashMap<>() : run;
    }

    public List<EvalTraceStep> getSteps() {
        return steps;
    }

    public void setSteps(List<EvalTraceStep> steps) {
        this.steps = steps == null ? new ArrayList<>() : steps;
    }

    public List<EvalTraceToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<EvalTraceToolCall> toolCalls) {
        this.toolCalls = toolCalls == null ? new ArrayList<>() : toolCalls;
    }

    public Optional<EvalTraceStep> firstStep(String stepType) {
        return steps.stream().filter(step -> stepType.equals(step.getStepType())).findFirst();
    }

    public boolean hasStep(String stepType) {
        return firstStep(stepType).isPresent();
    }

    public String joinedText(String... stepTypes) {
        StringBuilder builder = new StringBuilder();
        for (EvalTraceStep step : steps) {
            for (String type : stepTypes) {
                if (type.equals(step.getStepType())) {
                    builder.append(' ')
                            .append(nullToEmpty(step.getInputSummary()))
                            .append(' ')
                            .append(nullToEmpty(step.getOutputSummary()))
                            .append(' ')
                            .append(nullToEmpty(step.getRequestPayloadJson()))
                            .append(' ')
                            .append(nullToEmpty(step.getResponsePayloadJson()))
                            .append(' ')
                            .append(nullToEmpty(step.getMetadataJson()));
                }
            }
        }
        return builder.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
