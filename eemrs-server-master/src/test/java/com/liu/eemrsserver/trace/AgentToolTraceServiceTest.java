package com.liu.eemrsserver.trace;

import com.liu.eemrsserver.domain.DoctorInfo;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

public class AgentToolTraceServiceTest {

    @Test
    public void recordsDoctorQueryWhenTraceHeadersExist() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AgentToolTraceService service = new AgentToolTraceService(jdbcTemplate);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Agent-Trace-Id", "trace-1");
        request.addHeader("X-Agent-Run-Id", "run-1");
        request.addHeader("X-Agent-Step-Id", "step-1");

        service.recordDoctorQuery(request, "呼吸内科", Collections.<DoctorInfo>emptyList(),
                System.nanoTime(), 200, "SUCCESS", null, null);

        assertThat(mockingDetails(jdbcTemplate).getInvocations()).hasSize(1);
    }

    @Test
    public void ignoresDoctorQueryWithoutRunHeader() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AgentToolTraceService service = new AgentToolTraceService(jdbcTemplate);

        service.recordDoctorQuery(new MockHttpServletRequest(), "呼吸内科", Collections.<DoctorInfo>emptyList(),
                System.nanoTime(), 200, "SUCCESS", null, null);

        assertThat(mockingDetails(jdbcTemplate).getInvocations()).isEmpty();
    }
}
