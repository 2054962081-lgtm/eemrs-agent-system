package com.liu.eemrsagent.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@Component
public class JwtAgentUserResolver {
    private static final String SECRET = "eemrs-rest-api-phase2-jwt-secret-change-me";

    private final ObjectMapper objectMapper;

    public JwtAgentUserResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AgentUserPrincipal resolve(String authorizationHeader) {
        String token = bearerToken(authorizationHeader);
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new UnauthorizedException("Invalid token");
        }
        verifySignature(parts);
        try {
            JsonNode claims = objectMapper.readTree(base64UrlDecode(parts[1]));
            long exp = claims.path("exp").asLong(0L);
            if (exp > 0 && Instant.now().getEpochSecond() >= exp) {
                throw new UnauthorizedException("Invalid or expired token");
            }
            String idNumber = text(claims, "idNumber");
            String type = text(claims, "type");
            AgentRole role = AgentRole.from(text(claims, "role"));
            String department = text(claims, "department");
            if (idNumber == null || idNumber.isBlank()) {
                throw new UnauthorizedException("Missing idNumber");
            }
            return new AgentUserPrincipal(idNumber.trim().toUpperCase(), type, role, blankToNull(department));
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid or expired token");
        }
    }

    private String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Unauthorized");
        }
        String token = authorizationHeader.substring(7).trim();
        if (token.isBlank()) {
            throw new UnauthorizedException("Unauthorized");
        }
        return token;
    }

    private void verifySignature(String[] parts) {
        try {
            String signed = parts[0] + "." + parts[1];
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
                throw new UnauthorizedException("Invalid or expired token");
            }
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid or expired token");
        }
    }

    private byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
