# Thunes Money Transfer V2 API — 공식 문서 섹션별 정리

> 출처: https://docs.thunes.com/money-transfer/v2/ (공식 문서)
> 페이지 전체를 섹션 순서대로 한국어로 해석·정리. 필드/enum 명은 원문(영문) 유지.
> ⚠️ enum 전체값 등 "…등"으로 표기된 목록은 요약 과정의 누락 가능성이 있으니, **확정 전 라이브 페이지로 재확인** 권장(추측값 확정 금지 원칙).

---

## 1. 개요 (Introduction)

- 전 세계 송금을 처리하는 REST API. 지급 수단(payout ecosystem): **Mobile Wallet, Bank Account, Cards, Cash Pickup** 등.
- 환경: **Production / Pre-production**, 접속은 **IPSec VPN 또는 HTTPS(TLS 1.2+)**.
- **버전 정책**:
  - v2는 v1과 **완전 분리**.
  - **Non-breaking 변경**(신규 엔드포인트·선택 필드·신규 상태)은 **사전 공지 없이** 추가될 수 있음 → 클라이언트는 **유연하게(미지 필드/상태 허용)** 구현해야 함.
  - Breaking 변경은 **새 메이저 버전**으로만.
- **헤더**: RFC 7230 따라 **대소문자 구분 없음**. 클라이언트는 대소문자에 의존하지 말 것.

---

## 2. 시작하기 (Getting Started) — 표준 5단계

| 단계 | 엔드포인트 | 설명 |
|---|---|---|
| **1. Payer 조회** | `GET /payers` | 지급처(payer) 목록 + 필수 필드/능력 조회. payer는 여러 식별자 조합 지원, **첫 옵션이 선호값** |
| **1.1 (선택) 수취인 정보/검증** | `POST /payers/{id}/{transaction_type}/credit-party-information`<br>`POST /payers/{id}/{transaction_type}/credit-party-verification` | 거래 전 수취인 상세/계좌 상태 검증. C2C/B2C → beneficiary, C2B/B2B → receiving business 반환 |
| **2. 견적 생성** | `POST /quotations` | FX 환율 고정 + 수수료 계산. `wholesale_fx_rate`, `fee`, `expiration_date` 반환 |
| **3. 거래 생성** | `POST /quotations/{id}/transactions`<br>`POST /quotations/ext-{external_id}/transactions` | 송금인/수취인 상세 + 선택 문서 제출 |
| **4. 거래 확정** | `POST /transactions/{id}/confirm`<br>`POST /transactions/ext-{external_id}/confirm` | Thunes 망으로 디스패치. **가용 잔액에서 source 금액 hold** |
| **5. 상태 갱신 수신** | `GET /transactions/{id}`<br>`GET /transactions/ext-{external_id}` | **콜백(실시간)** 또는 **폴링** |

---

## 3. 인증 (Authentication)

- **HTTP Basic Authentication**
  - User-ID = **API key**, Password = **API secret**
  - 예: `curl https://${API_ENDPOINT}/ping -u "${API_KEY}:${API_SECRET}"`
- 자격증명은 **환경변수/Vault**에 안전 보관, **VCS 커밋 금지**.

> 우리 구현: `ThunesClientConfig`에서 `Authorization: Basic base64(key:secret)` 처리 — 일치 ✅

---

## 4. 거래 상태 (Transaction States)

### 상태 클래스 (status_class, 1~7)
| 코드 | 클래스 | 의미 |
|---|---|---|
| 1 | CREATED | 초기 생성 |
| 2 | CONFIRMED | 확정·큐잉 |
| 3 | REJECTED | 제출 시 거절 |
| 4 | CANCELLED | 사용자/시스템 취소 |
| 5 | SUBMITTED | 처리 중 |
| 6 | COMPLETED-WITH-EXCEPTION | 이슈와 함께 지급 완료 |
| 7 | COMPLETED | 정상 지급 완료 |

