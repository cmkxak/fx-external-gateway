package com.hectofinancial.fxgateway.provider.thunes.client;

import com.hectofinancial.fxgateway.provider.thunes.dto.error.ThunesError;
import com.hectofinancial.fxgateway.provider.thunes.dto.error.ThunesErrorResponse;

import java.util.List;

/**
 * Thunes API 가 2xx 가 아닌 응답을 줄 때 던지는 예외.
 * 응답 바디의 errors[] (code/message) 를 파싱해 구조화해서 들고 있다.
 */
public class ThunesApiException extends RuntimeException {

    private final int status;
    private final transient ThunesErrorResponse errorResponse;
    private final String rawBody;

    public ThunesApiException(int status, ThunesErrorResponse errorResponse, String rawBody) {
        super("Thunes API error " + status + ": " + rawBody);
        this.status = status;
        this.errorResponse = errorResponse;
        this.rawBody = rawBody;
    }

    public int status() {
        return status;
    }

    /** 파싱된 에러 목록 (없으면 빈 리스트). */
    public List<ThunesError> errors() {
        return (errorResponse == null || errorResponse.errors() == null)
                ? List.of()
                : errorResponse.errors();
    }

    /** 원본 응답 바디 (파싱 실패 시 진단용). */
    public String rawBody() {
        return rawBody;
    }
}
