package com.shopify.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class ShopifyService {
    private final WebClient webClient;
    
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

    public ShopifyService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://{shop}.myshopify.com").build();
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
            .bodyToMono(String.class)
            .map(response -> {
                // In production, parse JSON and store the access token
                return "Successfully authenticated with shop: " + shop;
            });
    }
}