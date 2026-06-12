package com.liu.eemrsagent.trace;

import com.liu.eemrsagent.common.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/agent-traces")
public class AgentTraceController {

    private final TraceRepository repository;

    public AgentTraceController(TraceRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/runs")
    public ApiResponse<?> runs(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String userIdHash,
            @RequestParam(required = false) String agentName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String modelName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(repository.findRuns(new TraceRunQuery(
                sessionId, userIdHash, agentName, status, modelName, startTime, endTime, page, size)));
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<?> run(@PathVariable String runId) {
        return ApiResponse.ok(repository.findRun(runId));
    }

    @GetMapping("/runs/{runId}/steps")
    public ApiResponse<?> steps(@PathVariable String runId) {
        return ApiResponse.ok(repository.findSteps(runId));
    }

    @GetMapping("/runs/{runId}/tool-calls")
    public ApiResponse<?> toolCalls(@PathVariable String runId) {
        return ApiResponse.ok(repository.findToolCalls(runId));
    }

    @GetMapping("/runs/{runId}/detail")
    public ApiResponse<?> detail(@PathVariable String runId) {
        return ApiResponse.ok(Map.of(
                "run", repository.findRun(runId),
                "steps", repository.findSteps(runId),
                "tool_calls", repository.findToolCalls(runId)
        ));
    }
}
