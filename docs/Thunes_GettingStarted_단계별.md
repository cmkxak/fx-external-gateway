# Thunes Money Transfer V2 — Getting Started 단계별 해석

> 출처: https://docs.thunes.com/money-transfer/v2/ — "Getting Started" 섹션 단계별 번역·해석.
> 필드/용어는 원문(영문) 유지. 단계가 추가되면 이 문서에 이어붙인다.
> 관련: [공식문서 전체 정리](./Thunes_MoneyTransfer_V2_공식문서_정리.md)

---

## Step 1. Payer 정보 조회 (Get Payer details)

### Payer 란?
**Payer = 수취인 계좌로 자금을 입금해주는 "도착측 지급처(destination)"** 를 가리키는 Thunes 용어다.
- 예: 가나의 **모바일 월렛**, 태국의 **은행** 등.
- **Payer마다 거래에 필요한 필수 필드가 다르다.**
  - 모바일 월렛 → 보통 **계좌주의 휴대폰번호(msisdn)** 요구
  - 은행 → **은행 계좌번호** 요구

### Discovery Endpoints
**Services / Countries / Payers** 목록을 조회하는 Discovery 엔드포인트가 제공된다.
Payer 정보의 일부로, 파트너는 선택한 Payer에 연결된 다음 정보를 가져올 수 있다:
- **최소/최대 한도**(minimum/maximum limits)
- **필수 필드**(required fields)
- **통화**(currency) — **precision(소수 정밀도)·increment(증분 단위) 포함**

> 💡 통화의 `precision`/`increment`가 여기서 나온다 → 금액을 구성할 때 **Payer별 정밀도 규칙을 지켜야** 한다(BigDecimal + 검증). IDR(0자리) vs USD(2자리)가 다른 이유.

### Payer 정보 유지 방법 (2가지)
| 방법 | 설명 | 권장 |
|---|---|---|
| **① 주기적 Discovery 호출** | Discovery 엔드포인트를 주기적으로 호출하고, **Thunes의 "service update 이메일" 수신 시** 갱신하여 거래에 필요한 요구사항 전부 확보 | ✅ **권장** |
| **② 다운로드 후 DB 저장** | Discovery에서 Payer 목록을 주기적으로 내려받아 **자체 DB에 저장**해두고 사용 | 대안 |

> 설계 함의: Payer 메타데이터(필수필드/한도/통화규칙)는 **자주 안 바뀌지만 바뀔 수 있다.** </br> → 
> **캐시 + 주기 갱신 + 이메일 트리거 무효화** 패턴. </br> 
> * 매 거래마다 Discovery 호출은 비효율, </br> 
> * 그렇다고 영구 하드코딩도 위험.
> * ++ (cmkwak) 매일 자정에 Batch로 찔러서 업데이트 하는게 나을듯. (정책 협의 필요)

### 필드 조합이 여러 개인 Payer (Multiple Combinations of Fields)
일부 Payer는 다음에 대해 **여러 조합**을 지원한다:
- `credit_party_identifiers_accepted` (허용 수취인 식별자)
- `required_sending_entity_fields` (송금측 필수 필드)
- `required_receiving_entity_fields` (수취측 필수 필드)

→ **이 경우 목록의 "첫 번째 옵션"이 preferred(선호) 옵션이며 우선 사용해야 한다.**

> 구현 규칙: 조합 배열에서 **무조건 `[0]`을 기본 선택**. 사용자가 그 식별자를 못 채울 때만 다음 조합으로 폴백.

---

## *[Optional]* Step 1.1. 수취인 정보/검증 호출 (Credit Party Information / Verification)

거래 **전에** 수취인 계좌를 검증하면 **거래 성공률이 올라간다**(선택 단계).<br/>
튠즈 미팅 시, 고객 경험을 위해 **CPI/CPV를 거래 전 필수로 권장**하는 사례가 많음.<br/>
**CPI**와 **CPV**는 잘못된 수취인 정보·입력 오류로 인한 **거래 거절(decline)을 사전 예방**한다.

### CPI — Credit Party Information (수취인 정보 조회)
- 수취인 상세(**이름/성 등**)를 확인하는 용도.
- 파트너의 **수취인(Beneficiary) 관리 시스템에 등록하기 전에**, 수취인 정보가 **송금인이 입력한 값과 일치하는지** 확인하도록 권장.

### CPV — Credit Party Verification (수취인 계좌 검증)
- 수취인 **계좌의 상태(account status)** 를 제공 — 예: **수취 가능 여부**, **barred(차단)** 여부 등.
- 일부 Payer의 CPV는 **이름 매칭(name matching)** 도 제공 → **계좌 상태 확인 + 이름 일치 검증을 한 번에**.

