package com.hectofinancial.fxgateway.provider.thunes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Thunes MoneyTransfer V2 연동 설정. (application.yml: thunes.*)
 */
@ConfigurationProperties(prefix = "thunes")
public record ThunesProperties(
        String baseUrl,
        String apiKey,
        String apiSecret,
        Duration connectTimeout,
        Duration readTimeout
) {}