### 상태 코드 (status, 5자리)
| 코드 | 상태 |
|---|---|
| 10000 | CREATED |
| 20000 | CONFIRMED |
| 20110 | CONFIRMED-UNDER-REVIEW-SLS |
| 30000 | REJECTED |
| 30110 | REJECTED-SLS-SENDER |
| 30120 | REJECTED-SLS-BENEFICIARY |
| 30200 | REJECTED-INVALID-BENEFICIARY |
| 30201 | REJECTED-BARRED-BENEFICIARY |
| 30202 | REJECTED-BARRED-SENDER |
| 30210 | REJECTED-INVALID-BENEFICIARY-DETAILS |
| 30392 | REJECTED-COMPLIANCE-REASON |
| 30400 | REJECTED-PAYER-CURRENTLY-UNAVAILABLE |
| 40000 | CANCELLED |
| 50000 | SUBMITTED |
| 60000 | COMPLETED-WITH-EXCEPTION |
| 60001 | COMPLETED-WITH-EXCEPTION-REVERSAL-INITIATED |
| 60010 | COMPLETED-WITH-EXCEPTION-REVERSAL-REJECTED |
| 60020 | COMPLETED-WITH-EXCEPTION-REVERSAL-COMPLETED |
| 70000 | COMPLETED |
| 80000 | EXPIRED |

> **반전(Reversal)/취소는 Thunes 고객지원팀 승인 필요.**

---

## 5. 콜백 (Callback)

- Thunes → 파트너 제공 URL로 **`POST`** (Transaction 객체 JSON 전체).
- 파트너는 **HTTP 2XX** 응답해야 정상 수신 처리.
- non-2XX면 **자동 재시도**, 끝내 실패하면 **수동 폴링** 필요.

> 우리 구현: `ThunesCallbackController` (`POST /webhooks/thunes/transactions`) — 2XX 반환 ✅. **멱등 처리(중복 콜백) + 서명검증 TODO** 남음.

---

## 6. 에러 (Errors)

### HTTP 응답 코드
| 코드 | 의미 |
|---|---|
| 200 | OK |
| 400 | Bad Request(형식 오류) |
| 401 | Unauthorized(자격증명 무효) |
| 404 | Not Found |
| 500 | Server Error |

### API 에러 코드 (대표)
| 코드 | 의미 |
|---|---|
| 1000401 | Unauthorized |
| 1000404 | Resource not found |
| 1000999 | Invalid parameter |
| 1000998 | Source country not authorized |
| 1000997 | Source·Payer country not authorized |
| 1003001 | Payer inactive in account |
| 1003002 | Invalid payer |
| 1003007 | Payer currently unavailable |
| 1003008 | Destination amount invalid |
| 1003010 | Destination currency not provided by payer |
| 1003011 | 최소 금액 미만 |
| 1003012 | 최대 금액 초과 |
| 1005001 | Account invalid |
| 1006001 | Transaction amount limit exceeded |
| 1006002 | Account quantity limit exceeded |
| 1006099 | Limit exceeded |
| 1007001 | External ID already used |
| 1007002 | Transaction already confirmed |
| 1007003 | Transaction cannot be confirmed |
| 1007004 | Cannot confirm; **quotation expired** |
| 1007005 | Cannot confirm; **insufficient balance** |
| 1007014 | Transaction cannot be cancelled |
| 1007100 | Method not supported by payer |
| 1007101 | Method currently unavailable |
| 1007401~1007405 | 첨부 관련(용량/개수/확정후/미존재/타입) |
| 1008002 | Quotation not found |
| 1008003 | Quotation expired |
| 1008004 | Transaction not found |
| 1009001 | Unexpected error; contact support |

> 우리 `ThunesApiException`이 status+body를 담고 있음. 추후 **이 에러코드 → 도메인 예외 매핑** 고려(추상화 단계).

---

## 7. 페이지네이션 (Pagination)

- 입력: `page`(기본 1), `per_page`(기본 50, 최대 100)
- 출력 헤더: `X-Total`, `X-Total-Pages`, `X-Per-Page`, `X-Page`, `X-Next-Page`, `X-Prev-Page`
- **Balance Movements는 커서 기반**(`X-Next-Cursor`)

---

## 8. 엔드포인트 전체

### 8.1 Connectivity
- `GET /ping` → `{"status":"up"}`

