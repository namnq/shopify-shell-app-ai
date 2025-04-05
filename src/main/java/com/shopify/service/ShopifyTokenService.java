package com.shopify.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Instant;
import java.util.*;

@Service
public class ShopifyTokenService {
  
  private final JwtParser jwtParser;
  
  @Value("${shopify.app.base-url}")
  private String appBaseUrl;
  @Value("${shopify.api.key}")
  private String apiKey;
  
  @Autowired
  public ShopifyTokenService(JwtParser jwtParser) {
    this.jwtParser = jwtParser;
  }
  
  public boolean validateToken(String token) {
    if (token == null || token.trim().isEmpty()) {
      System.err.println("Empty or null token");
      return false;
    }
    
    try {
      Claims claims = jwtParser.parseClaimsJws(token).getBody();
      
      // Validate essential claims
      Date now = new Date();
      Date expiry = claims.getExpiration();
      Date notBefore = claims.getNotBefore();
      
      // Check audience - it could be a single string or a list
      Object audienceClaim = claims.get(Claims.AUDIENCE);
      boolean validAudience = false;
      
      if (audienceClaim instanceof String) {
        validAudience = apiKey.equals(audienceClaim);
      } else if (audienceClaim instanceof List) {
        validAudience = ((List<?>) audienceClaim).contains(apiKey);
      }
      
      if (!validAudience) {
        System.err.println("JWT audience mismatch");
        return false;
      }
      
      if (expiry == null || expiry.before(now)) {
        System.err.println("JWT expired or missing expiry");
        return false;
      }
      
      if (notBefore != null && notBefore.after(now)) {
        System.err.println("JWT not yet valid (before nbf)");
        return false;
      }
      
      return true;
    } catch (SignatureException e) {
      System.err.println("JWT signature validation error: " + e.getMessage());
      return false;
    } catch (ExpiredJwtException e) {
      System.err.println("JWT expired: " + e.getMessage());
      return false;
    } catch (MalformedJwtException e) {
      System.err.println("JWT malformed: " + e.getMessage());
      return false;
    } catch (JwtException e) {
      System.err.println("JWT validation error: " + e.getMessage());
      return false;
    } catch (Exception e) {
      System.err.println("Unexpected error during token validation: " + e.getMessage());
      return false;
    }
  }
  
  /**
   * Extracts the shop domain from a Shopify JWT token.
   * According to Shopify's documentation, the shop domain can be found in:
   * 1. The 'dest' claim which contains the shop's myshopify.com domain
   * 2. If not present, falls back to the 'sub' claim which may also contain the shop domain
   *
   * @param token The JWT token from Shopify
   * @return The shop domain (e.g., "my-shop.myshopify.com") or null if not found
   */
  public String getShopDomainFromToken(String token) {
    try {
      Claims claims = jwtParser.parseClaimsJws(token).getBody();
      
      // Primary source: 'dest' claim (Shopify's standard for the shop URL)
      String dest = claims.get("dest", String.class);
      if (dest != null && !dest.isEmpty()) {
        // If dest is a full URL (https://shop-name.myshopify.com), extract just the hostname
        if (dest.startsWith("http")) {
          try {
            java.net.URL url = new java.net.URL(dest);
            return url.getHost();
          } catch (java.net.MalformedURLException e) {
            // If parsing fails, return the original dest value
            return dest;
          }
        }
        return dest;
      }
      
      // Fallback: check 'sub' claim
      String subject = claims.getSubject();
      if (subject != null && !subject.isEmpty()) {
        return subject;
      }
      
      // Last resort: check for 'shop_id' or 'shop' custom claims
      String shop = claims.get("shop", String.class);
      if (shop != null && !shop.isEmpty()) {
        return shop;
      }
      
      System.err.println("No shop domain found in JWT token claims");
      return null;
    } catch (JwtException e) {
      System.err.println("Error extracting shop domain from token: " + e.getMessage());
      return null;
    }
  }
  
}