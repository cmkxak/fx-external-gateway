package com.hectofinancial.fxgateway.provider.thunes.web;

import com.hectofinancial.fxgateway.provider.thunes.ThunesRemittanceProvider;
import com.hectofinancial.fxgateway.provider.thunes.dto.CpiRequest;
import com.hectofinancial.fxgateway.provider.thunes.dto.CpiResponse;
import com.hectofinancial.fxgateway.provider.thunes.dto.QuotationRequest;
import com.hectofinancial.fxgateway.provider.thunes.dto.QuotationResponse;
import com.hectofinancial.fxgateway.provider.thunes.dto.TransactionRequest;
import com.hectofinancial.fxgateway.provider.thunes.dto.TransactionResponse;
import com.hectofinancial.fxgateway.provider.thunes.dto.VerificationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thunes 망 인바운드 엔드포인트. API 서버가 송금 시퀀스의 각 단계를 개별 호출한다.
 * 호출 순서/중간 검증(고객 한도 누적액 등)은 API 서버가 제어한다:
 *   1) 견적 생성 → 2) (API서버) 한도 검증 → 3) 수취인 검증 → 4) 거래 생성 [→ 확정]
 */
@RestController
@RequestMapping("/v1/thunes")
public class ThunesRemittanceController {

    private final ThunesRemittanceProvider provider;

    public ThunesRemittanceController(ThunesRemittanceProvider provider) {
        this.provider = provider;
    }

    /** ① 견적 생성 */
    @PostMapping("/quotations")
    public ResponseEntity<QuotationResponse> createQuotation(@Valid @RequestBody QuotationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(provider.createQuotation(req));
    }

    /** ③ 수취인 검증. type = C2C | C2B | B2C | B2B */
    @PostMapping("/payers/{payerId}/{type}/credit-party-verification")
    public ResponseEntity<String> verifyBeneficiary(@PathVariable String payerId,
                                                    @PathVariable String type,
                                                    @Valid @RequestBody VerificationRequest req) {
        return ResponseEntity.ok(provider.verifyBeneficiary(payerId, type, req));
    }

    /** 수취인 정보 조회(CPI). type = C2C | C2B | B2C | B2B */
    @PostMapping("/payers/{payerId}/{type}/credit-party-information")
    public ResponseEntity<CpiResponse> retrieveBeneficiaryInformation(@PathVariable String payerId,
                                                                      @PathVariable String type,
                                                                      @Valid @RequestBody CpiRequest req) {
        return ResponseEntity.ok(provider.retrieveBeneficiaryInformation(payerId, type, req));
    }

    /** ④ 거래 생성 (견적 ID 기준) */
    @PostMapping("/quotations/{quotationId}/transactions")
    public ResponseEntity<TransactionResponse> createTransaction(@PathVariable long quotationId,
                                                                 @Valid @RequestBody TransactionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(provider.createTransaction(quotationId, req));
    }

    /** [선택] 거래 확정 — 자금 이동 트리거 */
    @PostMapping("/transactions/{transactionId}/confirm")
    public ResponseEntity<TransactionResponse> confirmTransaction(@PathVariable long transactionId) {
        return ResponseEntity.ok(provider.confirmTransaction(transactionId));
    }
}
