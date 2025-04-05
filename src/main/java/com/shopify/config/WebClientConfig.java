package com.shopify.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    private static final int DEFAULT_CONNECT_TIMEOUT = 10000; // 10 seconds
    private static final int DEFAULT_READ_TIMEOUT = 10000; // 10 seconds
    private static final int DEFAULT_WRITE_TIMEOUT = 10000; // 10 seconds
    private static final int MAX_IN_MEMORY_SIZE = 16 * 1024 * 1024; // 16MB

    @Bean
    public WebClient webClient() {
        // Configure connection pooling
        ConnectionProvider provider = ConnectionProvider.builder("shopify-connection-pool")
                .maxConnections(500)
                .maxIdleTime(Duration.ofSeconds(20))
                .maxLifeTime(Duration.ofMinutes(5))
                .pendingAcquireTimeout(Duration.ofSeconds(60))
                .evictInBackground(Duration.ofSeconds(30))
                .build();

        // Configure timeouts
        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, DEFAULT_CONNECT_TIMEOUT)
                .responseTimeout(Duration.ofMillis(DEFAULT_READ_TIMEOUT))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(DEFAULT_READ_TIMEOUT, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(DEFAULT_WRITE_TIMEOUT, TimeUnit.MILLISECONDS)))
                .wiretap(true); // For debugging if needed (consider disabling in production)

        // Configure memory allocation for responses
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .build();

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(exchangeStrategies)
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            System.out.println("Request: " + clientRequest.method() + " " + clientRequest.url());
            clientRequest.headers().forEach((name, values) -> 
                values.forEach(value -> System.out.println(name + ": " + value)));
            return Mono.just(clientRequest);
        });
    }
    
    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            System.out.println("Response status: " + clientResponse.statusCode());
            return Mono.just(clientResponse);
        });
    }
}
