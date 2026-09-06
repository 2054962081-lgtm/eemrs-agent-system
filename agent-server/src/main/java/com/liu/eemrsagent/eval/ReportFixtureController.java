package com.liu.eemrsagent.eval;

import com.liu.eemrsagent.common.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@Profile("eval")
@RequestMapping("/api/eval/fixtures/report")
@ConditionalOnProperty(prefix = "agent.eval-fixtures", name = "enabled", havingValue = "true")
public class ReportFixtureController {

    private final ReportFixtureService service;

    public ReportFixtureController(ReportFixtureService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "fixture", "report",
                "time", OffsetDateTime.now().toString()
        ));
    }

    @PostMapping("/prepare")
    public ApiResponse<ReportFixtureService.PreparedFixture> prepare(@RequestBody ReportFixtureRequest request) {
        return ApiResponse.ok(service.prepare(request));
    }

    @PostMapping("/cleanup")
    public ApiResponse<Map<String, Object>> cleanup(@RequestBody ReportFixtureCleanupRequest request) {
        int deleted = service.cleanup(request.evalRunId(), request.caseId());
        return ApiResponse.ok(Map.of("deleted", deleted));
    }
}
