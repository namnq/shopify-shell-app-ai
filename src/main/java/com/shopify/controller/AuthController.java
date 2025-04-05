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
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
public class AuthController {
  
  private final ShopifyService shopifyService;
  private final WebhookService webhookService;
  private final ShopifyTokenService tokenService;
  private final ObjectMapper objectMapper;
  private final ShopifyTokenRepository tokenRepository;
  
  @Value("${shopify.api.secret}")
  private String apiSecret;
  
  @Value("${shopify.app.base-url}")
  private String appBaseUrl;
  
  @Autowired
  public AuthController(ShopifyService shopifyService, WebhookService webhookService,
                        ShopifyTokenService tokenService, ObjectMapper objectMapper,
                        ShopifyTokenRepository tokenRepository) {
    this.shopifyService = shopifyService;
    this.webhookService = webhookService;
    this.tokenService = tokenService;
    this.objectMapper = objectMapper;
    this.tokenRepository = tokenRepository;
  }
  
  // Original endpoint path - kept for compatibility
  @GetMapping("/auth/login")
  public void standardLogin(@RequestParam(required = false) String shop,
                            @RequestParam(required = false) String host,
                            @RequestParam(required = false, name = "id_token") String tokenId,
                            HttpServletResponse response) {
    handleLoginWithAuth(shop,host, response, tokenId);
  }
  
  
  /**
   * Handle login request with optional auth header
   */
  private void handleLoginWithAuth(String shop, String host, HttpServletResponse response, String tokenId) {
    System.out.println("=== OAuth Login Start ===");
    System.out.println("Shop parameter: " + shop);
    if (isInvalidShopParameter(shop, response)) {
      return;
    }
    if(tokenRepository.existsByShopDomain(shop)) {
      // Check if already authenticated for this shop
      if (isAlreadyAuthenticated(shop, host, response, tokenId)) {
        return;
      }
    }
 
    
    // If not authenticated, proceed with OAuth
    initiateOAuthFlow(shop, response);
  }
  
  /**
   * Check if shop parameter is valid
   */
  private boolean isInvalidShopParameter(String shop, HttpServletResponse response) {
    if (shop == null || shop.isEmpty()) {
      System.out.println("Error: Missing shop parameter");
      response.setStatus(HttpStatus.BAD_REQUEST.value());
      return true;
    }
    return false;
  }
  
  /**
   * Check if the user is already authenticated for this shop
   */
  private boolean isAlreadyAuthenticated(String shop, String host, HttpServletResponse response, String token) {
    if (StringUtils.isNotBlank(token)) {
      try {
        if (tokenService.validateToken(token)) {
          String tokenShopDomain = tokenService.getShopDomainFromToken(token);
          
          // If token is valid and for the same shop, redirect to app home
          if (tokenShopDomain != null && tokenShopDomain.equals(shop)) {
            System.out.println("User already authenticated for shop: " + shop);
            redirectToAppHome(response, host , shop);
            return true;
          }
        }
      } catch (Exception e) {
        System.err.println("Error validating token: " + e.getMessage());
        // Continue with OAuth flow if token validation fails
      }
    }
    return false;
  }
  
  /**
   * Redirect the user to the app's home page
   */
  private void redirectToAppHome(HttpServletResponse response,String host, String shop) {
    String redirectUrl = buildAppHomeRedirectUrl(host, shop);
    response.setStatus(HttpServletResponse.SC_FOUND); // 302 Found
    response.setHeader(HttpHeaders.LOCATION, redirectUrl);
  }
  
  /**
   * Initiate the OAuth flow by redirecting to Shopify
   */
  private void initiateOAuthFlow(String shop, HttpServletResponse response) {
    System.out.println("=== OAuth Login Start ===");
    System.out.println("Shop parameter: " + shop);
    
    try {
      String state = generateStateToken();
      String callbackUrl = shopifyService.getRedirectUrl();
      String redirectUrl = buildShopifyAuthUrl(shop, state, callbackUrl);
      
      setStateCookie(response, state);
      redirectToShopify(response, redirectUrl);
    } catch (Exception e) {
      handleOAuthError(response, e, "Error in login endpoint");
    }
  }
  
  /**
   * Generate a random state token for OAuth security
   */
  private String generateStateToken() {
    String state = UUID.randomUUID().toString();
    System.out.println("Generated state: " + state);
    return state;
  }
  
  /**
   * Build the URL for Shopify's authorization page
   */
  private String buildShopifyAuthUrl(String shop, String state, String callbackUrl) {
    System.out.println("Callback URL: " + callbackUrl);
    
    String redirectUrl = "https://" + shop + "/admin/oauth/authorize" +
        "?client_id=" + shopifyService.getApiKey() +
        "&scope=" + shopifyService.getScopes() +
        "&state=" + state +
        "&redirect_uri=" + callbackUrl;
    
    System.out.println("Full redirect URL: " + redirectUrl);
    return redirectUrl;
  }
  
