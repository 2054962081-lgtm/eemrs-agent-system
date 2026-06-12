package com.liu.eemrsagent.trace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceRedactorTest {

    private final TraceRedactor redactor = new TraceRedactor();

    @Test
    void redactsSensitiveValues() {
        String text = "name=张三 phone=13812345678 id=110101199001011234 Authorization: Bearer abc.def apiKey=secret";

        String redacted = redactor.redact(text);

        assertThat(redacted).doesNotContain("13812345678", "110101199001011234", "abc.def", "secret");
        assertThat(redacted).contains("[REDACTED_MOBILE]", "[REDACTED_ID_CARD]", "[REDACTED_AUTH]");
    }

    @Test
    void hashesUserIdWithSalt() {
        assertThat(redactor.hashUserId("patient-1", "salt"))
                .startsWith("sha256:")
                .isEqualTo(redactor.hashUserId("patient-1", "salt"));
        assertThat(redactor.hashUserId("patient-1", "salt"))
                .isNotEqualTo(redactor.hashUserId("patient-1", "other"));
    }
}
