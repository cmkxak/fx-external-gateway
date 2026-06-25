package com.hectofinancial.fxgateway.provider.thunes.web;

import com.hectofinancial.fxgateway.provider.thunes.ThunesRemittanceProvider;
import com.hectofinancial.fxgateway.provider.thunes.dto.creditparty.CpiRequest;
import com.hectofinancial.fxgateway.provider.thunes.dto.creditparty.CpiResponse;
import com.hectofinancial.fxgateway.provider.thunes.dto.payer.Payer;
import com.hectofinancial.fxgateway.provider.thunes.dto.quotation.QuotationRequest;
import com.hectofinancial.fxgateway.provider.thunes.dto.quotation.QuotationResponse;
import com.hectofinancial.fxgateway.provider.thunes.dto.transaction.TransactionAttachment;
import com.hectofinancial.fxgateway.provider.thunes.dto.transaction.TransactionRequest;
import com.hectofinancial.fxgateway.provider.thunes.dto.transaction.TransactionResponse;
import com.hectofinancial.fxgateway.provider.thunes.dto.creditparty.VerificationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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

    /** Discovery: 지급처 목록 (견적 전 payer_id 확보용). */
    @GetMapping("/payers")
    public ResponseEntity<List<Payer>> listPayers(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage,
            @RequestParam(name = "service_id", required = false) Integer serviceId,
            @RequestParam(name = "country_iso_code", required = false) String countryIsoCode,
            @RequestParam(name = "currency", required = false) String currency) {
        return ResponseEntity.ok(provider.listPayers(page, perPage, serviceId, countryIsoCode, currency));
    }

    /** Discovery: 지급처 단건. */
    @GetMapping("/payers/{id}")
    public ResponseEntity<Payer> getPayer(@PathVariable long id) {
        return ResponseEntity.ok(provider.getPayer(id));
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

    /** ④' 거래 생성 (견적 external_id = 우리 번호 기준) */
    @PostMapping("/quotations/ext-{quotationExternalId}/transactions")
    public ResponseEntity<TransactionResponse> createTransactionByQuotationExternalId(
            @PathVariable String quotationExternalId,
            @Valid @RequestBody TransactionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(provider.createTransactionByQuotationExternalId(quotationExternalId, req));
    }

    /** [선택] 거래 확정 — 자금 이동 트리거 */
    @PostMapping("/transactions/{transactionId}/confirm")
    public ResponseEntity<TransactionResponse> confirmTransaction(@PathVariable long transactionId) {
        return ResponseEntity.ok(provider.confirmTransaction(transactionId));
    }

    /** [선택] 거래 확정 (external_id = 우리 번호 기준) */
    @PostMapping("/transactions/ext-{transactionExternalId}/confirm")
    public ResponseEntity<TransactionResponse> confirmTransactionByExternalId(@PathVariable String transactionExternalId) {
        return ResponseEntity.ok(provider.confirmTransactionByExternalId(transactionExternalId));
    }

    /** 거래 취소 (id 기준). CREATED / CONFIRMED-WAITING-FOR-PICKUP 만 가능 */
    @PostMapping("/transactions/{transactionId}/cancel")
    public ResponseEntity<TransactionResponse> cancelTransaction(@PathVariable long transactionId) {
        return ResponseEntity.ok(provider.cancelTransaction(transactionId));
    }

    /** 거래 취소 (external_id = 우리 번호 기준) */
    @PostMapping("/transactions/ext-{transactionExternalId}/cancel")
    public ResponseEntity<TransactionResponse> cancelTransactionByExternalId(@PathVariable String transactionExternalId) {
        return ResponseEntity.ok(provider.cancelTransactionByExternalId(transactionExternalId));
    }

    /** 증빙 첨부 추가 (multipart). type 예: invoice|purchase_order|contract */
    @PostMapping(value = "/transactions/{transactionId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TransactionAttachment> addAttachment(@PathVariable long transactionId,
                                                               @RequestParam String name,
                                                               @RequestParam String type,
                                                               @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(provider.addAttachment(transactionId, name, type, file));
    }

    /** 증빙 첨부 목록 조회 */
    @GetMapping("/transactions/{transactionId}/attachments")
    public ResponseEntity<List<TransactionAttachment>> listAttachments(@PathVariable long transactionId) {
        return ResponseEntity.ok(provider.listAttachments(transactionId));
    }
}
