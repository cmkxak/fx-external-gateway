package com.hectofinancial.fxgateway.provider.thunes.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Thunes 거래상태 웹훅(callback_url) 수신.
 */
@RestController
@RequestMapping("/webhooks/thunes")
public class ThunesCallbackController {

    @PostMapping("/transactions")
    public ResponseEntity<Void> onTransactionStatus(@RequestBody Map<String, Object> payload) {
        // TODO 1) 서명/출처 검증 (원본 문서의 콜백 인증 방식 확인 필요)
        // TODO 2) external_id / transaction_id 로 내부 거래 매핑
        // TODO 3) status 반영 (멱등 처리 — 중복 콜백 가능)
        return ResponseEntity.ok().build();
    }
}
