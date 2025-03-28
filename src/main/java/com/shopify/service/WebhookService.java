package com.shopify.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class WebhookService {
    @Value("${shopify.api.version}")
    private String apiVersion;

    private final WebClient webClient;

    public WebhookService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public Mono<Void> registerWebhooks(String shopDomain, String accessToken) {
        String apiUrl = "https://" + shopDomain + "/admin/api/" + apiVersion + "/webhooks.json";

        return webClient.post()
            .uri(apiUrl)
            .header("X-Shopify-Access-Token", accessToken)
            .bodyValue(buildWebhookPayload())
            .retrieve()
            .bodyToMono(Void.class);
    }

    private WebhookRegistration buildWebhookPayload() {
        return new WebhookRegistration(
            new Webhook(
                "app/uninstalled",
                "https://your-app-url.com/api/webhooks/uninstall",
                "json"
            )
        );
    }

    private record WebhookRegistration(Webhook webhook) {}
    private record Webhook(String topic, String address, String format) {}
}