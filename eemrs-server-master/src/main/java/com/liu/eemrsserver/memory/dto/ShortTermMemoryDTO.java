package com.liu.eemrsserver.memory.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ShortTermMemoryDTO {
    private String sessionId;
    private String patientIdHash;
    private String chiefComplaint;
    private Integer currentRound = 0;
    private List<String> askedQuestions = new ArrayList<>();
    private List<String> answers = new ArrayList<>();
    private List<String> pendingQuestions = new ArrayList<>();
    private List<String> ragContextIds = new ArrayList<>();
    private String temporaryConclusion;
    private boolean completed;
    private Long createdAt;
    private Long updatedAt;
    private Long expireAt;
}
