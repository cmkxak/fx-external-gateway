package com.hectofinancial.fxgateway.provider.thunes.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Thunes 호출용 RestClient 빈. Basic Auth(api_key:api_secret) + 공통 헤더 + 타임아웃.
 */
@Configuration
@EnableConfigurationProperties(ThunesProperties.class)
public class ThunesClientConfig {

    @Bean
    public RestClient thunesRestClient(ThunesProperties props) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) props.connectTimeout().toMillis());
        factory.setReadTimeout((int) props.readTimeout().toMillis());

        String basic = Base64.getEncoder().encodeToString(
                (props.apiKey() + ":" + props.apiSecret()).getBytes(StandardCharsets.UTF_8));

        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
