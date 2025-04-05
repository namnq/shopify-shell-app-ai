package com.shopify.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "shopify_tokens")
public class ShopifyToken {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "shop_domain", nullable = false, unique = true)
    private String shopDomain;
    
    @Column(name = "access_token", nullable = false)
    private String accessToken;
    
    @Column(name = "scope")
    private String scope;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "expires_at")
    private Instant expiresAt;
    
    @Column(name = "installed_at", nullable = false)
    private Instant installedAt;
    
    public ShopifyToken() {
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getShopDomain() {
        return shopDomain;
    }
    
    public void setShopDomain(String shopDomain) {
        this.shopDomain = shopDomain;
    }
    
    public String getAccessToken() {
        return accessToken;
    }
    
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
    
    public String getScope() {
        return scope;
    }
    
    public void setScope(String scope) {
        this.scope = scope;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public Instant getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public Instant getInstalledAt() {
        return installedAt;
    }
    
    public void setInstalledAt(Instant installedAt) {
        this.installedAt = installedAt;
    }
}