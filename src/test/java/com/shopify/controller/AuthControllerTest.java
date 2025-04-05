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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthControllerTest {

    @Mock
    private ShopifyService shopifyService;

    @Mock
    private WebhookService webhookService;

    @Mock
    private ShopifyTokenService tokenService;

    @Mock
    private ShopifyTokenRepository tokenRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HttpServletResponse response;

    @Spy
    @InjectMocks
    private AuthController authController;

    private final String SHOP_DOMAIN = "test-shop.myshopify.com";
    private final String HOST_VALUE = "test-host";
    private final String STATE_VALUE = "test-state";
    private final String CODE_VALUE = "test-code";
    private final String TOKEN_VALUE = "test-token";
    private final String ACCESS_TOKEN = "test-access-token";

    @BeforeEach
    public void setup() {
//        ReflectionTestUtils.setField(authController, "apiSecret", "test-secret");
        ReflectionTestUtils.setField(authController, "appBaseUrl", "https://test-app.com");
    }

    @Test
    public void login_WithValidShopAndNoExistingToken_InitiatesOAuth() {
        // Arrange
        when(tokenRepository.existsByShopDomain(SHOP_DOMAIN)).thenReturn(false);
        when(shopifyService.getRedirectUrl()).thenReturn("https://test-app.com/auth/callback");
        when(shopifyService.getApiKey()).thenReturn("test-api-key");
        when(shopifyService.getScopes()).thenReturn("read_products,write_products");

        // Act
        authController.login(SHOP_DOMAIN, HOST_VALUE, null, response);

        // Assert
        verify(response).setStatus(HttpStatus.SEE_OTHER.value());
        verify(response).setHeader(eq(HttpHeaders.LOCATION), anyString());
        verify(response).addCookie(any(Cookie.class));
    }

    @Test
    public void login_WithValidShopAndExistingToken_ValidatesToken() {
        // Arrange
        when(tokenRepository.existsByShopDomain(SHOP_DOMAIN)).thenReturn(true);
        when(tokenService.validateToken(TOKEN_VALUE)).thenReturn(true);
        when(tokenService.getShopDomainFromToken(TOKEN_VALUE)).thenReturn(SHOP_DOMAIN);

        // Act
        authController.login(SHOP_DOMAIN, HOST_VALUE, TOKEN_VALUE, response);

        // Assert
        verify(response).setStatus(HttpServletResponse.SC_FOUND);
        verify(response).setHeader(eq(HttpHeaders.LOCATION), contains(SHOP_DOMAIN));
    }

    @Test
    public void login_WithMissingShop_ReturnsBadRequest() {
        // Act
        authController.login(null, HOST_VALUE, null, response);

        // Assert
        verify(response).setStatus(HttpStatus.BAD_REQUEST.value());
        verifyNoMoreInteractions(shopifyService);
    }

    @Test
    public void callback_WithValidParameters_ExchangesCodeAndSavesToken() throws Exception {
        // Arrange
        Map<String, String> params = new HashMap<>();
        params.put("shop", SHOP_DOMAIN);
        params.put("code", CODE_VALUE);
        params.put("state", STATE_VALUE);
        params.put("host", HOST_VALUE);

        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken(ACCESS_TOKEN);
        tokenResponse.setScope("read_products,write_products");
        tokenResponse.setExpiresIn(7200L);

        ShopifyToken newToken = new ShopifyToken();
        
        when(shopifyService.exchangeCodeForToken(SHOP_DOMAIN, CODE_VALUE)).thenReturn(ACCESS_TOKEN);
        when(shopifyService.parseTokenResponse(ACCESS_TOKEN)).thenReturn(tokenResponse);
        when(tokenRepository.findByShopDomain(SHOP_DOMAIN)).thenReturn(Optional.empty());
        when(tokenRepository.save(any(ShopifyToken.class))).thenReturn(newToken);
        
        // Don't mock saveShopifyToken - test the real implementation
        doCallRealMethod().when(authController).saveShopifyToken(anyString(), anyString());

        // Act
        authController.callback(params, STATE_VALUE, response);

        // Assert
        verify(shopifyService).exchangeCodeForToken(SHOP_DOMAIN, CODE_VALUE);
        verify(shopifyService).parseTokenResponse(ACCESS_TOKEN);
        
        // Verify that tokenRepository.save was called with the correct parameters
        ArgumentCaptor<ShopifyToken> tokenCaptor = ArgumentCaptor.forClass(ShopifyToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        
        ShopifyToken savedToken = tokenCaptor.getValue();
        assertEquals(SHOP_DOMAIN, savedToken.getShopDomain());
        assertEquals(ACCESS_TOKEN, savedToken.getAccessToken());
        assertEquals("read_products,write_products", savedToken.getScope());
        assertNotNull(savedToken.getExpiresAt());
        
        // Verify redirection
        verify(response).setHeader(eq(HttpHeaders.LOCATION), contains(SHOP_DOMAIN));
        verify(response).setHeader(eq("Cache-Control"), contains("no-store"));
    }

    @Test
    public void callback_WithMissingCode_ThrowsException() {
        // Arrange
        Map<String, String> params = new HashMap<>();
        params.put("shop", SHOP_DOMAIN);
        params.put("state", STATE_VALUE);

        // Act & Assert
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            authController.callback(params, STATE_VALUE, response);
        });

        assertTrue(exception.getMessage().contains("Missing required parameters"));
    }

    @Test
    public void callback_WithInvalidState_ThrowsException() {
        // Arrange
        Map<String, String> params = new HashMap<>();
        params.put("shop", SHOP_DOMAIN);
        params.put("code", CODE_VALUE);
        params.put("state", "invalid-state");

        // Act & Assert
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            authController.callback(params, STATE_VALUE, response);
        });

        assertTrue(exception.getMessage().contains("State validation failed"));
    }

    @Test
    public void callback_WithNetworkError_RetriesAndEventuallySucceeds() throws Exception {
        // Arrange
        Map<String, String> params = new HashMap<>();
        params.put("shop", SHOP_DOMAIN);
        params.put("code", CODE_VALUE);
        params.put("state", STATE_VALUE);
        params.put("host", HOST_VALUE);

        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken(ACCESS_TOKEN);
        tokenResponse.setScope("read_products,write_products");
        tokenResponse.setExpiresIn(7200L);

        ShopifyToken newToken = new ShopifyToken();
        newToken.setShopDomain(SHOP_DOMAIN);
        
        // First attempt fails with network error wrapped in RuntimeException, second succeeds
        when(shopifyService.exchangeCodeForToken(SHOP_DOMAIN, CODE_VALUE))
                .thenThrow(new RuntimeException("Failed to exchange code for token",
                        new java.net.SocketException("Connection reset")))
                .thenReturn(ACCESS_TOKEN);
                
        when(shopifyService.parseTokenResponse(ACCESS_TOKEN)).thenReturn(tokenResponse);
        when(tokenRepository.findByShopDomain(SHOP_DOMAIN)).thenReturn(Optional.empty());
        when(tokenRepository.save(any(ShopifyToken.class))).thenReturn(newToken);

        // Act
        authController.callback(params, STATE_VALUE, response);

        // Assert
        verify(shopifyService, times(2)).exchangeCodeForToken(SHOP_DOMAIN, CODE_VALUE);
        verify(shopifyService).parseTokenResponse(ACCESS_TOKEN);
        
        // Verify token was saved
        ArgumentCaptor<ShopifyToken> tokenCaptor = ArgumentCaptor.forClass(ShopifyToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        
        ShopifyToken savedToken = tokenCaptor.getValue();
        assertEquals(SHOP_DOMAIN, savedToken.getShopDomain());
        assertEquals(ACCESS_TOKEN, savedToken.getAccessToken());
        assertEquals("read_products,write_products", savedToken.getScope());
        assertNotNull(savedToken.getExpiresAt());
        
        // Verify redirection
        verify(response).setHeader(eq(HttpHeaders.LOCATION), contains(SHOP_DOMAIN));
        verify(response).setHeader(eq("Cache-Control"), contains("no-store"));
    }

    @Test
    public void callback_WithNetworkErrorExhaustingRetries_WritesErrorResponse() throws Exception {
        // Arrange
        Map<String, String> params = new HashMap<>();
        params.put("shop", SHOP_DOMAIN);
        params.put("code", CODE_VALUE);
        params.put("state", STATE_VALUE);
        params.put("host", HOST_VALUE);

        // Create a SocketException wrapped in RuntimeException
        RuntimeException networkException = new RuntimeException(
            "Failed to exchange code for token",
            new java.net.SocketException("Connection reset")
        );

        // All attempts fail with network error
        when(shopifyService.exchangeCodeForToken(SHOP_DOMAIN, CODE_VALUE))
            .thenThrow(networkException);

        // Act & Assert
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            authController.callback(params, STATE_VALUE, response);
        });

        // Verify the exception is a network error
        assertTrue(thrown.getCause() instanceof java.net.SocketException);
        assertEquals("Connection reset", thrown.getCause().getMessage());
        
        // Verify correct number of retry attempts (3 total attempts)
        verify(shopifyService, times(3)).exchangeCodeForToken(SHOP_DOMAIN, CODE_VALUE);
    }

    @Test
    public void saveShopifyToken_CreatesNewToken_WhenNotExists() throws Exception {
        // Arrange
        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken(ACCESS_TOKEN);
        tokenResponse.setScope("read_products,write_products");
        tokenResponse.setExpiresIn(7200L);

        when(shopifyService.parseTokenResponse(ACCESS_TOKEN)).thenReturn(tokenResponse);
        when(tokenRepository.findByShopDomain(SHOP_DOMAIN)).thenReturn(Optional.empty());

        ShopifyToken newToken = new ShopifyToken();
        when(tokenRepository.save(any(ShopifyToken.class))).thenReturn(newToken);

        // Act
        authController.saveShopifyToken(SHOP_DOMAIN, ACCESS_TOKEN);

        // Assert
        ArgumentCaptor<ShopifyToken> tokenCaptor = ArgumentCaptor.forClass(ShopifyToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        
        ShopifyToken savedToken = tokenCaptor.getValue();
        assertEquals(SHOP_DOMAIN, savedToken.getShopDomain());
        assertEquals(ACCESS_TOKEN, savedToken.getAccessToken());
        assertEquals("read_products,write_products", savedToken.getScope());
        assertNotNull(savedToken.getExpiresAt());
    }

    @Test
    public void saveShopifyToken_UpdatesExistingToken_WhenExists() throws Exception {
        // Arrange
        ShopifyToken existingToken = new ShopifyToken();
        existingToken.setShopDomain(SHOP_DOMAIN);
        existingToken.setAccessToken("old-token");
        existingToken.setScope("read_products");
        existingToken.setCreatedAt(Instant.now().minusSeconds(86400)); // 1 day ago

        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken(ACCESS_TOKEN);
        tokenResponse.setScope("read_products,write_products");
        tokenResponse.setExpiresIn(7200L);

        when(shopifyService.parseTokenResponse(ACCESS_TOKEN)).thenReturn(tokenResponse);
        when(tokenRepository.findByShopDomain(SHOP_DOMAIN)).thenReturn(Optional.of(existingToken));
        when(tokenRepository.save(any(ShopifyToken.class))).thenReturn(existingToken);

        // Act
        authController.saveShopifyToken(SHOP_DOMAIN, ACCESS_TOKEN);

        // Assert
        ArgumentCaptor<ShopifyToken> tokenCaptor = ArgumentCaptor.forClass(ShopifyToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        
        ShopifyToken savedToken = tokenCaptor.getValue();
        assertEquals(SHOP_DOMAIN, savedToken.getShopDomain());
        assertEquals(ACCESS_TOKEN, savedToken.getAccessToken());
        assertEquals("read_products,write_products", savedToken.getScope());
        assertNotNull(savedToken.getExpiresAt());
    }

    @Test
    public void verifyAuth_WithValidToken_ReturnsOk() {
        // Arrange
        String authHeader = "Bearer " + TOKEN_VALUE;
        when(tokenService.validateToken(TOKEN_VALUE)).thenReturn(true);

        // Act
        ResponseEntity<?> response = authController.verifyAuth(authHeader);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    public void verifyAuth_WithInvalidToken_ReturnsUnauthorized() {
        // Arrange
        String authHeader = "Bearer " + TOKEN_VALUE;
        when(tokenService.validateToken(TOKEN_VALUE)).thenReturn(false);

        // Act
        ResponseEntity<?> response = authController.verifyAuth(authHeader);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    public void verifyAuth_WithMissingAuthHeader_ReturnsUnauthorized() {
        // Act
        ResponseEntity<?> response = authController.verifyAuth(null);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    public void verifyAuth_WithNonBearerToken_ReturnsUnauthorized() {
        // Arrange
        String authHeader = "Basic " + TOKEN_VALUE;

        // Act
        ResponseEntity<?> response = authController.verifyAuth(authHeader);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    public void verifyAuth_WithTokenValidationException_ReturnsUnauthorized() {
        // Arrange
        String authHeader = "Bearer " + TOKEN_VALUE;
        when(tokenService.validateToken(TOKEN_VALUE)).thenThrow(new RuntimeException("Token validation error"));

        // Act
        ResponseEntity<?> response = authController.verifyAuth(authHeader);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    public void callback_WithJwtEncodingException_PropagatesException() throws Exception {
        // Arrange
        Map<String, String> params = new HashMap<>();
        params.put("shop", SHOP_DOMAIN);
        params.put("code", CODE_VALUE);
        params.put("state", STATE_VALUE);
        params.put("host", HOST_VALUE);

        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken(ACCESS_TOKEN);
        tokenResponse.setScope("read_products,write_products");
        
        // Mock the token exchange flow
        when(shopifyService.exchangeCodeForToken(SHOP_DOMAIN, CODE_VALUE))
            .thenReturn(ACCESS_TOKEN);
        when(shopifyService.parseTokenResponse(ACCESS_TOKEN))
            .thenReturn(tokenResponse);
        
        // Mock finding no existing token (for a new installation)
        when(tokenRepository.findByShopDomain(SHOP_DOMAIN))
            .thenReturn(Optional.empty());
        
        // Mock saving the new token
        when(tokenRepository.save(any(ShopifyToken.class)))
            .thenAnswer(i -> i.getArgument(0));

        // Mock completeAuthentication to throw JwtEncodingException
        doThrow(new JwtEncodingException("Failed to create session token"))
            .when(authController).completeAuthentication(HOST_VALUE, SHOP_DOMAIN, response);

        // Act & Assert
        JwtEncodingException exception = assertThrows(JwtEncodingException.class, () -> {
            authController.callback(params, STATE_VALUE, response);
        });

        // Verify all mocked method calls were made in the correct order
        verify(shopifyService).exchangeCodeForToken(SHOP_DOMAIN, CODE_VALUE);
        verify(shopifyService).parseTokenResponse(ACCESS_TOKEN);
        verify(tokenRepository).findByShopDomain(SHOP_DOMAIN);
        verify(tokenRepository).save(any(ShopifyToken.class));
        verify(authController).completeAuthentication(HOST_VALUE, SHOP_DOMAIN, response);
        
        assertEquals("Failed to create session token", exception.getMessage());
    }

    @Test
    public void buildAppHomeRedirectUrl_WithShopAndHost_ReturnsCorrectUrl() throws Exception {
        // Act
        String method = "buildAppHomeRedirectUrl";
        String url = (String) ReflectionTestUtils.invokeMethod(authController, method, HOST_VALUE, SHOP_DOMAIN);

        // Assert
        assertNotNull(url);
        assertTrue(url.contains("shop=" + SHOP_DOMAIN));
        assertTrue(url.contains("host=" + HOST_VALUE));
    }

    @Test
    public void buildAppHomeRedirectUrl_WithOnlyShop_ReturnsCorrectUrl() throws Exception {
        // Act
        String method = "buildAppHomeRedirectUrl";
        String url = (String) ReflectionTestUtils.invokeMethod(authController, method, null, SHOP_DOMAIN);

        // Assert
        assertNotNull(url);
        assertTrue(url.contains("shop=" + SHOP_DOMAIN));
        assertFalse(url.contains("host="));
    }
}