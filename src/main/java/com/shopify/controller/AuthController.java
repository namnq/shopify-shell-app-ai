package com.shopify.controller;

import com.shopify.service.ShopifyService;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;
import java.net.URI;

@RestController
@RequestMapping("/auth")
public class AuthController {
    
    private final ShopifyService shopifyService;

    @Autowired
    public AuthController(ShopifyService shopifyService) {
        this.shopifyService = shopifyService;
    }
    
    @GetMapping("/install")
    public Mono<Void> install(@RequestParam String shop, ServerHttpResponse response) {
        String redirectUrl = "https://" + shop + "/admin/oauth/authorize" +
            "?client_id=" + shopifyService.getApiKey() +
            "&scope=read_products,write_products" +
            "&redirect_uri=https://your-app-url.com/auth/callback";
        response.setStatusCode(org.springframework.http.HttpStatus.SEE_OTHER);
        response.getHeaders().setLocation(URI.create(redirectUrl));
        return response.setComplete();
    }
    
    @GetMapping("/callback")
    public Mono<String> callback(@RequestParam String code, @RequestParam String shop) {
        return shopifyService.exchangeCodeForToken(shop, code);
    }
}