package com.liu.eemrsserver.mapper;

import com.liu.eemrsserver.domain.DoctorInfo;
import com.liu.eemrsserver.domain.VisitInfo;
import com.liu.eemrsserver.jsontrans.QueryConditions;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Scope;

import java.util.List;
@Mapper
@Scope("prototype")
public interface DataOpMappper {
    boolean insert(VisitInfo enPI);

    List<VisitInfo> queryByCondition(QueryConditions queryConditions);

    List<String> getCountersByIdHash(String hash);

    boolean updateCounter(String hash, String counter);

    DoctorInfo getDocInfoByHashCode(String hash);

    List<DoctorInfo> getDocNameByDepartment(@Param("department") String department,
                                            @Param("plainDepartment") String plainDepartment);
}