  /**
   * Set the state cookie for OAuth state validation
   */
  private void setStateCookie(HttpServletResponse response, String state) {
    Cookie stateCookie = new Cookie("state", state);
    stateCookie.setHttpOnly(true);
    stateCookie.setSecure(true);
    stateCookie.setPath("/");
    stateCookie.setMaxAge(300);
    response.addCookie(stateCookie);
    System.out.println("State cookie set");
  }
  
  /**
   * Redirect the user to Shopify's authorization page
   */
  private void redirectToShopify(HttpServletResponse response, String redirectUrl) {
    System.out.println("Redirecting to Shopify...");
    response.setStatus(HttpStatus.SEE_OTHER.value());
    response.setHeader(HttpHeaders.LOCATION, redirectUrl);
  }
  
  /**
   * Handle OAuth callback from Shopify
   */
  @GetMapping("/auth/callback")
  public void callback(@RequestParam Map<String, String> params,
                       @CookieValue(name = "state", required = false) String stateCookie,
                       HttpServletResponse response) throws Exception {
    System.out.println("\n=== OAuth Callback Start ===");
    System.out.println("Received parameters: " + params);
    System.out.println("State cookie value: " + stateCookie);
    
    try {
      String shop = params.get("shop");
      String code = params.get("code");
      String host = params.get("host");
      String state = params.get("state");
      validateCallbackParameters(code, shop, state, stateCookie);

      // Exchange code for access token with retry for network issues
      int maxRetries = 3;
      String accessToken = null;
      Exception lastException = null;
      
      for (int attempt = 0; attempt < maxRetries; attempt++) {
        try {
          System.out.println("Attempting to exchange code for token (attempt " + (attempt + 1) + ")");
          accessToken = exchangeCodeForAccessToken(shop, code);
          lastException = null;
          break; // Success, exit retry loop
        } catch (java.net.SocketException se) {
          lastException = se;
          System.err.println("Connection error on attempt " + (attempt + 1) + ": " + se.getMessage());
          if (attempt < maxRetries - 1) {
            // Exponential backoff
            long backoffMs = (long) Math.pow(2, attempt) * 1000;
            System.out.println("Retrying in " + backoffMs + "ms...");
            Thread.sleep(backoffMs);
          }
        } catch (Exception e) {
          lastException = e;
          System.err.println("Non-connection error: " + e.getMessage());
          if (e.getCause() instanceof java.net.SocketException) {
            if (attempt < maxRetries - 1) {
              long backoffMs = (long) Math.pow(2, attempt) * 1000;
              System.out.println("Underlying connection error, retrying in " + backoffMs + "ms...");
              Thread.sleep(backoffMs);
            }
          } else {
            throw e; // Non-connection error, don't retry
          }
        }
      }
      
      if (lastException != null) {
        throw lastException; // If all retries failed, throw the last exception
      }
      
      // Save or update token in database
      saveShopifyToken(shop, accessToken);
      
      // Complete the authentication process
      completeAuthentication(host, shop, response);
      
    } catch (java.net.SocketException se) {
      System.err.println("\n=== Connection Reset Error ===");
      System.err.println("Connection reset during OAuth callback: " + se.getMessage());
      System.err.println("This is likely a network issue or Shopify API temporary unavailability");
      se.printStackTrace();
      
      // Provide a more graceful error page
      response.setContentType("text/html");
      response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
      response.getWriter().write("<html><body><h1>Connection Error</h1>" +
                               "<p>We're having trouble connecting to Shopify. Please try again.</p>" +
                               "<p><a href=\"/api/auth/login?shop=" + params.get("shop") + "\">Retry</a></p>" +
                               "</body></html>");
    } catch (Exception e) {
      handleCallbackError(e);
      throw e;
    }
  }
  
  /**
   * Validate the callback parameters
   */
  private void validateCallbackParameters(String code, String shop, String state, String stateCookie) {

    
    System.out.println("Code present: " + (code != null));
    System.out.println("Shop: " + shop);
    System.out.println("State from params: " + state);
    
    if (code == null || shop == null) {
      throw new IllegalArgumentException("Missing required parameters");
    }
    
    // Validate state if present
    if (stateCookie != null && state != null && !stateCookie.equals(state)) {
      throw new IllegalArgumentException("State validation failed");
    }
  }
  
  /**
   * Exchange the code for an access token
   */
  private String exchangeCodeForAccessToken(String shop, String code) throws Exception {
    System.out.println("Exchanging code for access token...");
    String tokenResponseJson = shopifyService.exchangeCodeForToken(shop, code);
    System.out.println("Access token received successfully");
    return tokenResponseJson;
  }
  
