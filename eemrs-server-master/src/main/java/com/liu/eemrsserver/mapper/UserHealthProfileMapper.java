package com.liu.eemrsserver.mapper;

import com.liu.eemrsserver.memory.domain.UserHealthProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserHealthProfileMapper {
    UserHealthProfile findByPatientIdHash(@Param("patientIdHash") String patientIdHash);

    int insert(UserHealthProfile profile);

    int updateByPatientIdHash(UserHealthProfile profile);

    int softDeleteByPatientIdHash(@Param("patientIdHash") String patientIdHash);
}
