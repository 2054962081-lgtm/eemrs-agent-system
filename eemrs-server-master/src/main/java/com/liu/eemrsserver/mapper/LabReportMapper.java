package com.liu.eemrsserver.mapper;

import com.liu.eemrsserver.domain.LabReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LabReportMapper {
    List<String> queryReportTokensBySearchIndexes(@Param("indexTokens") List<String> indexTokens);

    List<LabReport> queryReportsByTokens(@Param("reportTokens") List<String> reportTokens);
}
