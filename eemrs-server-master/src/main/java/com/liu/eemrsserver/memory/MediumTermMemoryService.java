package com.liu.eemrsserver.memory;

import com.liu.eemrsserver.domain.VisitInfo;
import com.liu.eemrsserver.jsontrans.QueryConditions;
import com.liu.eemrsserver.memory.dto.MemoryUserScopeDTO;
import com.liu.eemrsserver.service.DataOpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MediumTermMemoryService {
    @Autowired
    private DataOpService dataOpService;

    public List<Map<String, Object>> getMediumTermMemory(MemoryUserScopeDTO scope) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (scope == null || scope.getIdNumber() == null) {
            return result;
        }
        QueryConditions conditions = new QueryConditions();
        conditions.setPatientIdNumber(scope.getIdNumber());
        try {
            List<VisitInfo> visits = dataOpService.query(conditions);
            if (visits == null) {
                return result;
            }
            return visits.stream()
                    .sorted(Comparator.comparing(VisitInfo::getVisitTime, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(10)
                    .map(this::toSummary)
                    .collect(Collectors.toList());
        } catch (RuntimeException ignored) {
            return result;
        }
    }

    private Map<String, Object> toSummary(VisitInfo visit) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("sourceType", "visit_summary");
        item.put("department", safe(visit.getDepartment()));
        item.put("eventTime", visit.getVisitTime());
        item.put("conditionDescription", safe(visit.getConditionDescription()));
        item.put("doctorName", safe(visit.getDoctorName()));
        item.put("medication", safe(visit.getMedication()));
        item.put("summary", buildSummary(visit));
        return item;
    }

    private String buildSummary(VisitInfo visit) {
        StringBuilder builder = new StringBuilder();
        builder.append("就诊科室：").append(safe(visit.getDepartment()));
        builder.append("；诊疗描述：").append(safe(visit.getConditionDescription()));
        if (visit.getMedication() != null && !visit.getMedication().trim().isEmpty()) {
            builder.append("；用药建议：").append(visit.getMedication().trim());
        }
        return builder.toString();
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "未记录" : value.trim();
    }
}
