package com.shopify.config;

import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Base64;

@Configuration
public class JwtConfig {

    @Value("${shopify.api.secret}")
    private String jwtSecret;
    
    @Autowired
    private Environment environment;

    @Bean
    public Key jwtSigningKey() {
        return new SecretKeySpec(jwtSecret.getBytes(), SignatureAlgorithm.HS256.getJcaName());
    }

    @Bean
    public JwtParser jwtParser() {
        return Jwts.parserBuilder()
                .setSigningKey(jwtSigningKey())
                .build();
    }
    
    private byte[] getSecretKeyBytes() {
        // For development environments only, generate a random secret if not configured
        if ((jwtSecret == null || jwtSecret.isEmpty()) &&
            (environment.getActiveProfiles().length > 0 &&
             environment.getActiveProfiles()[0].equals("dev"))) {
            byte[] randomSecret = new byte[64]; // 512 bits
            new SecureRandom().nextBytes(randomSecret);
            System.out.println("Using randomly generated key for development");
            return randomSecret;
        }
        
        try {
            // Try to decode the secret as Base64
            byte[] decodedBytes = Base64.getDecoder().decode(jwtSecret);
            System.out.println("Successfully decoded Base64 secret, length: " + decodedBytes.length + " bytes");
            return decodedBytes;
        } catch (IllegalArgumentException e) {
            System.out.println("Secret is not Base64 encoded, using raw bytes");
            // If not Base64, use as raw bytes
            byte[] rawBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
            
            // Ensure key is at least 32 bytes (256 bits) for HS256
            if (rawBytes.length < 32) {
                throw new IllegalArgumentException("JWT secret must be at least 32 bytes for HS256, current length: "
                    + rawBytes.length + " bytes");
            }
            
            System.out.println("Using raw secret bytes, length: " + rawBytes.length + " bytes");
            return rawBytes;
        }
    }
}