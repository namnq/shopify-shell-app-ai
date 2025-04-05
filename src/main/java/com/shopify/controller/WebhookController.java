package com.shopify.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopify.repository.ShopifyTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private final ShopifyTokenRepository tokenRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public WebhookController(ShopifyTokenRepository tokenRepository, ObjectMapper objectMapper) {
        this.tokenRepository = tokenRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/uninstall")
    public ResponseEntity<String> handleUninstall(@RequestBody String payload,
                                               @RequestHeader("X-Shopify-Shop-Domain") String shopDomain) {
        try {
            System.out.println("Received uninstall webhook for shop: " + shopDomain);
            System.out.println("Payload: " + payload);
            
            // Parse the payload to extract shop domain if not provided in header
            if (shopDomain == null || shopDomain.isEmpty()) {
                JsonNode jsonPayload = objectMapper.readTree(payload);
                shopDomain = jsonPayload.path("myshopify_domain").asText();
                
                if (shopDomain == null || shopDomain.isEmpty()) {
                    shopDomain = jsonPayload.path("domain").asText();
                }
            }
            
            if (shopDomain != null && !shopDomain.isEmpty()) {
                System.out.println("Removing token for shop: " + shopDomain);
                tokenRepository.deleteByShopDomain(shopDomain);
                System.out.println("Token removed successfully");
            } else {
                System.out.println("Warning: Could not determine shop domain from webhook");
            }
            
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            System.err.println("Error processing uninstall webhook: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok().build(); // Always return 200 to Shopify
        }
    }
}