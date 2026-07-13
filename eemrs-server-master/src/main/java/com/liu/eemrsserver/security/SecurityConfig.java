package com.liu.eemrsserver.security;

import com.liu.eemrsserver.common.ApiResponse;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .exceptionHandling()
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType(MediaType.APPLICATION_JSON_UTF8_VALUE);
                    response.getWriter().write(JSON.toJSONString(ApiResponse.fail("Unauthorized")));
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(403);
                    response.setContentType(MediaType.APPLICATION_JSON_UTF8_VALUE);
                    response.getWriter().write(JSON.toJSONString(ApiResponse.fail("Forbidden")));
                })
                .and()
                .authorizeRequests()
                .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .antMatchers("/api/auth/**").permitAll()
                .antMatchers(HttpMethod.GET, "/api/doctors").hasAnyRole("PATIENT", "DOCTOR")
                .antMatchers(HttpMethod.PUT, "/api/patients/me").hasRole("PATIENT")
                .antMatchers(HttpMethod.POST, "/api/appointments").hasRole("PATIENT")
                .antMatchers(HttpMethod.GET, "/api/doctors/me", "/api/doctors/me/waiting-list").hasRole("DOCTOR")
                .antMatchers(HttpMethod.POST, "/api/appointments/*/accept").hasRole("DOCTOR")
                .antMatchers(HttpMethod.POST, "/api/medical-records/sign").hasRole("DOCTOR")
                .antMatchers(HttpMethod.POST, "/api/medical-records").hasRole("DOCTOR")
                .antMatchers(HttpMethod.GET, "/api/medical-records", "/api/lab-reports/search-by-dept-time").hasAnyRole("PATIENT", "DOCTOR")
                .antMatchers(HttpMethod.POST, "/api/ai/pre-consultations", "/api/ai/report-interpretations").hasRole("PATIENT")
                .antMatchers(HttpMethod.POST, "/api/ai/record-drafts").hasRole("DOCTOR")
                .antMatchers("/api/memory/**").hasRole("PATIENT")
                .anyRequest().authenticated();

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    }
}
