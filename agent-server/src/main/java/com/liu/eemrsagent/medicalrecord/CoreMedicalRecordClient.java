package com.liu.eemrsagent.medicalrecord;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CoreMedicalRecordClient {
    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;

    public CoreMedicalRecordClient(@Value("${eemrs.core-base-url:http://localhost:8080}") String baseUrl) {
        this.baseUrl = trimTrailingSlash(baseUrl);
    }

    public boolean createFromDraft(MedicalRecordDraftRepository.MedicalRecordDraftRow draft,
                                   MedicalRecordDraftApplyRequest request,
                                   JsonNode appliedRecord,
                                   String authorizationHeader) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("department", firstNonBlank(request.department(), textAt(appliedRecord, "/visitInfo/recommendedDepartment/primary"), draft.recommendedDepartment()));
        body.put("medication", firstNonBlank(request.medication(), ""));
        body.put("conditionDescription", firstNonBlank(request.conditionDescription(),
                textAt(appliedRecord, "/chiefComplaint/text"),
                draft.chiefComplaint(),
                draft.consultationSummary()));
        body.put("cost", firstNonBlank(request.cost(), "0"));
        body.put("visitTime", request.visitTime() == null ? BigInteger.valueOf(System.currentTimeMillis()) : request.visitTime());
        body.put("patientName", firstNonBlank(request.patientName(), textAt(appliedRecord, "/patientBasicInfo/name")));
        body.put("patientIdNumber", draft.patientIdNumber());
        body.put("age", request.age());
        body.put("doctorName", request.doctorName());
        body.put("gender", firstNonBlank(request.gender(), textAt(appliedRecord, "/patientBasicInfo/gender")));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
        Object response = restTemplate.exchange(
                baseUrl + "/api/medical-records",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Object.class
        ).getBody();
        return response != null;
    }

    private String textAt(JsonNode node, String pointer) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.at(pointer);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8080";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
