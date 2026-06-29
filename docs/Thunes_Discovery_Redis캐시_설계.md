# Thunes Discovery — Redis 캐시 설계

- 문서 버전: v1 (2026-06-29)
- 대상: Thunes Money Transfer V2 Discovery(서비스·지급처) 조회 결과 캐싱
- 목적: **Thunes 직접 호출 최소화 → rate limit 회피 + 유저 트래픽과 Thunes 디커플**

---

## 1. 배경 / 목적

- Discovery 데이터(services, payers, payer 단건)는 **요청마다 Thunes를 찌르면** 트래픽 몰릴 때 rate limit에 걸린다.
- 이 데이터는 **거의 정적**(금액 규칙·필수필드·payer 메타)이라 캐싱에 적합.
- 원칙: **읽기는 캐시만 본다. Thunes는 스케줄러(갱신 잡)만 호출한다.**

---

## 2. 인프라 전제

- **App 인스턴스 3대 + 공유 단일 Redis 1대** (3대가 동일 Redis를 바라봄).
- 효과: ① 캐시 공유(갱신 1회 → 3대 공통) ② 분산 락도 같은 Redis로 해결.
- 리스크: **단일 Redis = SPOF** (→ §8 대비책).

---

## 3. 대상 엔드포인트 (V2 docs 기준)

| Discovery | 경로 | 필터 |
|---|---|---|
| Services | `GET /v2/money-transfer/services` | country_iso_code(opt) |
| Payers 목록 | `GET /v2/money-transfer/payers` | service_id · country_iso_code · currency (전부 opt) |
| Payer 단건 | `GET /v2/money-transfer/payers/{id}` | — |

> payers 목록 응답의 payer 객체는 **`transaction_types`까지 포함**(단건 GET과 동일 구조) → 목록 쓸기로 단건 캐시까지 워밍 가능.

---

## 4. 키 네이밍

네임스페이스 접두사 `thunes:disc:` + 콜론(`:`) 구분 (Redis 관례).

| 용도 | 키 패턴 | 값 | TTL |
|---|---|---|---|
| Payers (읽기 주력) | `thunes:disc:payers:{country}:{service_id}:{currency}` | `Payer[]` (JSON) | 36–48h |
| Payer 단건 | `thunes:disc:payer:{payer_id}` | `Payer` (JSON) | 36–48h |
| Services | `thunes:disc:services:{country|ALL}` | `Service[]` (JSON) | 36–48h |
| 통화 셀렉터 인덱스 (선택) | `thunes:disc:currencies:{country}:{service_id}` | `string[]` (JSON) | 36–48h |
| 갱신 락 | `thunes:disc:refresh:lock` | 락 토큰 | lease ≥ 잡 시간 + 여유 |

예시:
```
thunes:disc:payers:KHM:2:USD        ← 캄보디아 · BankAccount · USD
thunes:disc:payers:KHM:2:KHR        ← 캄보디아 · BankAccount · KHR (다통화)
thunes:disc:payer:214
thunes:disc:services:IDN
thunes:disc:currencies:KHM:2        → ["USD","KHR"]
```

### 키 차원 근거
- **currency 포함 필수**: 소액해외송금엔 **다통화 국가**(캄보디아 USD+KHR, 짐바브웨/파나마/에콰도르 USD 등)가 흔함. `(country, service)`만 키로 잡으면 다통화가 한 키에 뭉쳐 **잘못된 통화 payer 선택 위험**. country↔currency가 1:1이 아니므로 country·currency 둘 다 필요.
- 견적 시점엔 **country·수취수단(service)·수취통화 3개를 이미 알고 있음** → 키로 정확히 단건 매칭.
- `service`는 표시명(service_name)이 아니라 **`service_id`로 질의/키 구성** (name은 표시용).
- 값(value)은 **JSON 문자열(UTF-8)**, payer 객체 전체(`transaction_types` 포함) 저장.

---

## 5. TTL & 만료 정책

- **TTL = 36–48h** (갱신주기 24h보다 **반드시 길게**).
- **핵심 원칙: TTL은 안전망, 진짜 신선도는 스케줄러가 담당.**
  - 정상 운영 시 24h마다 갱신이 TTL(36–48h) 만료 전에 덮어쓰므로 **자연 만료가 안 일어남**.
  - 갱신 잡이 한 번 실패/지연해도 **하루치 여유**로 last-known을 계속 서빙 → 미스 폭주(=rate limit 직격) 방지.
- **금지: TTL = 갱신주기(24h=24h).** 갱신 시점 근처에서 키가 자연 만료 → 잡 한 번 삐끗하면 그 순간 캐시미스 → 유저 트래픽이 Thunes로 직격.
- (대안) TTL 없이 영속 + 잡 실패 모니터링. 단 스케줄러가 조용히 멈추면 영원히 stale → **비권장**.
- 갱신 락 키만 별도: lease(TTL) = 잡 최대 실행시간 + 여유, 또는 watchdog 자동연장.

---

## 6. 갱신 전략 (스케줄링)

### 트리거
- **매일 12:00 KST** 1회. (타임존 KST 명시, "정오/자정" 확정 필요 — 트래픽 적은 시간대 권장)

### 단일 실행 (3대 중 1대만)
- 3대가 모두 `@Scheduled` 발화 → **Redis 락으로 1대만 실제 실행**.
- 방법(택1):
  - **ShedLock (Redis provider)** ★ 가장 간단(`@SchedulerLock`). 기존 `fx-remittance-api`와 일관.
  - **Redisson lock** — watchdog가 락 자동 연장(잡 길어져도 안전).
  - **`SET NX PX` 직접** — 가볍지만 **해제 시 내 토큰일 때만 DEL**(Lua compare-and-del) 필수.
