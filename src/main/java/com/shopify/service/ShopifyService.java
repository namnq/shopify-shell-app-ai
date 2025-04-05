package com.shopify.service;

import com.shopify.model.ShopifyToken;
import com.shopify.model.dto.TokenResponse;
import com.shopify.repository.ShopifyTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ShopifyService {
    private final WebClient webClient;
    private final ShopifyTokenRepository tokenRepository;
    private final ShopifyTokenService tokenService;
    private final ObjectMapper objectMapper;
    
    @Value("${shopify.api.key}")
    private String apiKey;
    
    @Value("${shopify.api.secret}")
    private String apiSecret;
    
    @Value("${shopify.api.version}")
    private String apiVersion;
    
    @Value("${shopify.api.scopes}")
    private String scopes;
    
    @Value("${shopify.api.redirectUrl}")
    private String redirectUrl;
    
    public String getApiKey() {
        return apiKey;
    }
    
    public String getApiSecret() {
        return apiSecret;
    }
    
    public String getScopes() {
        return scopes;
    }
    
    public String getRedirectUrl() {
        return redirectUrl;
    }
    
    public ShopifyService(WebClient.Builder webClientBuilder,
                          ShopifyTokenRepository tokenRepository,
                          ShopifyTokenService tokenService,
                          ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.baseUrl("https://{shop}").build();
        this.tokenRepository = tokenRepository;
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }
    
    public boolean validateToken(String token) {
        try {
            String shopDomain = tokenService.getShopDomainFromToken(token);
            return tokenRepository.existsByShopDomain(shopDomain);
        } catch (Exception e) {
            return false;
        }
    }
    
    public String getShopDomainFromToken(String token) {
        return tokenService.getShopDomainFromToken(token);
    }

    public String getAccessTokenForShop(String shopDomain) {
        Optional<ShopifyToken> token = tokenRepository.findByShopDomain(shopDomain);
        return token.map(ShopifyToken::getAccessToken).orElse(null);
    }
    
    public String getShopInfo(String shopDomain) {
        String accessToken = getAccessTokenForShop(shopDomain);
        if (accessToken == null) {
            throw new IllegalStateException("No access token found for shop: " + shopDomain);
        }
        
        return webClient.get()
            .uri("https://{shop}/admin/api/{version}/shop.json", shopDomain, apiVersion)
            .header("X-Shopify-Access-Token", accessToken)
            .retrieve()
            .bodyToMono(String.class)
            .block();
    }
    
    /**
     * Exchange an authorization code for an access token
     * @param shop Shop domain
     * @param code Authorization code from OAuth flow
     * @return String containing the JSON response with the access token
     */
    public String exchangeCodeForToken(String shop, String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("Authorization code cannot be null or empty");
        }
        
        // Normalize shop domain to ensure consistent format
        String shopDomain = shop.contains(".myshopify.com") ? shop : shop + ".myshopify.com";
        
        System.out.println("=== Starting OAuth token exchange ===");
        System.out.println("Shop Domain: " + shopDomain);
        System.out.println("API Key: " + apiKey);
        System.out.println("API Secret: " + apiSecret.substring(0, 4) + "...");
        System.out.println("Code length: " + code.length());
        
        String tokenUrl = "https://" + shopDomain + "/admin/oauth/access_token";
        System.out.println("Token URL: " + tokenUrl);
        
        try {
            // Create a specific WebClient instance with the exact shop domain
            String response = WebClient.builder()
                .baseUrl("https://" + shopDomain)
                .build()
                .post()
                .uri("/admin/oauth/access_token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("client_id", apiKey)
                    .with("client_secret", apiSecret)
                    .with("code", code))
                .retrieve()
                .bodyToMono(String.class)
                .doOnSubscribe(s -> System.out.println("Sending request to Shopify..."))
                .doOnNext(r -> System.out.println("Received response from Shopify"))
                .block(); // Convert to blocking call
                
            System.out.println("Success! Response received");
            return response;
        } catch (Exception e) {
            System.err.println("=== OAuth Exchange Error ===");
            if (e instanceof WebClientResponseException) {
                WebClientResponseException wcre = (WebClientResponseException) e;
                System.err.println("HTTP Status: " + wcre.getStatusCode());
                System.err.println("Response Headers: " + wcre.getHeaders());
                System.err.println("Response Body: " + wcre.getResponseBodyAsString());
            }
            System.err.println("Error Class: " + e.getClass().getName());
            System.err.println("Error Message: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to exchange code for token", e);
        }
    }
    
    /**
     * Get token information as a TokenResponse object from an access token response string
     * @param tokenResponseJson The JSON response string from token exchange
     * @return TokenResponse object with parsed token information
     */
    public TokenResponse parseTokenResponse(String tokenResponseJson) {
        try {
            return objectMapper.readValue(tokenResponseJson, TokenResponse.class);
        } catch (Exception e) {
            System.err.println("Error parsing token response: " + e.getMessage());
            throw new RuntimeException("Failed to parse token response", e);
        }
    }
}