package com.shopify.config;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import java.util.Collection;

public class ShopifyAuthentication extends AbstractAuthenticationToken {

    private final String shopDomain;

    public ShopifyAuthentication(String shopDomain) {
        super(null);
        this.shopDomain = shopDomain;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return shopDomain;
    }

    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        return null;
    }
}