package com.api.v1.newssystem.security;

import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.common.response.TokenResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Component
public class JwtService {
    private final AppProperty appProperty;
    private final SecretKey signingKey;
    // The ids (jti) of access tokens that were logged out. An entry is kept as long as an access token can live, so by
    // the time it is dropped the token has expired by itself. It is in memory: a restart empties it, which only matters
    // for the access tokens that were still alive (at most app.jwt.access-expiration).
    private final Cache<String, Boolean> revoked;

    public JwtService(AppProperty appProperty) {
        this.appProperty = appProperty;
        this.signingKey = Keys.hmacShaKeyFor(appProperty.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        this.revoked = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMillis(appProperty.getJwt().getAccessExpiration()))
                .build();
    }

    //1. Create a token and the refresh token for a user
    private String token(String subject) {
        Date now = new Date();

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claim("type", "access")
                .subject(subject)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + appProperty.getJwt().getAccessExpiration()))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    private String refresh(String subject) {
        Date now = new Date();

        return Jwts.builder()
                .claim("type", "refresh")
                .subject(subject)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + appProperty.getJwt().getRefreshExpiration()))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    //2. generate token for user
    public TokenResponse generateAccess(UserDetails userDetails) {
        Date issuedAt = new Date();
        Date expiredAt = new Date(issuedAt.getTime() + appProperty.getJwt().getAccessExpiration());

        String token = this.token(userDetails.getUsername());
        String refresh = this.refresh(userDetails.getUsername());

        return TokenResponse.builder()
                .access(token)
                .refresh(refresh)
                .issuedAt(issuedAt)
                .expiredAt(expiredAt)
                .build();
    }

    public TokenResponse generateRefresh(UserDetails userDetails) {
        Date issuedAt = new Date();
        Date expiredAt = new Date(issuedAt.getTime() + appProperty.getJwt().getAccessExpiration());

        String token = this.token(userDetails.getUsername());

        return TokenResponse.builder()
                .access(token)
                .issuedAt(issuedAt)
                .expiredAt(expiredAt)
                .build();
    }

    //3. retrieving any information from token we will need the secret key
    private Claims extractAllClaim(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    //4. extract a specific claim from the JWT token’s claims.
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaim(token);
        return claimsResolver.apply(claims);
    }

    //5. retrieve username from jwt token
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    //6. retrieve expiration date from jwt token
    public Date extractExpirationDate(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    //7. check an expired token
    private boolean isTokenValid(String token) {
        return extractExpirationDate(token).after(new Date());
    }

    //8. validate token
    public boolean validateAccessToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);

        return "access".equals(extractClaim(token, claims -> claims.get("type", String.class)))
                && username.equals(userDetails.getUsername())
                && isTokenValid(token)
                && !isRevoked(token);
    }

    //9. logout: from now on this access token is refused, although it has not expired yet.
    // A header value with "Bearer " in front is accepted. A token that is missing, invalid, expired or has no id
    // (issued before tokens had one) is ignored: there is nothing to revoke.
    public void revoke(String accessToken) {
        if (accessToken == null) {
            return;
        }
        String token = accessToken.startsWith("Bearer ") ? accessToken.substring(7) : accessToken;
        try {
            String id = extractClaim(token, Claims::getId);
            if (id != null) {
                revoked.put(id, Boolean.TRUE);
            }
        } catch (JwtException | IllegalArgumentException ignored) {
            // nothing to revoke
        }
    }

    private boolean isRevoked(String token) {
        String id = extractClaim(token, Claims::getId);
        return id != null && revoked.getIfPresent(id) != null;
    }

    public boolean validateRefreshToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);

        return "refresh".equals(extractClaim(token, claims -> claims.get("type", String.class)))
                && username.equals(userDetails.getUsername())
                && isTokenValid(token);
    }
}
