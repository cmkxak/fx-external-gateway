package com.hectofinancial.fxgateway.provider.thunes.client;

import org.springframework.web.client.ResourceAccessException;

/**
 * Thunes 호출 결과 분류 유틸.
 */
public final class ThunesErrors {

    private ThunesErrors() {
    }

    /**
     * "불확실"(갔는지 모름) 여부 — 타임아웃/IO/5xx.
     * 이 경우 작업이 Thunes에 반영됐는지 알 수 없음 → API 서버가 GET 조회로 reconcile 해야 함.
     * 4xx 등 HTTP 응답을 받은 명확한 실패는 false.
     */
    public static boolean isUncertain(RuntimeException e) {
        if (e instanceof ResourceAccessException) {
            return true; // connect/read timeout, IOException
        }
        if (e instanceof ThunesApiException t) {
            return t.status() >= 500;
        }
        return false;
    }
}