  /**
   * Save or update the shop's token in the database using an upsert pattern
   */
  @Transactional
  protected void saveShopifyToken(String shop, String accessToken) throws Exception {
    System.out.println("Saving token for shop: " + shop);
    
    try {
      // Parse token information
      TokenResponse tokenResponse = shopifyService.parseTokenResponse(accessToken);
      Instant now = Instant.now();
      
      // Find existing token or create new one (upsert pattern)
      ShopifyToken shopifyToken = tokenRepository.findByShopDomain(shop)
          .orElseGet(() -> {
            System.out.println("Creating new token record for shop: " + shop);
            ShopifyToken newToken = new ShopifyToken();
            newToken.setShopDomain(shop);
            newToken.setCreatedAt(now);
            return newToken;
          });
      
      // Update token fields
      updateTokenFields(shopifyToken, tokenResponse, now);
      
      // Save to database
      tokenRepository.save(shopifyToken);
      System.out.println("Token saved successfully for shop: " + shop);
      
    } catch (Exception e) {
      System.err.println("Error saving token: " + e.getMessage());
      throw e;
    }
  }
  
  /**
   * Update the token fields with new values
   */
  private void updateTokenFields(ShopifyToken token, TokenResponse tokenResponse, Instant now) {
    token.setAccessToken(tokenResponse.getAccessToken());
    token.setScope(tokenResponse.getScope());
    token.setInstalledAt(now);
    
    // Set expiration time if available
    if (tokenResponse.getExpiresIn() != null) {
      token.setExpiresAt(now.plusSeconds(tokenResponse.getExpiresIn()));
    }
  }
  
  /**
   * Complete the authentication process and redirect to the app
   */
  protected void completeAuthentication(String host, String shop,
                                       HttpServletResponse response) {
    try {
      // Register webhooks
      System.out.println("Registering webhooks...");
//            webhookService.registerWebhooks(shop, accessToken);
      System.out.println("Webhooks registered successfully");
      
      // Set shop and host in cookies (not HttpOnly so they're accessible to JS)
      
      // Add cache control headers to prevent caching
      response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
      response.setHeader("Pragma", "no-cache");
      response.setHeader("Expires", "0");
      
      // Redirect to the application with the necessary parameters
      redirectToAppHome(response, host, shop);
      System.out.println("=== OAuth Callback Complete ===");
      
    } catch (JwtEncodingException jee) {
      System.err.println("JWT encoding error in callback: " + jee.getMessage());
      System.err.println("Root cause: " + (jee.getCause() != null ? jee.getCause().getMessage() : "unknown"));
      throw jee;
    }
  }
  
  /**
   * Build the redirect URL to return to the app
   */
  private String buildAppHomeRedirectUrl(String host , String shop) {
    StringBuilder redirectUrl = new StringBuilder(appBaseUrl);
    // Add query parameter separator if needed
    if (!appBaseUrl.contains("?")) {
      redirectUrl.append("?");
    } else if (!appBaseUrl.endsWith("&")) {
      redirectUrl.append("&");
    }
    
    // Only add shop and host as these are required for App Bridge
    if (shop != null) redirectUrl.append("shop=").append(shop);
    if (host != null) {
      if (shop != null) redirectUrl.append("&");
      redirectUrl.append("host=").append(host);
    }
    
    return redirectUrl.toString();
  }
  
  /**
   * Handle errors in the OAuth callback
   */
  private void handleCallbackError(Exception e) {
    System.err.println("\n=== OAuth Callback Error ===");
    System.err.println("Error type: " + e.getClass().getName());
    System.err.println("Error message: " + e.getMessage());
    if (e.getCause() != null) {
      System.err.println("Cause: " + e.getCause().getMessage());
    }
    e.printStackTrace();
  }
  
  /**
   * Handle errors in the OAuth flow
   */
  private void handleOAuthError(HttpServletResponse response, Exception e, String context) {
    System.err.println(context + ":");
    e.printStackTrace();
    response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
  }
  
  /**
   * Verify authentication token
   */
  @GetMapping("/auth/verify")
  public ResponseEntity<?> verifyAuth(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
    System.out.println("\n=== Token Verification Start ===");
    System.out.println("Auth header present: " + (authHeader != null));
    
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      System.out.println("Error: Missing or invalid Authorization header");
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    
    String token = authHeader.substring(7);
    System.out.println("Token length: " + token.length());
    
    try {
      if (tokenService.validateToken(token)) {
        System.out.println("Token is valid");
        return ResponseEntity.ok().build();
      } else {
        System.out.println("Token is invalid or expired");
      }
    } catch (Exception e) {
      logTokenValidationError(e);
    }
    
    System.out.println("Token verification failed");
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }
  
  /**
   * Log token validation errors
   */
  private void logTokenValidationError(Exception e) {
    System.err.println("Token validation error:");
    System.err.println("Error type: " + e.getClass().getName());
    System.err.println("Error message: " + e.getMessage());
  }
}