package com.hectofinancial.fxgateway.provider.thunes.client;

import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Thunes API 가 2xx 가 아닌 응답을 줄 때 던지는 예외.
 */
public class ThunesApiException extends RuntimeException {

    private final int status;

    public ThunesApiException(int status, String body) {
        super("Thunes API error " + status + ": " + body);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static ThunesApiException from(ClientHttpResponse response) {
        try {
            String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
            return new ThunesApiException(response.getStatusCode().value(), body);
        } catch (IOException e) {
            return new ThunesApiException(-1, "unreadable error body");
        }
    }
}