### 8.2 Discovery
- **Services**: `GET /v2/money-transfer/services` — `country_iso_code`(ISO 3166-1 **alpha-3**), `page`, `per_page`. 서비스 종류: MobileWallet/BankAccount/CashPickup/Card/HomeDelivery/**DigitalAssetWallet**
- **Payers (목록)**: `GET /v2/money-transfer/payers` — `service_id`, `country_iso_code`, `currency`. payer 객체에 **transaction_types별 min/max 금액, 허용 식별자, 필수 필드, 필요 문서, CPI/CPV 정보** 포함
- **Payer 상세**: `GET /v2/money-transfer/payers/{id}`
- **Payer Rates**: `GET /v2/money-transfer/payers/{id}/rates` — 통화별 rate tier(`source_amount_min/max`, `wholesale_fx_rate`)
- **Countries**: `GET /v2/money-transfer/countries`
- **BIC Lookup**: `GET /v2/money-transfer/lookups/BIC/{swift_bic_code}` (일부 국가만)

### 8.3 Account
- **Balances**: `GET /v2/money-transfer/balances` — 통화별 `balance, pending, available, credit_facility`
  - **공식: `available = balance − pending + credit_facility`**
- **Balance Movements**: `GET /v2/money-transfer/balances/{balance_id}/movements` — `from_date`,`to_date`(필수, 24시간 이내), `limit`(기본100/최대200), `cursor`. 객체: `movement_type`(PAYOUT/PAYOUT_FEES 등), `operation`(AUTHORIZE/SETTLE), `transaction_reference_id`
- **Topup Instructions**: `GET /v2/money-transfer/balances/{balance_id}/topup_instructions` — 충전용 은행 계좌 정보(account_number, iban, swift_code, 중계은행 등)
- **Reports**: `GET /reports`, `GET /reports/{id}`, `GET /reports/{id}/files`, `GET /reports/{report_id}/files/{id}`(서명된 다운로드 URL)

### 8.4 Credit Parties
- **Information**: `POST /v2/money-transfer/payers/{id}/{transaction_type}/credit-party-information` → beneficiary 또는 receiving_business 상세
- **Verification**: `POST /v2/money-transfer/payers/{id}/{transaction_type}/credit-party-verification` → `id`(검증요청ID), `account_status`

### 8.5 Transfers
- **Quotation 생성**: `POST /v2/money-transfer/quotations`
  - 필수: `external_id`(≤64), `payer_id`(int), `mode`, `transaction_type`, `source{country_iso_code, currency, [amount]}`, `destination{currency, [amount]}`
  - 반환: `id`, `wholesale_fx_rate`, `fee`, `expiration_date`
- **Quotation 조회**: `GET /quotations/{id}`, `GET /quotations/ext-{external_id}`
- **Transaction 생성**: `POST /quotations/{id}/transactions`, `POST /quotations/ext-{external_id}/transactions`
  - 필수: `credit_party_identifier`, `external_id`(≤64), `purpose_of_remittance`, `sender`(C2C/C2B) 또는 `sending_business`(B2C/B2B), `beneficiary`(C2C/B2C) 또는 `receiving_business`(C2B/B2B)
  - 선택: `retail_rate`, `retail_fee`, `retail_fee_currency`, `external_code`, `callback_url`, `document_reference_number`, `additional_information_1/2/3`, `reference`
  - **B2B는 `document_reference_number` 필수**
  - 반환: status **10000(CREATED)**
- **첨부 추가**: `POST /transactions/{id}/attachments` (multipart/form-data) — `name`(≤64), `type`, `file`. **최대 3개/건, 8MB/개, 특정 타입만, 확정 후 추가 불가**
- **확정**: `POST /transactions/{id}/confirm` → source 금액 hold, status **20000(CONFIRMED)**
- **조회**: `GET /transactions/{id}` (시뮬레이션이면 헤더 `x-simulated-transaction=true`)
- **첨부 목록**: `GET /transactions/{id}/attachments`
- **취소**: `POST /transactions/{id}/cancel` — **CREATED 또는 CONFIRMED-WAITING-FOR-PICKUP 상태만** 가능, 불가 시 `1007014`, 성공 시 status **40000(CANCELLED)**
- (모든 거래 엔드포인트는 `ext-{external_id}` 변형 존재)

### 8.6 Simulation (Pre-production 전용)
- `GET /v2/simulation/transactions/{id}/possible-transitions` — 전이 가능한 상태 목록
- `GET /v2/simulation/transactions/{id}/{status_name}` — 상태 강제 변경(대소문자 무시)
- `GET /v2/simulation/transactions/ext-{external_id}/{status_name}`
- 절차: 견적생성 → 거래생성(헤더 `x-simulated-transaction: true` 또는 전역설정) → confirm → 상태 시뮬레이션

---

## 9. 주요 데이터 객체 (Resources)

- **Quotation**: `id, external_id, payer, mode, transaction_type, source{currency,amount,country_iso_code}, destination{currency,amount}, sent_amount, wholesale_fx_rate, fee, creation_date, expiration_date`
- **Transaction**: `id, status(5자리), status_message, status_class(1~7), status_class_message, external_id, external_code, transaction_type, payer_transaction_reference/code, creation_date, expiration_date, credit_party_identifier, source, destination, payer, sender, beneficiary, sending_business, receiving_business, sent_amount, wholesale_fx_rate, retail_rate, retail_fee, fee{amount,currency}, purpose_of_remittance, document_reference_number, additional_information_1/2/3, reference, callback_url`
- **Source/Destination**: `amount`(**decimal**), `currency`(ISO 4217), `country_iso_code`(source만)
- **Balance**: `id, currency, balance, pending, available, credit_facility`
- **Balance Movement**: `balance_operation_number, creation_date, movement_type, amount, currency, transaction_reference_id, operation, balance, pending_balance`
- **Sender**: 성명(lastname/firstname/middlename/lastname2/nativename), date_of_birth, nationality/country_of_birth, gender, 주소, msisdn/email, id_type/id_number/id_country/id_delivery_date/id_expiration_date, occupation, **beneficiary_relationship, source_of_funds**, bank_account_number, code
- **Beneficiary**: Sender와 유사 + `bank_account_holder_name`, (source_of_funds·relationship 없음)
- **Sending/Receiving Business**: registered_name, 대표자 성명, 주소/연락처, 사업자등록정보, tax_id, business_type
- **RFI(Request For Information)**: `id, status, creation_date, expiration_date, attachments, provided_information, requested_information, transaction_id` — 추가 정보 요청 워크플로

---

## 10. Enumerations (열거값)

> ⚠️ 아래 목록 중 "…등" 표기/장문 목록은 **요약본**이라 누락 가능. 실제 코드 매핑 시 라이브 페이지에서 verbatim 확인.

- **Quotation Mode**: `SOURCE_AMOUNT`, `DESTINATION_AMOUNT`
- **Transaction Type**: `C2C`, `C2B`, `B2C`, `B2B`
- **Gender**: `MALE`, `FEMALE`, `OTHER`
- **Status Class**: 1~7 (§4)
- **Account Status**: `AVAILABLE`, `BARRED`, `INACTIVE`, `SUSPENDED`, `UNVERIFIED` 등
- **ID Type**: `PASSPORT`, `NATIONAL_ID`, `DRIVER_LICENSE`, `SOCIAL_SECURITY`, `BUSINESS_LICENSE`, `TAX_ID`, `BIRTH_CERTIFICATE` 등
- **Purpose of Remittance**: `FAMILY_SUPPORT`, `SALARY`, `BUSINESS_PAYMENT`, `INVOICE_PAYMENT`, `LOAN_REPAYMENT`, `DEPOSIT`, `TRANSFER`, `GOODS_PAYMENT`, `SERVICES_PAYMENT`, `DONATION`, `INVESTMENT`, `EDUCATION`, `MEDICAL`, `UTILITIES`, `RENT`, `GOVERNMENT_PAYMENT`, `INSURANCE`, `DEBT_REPAYMENT`, `REFUND` 등 (⚠️ 전체값 재확인)
- **Beneficiary Relationship**: `SPOUSE`, `PARENT`, `SIBLING`, `CHILD`, `RELATIVE`, `FRIEND`, `BUSINESS_ASSOCIATE` 등
- **Source of Funds**: `EMPLOYMENT`, `BUSINESS`, `INVESTMENT`, `GIFT`, `SAVINGS`, `PENSION`, `INHERITANCE` 등
- **Bank Account Type**: `CHECKING`, `SAVINGS`, `MONEY_MARKET`, `INVESTMENT`, `CORPORATE` 등
- **Balance Movement Type**: `PAYOUT`, `PAYOUT_FEES`, `TOPUP`, `REVERSAL`, `ADJUSTMENT`, `INTEREST` 등
- **Balance Operation**: `AUTHORIZE`(hold), `SETTLE`(차감), `REVERSE`, `ADJUST`
- **Local Account Identifier Type**: `NUBAN`(나이지리아), `CLABE`(멕시코) 등
- **Attachment 허용 파일**: PDF/DOC/DOCX/XLS/XLSX/JPG/JPEG/PNG/GIF (payer별 제한)
- **RFI Status**: `OPEN`, `PENDING`, `PROVIDED`, `REJECTED`, `CLOSED`, `EXPIRED`, `CANCELLED`
- **Report Type**: `PARTNER_TRANSACTION_REPORT_DAILY`, `..._MONTHLY` 등

---

## 11. 국가별 구현 가이드 (Implementation Insights)

문서에 **Pay to Stablecoin** + 30여개국 개별 가이드 존재 (방글라데시·브라질·캐나다·칠레·중국·콜롬비아·이집트·에티오피아·가나·인도·인도네시아·일본·케냐·**한국(Korea)**·말레이시아·멕시코·파키스탄·필리핀·SEPA·남아공·대만·탄자니아·태국·터키·우간다·UK·USA·베트남·잠비아 등).
각 국가별: payer 요구사항, 필드 매핑/검증, 현지 규제, 식별자 규칙, 통화 precision/limit, 특수 문서 요구.

---

## 12. 중요 기술 노트

1. **ID는 64-bit 사용 필수** — Quotation/Transaction/Verification/RFI ID가 32-bit 정수 한계 초과 → `BIGINT/int64/long/Number` 사용 (오버플로 방지)
2. **Non-breaking 변경 무공지** — 신규 필드/상태에 견고하게(미지값 허용)
3. **Quotation 만료** — 만료창 내 confirm 필요 (`1007004`, `1008003`)
4. **External ID** — 파트너 고유값으로 응답유실 복구·중복방지(멱등)
5. **Callback + 폴링 병행** — 콜백 신뢰성 보강
6. **통화 precision/increment** — payer별 규칙 준수해 금액 구성
7. **식별자 다중 조합** — payer가 여러 옵션 반환, **첫 옵션 선호**
8. **첨부 제한** — 3개/8MB/타입제한/확정후 불가
9. **시뮬레이션은 pre-prod 전용**

---

## 13. 우리 GW 구현 대조 (이 문서가 확정해준 것)

| 항목 | 문서 확인 | 우리 코드 |
|---|---|---|
| 금액 타입 | source/destination **amount = decimal** | `BigDecimal` 통일 ✅ (확정 근거 확보) |
| ID 타입 | **64-bit 필수** | `id`=`Long`, path=`long` ✅ |
| 인증 | Basic(key:secret) | `ThunesClientConfig` ✅ |
| 5단계 흐름 | payer→(CPV)→quotation→transaction→confirm→status | provider 메서드 분리 ✅ |
| 콜백 2XX | 필수 | `ThunesCallbackController` ✅ (멱등/서명검증 TODO) |
| `purpose_of_remittance` | 필수 + enum 값 공개됨 | 현재 `String` (enum화는 추상화 단계에서) |
| 미지 필드/상태 허용 | 권고 | Jackson `FAIL_ON_UNKNOWN_PROPERTIES=false` 설정 검토 필요 ⚠️ |
| 에러코드 체계 | 7자리 API 코드 | `ThunesApiException`(status+body) → 코드매핑 추후 |

### 후속 반영 후보 (추상화 단계에서)
- `purpose_of_remittance`, `gender`, `id_type`, status(class/code) **enum화** — 단 값은 verbatim 재확인 후
- Jackson `fail-on-unknown-properties: false` (non-breaking 신규필드 대비)
- 에러코드 → 도메인 예외 매핑
- `GET /balances` 잔액 모니터링, 취소(`/cancel`), 첨부(`/attachments`), RFI 워크플로 (필요 시)

---

### 관련 문서
- [정보 흐름](./Thunes_정보흐름_information-flow.md)
- [자금 흐름(Prefunding)](./Thunes_자금흐름_funds-flow.md)
- [Pay 개발가이드(Postman 기반)](./Thunes_Pay_개발가이드_정리.md)
