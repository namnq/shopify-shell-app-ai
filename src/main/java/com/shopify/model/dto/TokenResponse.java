package com.shopify.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class TokenResponse {
    @JsonProperty("access_token")
    private String accessToken;
    
    @JsonProperty("scope")
    private String scopes;
    
    @JsonProperty("expires_in")
    private Integer expiresIn;

    // Getters and Setters
    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
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
}