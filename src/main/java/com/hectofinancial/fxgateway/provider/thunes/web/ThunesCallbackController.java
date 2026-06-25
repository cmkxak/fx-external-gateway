package com.hectofinancial.fxgateway.provider.thunes.web;

import com.hectofinancial.fxgateway.provider.thunes.dto.transaction.TransactionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thunes 거래상태 웹훅(callback_url) 수신.
 * 콜백 바디 = Transaction 객체(= TransactionResponse). 상태 변경 시마다 호출되며 at-least-once 라
 * 중복 콜백이 정상 → 멱등 처리 필요. 2XX 를 반환해야 Thunes 가 재시도하지 않는다.
 */
@RestController
@RequestMapping("/v1/thunes/callback")
public class ThunesCallbackController {

    @PostMapping("/transactions")
    public ResponseEntity<Void> onTransactionStatus(@RequestBody TransactionResponse payload) {
        // TODO 1) 서명/출처 검증 (원본 문서의 콜백 인증 방식 확인 필요)
        // TODO 2) payload.externalId() / payload.id() 로 내부 거래 매핑
        // TODO 3) payload.status() / statusClass() 반영 (멱등 — 중복 콜백, 상태 단조성 가드)
        return ResponseEntity.ok().build();
    }
}
