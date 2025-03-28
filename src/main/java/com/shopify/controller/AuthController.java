package com.shopify.controller;

import com.shopify.service.ShopifyService;
import com.shopify.util.ShopifyHmacValidator;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;
import java.net.URI;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {
    
    private final ShopifyService shopifyService;

    @Autowired
    public AuthController(ShopifyService shopifyService) {
        this.shopifyService = shopifyService;
    }
    
    @Value("${shopify.api.secret}")
    private String apiSecret;

    @GetMapping("/install")
    public Mono<Void> install(@RequestParam String shop, ServerHttpResponse response) {
        String state = UUID.randomUUID().toString();
        String redirectUrl = "https://" + shop + "/admin/oauth/authorize" +
            "?client_id=" + shopifyService.getApiKey() +
            "&scope=read_products,write_products" +
            "&state=" + state +
            "&redirect_uri=https://your-app-url.com/auth/callback";
        
        response.addCookie(createStateCookie(state));
        response.setStatusCode(HttpStatus.SEE_OTHER);
        response.getHeaders().setLocation(URI.create(redirectUrl));
        return response.setComplete();
    }
    
    @GetMapping("/callback")
    public Mono<String> callback(@RequestParam Map<String, String> params, 
                               @CookieValue(name = "state", required = false) String stateCookie) {
        
        // Verify HMAC
        if (!ShopifyHmacValidator.validateHmac(params, apiSecret)) {
            return Mono.error(new SecurityException("Invalid HMAC"));
        }

        // Verify state parameter
        if (!params.get("state").equals(stateCookie)) {
            return Mono.error(new SecurityException("Invalid state parameter"));
        }

        return shopifyService.exchangeCodeForToken(params.get("shop"), params.get("code"));
    }

    private ResponseCookie createStateCookie(String state) {
        return ResponseCookie.from("state", state)
            .httpOnly(true)
            .secure(true)
            .path("/")
            .maxAge(300)
            .sameSite("Lax")
            .build();
    }
}