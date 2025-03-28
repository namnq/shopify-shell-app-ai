package com.shopify.service;

import com.shopify.model.ShopifyToken;
import com.shopify.model.dto.TokenResponse;
import com.shopify.repository.ShopifyTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.time.Instant;

@Service
public class ShopifyService {
    private final WebClient webClient;
    private final ShopifyTokenRepository tokenRepository;
    
    @Value("${shopify.api.key}")
    private String apiKey;
    
    @Value("${shopify.api.secret}")
    private String apiSecret;

    public String getApiKey() {
        return apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public ShopifyService(WebClient.Builder webClientBuilder, ShopifyTokenRepository tokenRepository) {
        this.webClient = webClientBuilder.baseUrl("https://{shop}.myshopify.com").build();
        this.tokenRepository = tokenRepository;
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