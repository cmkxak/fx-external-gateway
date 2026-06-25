package com.hectofinancial.fxgateway.provider.thunes.web;

import com.hectofinancial.fxgateway.provider.thunes.client.ThunesApiException;
import com.hectofinancial.fxgateway.provider.thunes.client.ThunesErrors;
import com.hectofinancial.fxgateway.provider.thunes.dto.error.GatewayErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

/**
 * Thunes 호출 결과를 API 서버용 공용어로 번역(ACL).
 * - 불확실(타임아웃/IO/5xx) → 504 UNKNOWN: "결과 모름 → GET 조회로 reconcile"
 * - 명확한 Thunes 에러(4xx) → 원 status + errors[] relay
 */
@RestControllerAdvice
public class ThunesExceptionHandler {

    /** 타임아웃/IO = 결과 불확실. */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<GatewayErrorResponse> onUncertain(ResourceAccessException e) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(new GatewayErrorResponse("UNKNOWN", null,
                        "Thunes 응답 불확실(타임아웃/IO) — GET 조회로 reconcile 필요"));
    }

    /** Thunes HTTP 에러: 5xx=불확실(UNKNOWN), 4xx=명확 실패(relay). */
    @ExceptionHandler(ThunesApiException.class)
    public ResponseEntity<GatewayErrorResponse> onThunesError(ThunesApiException e) {
        if (ThunesErrors.isUncertain(e)) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(new GatewayErrorResponse("UNKNOWN", e.errors(),
                            "Thunes 5xx — 결과 불확실, reconcile 필요"));
        }
        int status = (e.status() >= 400 && e.status() < 600) ? e.status() : 502;
        return ResponseEntity.status(status)
                .body(new GatewayErrorResponse("THUNES_ERROR", e.errors(), null));
    }
}
