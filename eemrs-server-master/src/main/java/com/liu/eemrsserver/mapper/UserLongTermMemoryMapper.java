package com.liu.eemrsserver.mapper;

import com.liu.eemrsserver.memory.domain.UserLongTermMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserLongTermMemoryMapper {
    List<UserLongTermMemory> findActiveByPatientIdHash(@Param("patientIdHash") String patientIdHash);

    List<UserLongTermMemory> findActiveByPatientIdHashAndType(@Param("patientIdHash") String patientIdHash,
                                                              @Param("memoryType") String memoryType);

    int insert(UserLongTermMemory memory);

    int updateByIdAndPatientIdHash(UserLongTermMemory memory);

    int softDeleteByIdAndPatientIdHash(@Param("id") Long id, @Param("patientIdHash") String patientIdHash);

    int existsSimilarMemory(@Param("patientIdHash") String patientIdHash,
                            @Param("memoryType") String memoryType,
                            @Param("memoryKey") String memoryKey,
                            @Param("memoryValue") String memoryValue);
}
