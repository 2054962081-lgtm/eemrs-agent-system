package com.liu.eemrsserver.labreport;

import com.liu.eemrsserver.domain.LabReport;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LabReportSearchResponse {
    private LabReport latestReport;
    private List<LabReport> historyReports = new ArrayList<>();
}
