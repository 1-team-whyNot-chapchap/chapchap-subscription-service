# Subscription Service Postman 검증

## 로컬 검증 환경

- Java 21
- MySQL: `.env`의 `DB_URL` 또는 테스트 전용 `TEST_DB_URL`
- Kafka: `localhost:9092`

Kafka Broker 통합 테스트는 공식 Apache Kafka 이미지 기반의 로컬 구성을 사용한다.

```powershell
docker compose -f compose.kafka.yml up -d --wait
./gradlew.bat kafkaIntegrationTest
docker compose -f compose.kafka.yml down
```

일반 테스트는 Kafka 없이 실행되며 `chapchap_subscription_test` 전용 스키마를 기본값으로 사용한다.

```powershell
./gradlew.bat clean test
```

개별 환경에서는 `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`로 테스트 DB 접속 정보를 덮어쓸 수 있다.

## 실행

애플리케이션을 `http://localhost:8082`에서 실행한 뒤 Repository 루트에서 다음 명령을 실행한다.

```powershell
postman collection run postman/chapchap-subscription.postman_collection.json `
  -e postman/environments/local.postman_environment.example.json `
  --bail failure `
  --no-report-events
```

공용 Collection은 다음 폴더를 포함한다.

| 폴더 | 검증 범위 | 주요 사전조건 |
|---|---|---|
| `SUB-FN-001` | 배송지 등록·목록·수정·기본 지정·복원·삭제 | 기존 기본 배송지가 있는 테스트 고객 |
| `SUB-FN-002` | 현재 약관 조회·동의 | 현재 필수 약관 Fixture |
| `SUB-FN-003` | 결제수단 목록·현재 선택·소유권·등록·삭제 | 기존 결제수단 2개, 등록은 `testBillingKey`가 있을 때만 |
| `SUB-FN-004` | 첫 구독 신청·첫 결제 | 약관·배송지·결제수단·PortOne Test Channel |
| `SUB-FN-005` | 현재 구독 조회 | 조회 대상 구독 |
| `SUB-FN-006` | 설정 변경·증액 결제 확인 | 이용 중 구독, 변경 플랜, 제어 가능한 PG 결과 |
| `SUB-FN-007` | 해지·시작 취소·재시도 중단 | 취소 가능한 상태의 구독 |
| `SUB-FN-008` | 결제·환불 목록·상세 | 상세 요청은 목록이 비면 자동 Skip |
| `SUB-FN-017` | 플랜·메뉴·주문 목록·상세 | 주문 상세는 목록이 비면 자동 Skip |
| `E2E` | 첫 구독, 취소·환불, 자동 갱신 결과 검증 | 아래 업무별 Fixture와 외부 연동 |

상태 변경 폴더는 전체 Collection을 한 번에 실행하기보다 필요한 Fixture를 준비하고 `-i`로 선택 실행한다. 조회 중심 폴더는 다음처럼 바로 실행할 수 있다.

```powershell
postman collection run postman/chapchap-subscription.postman_collection.json `
  -e postman/environments/local.postman_environment.json `
  -i "SUB-FN-002" `
  -i "SUB-FN-008" `
  -i "SUB-FN-017" `
  --bail failure `
  --no-report-events
```

Postman CLI가 설치되지 않은 환경에서는 Node.js의 일회성 Newman 실행을 사용할 수 있다.

```powershell
npx --yes newman run postman/chapchap-subscription.postman_collection.json `
  -e postman/environments/local.postman_environment.json `
  --folder "SUB-FN-017" `
  --bail failure
```

## E2E 실행 경계

Collection의 `E2E` 폴더는 HTTP 결과 검증을 담당한다. Scheduler 실행, DB Fixture 구성, Kafka Topic 확인과 PG 결과 제어는 HTTP 요청만으로 대신하지 않는다.

### 첫 구독

1. `SUB-FN-017`에서 플랜을 선택한다.
2. `SUB-FN-001`, `002`, `003`으로 배송지·약관·결제수단을 준비한다.
3. 제어 가능한 PortOne Test Channel에서 `SUB-FN-004`를 실행한다.
4. `E2E / 첫 구독`으로 구독·결제·주문을 확인한다.
5. 이용 시작 Scheduler 이후 구독 상태와 Delivery Kafka Topic을 확인한다.

### 취소·환불

1. 취소 유형에 맞는 구독·기간·주문·결제 Fixture를 준비한다.
2. `SUB-FN-007`을 실행한다.
3. `E2E / 취소·환불`로 구독과 환불 결과를 확인한다.
4. DB 배분 금액과 Auth·Customer Event를 함께 확인한다.

### 자동 갱신

1. 다음 이용 기간 생성 대상 Fixture를 준비한다.
2. 다음 기간 생성, 09시 결제, 필요한 경우 13시 재시도 Scheduler를 실행한다.
3. `E2E / 자동 갱신`으로 결제·주문·구독 결과를 확인한다.
4. Payment·Customer·Delivery Kafka Event와 DB 최종 상태를 확인한다.

