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
        try {
            String hmac = params.get("hmac");
            String calculatedHmac = calculateHmac(params, clientSecret);
            return MessageDigest.isEqual(hmac.getBytes(), calculatedHmac.getBytes());
        } catch (Exception e) {
            return false;
        }
    }

    private static String calculateHmac(Map<String, String> params, String clientSecret) throws Exception {
        String queryString = params.entrySet().stream()
                .filter(entry -> !entry.getKey().equals("hmac"))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] rawHmac = mac.doFinal(queryString.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(rawHmac);
    }
}