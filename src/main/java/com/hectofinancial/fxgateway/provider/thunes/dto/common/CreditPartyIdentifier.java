package com.hectofinancial.fxgateway.provider.thunes.dto.common;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreditPartyIdentifier(
        @Size(min = 5, max = 32) String msisdn,                          // international format
        @Size(max = 30) String bankAccountNumber,
        @Size(min = 14, max = 34) String iban,
        @Pattern(regexp = "\\d{18}") String clabe,                       // exactly 18 digits
        @Pattern(regexp = "\\d{22}") String cbu,                         // exactly 22 digits
        @Size(max = 20) String cbuAlias,
        @Size(max = 11) String swiftBicCode,                             // 8 or 11 characters
        @Size(max = 11) String bikCode,                                  // 9 digits (or 11 with ru prefix)
        @Size(min = 11, max = 11) String ifsCode,                        // exactly 11 characters
        @Pattern(regexp = "\\d{6}") String sortCode,                     // exactly 6 digits
        @Pattern(regexp = "\\d{9}") String abaRoutingNumber,             // exactly 9 digits (mod-10 미검증)
        @Pattern(regexp = "\\d{6}") String bsbNumber,                    // exactly 6 digits
        @Size(max = 12) String branchNumber,
        @Size(min = 4, max = 14) String routingCode,
        @Size(max = 32) String entityTtId,
        @Pattern(regexp = "CHECKING|SAVINGS|DEPOSIT|OTHERS") String accountType,
        @Size(max = 128) String accountNumber,
        @Size(max = 64) @Email String email,
        @Size(max = 64) String cardNumber,
        @Size(max = 512) String qrCode
) {
}
