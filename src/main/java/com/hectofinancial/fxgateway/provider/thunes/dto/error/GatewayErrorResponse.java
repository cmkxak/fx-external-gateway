package com.hectofinancial.fxgateway.provider.thunes.dto.error;

import java.util.List;

/**
 * GW → API 서버 에러 응답.
 * outcome: "THUNES_ERROR"(명확 실패) | "UNKNOWN"(불확실 — 결과 모름, GET 으로 reconcile 필요)
 */
public record GatewayErrorResponse(
        String outcome,
        List<ThunesError> errors,
        String message
) {}
