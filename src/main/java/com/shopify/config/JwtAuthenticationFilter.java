package com.shopify.config;

import com.shopify.service.ShopifyTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final ShopifyTokenService tokenService;

    public JwtAuthenticationFilter(ShopifyTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Skip authentication for paths that are permitAll in SecurityConfig
        String path = request.getServletPath();
        if (isPermitAllPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Get Authorization header
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        
        // If no Authorization header or doesn't start with "Bearer ", proceed without authentication
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract token (remove "Bearer " prefix)
        String token = authHeader.substring(7);
        
        try {
            // Validate token
            if (tokenService.validateToken(token)) {
                // Get shop domain from token
                String shopDomain = tokenService.getShopDomainFromToken(token);
                
                if (shopDomain != null) {
                    // Create authentication object with shop domain as principal
                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                            shopDomain,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_SHOP_OWNER"))
                        );
                    
                    // Set authentication in SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception e) {
            logger.error("JWT Authentication error", e);
            // Do not set authentication if token is invalid
        }
        
        filterChain.doFilter(request, response);
    }
    
    private boolean isPermitAllPath(String path) {
        return path.startsWith("/auth/") ||
               path.startsWith("/api/auth/") ||
               path.startsWith("/oauth/") ||
               path.startsWith("/api/oauth/") ||
               path.startsWith("/webhooks/") ||
               path.startsWith("/api/webhooks/") ||
               path.equals("/api/graphql") ||
               path.startsWith("/api/proxy/");
    }
}