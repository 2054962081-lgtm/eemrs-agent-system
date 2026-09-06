package com.liu.eemrsagent.trace;

import com.liu.eemrsagent.common.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/agent-traces")
@ConditionalOnProperty(prefix = "agent.trace", name = "query-api-enabled", havingValue = "true")
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
        AgentRunRecord run = repository.findRun(runId);
        if (run == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trace run not found: " + runId);
        }
        return ApiResponse.ok(run);
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
        AgentRunRecord run = repository.findRun(runId);
        if (run == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trace run not found: " + runId);
        }
        return ApiResponse.ok(detailFor(run));
    }

    @GetMapping("/lookup/request/{requestId}/detail")
    public ApiResponse<?> detailByRequestId(@PathVariable String requestId) {
        List<AgentRunRecord> runs = repository.findRunsByRequestId(requestId);
        if (runs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trace run not found for request: " + requestId);
        }
        AgentRunRecord latest = runs.get(runs.size() - 1);
        Map<String, Object> detail = detailFor(latest);
        detail.put("runs", runs);
        detail.put("run_count", runs.size());
        detail.put("details", runs.stream().map(this::detailFor).toList());
        return ApiResponse.ok(detail);
    }

    private Map<String, Object> detailFor(AgentRunRecord run) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("run", run);
        detail.put("steps", repository.findSteps(run.runId()));
        detail.put("tool_calls", repository.findToolCalls(run.runId()));
        return detail;
    }
}
