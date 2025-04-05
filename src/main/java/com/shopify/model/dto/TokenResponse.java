package com.shopify.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TokenResponse {
    
    @JsonProperty("access_token")
    private String accessToken;
    
    @JsonProperty("scope")
    private String scope;
    
    @JsonProperty("expires_in")
    private Long expiresIn;
    
    public TokenResponse() {
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
    
    public Long getExpiresIn() {
        return expiresIn;
    }
    
    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }
}