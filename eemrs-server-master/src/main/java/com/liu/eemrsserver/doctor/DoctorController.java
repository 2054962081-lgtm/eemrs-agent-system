package com.liu.eemrsserver.doctor;

import com.liu.eemrsserver.common.ApiResponse;
import com.liu.eemrsserver.domain.DoctorInfo;
import com.liu.eemrsserver.domain.Waiting;
import com.liu.eemrsserver.security.CurrentUser;
import com.liu.eemrsserver.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {
    @Autowired
    private DoctorServiceAdapter doctorServiceAdapter;

    @GetMapping
    public ApiResponse<List<DoctorInfo>> listByDepartment(@RequestParam("department") String department) {
        return ApiResponse.ok(doctorServiceAdapter.listByDepartment(department));
    }

    @GetMapping("/me")
    public ApiResponse<DoctorInfo> me(@CurrentUser UserPrincipal currentUser) {
        return ApiResponse.ok(doctorServiceAdapter.getDoctorInfo(currentUser));
    }

    @GetMapping("/me/waiting-list")
    public ApiResponse<List<Waiting>> waitingList(@RequestParam("department") String department,
                                                  @RequestParam(value = "doctorIdNumber", required = false) String doctorIdNumber,
                                                  @CurrentUser UserPrincipal currentUser) {
        return ApiResponse.ok(doctorServiceAdapter.waitingList(department, doctorIdNumber, currentUser));
    }
}