- 락 설정: `lockAtMostFor` ≥ 잡 최대 소요(인스턴스 죽어도 자동 해제), `lockAtLeastFor`로 초고속 재실행 방지.

### 쓸기 방식 — **country×service fan-out 금지**
country×service로 돌리면 수백 콜 → 정오에 그 자체가 rate limit 직격.
대신 **`/payers` 필터 없이 전체를 페이지네이션으로 1회 쓸고 → 로컬 버킷팅.**

```
[12:00 KST · 락 획득한 1대만]
 1) GET /v2/money-transfer/payers  (필터X, 페이지 끝까지)
        → Thunes 콜 = 페이지 수만큼만 (예: 전체 2,000개 / 100 = 20콜)
 2) 메모리 버킷팅:
        payers[]  group by (country, service_id, currency)
                  → SET thunes:disc:payers:{c}:{svc}:{ccy}
        payer 각각 → SET thunes:disc:payer:{id}
        (선택) group by (country, service_id) → currencies 인덱스
 3) services: 위 결과에서 (국가별 존재 서비스) 파생  또는  GET /services 1콜
 4) 성공한 키만 덮어쓰기(TTL 36–48h)
```

- **콜 수 비교**: (국가 50 × 서비스 3 = 150콜) vs (전체 payer 2,000 = 20콜) → ~10배 절감.
- country×service×currency 조합이 몇 개든 **콜 수 불변**(한 번 쓸고 로컬 분배).
- **페이싱**: 콜 수 적어 거의 불필요. 단 안전하게 페이지 간 소량 딜레이 + 재시도(지수 백오프).

### 원자성 / 부분 실패
- 새 값 전부 만든 뒤 SET(덮어쓰기). 키별 try/catch.
- **부분 실패 시 기존 좋은 키는 절대 삭제 금지** → last-known 유지 + 운영 알림.

---

## 7. 읽기 경로

- 정확한 키 1회 조회. **Hit → 사용.**
- **Miss / Redis 다운 → 동기 Thunes 호출 금지.** last-known·기본 폴백 응답 + 로그/알림.
  - 굳이 라이브 보충이 필요하면 **단일 요청만(락)** 으로 single-flight 후 캐시 적재(기본은 cache-only).
- 목적이 "유저 트래픽을 Thunes와 분리"이므로 **읽기에서 Thunes 직접 호출은 예외(폴백 실패 최후수단)로만.**

---

## 8. 유의사항 (체크리스트)

1. **TTL > 갱신주기** — 같으면 만료 폭주. (36–48h vs 24h)
2. **단일 실행 락** — 3 인스턴스가 정오에 동시에 찌르면 미니 스탬피드. Redis 락으로 1대만.
3. **fan-out 금지** — country×service 루프 대신 `/payers` 전체 1회 쓸기 + 로컬 버킷팅.
4. **성공 시에만 덮어쓰기** — 부분 실패에 기존 키 보존, 알림.
5. **다통화 국가** — 키에 `currency` 차원 포함.
6. **Thunes 무공지 변경** — V2는 non-breaking 변경(신규 필수필드·payer 추가 등)을 **무공지로** 투입(docs 명시). 캐시를 불변 취급 금지 → TTL·일일 갱신으로 흡수.
7. **금액 직렬화** — `min/maximum_transaction_amount` 가 docs상 `Decimal`이나 **실측 응답에서 문자열(`"50.00000000"`)로 내려온 사례** 있음 → `BigDecimal` 파싱.
8. **payer 가용성은 캐시 대상 아님** — "이용 가능 여부"는 Discovery 객체에 없고 **거래 시점 에러(`1003007` 등)**로 옴. Discovery 캐시는 메타데이터만.
9. **Rate limit 실제 수치 미확인** — 본 작업에서 본 V2 docs 페이지엔 한도/429 정책 수치 없음. TTL·동시성 튜닝하려면 **계약서/Thunes 지원에 확인**.
10. **SPOF** — 단일 Redis. 대비: L1 인-프로세스 캐시(짧은 TTL) / Redis Sentinel·HA / Redis 다운 시 graceful degrade.
11. **타임존** — cron에 KST 명시, 정오/자정 확정.

---

## 9. 최종 요약

```
인프라 : app 3대 + 공유 Redis 1대

읽기 키 :
  thunes:disc:payers:{country}:{service_id}:{currency}   ← 주력(다통화 안전)
  thunes:disc:payer:{payer_id}                           ← 단건
  thunes:disc:services:{country|ALL}                     ← 서비스
  thunes:disc:currencies:{country}:{service_id}          ← (선택) 통화 셀렉터

TTL    : 36–48h  (갱신 24h보다 길게 = 안전망)

갱신   : 매일 12:00 KST · ShedLock(Redis)로 1대만 실행
         GET /payers 전체 1회 쓸기 → (country,service_id,currency) 버킷팅
         → payers/payer/services/currencies 키 SET
         성공 시에만 덮어쓰기, 부분 실패 시 기존 보존 + 알림

읽기   : 캐시-only. miss/Redis다운 → 폴백(동기 Thunes 호출 X)

락     : thunes:disc:refresh:lock, lease ≥ 잡 시간 + 여유 (또는 watchdog)
```

---

## 10. 미결정 / 확인 필요

- **통화 셀렉터 인덱스**(`currencies:{country}:{service_id}`) 둘지 — 통화 선택 UX 필요 여부에 따라.
- **L1 인-프로세스 캐시 vs Redis HA** — SPOF 대비 수준 결정.
- **갱신 시각** 정오/자정 확정 + 트래픽 패턴 확인.
- **Thunes rate limit 실제 수치** 확보(TTL·페이싱 정밀 튜닝용).
- 값 직렬화 포맷(원본 JSON 그대로 vs 정규화 DTO) 확정.
