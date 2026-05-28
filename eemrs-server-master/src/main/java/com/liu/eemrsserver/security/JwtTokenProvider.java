package com.liu.eemrsserver.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtTokenProvider {
    private static final String SECRET = "eemrs-rest-api-phase2-jwt-secret-change-me";
    private static final long EXPIRATION_MILLIS = 24L * 60L * 60L * 1000L;

    public String createToken(UserPrincipal principal) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + EXPIRATION_MILLIS);
        return Jwts.builder()
                .setSubject(principal.getIdNumber())
                .claim("idNumber", principal.getIdNumber())
                .claim("type", principal.getType())
                .claim("role", principal.getRole().name())
                .claim("department", principal.getDepartment())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(SignatureAlgorithm.HS256, SECRET)
                .compact();
    }

    public UserPrincipal parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(SECRET)
                    .parseClaimsJws(token)
                    .getBody();
            String idNumber = claims.get("idNumber", String.class);
            String type = claims.get("type", String.class);
            String roleText = claims.get("role", String.class);
            String department = claims.get("department", String.class);
            Role role = Role.valueOf(roleText);
            return new UserPrincipal(idNumber, idNumber, type, role, department);
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid or expired token");
        }
    }

    public long getExpirationMillis() {
        return EXPIRATION_MILLIS;
    }
}
