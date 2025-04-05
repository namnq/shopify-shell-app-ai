package com.shopify.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopify.model.ShopifyToken;
import com.shopify.model.dto.TokenResponse;
import com.shopify.repository.ShopifyTokenRepository;
import com.shopify.service.ShopifyService;
import com.shopify.service.ShopifyTokenService;
import com.shopify.service.WebhookService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final int MAX_RETRIES = 3;
    private static final String MYSHOPIFY_DOMAIN = ".myshopify.com";
    private static final int STATE_COOKIE_MAX_AGE = 300; // 5 minutes

    private final ShopifyService shopifyService;
    private final ShopifyTokenService tokenService;
    private final ShopifyTokenRepository tokenRepository;

    @Value("${shopify.app.base-url}")
    private String appBaseUrl;

    public AuthController(ShopifyService shopifyService,
                        ShopifyTokenService tokenService,
                        ShopifyTokenRepository tokenRepository) {
        this.shopifyService = shopifyService;
        this.tokenService = tokenService;
        this.tokenRepository = tokenRepository;
    }

    @GetMapping("/login")
    public void login(
                    @RequestParam(required = false) String embedded,
                    @RequestParam(required = false) String shop,
                    @RequestParam(required = false) String host,
                    @RequestParam(required = false, name = "id_token") String tokenId,
                    HttpServletResponse response) {
        log.info("Initiating login flow for shop: {}", shop);
        if(isNeedAuthenticate(shop, host, tokenId, response))
            initiateOAuthFlow(shop, response, embedded);
    }

    @GetMapping("/callback")
    public void callback(@RequestParam Map<String, String> params,
                        @CookieValue(name = "state", required = false) String stateCookie,
                        HttpServletResponse response) {
        log.info("Processing OAuth callback for params: {}", params);
        processOAuthCallback(params, stateCookie, response);
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verifyAuth(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        log.debug("Verifying authentication token");
        return verifyAuthenticationToken(authHeader);
    }

    private boolean isNeedAuthenticate(String shop, String host, String tokenId, HttpServletResponse response) {
        if (!isValidShopDomain(shop)) {
            sendErrorResponse(response, HttpStatus.BAD_REQUEST, "Invalid shop parameter");
            return false;
        }

        if (isAlreadyAuthenticated(shop, tokenId)) {
            redirectToApp(response, host, shop);
            return false;
        }
        return true;
     
    }

    private boolean isValidShopDomain(String shop) {
        return StringUtils.isNotBlank(shop) &&
               (shop.endsWith(MYSHOPIFY_DOMAIN) || !shop.contains("."));
    }

    private boolean isAlreadyAuthenticated(String shop, String token) {
        return StringUtils.isNotBlank(token) &&
               tokenRepository.existsByShopDomain(shop) &&
               tokenService.validateToken(token) &&
               shop.equals(tokenService.getShopDomainFromToken(token));
    }

    private void initiateOAuthFlow(String shop, HttpServletResponse response, String embedded) {
        try {
            String state = UUID.randomUUID().toString();
            String normalizedShop = normalizeShopDomain(shop);
            String authUrl = buildAuthorizationUrl(normalizedShop, state);

            setStateCookie(response, state);
            if (StringUtils.isNotBlank(embedded)) {
                //redirect to authUrl by render a html shopify app bridge redirect
                response.setContentType("text/html");
                response.setStatus(HttpStatus.OK.value());
                response.getWriter().write(createAppBridgeRedirectHtml(authUrl));
                return;
                
            }
            redirect(response, authUrl, HttpStatus.SEE_OTHER);
            
            log.info("Initiated OAuth flow for shop: {}", normalizedShop);
        } catch (Exception e) {
            log.error("Error initiating OAuth flow", e);
            sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR, "Failed to initiate OAuth flow");
        }
    }
    
    private String createAppBridgeRedirectHtml(String authUrl) {
        return String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Redirecting sto Shopify...</title>
                    <script src="https://unpkg.com/@shopify/app-bridge"></script>
                </head>
                <body>
                    <script>
                        document.addEventListener('DOMContentLoaded', function() {
                            var AppBridge = window['app-bridge'];
                            var createApp = AppBridge.default;
                            var actions = AppBridge.actions;
                            var Redirect = actions.Redirect;
                            
                            try {
                                var app = createApp({
                                    apiKey: '%s',
                                    host: new URLSearchParams(window.location.search).get('host')
                                });
                                
                                var redirect = Redirect.create(app);
                                redirect.dispatch(Redirect.Action.ADMIN_PATH, '%s');
                            } catch (error) {
                                console.error('App Bridge redirection error:', error);
                                // Fallback to regular redirect
                                window.location.href = '%s';
                            }
                        });
                    </script>
                    <p>Redirecting to Shopify authorization...</p>
                </body>
                </html>
                """, shopifyService.getApiKey(), authUrl, authUrl);
    }
    
    private String normalizeShopDomain(String shop) {
        return shop.contains(MYSHOPIFY_DOMAIN) ? shop : shop + MYSHOPIFY_DOMAIN;
    }

    private String buildAuthorizationUrl(String shop, String state) {
        return String.format("/admin/oauth/authorize" +
                           "?client_id=%s" +
                           "&scope=%s" +
                           "&state=%s" +
                           "&redirect_uri=%s",
                           shopifyService.getApiKey(),
                           shopifyService.getScopes(),
                           state,
                           shopifyService.getRedirectUrl());
    }

    private void setStateCookie(HttpServletResponse response, String state) {
        Cookie stateCookie = new Cookie("state", state);
        stateCookie.setHttpOnly(true);
        stateCookie.setSecure(true);
        stateCookie.setPath("/");
        stateCookie.setMaxAge(STATE_COOKIE_MAX_AGE);
        response.addCookie(stateCookie);
    }

    private void processOAuthCallback(Map<String, String> params, String stateCookie, HttpServletResponse response) {
        try {
            validateCallbackParameters(params, stateCookie);
            String accessToken = exchangeCodeForTokenWithRetry(params.get("shop"), params.get("code"));
            saveShopifyToken(params.get("shop"), accessToken);
            completeAuthentication(params.get("host"), params.get("shop"), response);
        } catch (Exception e) {
            handleCallbackError(response, e, params.get("shop"));
        }
    }

    private void validateCallbackParameters(Map<String, String> params, String stateCookie) {
        String shop = params.get("shop");
        String code = params.get("code");
        String state = params.get("state");

        if (StringUtils.isAnyBlank(shop, code)) {
            throw new IllegalArgumentException("Missing required parameters");
        }

        if (stateCookie != null && state != null && !stateCookie.equals(state)) {
            throw new IllegalArgumentException("State validation failed");
        }
    }

    private String exchangeCodeForTokenWithRetry(String shop, String code) throws Exception {
        Exception lastException = null;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return shopifyService.exchangeCodeForToken(shop, code);
            } catch (Exception e) {
                lastException = e;
                if (shouldRetry(e, attempt)) {
                    backoff(attempt);
                    continue;
                }
                throw e;
            }
        }
        
        throw Optional.ofNullable(lastException)
                     .orElse(new RuntimeException("Failed to exchange code for token"));
    }

    private boolean shouldRetry(Exception e, int attempt) {
        return (e instanceof java.net.SocketException ||
                e.getCause() instanceof java.net.SocketException) &&
               attempt < MAX_RETRIES - 1;
    }

    private void backoff(int attempt) throws InterruptedException {
        Thread.sleep((long) Math.pow(2, attempt) * 1000);
    }

    protected void saveShopifyToken(String shop, String accessToken) {
        TokenResponse tokenResponse = shopifyService.parseTokenResponse(accessToken);
        Instant now = Instant.now();

        ShopifyToken token = tokenRepository.findByShopDomain(shop)
                .orElseGet(() -> createNewToken(shop, now));

        updateToken(token, tokenResponse, now);
        tokenRepository.save(token);
        log.info("Saved token for shop: {}", shop);
    }

    private ShopifyToken createNewToken(String shop, Instant now) {
        ShopifyToken token = new ShopifyToken();
        token.setShopDomain(shop);
        token.setCreatedAt(now);
        return token;
    }

    private void updateToken(ShopifyToken token, TokenResponse tokenResponse, Instant now) {
        token.setAccessToken(tokenResponse.getAccessToken());
        token.setScope(tokenResponse.getScope());
        token.setInstalledAt(now);
        if (tokenResponse.getExpiresIn() != null) {
            token.setExpiresAt(now.plusSeconds(tokenResponse.getExpiresIn()));
        }
    }

    protected void completeAuthentication(String host, String shop, HttpServletResponse response) {
//        setSecurityHeaders(response);
        redirectToApp(response, host, shop);
        log.info("Completed authentication for shop: {}", shop);
    }

    private void setSecurityHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
    }

    private void redirectToApp(HttpServletResponse response, String host, String shop) {
        String redirectUrl = buildRedirectUrl(host, shop);
        redirect(response, redirectUrl, HttpStatus.FOUND);
    }

    private String buildRedirectUrl(String host, String shop) {
        StringBuilder url = new StringBuilder(appBaseUrl);
        if (!appBaseUrl.contains("?")) {
            url.append('?');
        } else if (!appBaseUrl.endsWith("&")) {
            url.append('&');
        }

        if (shop != null) {
            url.append("shop=").append(shop);
        }
        if (host != null) {
            if (shop != null) url.append('&');
            url.append("host=").append(host);
        }

        return url.toString();
    }

    private void redirect(HttpServletResponse response, String url, HttpStatus status) {
        response.setStatus(status.value());
        response.setHeader(HttpHeaders.LOCATION, url);
        response.setHeader("content-security-policy", "frame-ancestors *");
        
    }

    private void handleCallbackError(HttpServletResponse response, Exception e, String shop) {
        log.error("OAuth callback error", e);
        if (e instanceof java.net.SocketException) {
            sendRetryResponse(response, shop);
        } else {
            throw new RuntimeException("OAuth callback failed", e);
        }
    }

    private void sendRetryResponse(HttpServletResponse response, String shop) {
        response.setContentType("text/html");
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        try {
            response.getWriter().write(
                String.format("""
                    <html><body>
                    <h1>Connection Error</h1>
                    <p>We're having trouble connecting to Shopify. Please try again.</p>
                    <p><a href="/api/auth/login?shop=%s">Retry</a></p>
                    </body></html>""",
                    shop));
        } catch (Exception e) {
            log.error("Error writing retry response", e);
        }
    }

    private ResponseEntity<?> verifyAuthenticationToken(String authHeader) {
        if (!isValidAuthHeader(authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.substring(7);
        try {
            return tokenService.validateToken(token) ?
                   ResponseEntity.ok().build() :
                   ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            log.error("Token validation error", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    private boolean isValidAuthHeader(String authHeader) {
        return StringUtils.isNotBlank(authHeader) && authHeader.startsWith("Bearer ");
    }

    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message) {
        response.setStatus(status.value());
        log.error(message);
    }
}