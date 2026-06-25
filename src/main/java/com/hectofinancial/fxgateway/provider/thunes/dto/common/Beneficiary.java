package com.hectofinancial.fxgateway.provider.thunes.dto.common;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 수취인. CPI 응답(C2C/B2C) 및 거래의 beneficiary 로 사용.
 * 제약은 Thunes beneficiary 스펙(길이/enum) 기준. 응답으로 쓰일 땐 검증이 돌지 않고,
 * request 로 쓰여 @Valid cascade 될 때 동작한다. 날짜는 스펙상 String(ISO 8601).
 */
public record Beneficiary(
        @Size(max = 80) String lastname,
        @Size(max = 64) String lastname2,
        @Size(max = 64) String middlename,
        @Size(max = 64) String firstname,
        @Size(max = 64) String nativename,
        @Size(max = 3) String nationalityCountryIsoCode,                 // ISO 3166-1 alpha-3
        @Size(max = 64) String code,
        @Size(max = 64) String dateOfBirth,                              // ISO 8601
        @Size(max = 3) String countryOfBirthIsoCode,                     // ISO 3166-1 alpha-3
        @Pattern(regexp = "MALE|FEMALE") String gender,
        @Size(max = 256) String address,
        @Size(max = 64) String postalCode,
        @Size(max = 64) String city,
        @Size(max = 3) String countryIsoCode,                            // ISO 3166-1 alpha-3
        @Size(max = 64) String msisdn,                                   // international format
        @Size(max = 64) @Email String email,
        @Pattern(regexp = "PASSPORT|NATIONAL_ID|DRIVING_LICENSE|SOCIAL_SECURITY|TAX_ID|"
                + "SENIOR_CITIZEN_ID|BIRTH_CERTIFICATE|VILLAGE_ELDER_ID|RESIDENT_CARD|"
                + "ALIEN_REGISTRATION|PAN_CARD|VOTERS_ID|HEALTH_CARD|EMPLOYER_ID|OTHER")
        String idType,
        @Size(max = 3) String idCountryIsoCode,                          // ISO 3166-1 alpha-3
        @Size(max = 64) String idNumber,
        @Size(max = 64) String idDeliveryDate,                           // ISO 8601
        @Size(max = 64) String idExpirationDate,                         // ISO 8601
        @Size(max = 80) String occupation,
        @Size(max = 144) String bankAccountHolderName,
        @Size(max = 64) String provinceState
) {}