운영 표준에 따라 Scheduler를 대신하는 테스트 전용 업무 API는 추가하지 않는다.

## 자동결제수단 선택 사전조건

- 테스트 고객(`testUserId`, 기본값 `1`)에게 `AVAILABLE` 결제수단이 두 개 이상 존재한다.
- 테스트 고객의 현재 결제수단은 정확히 하나다.
- 다른 고객(`otherUserId`, 기본값 `2`)에게 `AVAILABLE` 결제수단이 하나 이상 존재한다.
- 구독 없음 조회용 고객(`noSubscriptionUserId`, 기본값 `999999`)에게 구독 행이 없어야 한다.
- 실행 과정에서 테스트 고객의 다른 결제수단을 현재 수단으로 선택한 뒤 최초 현재 수단으로 복원한다.

Collection은 `GET /api/subscription/payment-methods` 응답에서 필요한 공개 식별자를 자동으로 찾는다. 사전조건이 충족되지 않으면 Setup 단계에서 실패하고 이후 요청을 실행하지 않는다.

## 민감정보

실제 JWT, Secret, PortOne Secret, 빌링키는 Collection 또는 Git에 저장하지 않는다. 개인별 실제 Environment 파일은 `postman/environments/`에 둘 수 있지만 `.gitignore` 대상이며, Git에는 `*.example.postman_environment.json`만 포함한다.

## 검증 한계

Collection은 HTTP 응답과 결제수단 목록 API로 현재 수단 변경·복원을 검증한다. `last_selected_at`이 재선택 시 변경되지 않는지는 고객 API로 노출되지 않으므로 별도의 DB 확인 또는 서비스 테스트가 필요하다.

## SUB-FN-004 첫 구독 Draft Collection

`postman/drafts/sub-fn-004-first-subscription.postman_collection.json`은 첫 구독 API 통합 전에 준비한 초안이다. 아직 위의 기본 CLI 명령에는 포함하지 않는다. 각 요청은 서로 다른 DB·Provider 상태를 요구하므로 Collection 전체를 한 번에 실행하지 않고, 필요한 Fixture를 구성한 뒤 `-i` 옵션으로 한 시나리오씩 실행한다.

```powershell
$scenarioName = "03 미인증"
postman collection run postman/drafts/sub-fn-004-first-subscription.postman_collection.json `
  -e postman/environments/local.postman_environment.json `
  -i $scenarioName `
  --bail failure `
  --no-report-events
```

Git에 포함된 Example Environment로 외부 결제를 호출하지 않는 미인증 시나리오만 확인하려면 다음과 같이 실행한다.

```powershell
postman collection run postman/drafts/sub-fn-004-first-subscription.postman_collection.json `
  -e postman/environments/local.postman_environment.example.json `
  -i "03 미인증" `
  --bail failure `
  --no-report-events
```

`03 미인증`, `04 필수 약관 미동의`, `12 요청 형식 오류`는 빈 `testPlanId`·`testAddressId`와 무관하게 해당 실패 조건만 확인할 수 있도록 문법상 유효한 고정 ID 또는 의도적으로 잘못된 Request를 사용한다. `04`는 약관 동의 이력이 없는 로컬 전용 사용자 ID로 실행해야 한다.

실행 전 Git에서 제외된 개인 Local Environment를 만들고 `testPlanId`, `testAddressId`를 실제 로컬 Fixture의 공개 ID로 채운다. 테스트 사용자에게는 현재 필수 약관 동의, 활성 배송지, 현재 `AVAILABLE` 자동결제수단이 필요하다. 시나리오마다 요구하는 구독·약관·결제수단·Provider 상태가 다르므로 Description의 사전조건에 맞게 DB Fixture 또는 제어 가능한 Mock을 재구성한 뒤 해당 요청만 실행한다.

Draft Collection이 확인하는 범위는 HTTP Status, 공통 응답의 `code`·`message`, 공개 구독 ID 형식과 응답 상태다. 다음 항목은 API 응답만으로 확인할 수 없으므로 별도 DB 검증이 필요하다.

- Subscription·Period·Setting·Order의 상태와 생성 개수
- Payment Transaction·Attempt·Allocation의 상태와 생성 여부
- 첫 할인 사용 이력
- 기존 `PROCESSING` 재요청에서 새 데이터와 PG 호출이 생기지 않았는지

Mock 검증은 timeout·5xx 등 결과 분류와 내부 상태 전이를 재현하기 위한 것이며 실제 PortOne 계약·채널 설정·PG별 응답을 검증하지 않는다. 실제 PortOne Test Channel 검증은 별도 승인과 테스트 전용 Credential·빌링키·정리 절차를 준비한 뒤 수행한다.
