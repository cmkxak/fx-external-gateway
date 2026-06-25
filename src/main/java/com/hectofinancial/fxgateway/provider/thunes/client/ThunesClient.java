package com.hectofinancial.fxgateway.provider.thunes.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hectofinancial.fxgateway.provider.thunes.dto.creditparty.CpiRequest;
import com.hectofinancial.fxgateway.provider.thunes.dto.creditparty.CpiResponse;
import com.hectofinancial.fxgateway.provider.thunes.dto.payer.Payer;
import com.hectofinancial.fxgateway.provider.thunes.dto.quotation.QuotationRequest;
import com.hectofinancial.fxgateway.provider.thunes.dto.quotation.QuotationResponse;
import com.hectofinancial.fxgateway.provider.thunes.dto.error.ThunesErrorResponse;
import com.hectofinancial.fxgateway.provider.thunes.dto.transaction.TransactionRequest;
import com.hectofinancial.fxgateway.provider.thunes.dto.transaction.TransactionResponse;
import com.hectofinancial.fxgateway.provider.thunes.dto.creditparty.VerificationRequest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static com.hectofinancial.fxgateway.provider.thunes.client.ThunesUri.CONFIRM_TRANSACTION;
import static com.hectofinancial.fxgateway.provider.thunes.client.ThunesUri.CONFIRM_TRANSACTION_BY_EXT;
import static com.hectofinancial.fxgateway.provider.thunes.client.ThunesUri.CREATE_QUOTATION;
import static com.hectofinancial.fxgateway.provider.thunes.client.ThunesUri.CREATE_TRANSACTION;
import static com.hectofinancial.fxgateway.provider.thunes.client.ThunesUri.CREATE_TRANSACTION_BY_EXT_QUOTATION;
import static com.hectofinancial.fxgateway.provider.thunes.client.ThunesUri.CREDIT_PARTY_INFORMATION;
import static com.hectofinancial.fxgateway.provider.thunes.client.ThunesUri.GET_PAYER_BY_ID;
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
    private final ObjectMapper objectMapper;

    public ThunesClient(RestClient thunesRestClient, ObjectMapper objectMapper) {
        this.rc = thunesRestClient;
        this.objectMapper = objectMapper;
    }

    /** 비-2xx 응답을 ThunesApiException 으로 변환. errors[] 를 파싱해 구조화한다. */
    private void raiseThunesError(HttpRequest request, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        ThunesErrorResponse parsed = null;
        try {
            parsed = objectMapper.readValue(body, ThunesErrorResponse.class);
        } catch (Exception ignore) {
            // JSON 이 아니거나 예상 외 형태면 raw 만 보존
        }
        throw new ThunesApiException(status, parsed, body);
    }

    // ----- Connectivity / Discovery / Account -----

    public String ping() {
        return rc.get().uri(PING.path()).retrieve().body(String.class);
    }

    public String listServices() {
        return rc.get().uri(LIST_SERVICES.path()).retrieve().body(String.class);
    }

    /** 지급처 목록 (필터 optional). */
    public List<Payer> getPayers(Integer page, Integer perPage, Integer serviceId,
                                 String countryIsoCode, String currency) {
        return rc.get()
                .uri(b -> b.path(GET_PAYERS.path())
                        .queryParamIfPresent("page", Optional.ofNullable(page))
                        .queryParamIfPresent("per_page", Optional.ofNullable(perPage))
                        .queryParamIfPresent("service_id", Optional.ofNullable(serviceId))
                        .queryParamIfPresent("country_iso_code", Optional.ofNullable(countryIsoCode))
                        .queryParamIfPresent("currency", Optional.ofNullable(currency))
                        .build())
                .retrieve()
                .onStatus(IS_ERROR, this::raiseThunesError)
                .body(new ParameterizedTypeReference<List<Payer>>() {});
    }

    /** 지급처 단건 조회. */
    public Payer getPayer(long id) {
        return rc.get()
                .uri(GET_PAYER_BY_ID.path(), id)
                .retrieve()
                .onStatus(IS_ERROR, this::raiseThunesError)
                .body(Payer.class);
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
                .onStatus(IS_ERROR, this::raiseThunesError)
                .body(String.class);
    }

    /**
     * 수취인 정보 조회(CPI). type = C2C | C2B | B2C | B2B
     */
    public CpiResponse retrieveCreditPartyInformation(String payerId, String type, CpiRequest req) {
        return rc.post()
                .uri(CREDIT_PARTY_INFORMATION.path(), payerId, type)
                .body(req)
                .retrieve()
                .onStatus(IS_ERROR, this::raiseThunesError)
                .body(CpiResponse.class);
    }

    // ----- 견적 -----

    public QuotationResponse createQuotation(QuotationRequest req) {
        return rc.post()
                .uri(CREATE_QUOTATION.path())
                .body(req)
                .retrieve()
                .onStatus(IS_ERROR, this::raiseThunesError)
                .body(QuotationResponse.class);
    }

    // ----- 거래 -----

    public TransactionResponse createTransaction(long quotationId, TransactionRequest req) {
        return rc.post()
                .uri(CREATE_TRANSACTION.path(), quotationId)
                .body(req)
                .retrieve()
                .onStatus(IS_ERROR, this::raiseThunesError)
                .body(TransactionResponse.class);
    }

    /** 거래 생성 (견적 external_id = 우리 번호 기준). 멱등/복구용. */
    public TransactionResponse createTransactionByQuotationExternalId(String quotationExternalId, TransactionRequest req) {
        return rc.post()
                .uri(CREATE_TRANSACTION_BY_EXT_QUOTATION.path(), quotationExternalId)
                .body(req)
                .retrieve()
                .onStatus(IS_ERROR, this::raiseThunesError)
                .body(TransactionResponse.class);
    }

    /**
     * 거래 확정 — 실제 자금 이동 트리거 (바디 없음).
     */
    public TransactionResponse confirmTransaction(long transactionId) {
        return rc.post()
                .uri(CONFIRM_TRANSACTION.path(), transactionId)
                .retrieve()
                .onStatus(IS_ERROR, this::raiseThunesError)
                .body(TransactionResponse.class);
    }

    /** 거래 확정 (external_id = 우리 번호 기준). 멱등/복구용. */
    public TransactionResponse confirmTransactionByExternalId(String transactionExternalId) {
        return rc.post()
                .uri(CONFIRM_TRANSACTION_BY_EXT.path(), transactionExternalId)
                .retrieve()
                .onStatus(IS_ERROR, this::raiseThunesError)
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
