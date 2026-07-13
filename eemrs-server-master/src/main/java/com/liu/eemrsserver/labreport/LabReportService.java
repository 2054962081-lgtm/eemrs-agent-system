package com.liu.eemrsserver.labreport;

import com.liu.eemrsserver.common.BadRequestException;
import com.liu.eemrsserver.common.RequestValidator;
import com.liu.eemrsserver.config.SMServerKey;
import com.liu.eemrsserver.crypto.UserLogCrypto;
import com.liu.eemrsserver.domain.LabReport;
import com.liu.eemrsserver.mapper.LabReportMapper;
import com.liu.eemrsserver.security.ForbiddenException;
import com.liu.eemrsserver.security.Role;
import com.liu.eemrsserver.security.UserPrincipal;
import com.liu.eemrsserver.utils.crypto.SM3;
import com.liu.eemrsserver.utils.crypto.homomorphic.boldyreva.BoldyrevaCipher;
import com.liu.eemrsserver.utils.crypto.sm4.SM4_String;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LabReportService {
    @Autowired
    private LabReportMapper labReportMapper;
    @Autowired
    private UserLogCrypto userLogCrypto;
    @Autowired
    private SMServerKey smServerKey;
    @Autowired
    private BoldyrevaCipher boldyrevaCipher;

    public LabReportSearchResponse searchByDepartmentAndQueryTime(String department,
                                                                  String queryTime,
                                                                  String patientIdNumber,
                                                                  UserPrincipal currentUser) {
        RequestValidator.notBlank(department, "department");
        RequestValidator.notNull(currentUser, "currentUser");
        String normalizedDepartment = department.trim();
        String scopedPatientIdNumber = resolvePatientScope(patientIdNumber, currentUser);
        BigInteger endMillis = parseQueryTime(queryTime);
        LocalDate endDate = toLocalDate(endMillis);
        LocalDate startDate = endDate.minusYears(1);
        List<String> indexTokens = buildLabReportMonthTokens(scopedPatientIdNumber, normalizedDepartment, startDate, endDate);
        if (indexTokens.isEmpty()) {
            return emptyResponse();
        }
        try {
            List<String> reportTokens = labReportMapper.queryReportTokensBySearchIndexes(indexTokens);
            if (reportTokens == null || reportTokens.isEmpty()) {
                return emptyResponse();
            }
            List<LabReport> encryptedReports = labReportMapper.queryReportsByTokens(new ArrayList<>(new LinkedHashSet<>(reportTokens)));
            if (encryptedReports == null || encryptedReports.isEmpty()) {
                return emptyResponse();
            }
            BigInteger startMillis = BigInteger.valueOf(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
            List<LabReport> historyReports = encryptedReports.stream()
                    .map(this::decryptReport)
                    .filter(report -> normalizedDepartment.equals(report.getDepartment()))
                    .filter(report -> isWithinRange(report.getReportTime(), startMillis, endMillis))
                    .sorted(Comparator.comparing(LabReport::getReportTime,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .collect(Collectors.toList());
            LabReport latestReport = historyReports.isEmpty() ? null : historyReports.get(0);
            return new LabReportSearchResponse(latestReport, historyReports);
        } catch (RuntimeException e) {
            // Report tables are optional until the lab-report module migration is applied.
            return emptyResponse();
        }
    }

    public List<String> buildLabReportMonthTokens(String patientIdNumber,
                                                  String department,
                                                  LocalDate startDate,
                                                  LocalDate endDate) {
        List<String> tokens = new ArrayList<>();
        YearMonth cursor = YearMonth.from(startDate);
        YearMonth end = YearMonth.from(endDate);
        while (!cursor.isAfter(end)) {
            tokens.add(buildLabReportMonthToken(patientIdNumber, department, cursor));
            cursor = cursor.plusMonths(1);
        }
        return tokens;
    }

    public String buildLabReportMonthToken(String patientIdNumber, String department, YearMonth month) {
        String normalizedIdNumber = userLogCrypto.normalizeIdNumber(patientIdNumber);
        String keyword = "LAB_REPORT#ID_CARD#" + normalizedIdNumber
                + "#DEPT#" + department.trim()
                + "#MONTH#" + month;
        return SM3.hash(keyword);
    }

    private String resolvePatientScope(String patientIdNumber, UserPrincipal currentUser) {
        if (currentUser.getRole() == Role.PATIENT) {
            if (patientIdNumber != null
                    && !patientIdNumber.trim().isEmpty()
                    && !currentUser.getIdNumber().equals(userLogCrypto.normalizeIdNumber(patientIdNumber))) {
                throw new ForbiddenException("Patients can only query their own lab reports");
            }
            return currentUser.getIdNumber();
        }
        if (currentUser.getRole() == Role.DOCTOR) {
            RequestValidator.notBlank(patientIdNumber, "patientIdNumber");
            return patientIdNumber;
        }
        throw new ForbiddenException("Unsupported role");
    }

    private LabReport decryptReport(LabReport encryptedReport) {
        LabReport out = new LabReport();
        out.setId(encryptedReport.getId());
        out.setPatientIdHashCode(encryptedReport.getPatientIdHashCode());
        out.setReportToken(encryptedReport.getReportToken());
        out.setReportPayloadCipher(encryptedReport.getReportPayloadCipher());
        out.setDepartmentCipher(encryptedReport.getDepartmentCipher());
        out.setReportTimeOpe(encryptedReport.getReportTimeOpe());
        out.setReportTypeCipher(encryptedReport.getReportTypeCipher());
        out.setImageCipherUrl(encryptedReport.getImageCipherUrl());
        out.setCreatedAt(encryptedReport.getCreatedAt());
        out.setUpdatedAt(encryptedReport.getUpdatedAt());
        out.setReportPayload(decryptWithIv(encryptedReport.getReportPayloadCipher()));
        out.setDepartment(decryptWithoutIv(encryptedReport.getDepartmentCipher()));
        out.setReportType(decryptWithoutIv(encryptedReport.getReportTypeCipher()));
        out.setReportTime(decryptOpe(encryptedReport.getReportTimeOpe()));
        return out;
    }

    private String decryptWithIv(String value) {
        if (value == null) {
            return null;
        }
        try {
            return SM4_String.decWithIV(value, smServerKey.getSm4Key());
        } catch (Exception e) {
            return null;
        }
    }

    private String decryptWithoutIv(String value) {
        if (value == null) {
            return null;
        }
        try {
            return SM4_String.decWithoutIV(value, smServerKey.getSm4Key());
        } catch (Exception e) {
            return null;
        }
    }

    private BigInteger decryptOpe(BigInteger value) {
        if (value == null) {
            return null;
        }
        try {
            return boldyrevaCipher.decrypted(value, smServerKey.getOpeKey());
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isWithinRange(BigInteger value, BigInteger start, BigInteger end) {
        return value == null || (value.compareTo(start) >= 0 && value.compareTo(end) <= 0);
    }

    private LabReportSearchResponse emptyResponse() {
        return new LabReportSearchResponse(null, new ArrayList<>());
    }

    private BigInteger parseQueryTime(String queryTime) {
        if (queryTime == null || queryTime.trim().isEmpty()) {
            return BigInteger.valueOf(System.currentTimeMillis());
        }
        String value = queryTime.trim();
        if (value.matches("\\d+")) {
            return new BigInteger(value);
        }
        try {
            LocalDate date = LocalDate.parse(value);
            long inclusiveEndOfDay = date.plusDays(1)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli() - 1;
            return BigInteger.valueOf(inclusiveEndOfDay);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("queryTime must be epoch milliseconds or yyyy-MM-dd");
        }
    }

    private LocalDate toLocalDate(BigInteger epochMillis) {
        return Instant.ofEpochMilli(epochMillis.longValue()).atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
