package com.shopify.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

public class ShopifyHmacValidator {

    public static boolean validateHmac(Map<String, String> params, String clientSecret) {
        String hmac = params.get("hmac");
        if (hmac == null) {
            return false;
        }

        try {
            String calculatedHmac = calculateHmac(params, clientSecret);
            return hmac.equals(calculatedHmac);
        } catch (Exception e) {
            return false;
        }
    }
    
    private static String calculateHmac(Map<String, String> params, String clientSecret) throws Exception {
        params.remove("hmac");
        String queryString = params.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining("&"));
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), mac.getAlgorithm());
        mac.init(secretKeySpec);
        byte[] hmacBytes = mac.doFinal(queryString.getBytes(StandardCharsets.UTF_8));
        
        // Convert computed HMAC to hex
        StringBuilder sb = new StringBuilder();
        for (byte b : hmacBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}