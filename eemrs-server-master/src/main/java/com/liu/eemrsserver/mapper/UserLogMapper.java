package com.liu.eemrsserver.mapper;

import com.liu.eemrsserver.domain.DocLog;
import com.liu.eemrsserver.domain.PatLog;
import com.liu.eemrsserver.jsontrans.UserLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Scope;

import java.util.List;

/**
 * @author L
 * @date 2019-09-21 12:28
 * @desc
 **/
@Mapper
@Scope("prototype")
public interface UserLogMapper {
    boolean insertUserWithType(UserLog userOP);

    List<DocLog> getDocByHash(@Param("hashCode") String hashCode);

    boolean deleteUserByHash(@Param("hashCode") String hashCode, @Param("type") String type);

    List<PatLog> getPatByHash(@Param("hashCode")String hashCode);
}
