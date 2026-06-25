package com.hectofinancial.fxgateway.provider.thunes.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Thunes 호출용 RestClient 빈. Basic Auth(api_key:api_secret) + 공통 헤더 + 타임아웃.
 *
 * 전송 계층: JDK java.net.http.HttpClient 기반(JdkClientHttpRequestFactory).
 * - 커넥션 풀링/keep-alive 내장 → SimpleClientHttpRequestFactory(풀 없음) 대비 부하에 강함
 * - HTTP/2 지원(미지원 시 1.1 fallback), 외부 의존성 추가 없음(폐쇄망 안전)
 * - 트러스트스토어는 JVM 기본 cacerts 사용(사내 ePrism CA import 필요)
 */
@Configuration
@EnableConfigurationProperties(ThunesProperties.class)
public class ThunesClientConfig {

    @Bean
    public RestClient thunesRestClient(ThunesProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(props.readTimeout());

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
