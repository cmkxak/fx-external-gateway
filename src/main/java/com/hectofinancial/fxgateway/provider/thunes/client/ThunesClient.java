package com.hectofinancial.fxgateway.provider.thunes.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hectofinancial.fxgateway.provider.thunes.dto.creditparty.CpiRequest;
import com.hectofinancial.fxgateway.provider.thunes.dto.creditparty.CpiResponse;
import com.hectofinancial.fxgateway.provider.thunes.dto.payer.Payer;
import com.hectofinancial.fxgateway.provider.thunes.dto.quotation.QuotationRequest;
import com.hectofinancial.fxgateway.provider.thunes.dto.quotation.QuotationResponse;
import com.hectofinancial.fxgateway.provider.thunes.dto.error.ThunesErrorResponse;
import com.hectofinancial.fxgateway.provider.thunes.dto.transaction.TransactionAttachment;
import com.hectofinancial.fxgateway.provider.thunes.dto.transaction.TransactionRequest;
import com.hectofinancial.fxgateway.provider.thunes.dto.transaction.TransactionResponse;
import com.hectofinancial.fxgateway.provider.thunes.dto.creditparty.VerificationRequest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static com.hectofinancial.fxgateway.provider.thunes.client.ThunesUri.CANCEL_TRANSACTION;
import static com.hectofinancial.fxgateway.provider.thunes.client.ThunesUri.CANCEL_TRANSACTION_BY_EXT;
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
import static com.hectofinancial.fxgateway.provider.thunes.client.ThunesUri.TRANSACTION_ATTACHMENTS;
import static com.hectofinancial.fxgateway.provider.thunes.client.ThunesUri.VERIFY_CREDIT_PARTY;

/**
 * Thunes MoneyTransfer V2 API 호출 래퍼.
 * 엔드포인트 목록은 docs/Thunes_Pay_개발가이드_정리.md §4 참조.
 */
@Component
public class ThunesClient {

    private static final Predicate<HttpStatusCode> IS_ERROR = HttpStatusCode::isError;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ThunesClient(RestClient thunesRestClient, ObjectMapper objectMapper) {
        this.restClient = thunesRestClient;
        this.objectMapper = objectMapper;
    }

    /** 비-2xx 응답을 ThunesApiException 으로 변환. errors[] 를 파싱해 구조화한다. */
    private void throwThunesApiException(HttpRequest request, ClientHttpResponse response) throws IOException {
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
        return restClient.get().uri(PING.path()).retrieve().body(String.class);
    }

    public String listServices() {
        return restClient.get().uri(LIST_SERVICES.path()).retrieve().body(String.class);
    }

    /** 지급처 목록 (필터 optional). */
    public List<Payer> getPayers(Integer page, Integer perPage, Integer serviceId,
                                 String countryIsoCode, String currency) {
        return restClient.get()
                .uri(b -> b.path(GET_PAYERS.path())
                        .queryParamIfPresent("page", Optional.ofNullable(page))
                        .queryParamIfPresent("per_page", Optional.ofNullable(perPage))
                        .queryParamIfPresent("service_id", Optional.ofNullable(serviceId))
                        .queryParamIfPresent("country_iso_code", Optional.ofNullable(countryIsoCode))
                        .queryParamIfPresent("currency", Optional.ofNullable(currency))
                        .build())
                .retrieve()
                .onStatus(IS_ERROR, this::throwThunesApiException)
                .body(new ParameterizedTypeReference<List<Payer>>() {});
    }

    /** 지급처 단건 조회. */
    public Payer getPayer(long id) {
        return restClient.get()
                .uri(GET_PAYER_BY_ID.path(), id)
                .retrieve()
                .onStatus(IS_ERROR, this::throwThunesApiException)
                .body(Payer.class);
    }

    // ----- 수취인 검증 -----

    /**
     * 수취인 계좌/번호 유효성 검증. type = C2C | C2B | B2C | B2B
     */
    public String verifyCreditParty(String payerId, String type, VerificationRequest req) {
        return restClient.post()
                .uri(VERIFY_CREDIT_PARTY.path(), payerId, type)
                .body(req)
                .retrieve()
                .onStatus(IS_ERROR, this::throwThunesApiException)
                .body(String.class);
    }

    /**
     * 수취인 정보 조회(CPI). type = C2C | C2B | B2C | B2B
     */
    public CpiResponse retrieveCreditPartyInformation(String payerId, String type, CpiRequest req) {
        return restClient.post()
                .uri(CREDIT_PARTY_INFORMATION.path(), payerId, type)
                .body(req)
                .retrieve()
                .onStatus(IS_ERROR, this::throwThunesApiException)
                .body(CpiResponse.class);
    }

