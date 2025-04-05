package com.shopify.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class ShopifyTokenService {
    private static final Logger log = LoggerFactory.getLogger(ShopifyTokenService.class);
    private static final String DEST_CLAIM = "dest";
    private static final String SHOP_CLAIM = "shop";
    
    private final JwtParser jwtParser;
    
    @Value("${shopify.app.base-url}")
    private String appBaseUrl;
    
    @Value("${shopify.api.key}")
    private String apiKey;
    
    @Autowired
    public ShopifyTokenService(JwtParser jwtParser) {
        this.jwtParser = jwtParser;
    }
    
    /**
     * Validates a JWT token against multiple criteria
     * @param token The JWT token to validate
     * @return true if the token is valid, false otherwise
     */
    public boolean validateToken(String token) {
        if (!isTokenPresent(token)) {
            return false;
        }
        
        try {
            Claims claims = parseToken(token);
            return validateClaims(claims);
        } catch (JwtException e) {
            logTokenError(e);
            return false;
        } catch (Exception e) {
            log.error("Unexpected error during token validation", e);
            return false;
        }
    }
    
    /**
     * Extracts the shop domain from a Shopify JWT token
     * @param token The JWT token from Shopify
     * @return Optional containing the shop domain or empty if not found
     */
    public String getShopDomainFromToken(String token) {
        try {
            Claims claims = parseToken(token);
            return extractShopDomain(claims).orElse(null);
        } catch (JwtException e) {
            log.error("Error extracting shop domain from token", e);
            return null;
        }
    }
    
    private boolean isTokenPresent(String token) {
        if (token == null || token.trim().isEmpty()) {
            log.debug("Empty or null token provided");
            return false;
        }
        return true;
    }
    
    private Claims parseToken(String token) throws JwtException {
        return jwtParser.parseClaimsJws(token).getBody();
    }
    
    private boolean validateClaims(Claims claims) {
        Date now = new Date();
        
        return validateTiming(claims, now) &&
               validateAudience(claims);
    }
    
    private boolean validateTiming(Claims claims, Date now) {
        Date expiry = claims.getExpiration();
        Date notBefore = claims.getNotBefore();
        
        if (expiry == null || expiry.before(now)) {
            log.debug("Token expired or missing expiry");
            return false;
        }
        
        if (notBefore != null && notBefore.after(now)) {
            log.debug("Token not yet valid (before nbf)");
            return false;
        }
        
        return true;
    }
    
    private boolean validateAudience(Claims claims) {
        return switch (claims.get(Claims.AUDIENCE)) {
            case String audience -> apiKey.equals(audience);
            case List<?> audiences -> audiences.contains(apiKey);
            case null -> {
                log.debug("Missing audience claim");
                yield false;
            }
            default -> {
                log.debug("Invalid audience claim type");
                yield false;
            }
        };
    }
    
    private Optional<String> extractShopDomain(Claims claims) {
        // Try dest claim first
        String dest = claims.get(DEST_CLAIM, String.class);
        if (dest != null && !dest.isEmpty()) {
            return Optional.of(normalizeShopUrl(dest));
        }
        
        // Try subject claim
        String subject = claims.getSubject();
        if (subject != null && !subject.isEmpty()) {
            return Optional.of(subject);
        }
        
        // Try shop claim
        String shop = claims.get(SHOP_CLAIM, String.class);
        if (shop != null && !shop.isEmpty()) {
            return Optional.of(shop);
        }
        
        log.debug("No shop domain found in JWT claims");
        return Optional.empty();
    }
    
    private String normalizeShopUrl(String dest) {
        if (dest.startsWith("http")) {
            try {
                return new URL(dest).getHost();
            } catch (MalformedURLException e) {
                log.debug("Failed to parse shop URL: {}", dest);
                return dest;
            }
        }
        return dest;
    }
    
    private void logTokenError(JwtException e) {
        if (e instanceof SignatureException) {
            log.error("JWT signature validation error", e);
        } else if (e instanceof ExpiredJwtException) {
            log.error("JWT token expired", e);
        } else if (e instanceof MalformedJwtException) {
            log.error("JWT token malformed", e);
        } else {
            log.error("JWT validation error", e);
        }
    }
}