package com.liu.eemrsserver.medicalrecord;

import com.liu.eemrsserver.domain.VisitInfo;
import com.liu.eemrsserver.medicalrecord.dto.MedicalRecordRequest;
import com.liu.eemrsserver.utils.crypto.SM3;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class MedicalRecordSignatureBuilder {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private MedicalRecordSignatureBuilder() {
    }

    public static String build(MedicalRecordRequest request, String doctorIdNumber) {
        return build(
                doctorIdNumber,
                request.getPatientIdNumber(),
                request.getDepartment(),
                request.getVisitTime(),
                request.getPatientName(),
                request.getDoctorName(),
                request.getConditionDescription(),
                request.getMedication(),
                request.getCost(),
                request.getAge(),
                request.getGender()
        );
    }

    public static String build(VisitInfo visitInfo) {
        return build(
                visitInfo.getDoctorIdNumber(),
                visitInfo.getPatientIdNumber(),
                visitInfo.getDepartment(),
                visitInfo.getVisitTime(),
                visitInfo.getPatientName(),
                visitInfo.getDoctorName(),
                visitInfo.getConditionDescription(),
                visitInfo.getMedication(),
                visitInfo.getCost(),
                visitInfo.getAge(),
                visitInfo.getGender()
        );
    }

    private static String build(String doctorIdNumber,
                                String patientIdNumber,
                                String department,
                                BigInteger visitTime,
                                String patientName,
                                String doctorName,
                                String conditionDescription,
                                String medication,
                                String cost,
                                BigInteger age,
                                String gender) {
        StringBuilder builder = new StringBuilder();
        append(builder, "doctorIdHash", hashOrBlank(doctorIdNumber));
        append(builder, "patientIdHash", hashOrBlank(patientIdNumber));
        append(builder, "department", normalize(department));
        append(builder, "visitTime", formatVisitTime(visitTime));
        append(builder, "patientName", normalize(patientName));
        append(builder, "doctorName", normalize(doctorName));
        append(builder, "conditionDescription", normalize(conditionDescription));
        append(builder, "medication", normalize(medication));
        append(builder, "cost", normalize(cost));
        append(builder, "age", age == null ? "" : age.toString());
        append(builder, "gender", normalize(gender));
        return builder.toString();
    }

    private static void append(StringBuilder builder, String field, String value) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(field).append('=').append(value);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String hashOrBlank(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? "" : SM3.hash(normalized);
    }

    private static String formatVisitTime(BigInteger visitTime) {
        if (visitTime == null) {
            return "";
        }
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(visitTime.longValue()),
                ZoneId.systemDefault()
        );
        return FORMATTER.format(dateTime);
    }
}
