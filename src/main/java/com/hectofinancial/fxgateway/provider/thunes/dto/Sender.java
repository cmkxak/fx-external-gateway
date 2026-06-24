package com.hectofinancial.fxgateway.provider.thunes.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 송금인(C2C/C2B). 거래 생성 요청의 sender.
 * 실제 필수 필드는 Payer 의 required_sending_entity_fields 가 결정(payer별 상이).
 * firstname/lastname 은 Sender 리소스 스펙상 필수.
 * ⚠️ 대부분 필드의 max length 와 id_type/beneficiary_relationship/source_of_funds enum 값은
 *    스펙 표 확인 후 @Size/@Pattern 추가 예정(현재 미반영 — 유추 금지).
 * 날짜는 스펙상 String(ISO 8601).
 */
public record Sender(
        @NotBlank String firstname,
        @NotBlank String lastname,
        String lastname2,
        String middlename,
        String nativename,
        @Size(max = 3) String nationalityCountryIsoCode,                 // ISO 3166-1 alpha-3
        String dateOfBirth,                                              // ISO 8601
        @Size(max = 3) String countryOfBirthIsoCode,                     // ISO 3166-1 alpha-3
        @Pattern(regexp = "MALE|FEMALE") String gender,
        String address,
        String postalCode,
        String city,
        @Size(max = 3) String countryIsoCode,                            // ISO 3166-1 alpha-3
        String provinceState,
        String msisdn,
        @Email String email,
        String idType,
        @Size(max = 3) String idCountryIsoCode,                          // ISO 3166-1 alpha-3
        String idNumber,
        String idDeliveryDate,                                           // ISO 8601
        String idExpirationDate,                                         // ISO 8601
        String occupation,
        String code,
        String beneficiaryRelationship,
        String sourceOfFunds,
        String bankAccountNumber
) {}
