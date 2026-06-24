package com.hectofinancial.fxgateway.provider.thunes.client;

import com.hectofinancial.fxgateway.provider.thunes.dto.ThunesDtos.QuotationRequest;
import com.hectofinancial.fxgateway.provider.thunes.dto.ThunesDtos.QuotationResponse;
import com.hectofinancial.fxgateway.provider.thunes.dto.ThunesDtos.TransactionRequest;
import com.hectofinancial.fxgateway.provider.thunes.dto.ThunesDtos.TransactionResponse;
import com.hectofinancial.fxgateway.provider.thunes.dto.ThunesDtos.VerificationRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.function.Predicate;

import static com.hectofinancial.fxgateway.provider.thunes.client.ThunesUri.CONFIRM_TRANSACTION;
import static com.hectofinancial.fxgateway.provider.thunes.client.ThunesUri.CREATE_QUOTATION;
import static com.hectofinancial.fxgateway.provider.thunes.client.ThunesUri.CREATE_TRANSACTION;
import static com.hectofinancial.fxgateway.provider.thunes.client.ThunesUri.GET_PAYERS;
import static com.hectofinancial.fxgateway.provider.thunes.client.ThunesUri.GET_TRANSACTION;
import static com.hectofinancial.fxgateway.provider.thunes.client.ThunesUri.GET_TRANSACTION_BY_EXTERNAL_ID;
import static com.hectofinancial.fxgateway.provider.thunes.client.ThunesUri.LIST_SERVICES;
import static com.hectofinancial.fxgateway.provider.thunes.client.ThunesUri.PING;
import static com.hectofinancial.fxgateway.provider.thunes.client.ThunesUri.VERIFY_CREDIT_PARTY;

/**
 * Thunes MoneyTransfer V2 API 호출 래퍼.
 * 엔드포인트 목록은 docs/Thunes_Pay_개발가이드_정리.md §4 참조.
 */
@Component
public class ThunesClient {

    private static final Predicate<HttpStatusCode> IS_ERROR = HttpStatusCode::isError;

    private final RestClient rc;

    public ThunesClient(RestClient thunesRestClient) {
        this.rc = thunesRestClient;
    }

    // ----- Connectivity / Discovery / Account -----

    public String ping() {
        return rc.get().uri(PING.path()).retrieve().body(String.class);
    }

    public String listServices() {
        return rc.get().uri(LIST_SERVICES.path()).retrieve().body(String.class);
    }

    public String getPayers() {
        return rc.get().uri(GET_PAYERS.path()).retrieve().body(String.class);
    }

    // ----- 수취인 검증 -----

    /**
     * 수취인 계좌/번호 유효성 검증. type = C2C | C2B | B2C | B2B
     */
    public String verifyCreditParty(String payerId, String type, VerificationRequest req) {
        return rc.post()
                .uri(VERIFY_CREDIT_PARTY.path(), payerId, type)
                .body(req)
                .retrieve()
                .onStatus(IS_ERROR, (rq, rs) -> {
                    throw ThunesApiException.from(rs);
                })
                .body(String.class);
    }

    // ----- 견적 -----

    public QuotationResponse createQuotation(QuotationRequest req) {
        return rc.post()
                .uri(CREATE_QUOTATION.path())
                .body(req)
                .retrieve()
                .onStatus(IS_ERROR, (rq, rs) -> {
                    throw ThunesApiException.from(rs);
                })
                .body(QuotationResponse.class);
    }

    // ----- 거래 -----

    public TransactionResponse createTransaction(long quotationId, TransactionRequest req) {
        return rc.post()
                .uri(CREATE_TRANSACTION.path(), quotationId)
                .body(req)
                .retrieve()
                .onStatus(IS_ERROR, (rq, rs) -> {
                    throw ThunesApiException.from(rs);
                })
                .body(TransactionResponse.class);
    }

    /**
     * 거래 확정 — 실제 자금 이동 트리거 (바디 없음).
     */
    public TransactionResponse confirmTransaction(long transactionId) {
        return rc.post()
                .uri(CONFIRM_TRANSACTION.path(), transactionId)
                .retrieve()
                .onStatus(IS_ERROR, (rq, rs) -> {
                    throw ThunesApiException.from(rs);
                })
                .body(TransactionResponse.class);
    }

    public TransactionResponse getTransaction(long transactionId) {
        return rc.get()
                .uri(GET_TRANSACTION.path(), transactionId)
                .retrieve()
                .body(TransactionResponse.class);
    }

    /**
     * external_id 기준 조회 — 멱등/재시도 복구용 (ext- 접두어).
     */
    public TransactionResponse getTransactionByExternalId(String externalId) {
        return rc.get()
                .uri(GET_TRANSACTION_BY_EXTERNAL_ID.path(), externalId)
                .retrieve()
                .body(TransactionResponse.class);
    }
}
