package com.liu.eemrsagent.agent;

public record PreConsultationRequest(
        String question
) {
    public String userInput() {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("Please enter a question");
        }
        return question.trim();
    }
}