### CPI vs CPV 비교
| | CPI (Information) | CPV (Verification) |
|---|---|---|
| 목적 | 수취인 **정보(이름 등) 조회·대조** | 수취인 **계좌 상태 검증** |
| 반환 | beneficiary/receiving_business 상세 | `account_status` (+ 일부는 name matching) |
| 쓰는 시점 | Beneficiary 등록 전 정보 정합 확인 | 거래 전 계좌 수취가능/차단 확인 |
| 엔드포인트 | `POST /payers/{id}/{transaction_type}/credit-party-information` | `POST /payers/{id}/{transaction_type}/credit-party-verification` |

> 우리 구현 매핑: 현재 `ThunesClient.verifyCreditParty(...)` = **CPV**. **CPI는 아직 미구현** → 필요 시 `credit-party-information` 메서드 추가.
> 위치상 둘 다 **Step 2(견적)~Step 3(거래) 사이, 거래 생성 전** 끼우는 선택적 사전검증.

---

## 개발 관점 요약 (Step 1 / 1.1)
- **Payer = 도착측 지급처**, 거래 가능 여부·필수필드·한도·통화정밀도의 **단일 진실원(source of truth)**.
- Payer 메타는 **캐시 + 주기 갱신**(권장: Discovery 폴링 + service-update 이메일 트리거).
- 필드 조합은 **첫 옵션 우선**.
- **CPI/CPV는 선택이지만 거래 성공률↑** → 고가치/오류잦은 코리도엔 사실상 필수. CPV로 barred 계좌를 거래 전 걸러내면 `30200/30201` 류 거절을 예방.

---

## Step 2. 견적 생성 (Create Quotation)

### 무엇을 하나
**Quotation = 특정 Payer로의 거래에 대해 "환율(rate)을 고정(lock)"하는 단계.**
이후 거래/확정이 이 고정된 견적 위에서 진행된다.

### mode — 금액 기준 방향 (둘 중 택1)
파트너는 **보내는 금액**을 줄지, **받는 금액**을 줄지 선택할 수 있고, 이를 `mode`가 제어한다.

| mode | 입력 | 반환(계산됨) |
|---|---|---|
| `SOURCE_AMOUNT` | **source amount**(보내는 금액) | destination amount(받는 금액) |
| `DESTINATION_AMOUNT` | **destination amount**(받는 금액) | 필요한 source amount(보내는 금액) |

### Source vs Destination amount (정확한 정의)
- **Source amount**: **funding 통화** 기준 금액. → **파트너의 Thunes 잔액에서 차감될 금액.**
- **Destination amount**: **수취인이 외화로 받게 될 금액.**

> 💡 Source amount가 곧 **선충전 잔액에서 빠지는 돈**이다 (Funds Flow의 prefunding과 직결). 즉 quotation은 "얼마 빠지고 얼마 도착하는지"를 **확정**하는 계약.

### Quotation 객체가 담는 것
- **FX rate(환율)** — `wholesale_fx_rate`. **견적 유효기간 동안 고정(lock)**.
- **Thunes fees** — 해당 거래에 연관된 Thunes 수수료(`fee`).
- (+ 공식문서 기준) `id`, `external_id`, `source/destination`, `sent_amount`, `expiration_date`.

### 설계 함의 (아키텍트 노트)
- **환율 락 + 만료**: `wholesale_fx_rate`는 `expiration_date`까지만 유효 → **만료 전에 confirm** 해야 함. 만료 시 `1007004`(cannot confirm; quotation expired) / `1008003`. → 견적~확정 사이 우리 검증(한도/CPV)이 **만료창 안에** 끝나야 한다.
- **가격 일관성**: 고객에게 보여준 환율 = 실제 차감 환율 보장. 그래서 견적을 먼저 받고 그 위에 거래를 올리는 2-phase 구조.
- **wholesale vs retail**: 응답의 `wholesale_fx_rate`/`fee`는 **Thunes 원가**. 우리가 고객에게 붙이는 마크업은 거래 생성 시 `retail_rate`/`retail_fee`로 별도 전달(우리 마진).

### 우리 구현 매핑
- `QuotationRequest`: `mode`, `transactionType`, `source{amount,currency,countryIsoCode}`, `destination{amount,currency}`, `payerId`, `externalId` ✅
- `mode`에 따라 한쪽 `amount`는 `null`(SOURCE_AMOUNT면 destination.amount=null) — `Money.amount`가 nullable `BigDecimal`인 이유.
- `QuotationResponse`: `id`, `wholesaleFxRate`, `fee` 등 → 현재 `wholesaleFxRate`는 `Object`(스펙 확정 후 타입 강화 예정).

---

