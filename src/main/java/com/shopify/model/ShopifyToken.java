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

    @Column(name = "installed_at", nullable = false)
    private Instant installedAt;

    @Column(name = "scopes")
    private String scopes;

    @Column(name = "expires_in")
    private Integer expiresIn;

    // Getters and Setters
    public Long getId() {
        return id;
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

    public Instant getInstalledAt() {
        return installedAt;
    }

    public void setInstalledAt(Instant installedAt) {
        this.installedAt = installedAt;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public Integer getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Integer expiresIn) {
        this.expiresIn = expiresIn;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
}