package com.shopify.config;


import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.UUID;

@Configuration
public class JwtConfig {
  
  @Value("${shopify.api.jwt-secret}")
  private String jwtSecret;
  
  @Bean
  public JwtDecoder jwtDecoder() {
    byte[] decodedKey = Base64.getDecoder().decode(jwtSecret);
    SecretKey key = new SecretKeySpec(decodedKey, 0, decodedKey.length, "HmacSHA256");
    return NimbusJwtDecoder.withSecretKey(key).build();
  }
  
  @Bean
  public JwtEncoder jwtEncoder() {
    byte[] decodedKey = Base64.getDecoder().decode(jwtSecret);
    SecretKey key = new SecretKeySpec(decodedKey, 0, decodedKey.length, "HmacSHA256");
    
    // Create a JWK from the secret key
    JWK jwk = new OctetSequenceKey.Builder(decodedKey)
        .keyID(UUID.randomUUID().toString())
        .algorithm(JWSAlgorithm.HS256)
        .build();
    
    // Create a JWK source
    JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwk));
    
    // Create the encoder with the JWK source
    return new NimbusJwtEncoder(jwkSource);
  }
}