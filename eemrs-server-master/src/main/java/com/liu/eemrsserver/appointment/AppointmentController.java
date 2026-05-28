package com.liu.eemrsserver.appointment;

import com.liu.eemrsserver.appointment.dto.AcceptAppointmentResponse;
import com.liu.eemrsserver.appointment.dto.CreateAppointmentRequest;
import com.liu.eemrsserver.common.ApiResponse;
import com.liu.eemrsserver.security.CurrentUser;
import com.liu.eemrsserver.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {
    @Autowired
    private AppointmentServiceAdapter appointmentServiceAdapter;

    @PostMapping
    public ApiResponse<Boolean> create(@RequestBody CreateAppointmentRequest request,
                                       @CurrentUser UserPrincipal currentUser) {
        return ApiResponse.ok("appointment handled", appointmentServiceAdapter.create(request, currentUser));
    }

    @PostMapping("/{idNumber}/accept")
    public ApiResponse<AcceptAppointmentResponse> accept(@PathVariable("idNumber") String idNumber,
                                                         @CurrentUser UserPrincipal currentUser) {
        return ApiResponse.ok(appointmentServiceAdapter.accept(idNumber, currentUser));
    }
}
