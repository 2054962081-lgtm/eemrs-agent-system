package com.liu.eemrsagent.reporttrend;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "agent.report-trend")
public class ReportTrendProperties {
    private double stableChangeThresholdPercent = 10.0;

    public double getStableChangeThresholdPercent() {
        return stableChangeThresholdPercent;
    }

    public void setStableChangeThresholdPercent(double stableChangeThresholdPercent) {
        this.stableChangeThresholdPercent = stableChangeThresholdPercent;
    }
}
