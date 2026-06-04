package com.liu.eemrsagent.agent;

import java.util.List;

public record PreConsultationRequest(
        String mode,
        String sessionId,
        String question,
        Integer round,
        List<Message> history
) {
    public String normalizedMode() {
        if (mode == null || mode.trim().isEmpty()) {
            return "quick";
        }
        String value = mode.trim().toLowerCase();
        if (!"quick".equals(value) && !"deep".equals(value)) {
            throw new IllegalArgumentException("mode must be quick or deep");
        }
        return value;
    }

    public int safeRound() {
        if (round == null || round < 1) {
            return 1;
        }
        return round;
    }

    public String userInput() {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("Please enter a question");
        }
        return question.trim();
    }

    public List<Message> safeHistory() {
        return history == null ? List.of() : history;
    }

    public record Message(String role, String content) {
    }
}
