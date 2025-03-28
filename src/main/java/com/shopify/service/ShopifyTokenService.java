package com.shopify.service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ShopifyTokenService {
  
  private final JwtEncoder jwtEncoder;
  private final JwtDecoder jwtDecoder;
  
  @Autowired
  public ShopifyTokenService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder) {
    this.jwtEncoder = jwtEncoder;
    this.jwtDecoder = jwtDecoder;
  }
  
  public boolean validateToken(String token) {
    try {
      Jwt jwt = jwtDecoder.decode(token);
      return jwt.getExpiresAt() != null && jwt.getExpiresAt().isAfter(Instant.now());
    } catch (JwtException e) {
      return false;
    }
  }
  
  public String getShopDomainFromToken(String token) {
    try {
      Jwt jwt = jwtDecoder.decode(token);
      return jwt.getSubject();
    } catch (JwtException e) {
      return null;
    }
  }
  
  public String generateToken(String shopDomain) {
    Instant now = Instant.now();
    
    // Build claims set instead of Jwt
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .subject(shopDomain)
        .issuedAt(now)
        .expiresAt(now.plusSeconds(3600)) // 1 hour expiration
        .build();
    return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
  }
}