package com.hectofinancial.fxgateway.provider.thunes.client;

public enum ThunesUri {

    PING("/ping"),
    LIST_SERVICES("/v2/money-transfer/services"),
    GET_PAYERS("/v2/money-transfer/payers"),
    GET_PAYER_BY_ID("/v2/money-transfer/payers/{id}"),
    VERIFY_CREDIT_PARTY("/v2/money-transfer/payers/{payerId}/{type}/credit-party-verification"),
    CREDIT_PARTY_INFORMATION("/v2/money-transfer/payers/{payerId}/{type}/credit-party-information"),
    CREATE_QUOTATION("/v2/money-transfer/quotations"),
    CREATE_TRANSACTION("/v2/money-transfer/quotations/{quotationId}/transactions"),
    CONFIRM_TRANSACTION("/v2/money-transfer/transactions/{transactionId}/confirm"),
    GET_TRANSACTION("/v2/money-transfer/transactions/{transactionId}"),
    GET_TRANSACTION_BY_EXTERNAL_ID("/v2/money-transfer/transactions/ext-{externalId}");

    private final String path;

    ThunesUri(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}
