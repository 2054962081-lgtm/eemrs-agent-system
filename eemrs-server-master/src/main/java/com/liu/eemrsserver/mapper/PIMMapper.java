package com.liu.eemrsserver.mapper;

import com.liu.eemrsserver.domain.PatientInfo;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author L
 * @date 2019-10-14 18:55
 * @desc
 **/
@Mapper
@Scope("prototype")
public interface PIMMapper {
    boolean updatePatientPassword(String type, String hash, String pass);

    boolean updatePatient(PatientInfo patientInfo);

    PatientInfo getPatientById(String hash);
}
