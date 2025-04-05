package com.shopify.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class WebhookService {
    @Value("${shopify.api.version}")
    private String apiVersion;
    
    @Value("${shopify.webhooks.uninstallUrl}")
    private String uninstallWebhookUrl;

    private final WebClient webClient;

    public WebhookService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public void registerWebhooks(String shopDomain, String accessToken) {
        String apiUrl = "https://" + shopDomain + "/admin/api/" + apiVersion + "/webhooks.json";
        
        System.out.println("Registering app uninstall webhook at: " + uninstallWebhookUrl);

        try {
            webClient.post()
                .uri(apiUrl)
                .header("X-Shopify-Access-Token", accessToken)
                .bodyValue(buildWebhookPayload())
                .retrieve()
                .bodyToMono(String.class)
                .block();
                
            System.out.println("Webhook registered successfully");
        } catch (Exception e) {
            System.err.println("Failed to register webhook: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private WebhookRegistration buildWebhookPayload() {
        return new WebhookRegistration(
            new Webhook(
                "app/uninstalled",
                uninstallWebhookUrl,
                "json"
            )
        );
    }

    private record WebhookRegistration(Webhook webhook) {}
    private record Webhook(String topic, String address, String format) {}
}