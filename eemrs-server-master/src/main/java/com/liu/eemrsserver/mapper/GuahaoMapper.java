package com.liu.eemrsserver.mapper;

import com.liu.eemrsserver.domain.GuahaoInfo;
import com.liu.eemrsserver.domain.PatientInfo;
import com.liu.eemrsserver.domain.Waiting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Scope;

import java.util.List;

/**
 * @author L
 * @date 2019-10-06 21:26
 * @desc
 **/
@Mapper
@Scope("prototype")
public interface GuahaoMapper {
    boolean insertInfo(GuahaoInfo guahaoInfo);

    List<Waiting> queryByDept(@Param("enDept")  String department,@Param("hash") String hash);

    PatientInfo getPatientByHashCode(String hashCode);
    boolean deleteGuaHaoByHash(String hash);
}