    // ----- 견적 -----

    public QuotationResponse createQuotation(QuotationRequest req) {
        return restClient.post()
                .uri(CREATE_QUOTATION.path())
                .body(req)
                .retrieve()
                .onStatus(IS_ERROR, this::throwThunesApiException)
                .body(QuotationResponse.class);
    }

    // ----- 거래 -----

    public TransactionResponse createTransaction(long quotationId, TransactionRequest req) {
        return restClient.post()
                .uri(CREATE_TRANSACTION.path(), quotationId)
                .body(req)
                .retrieve()
                .onStatus(IS_ERROR, this::throwThunesApiException)
                .body(TransactionResponse.class);
    }

    /** 거래 생성 (견적 external_id = 우리 번호 기준). 멱등/복구용. */
    public TransactionResponse createTransactionByQuotationExternalId(String quotationExternalId, TransactionRequest req) {
        return restClient.post()
                .uri(CREATE_TRANSACTION_BY_EXT_QUOTATION.path(), quotationExternalId)
                .body(req)
                .retrieve()
                .onStatus(IS_ERROR, this::throwThunesApiException)
                .body(TransactionResponse.class);
    }

    /**
     * 거래 확정 — 실제 자금 이동 트리거 (바디 없음).
     */
    public TransactionResponse confirmTransaction(long transactionId) {
        return restClient.post()
                .uri(CONFIRM_TRANSACTION.path(), transactionId)
                .retrieve()
                .onStatus(IS_ERROR, this::throwThunesApiException)
                .body(TransactionResponse.class);
    }

    /** 거래 확정 (external_id = 우리 번호 기준). 멱등/복구용. */
    public TransactionResponse confirmTransactionByExternalId(String transactionExternalId) {
        return restClient.post()
                .uri(CONFIRM_TRANSACTION_BY_EXT.path(), transactionExternalId)
                .retrieve()
                .onStatus(IS_ERROR, this::throwThunesApiException)
                .body(TransactionResponse.class);
    }

    public TransactionResponse getTransaction(long transactionId) {
        return restClient.get()
                .uri(GET_TRANSACTION.path(), transactionId)
                .retrieve()
                .body(TransactionResponse.class);
    }

    /**
     * external_id 기준 조회 — 멱등/재시도 복구용 (ext- 접두어).
     */
    public TransactionResponse getTransactionByExternalId(String externalId) {
        return restClient.get()
                .uri(GET_TRANSACTION_BY_EXTERNAL_ID.path(), externalId)
                .retrieve()
                .body(TransactionResponse.class);
    }

    // ----- 취소 -----

    /** 거래 취소 — CREATED 또는 CONFIRMED-WAITING-FOR-PICKUP 상태만 가능 (바디 없음). */
    public TransactionResponse cancelTransaction(long transactionId) {
        return restClient.post()
                .uri(CANCEL_TRANSACTION.path(), transactionId)
                .retrieve()
                .onStatus(IS_ERROR, this::throwThunesApiException)
                .body(TransactionResponse.class);
    }

    /** 거래 취소 (external_id = 우리 번호 기준). */
    public TransactionResponse cancelTransactionByExternalId(String transactionExternalId) {
        return restClient.post()
                .uri(CANCEL_TRANSACTION_BY_EXT.path(), transactionExternalId)
                .retrieve()
                .onStatus(IS_ERROR, this::throwThunesApiException)
                .body(TransactionResponse.class);
    }

    // ----- 첨부(증빙 문서) -----

    /** 첨부 추가(multipart). 거래당 최대 3개·8MB, 확정 후 불가. type 예: invoice|purchase_order|contract */
    public TransactionAttachment addAttachment(long transactionId, String name, String type, MultipartFile file) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("name", name);
        builder.part("type", type);
        builder.part("file", file.getResource());
        MultiValueMap<String, HttpEntity<?>> body = builder.build();
        return restClient.post()
                .uri(TRANSACTION_ATTACHMENTS.path(), transactionId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .onStatus(IS_ERROR, this::throwThunesApiException)
                .body(TransactionAttachment.class);
    }

    /** 첨부 목록 조회. */
    public List<TransactionAttachment> listAttachments(long transactionId) {
        return restClient.get()
                .uri(TRANSACTION_ATTACHMENTS.path(), transactionId)
                .retrieve()
                .onStatus(IS_ERROR, this::throwThunesApiException)
                .body(new ParameterizedTypeReference<List<TransactionAttachment>>() {});
    }
}
