package com.liu.eemrsserver.labreport;

import com.liu.eemrsserver.common.ApiResponse;
import com.liu.eemrsserver.security.CurrentUser;
import com.liu.eemrsserver.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lab-reports")
public class LabReportController {
    @Autowired
    private LabReportService labReportService;

    @GetMapping("/search-by-dept-time")
    public ApiResponse<LabReportSearchResponse> searchByDepartmentAndQueryTime(
            @RequestParam("department") String department,
            @RequestParam(value = "queryTime", required = false) String queryTime,
            @RequestParam(value = "patientIdNumber", required = false) String patientIdNumber,
            @CurrentUser UserPrincipal currentUser) {
        return ApiResponse.ok(labReportService.searchByDepartmentAndQueryTime(
                department,
                queryTime,
                patientIdNumber,
                currentUser
        ));
    }
}
