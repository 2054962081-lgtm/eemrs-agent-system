package com.liu.eemrsagent.agent;

import com.liu.eemrsagent.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final PreConsultationService preConsultationService;

    public AgentController(PreConsultationService preConsultationService) {
        this.preConsultationService = preConsultationService;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "time", OffsetDateTime.now().toString()
        ));
    }

    @PostMapping("/pre-consultation")
    public ApiResponse<PreConsultationResponse> preConsultation(@RequestBody PreConsultationRequest request) {
        return ApiResponse.ok(preConsultationService.ask(request));
    }
}
