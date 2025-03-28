package com.shopify.service;

import com.shopify.model.ShopifyToken;
import com.shopify.model.dto.TokenResponse;
import com.shopify.repository.ShopifyTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Service
public class ShopifyService {
    private final WebClient webClient;
    private final ShopifyTokenRepository tokenRepository;
    private final JwtEncoder jwtEncoder;
    
    @Value("${shopify.api.key}")
    private String apiKey;
    
    @Value("${shopify.api.secret}")
    private String apiSecret;
    
    @Value("${shopify.api.jwt-secret}")
    private String jwtSecret;

    public String getApiKey() {
        return apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public ShopifyService(WebClient.Builder webClientBuilder, 
                        ShopifyTokenRepository tokenRepository,
                        JwtEncoder jwtEncoder) {
        this.webClient = webClientBuilder.baseUrl("https://{shop}.myshopify.com").build();
        this.tokenRepository = tokenRepository;
        this.jwtEncoder = jwtEncoder;
    }

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
        return new NimbusJwtEncoder(key);
    }

    public boolean validateToken(String token) {
        try {
            Jwt decoded = jwtDecoder().decode(token);
            return tokenRepository.existsByShopDomain(decoded.getSubject());
        } catch (Exception e) {
            return false;
        }
    }

    public String getShopDomainFromToken(String token) {
        return jwtDecoder().decode(token).getSubject();
    }

    public String generateToken(String shopDomain) {
        Instant now = Instant.now();
        return jwtEncoder.encode(Jwt.withSubject(shopDomain)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .build()).getTokenValue();
    }

    public Mono<String> getShopInfo(String shopDomain) {
        return webClient.get()
            .uri("/admin/api/2023-10/shop.json")
            .header("X-Shopify-Access-Token", apiSecret)
            .retrieve()
            .bodyToMono(String.class);
    }

    public Mono<String> exchangeCodeForToken(String shop, String code) {
        return webClient.post()
            .uri("/admin/oauth/access_token")
            .bodyValue(String.format("client_id=%s&client_secret=%s&code=%s", 
                    apiKey, apiSecret, code))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .retrieve()
            .bodyToMono(TokenResponse.class)
            .flatMap(tokenResponse -> {
                ShopifyToken token = new ShopifyToken();
                token.setShopDomain(shop);
                token.setAccessToken(tokenResponse.getAccessToken());
                token.setScopes(tokenResponse.getScopes());
                token.setExpiresIn(tokenResponse.getExpiresIn());
                token.setInstalledAt(Instant.now());
                
                tokenRepository.findByShopDomain(shop)
                    .ifPresent(existingToken -> token.setId(existingToken.getId()));
                
                tokenRepository.save(token);
                return Mono.just("Successfully authenticated and stored token for shop: " + shop);
            });
    }
}