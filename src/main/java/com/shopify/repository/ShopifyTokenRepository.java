package com.shopify.repository;

import com.shopify.model.ShopifyToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ShopifyTokenRepository extends JpaRepository<ShopifyToken, Long> {
    Optional<ShopifyToken> findByShopDomain(String shopDomain);
    boolean existsByShopDomain(String shopDomain);
    void deleteByShopDomain(String shopDomain);
}