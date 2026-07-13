package com.liu.eemrsagent.reporttrend;

public class ReportTrendException extends RuntimeException {
    private final ReportTrendErrorCode errorCode;

    public ReportTrendException(ReportTrendErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ReportTrendException(ReportTrendErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ReportTrendErrorCode errorCode() {
        return errorCode;
    }
}
