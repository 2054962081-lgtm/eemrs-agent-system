package com.liu.eemrsserver.memory.dto;

import lombok.Data;

@Data
public class ShortTermQuestionAnswerRequest {
    private String question;
    private String answer;
    private Integer round;
    private String temporaryConclusion;
}
