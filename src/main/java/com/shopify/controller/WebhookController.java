package com.shopify.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    @PostMapping("/uninstall")
    public ResponseEntity<String> handleUninstall(@RequestBody String payload) {
        // Handle app uninstallation
        System.out.println("Received uninstall webhook: " + payload);
        return ResponseEntity.ok("Webhook received");
    }
}