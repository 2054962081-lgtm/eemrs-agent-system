package com.liu.eemrsagent.reporttrend;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class LocalPiiRedactor {
    private static final Pattern ID_CARD = Pattern.compile("\\b\\d{17}[0-9Xx]\\b");
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern PATIENT_FIELD = Pattern.compile("(?i)(姓名|name|patientId|patient_id|idCard|phone|address)\\s*[:：]\\s*[^,，;；\\n]+");

    public String redact(String text) {
        if (text == null) {
            return "";
        }
        String out = ID_CARD.matcher(text).replaceAll("[REDACTED_ID_CARD]");
        out = MOBILE.matcher(out).replaceAll("[REDACTED_MOBILE]");
        out = PATIENT_FIELD.matcher(out).replaceAll("$1:[REDACTED]");
        return out;
    }
}