## Step 3. 거래 생성 (Create Transaction)

### 무엇을 하나
앞서 만든 **quotation을 바탕으로 거래를 생성**하고, 추가 상세를 함께 전달한다:
- **sender / beneficiary 정보** (송금인/수취인)
- **account 정보** (`credit_party_identifier` 등)
- **purpose_of_remittance** (송금 목적)

이 단계에서 선택한 **Payer의 요구사항에 따라 선택 문서(documents)를 업로드**할 수 있다 (`/transactions/{id}/attachments`).

> 핵심: 이 시점엔 거래 객체가 **CREATED(10000)** 로 만들어질 뿐, **아직 자금은 안 움직인다.** sender/beneficiary 필수 필드는 Step 1의 Payer `required_*_fields`를 따라야 함.

---

## Step 4. 거래 확정 (Confirm Transaction)

### 무엇을 하나
거래 생성 후, **Thunes 망으로 실제 송금을 보내는 마지막 필수 단계 = confirm.**

### 잔액에 일어나는 일 (중요)
confirm 하면 **견적 단계의 source amount를 hold** 한다:

```
[confirm]  available balance ──hold──▶ pending balance      (AUTHORIZE)
[성공 완료] pending ──▶ partner balance에서 차감              (SETTLE)
[미완료]    blocked(hold)된 금액 ──▶ 그대로 반환(돌려받음)     (REVERSE)
```

- **confirm 시**: source amount가 `available` → `pending` 으로 이동(hold/blocked).
- **거래 성공 완료 시**: 그 금액이 **파트너 balance에서 실제 차감**.
- **어떤 이유로든 미완료 시**: blocked된 금액은 **반환**된다.

> 카드의 **가승인(authorize) → 매입(capture)** 과 동형. confirm = hold, 완료 = 차감, 실패 = 반환. 그래서 `available = balance − pending + credit_facility` 공식이 성립.

---

## Step 5. 거래 상태 갱신 (Transaction Status Update)

거래 확정 후, 상태를 얻는 방법은 **두 가지**:

| 방법 | 설명 | 권장 |
|---|---|---|
| **Callback** | 거래 상태가 **갱신될 때마다** Thunes가 파트너의 callback URL로 알림(push) | ✅ **권장** |
| **Polling** | 파트너가 **주기적으로** 거래 상태 API(`GET /transactions/{id}`)를 호출해 현재 상태 확인(pull) | 보조 |

> 실무: **Callback(push) 우선 + Polling(pull) 백업.** 콜백은 at-least-once라 **멱등 처리** 필수, 콜백 유실 대비 **stuck 거래 폴링 reconciler** 병행.

---

## 🔁 전체 플로우 요약 (Step 1 ~ 5)

```
[1]  GET  /payers                          지급처·필수필드·통화규칙 조회 (+ Countries/Services)
[1.1](선택) POST .../credit-party-verification   수취인 계좌상태/이름 검증(CPV), CPI=정보조회
[2]  POST /quotations                      환율·수수료 lock → quotation_id, expiration_date
       └ (우리) 자체 한도 누적액 검증
[3]  POST /quotations/{id}/transactions     sender/beneficiary/purpose + (선택)문서 → transaction(CREATED)
[4]  POST /transactions/{id}/confirm         source 금액 hold(available→pending) → 망 디스패치(CONFIRMED)
[5]  콜백(권장) / 폴링                        상태 추적 → COMPLETED(차감) | REJECTED·실패(반환)
```

핵심 의미론:
- **2-phase**: 견적(가격 lock) ↔ 확정(자금 hold) 분리. confirm 전엔 자금 무이동.
- **확정 = 끝 아님**: confirm은 hold + 디스패치까지. **최종 완료는 비동기(콜백/폴링)** 로만 확정 → confirm 성공을 "송금 완료"로 노출 금지.
- **잔액 회계**: hold(pending) → 완료 시 차감 / 실패 시 반환.
- **만료**: quotation은 `expiration_date` 내 confirm 필요(`1007004`/`1008003`).

### 우리 GW 구현 매핑
| 단계 | 엔드포인트(인바운드) | Provider 메서드 |
|---|---|---|
| 견적 | `POST /v1/thunes/quotations` | `createQuotation` |
| 수취인 검증(CPV) | `POST /v1/thunes/payers/{payerId}/{type}/credit-party-verification` | `verifyBeneficiary` |
| 거래 생성 | `POST /v1/thunes/quotations/{quotationId}/transactions` | `createTransaction` |
| 확정 | `POST /v1/thunes/transactions/{transactionId}/confirm` | `confirmTransaction` |
| 상태 콜백 | `POST /webhooks/thunes/transactions` | `ThunesCallbackController` |
