# 0000 - 초기 프로젝트 설정

## 개요
Spring Boot 4.0.1 기반의 모놀리식 백엔드 애플리케이션 초기 설정

## 기술 스택
- Java 25
- Spring Boot 4.0.1
- Spring Data JPA
- Spring Batch
- H2 Database
- Lombok

## 주요 도메인
- Member: 회원 관리
- Market: 상품 및 주문 관리
- Cash: 지갑 및 결제 관리
- Payout: 정산 관리
- Post: 게시글 관리

---

# 0001 - Spring Security 인증/인가 시스템 구현

## 개요
JWT 기반 인증/인가 시스템 도입

## 변경 사항

### 의존성 추가
- spring-boot-starter-security
- spring-boot-starter-json
- springdoc-openapi-starter-webmvc-ui
- jjwt-api, jjwt-impl, jjwt-jackson

### Security 패키지 (com.back.global.security)
- SecurityConfig.java: Spring Security 설정
- CustomAuthenticationFilter.java: JWT/API Key 기반 인증 필터
- SecurityUser.java: UserDetails 구현체
- PasswordEncoderConfig.java: BCrypt 패스워드 인코더

### Rq 클래스 (com.back.global.rq)
- Request Scope 빈으로 현재 요청 컨텍스트 관리

### 전역 설정 (com.back.global.global)
- SwaggerConfig.java: Swagger/OpenAPI 설정
- JacksonConfig.java: ObjectMapper 빈 등록

### Util 클래스 확장
- Util.jwt: JWT 토큰 생성/검증/파싱
- Util.json: JSON 직렬화

### Member 도메인 변경
- BaseMember: apiKey 필드, isAdmin(), getAuthorities() 추가
- SourceMember: UUID 기반 apiKey 자동 생성
- MemberRepository: findByApiKey() 추가
- MemberJoinUseCase: 비밀번호 BCrypt 암호화
- MemberLoginUseCase: 로그인 처리 (신규)
- MemberAuthTokenUseCase: JWT 토큰 생성/검증 (신규)

### API 엔드포인트
- POST /api/v1/member/members/join: 회원가입
- POST /api/v1/member/members/login: 로그인
- DELETE /api/v1/member/members/logout: 로그아웃
- GET /api/v1/member/members/me: 현재 로그인 사용자 정보

### API 권한 설정 (SecurityConfig)
| 엔드포인트 | 권한 |
|-----------|------|
| /api/v1/member/members/login | permitAll |
| /api/v1/member/members/join | permitAll |
| /api/v1/member/members/logout | permitAll |
| /api/v1/member/members/randomSecureTip | permitAll |
| /api/v1/member/members/me | authenticated |
| /api/v1/market/orders/** | authenticated |
| 내부용 API (/{id}, /by-apikey, /validate-token 등) | systemApiKey 검증 |

---

# 0002 - 멀티모듈 프로젝트 구조 설정

## 개요
Gradle 멀티모듈 프로젝트 구조 설정 (post-service 모듈 준비)

## 변경 사항

### settings.gradle.kts
- rootProject.name = "back"
- include("post-service")

### build.gradle.kts
- bootJar { archiveFileName.set("back.jar") } 추가

### post-service 모듈 생성
- post-service/build.gradle.kts 생성
- 메인 클래스: com.back.PostApplication
- 빌드 파일: post-service.jar

---

# 0003 - post-service 모듈 분리

## 개요
Post 도메인을 독립적인 마이크로서비스로 분리

## 분리 순서 결정 기준
모듈 간 의존성 분석 결과, **의존성이 적은 순서**로 분리:

| 순서 | 모듈 | 이유 |
|-----|------|------|
| 1 | post | member에만 의존, 다른 모듈에서 의존 없음 (가장 독립적) |
| 2 | payout | cash/market 이벤트 수신, 다른 모듈에서 의존 없음 |
| 3 | cash | market에서 결제 시 의존 |
| 4 | market | cash에 의존 (결제 처리) |
| - | member | 모든 모듈에서 인증/인가로 의존 → 메인 서비스에 유지 |

## 변경 사항

### PostApplication.java
- com.back.PostApplication 메인 클래스 생성

### 패키지 복사
- boundedContext/post: Post 도메인 전체
- global: 전역 설정 (security, rq, exception 등)
- shared: 공유 DTO/이벤트
- standard: Util 클래스

### CustomAuthenticationFilter 수정
- MemberFacade 의존성 제거
- JWT 페이로드에서 직접 사용자 정보 추출 (독립성 확보)

### application.yml 설정
- 포트: 8081
- 애플리케이션명: post-service

---

# 0004 - payout-service 모듈 분리

## 개요
Payout(정산) 도메인을 독립적인 마이크로서비스로 분리

## 분리 이유
- 다른 모듈에서 payout을 직접 의존하지 않음
- cash/market 이벤트를 수신하여 정산 처리 (이벤트 기반 느슨한 결합)
- 배치 작업(정산 집계/완료) 독립 실행 가능

## 변경 사항

### PayoutApplication.java
- com.back.PayoutApplication 메인 클래스 생성

### 패키지 복사
- boundedContext/payout: Payout 도메인 전체
- global: 전역 설정
- shared: 공유 DTO/이벤트
- standard: Util 클래스

### settings.gradle.kts
- include("payout-service") 추가

### application.yml 설정
- 포트: 8082
- 애플리케이션명: payout-service
- custom.payout.readyWaitingDays: 정산 대기 일수 (기본값: 14일)

### .env.default
- PAYOUT_READY_WAITING_DAYS 추가

---

# 0005 - cash-service 모듈 분리

## 개요
Cash(지갑/결제) 도메인을 독립적인 마이크로서비스로 분리

## 분리 이유
- market에서 결제 요청 시 의존하지만, 이벤트 기반으로 통신 가능
- 지갑 잔액 관리 및 결제 처리를 독립적으로 운영

## 변경 사항

### CashApplication.java
- com.back.CashApplication 메인 클래스 생성

### 패키지 복사
- boundedContext/cash: Cash 도메인 전체
- global: 전역 설정
- shared: 공유 DTO/이벤트
- standard: Util 클래스

### settings.gradle.kts
- include("cash-service") 추가

### application.yml 설정
- 포트: 8083
- 애플리케이션명: cash-service

---

# 0006 - market-service 모듈 분리

## 개요
Market(상품/주문) 도메인을 독립적인 마이크로서비스로 분리

## 분리 이유
- cash에 의존하여 결제 처리하지만, 이벤트 기반으로 통신 가능
- 상품/장바구니/주문 관리를 독립적으로 운영
- 가장 마지막에 분리 (다른 모듈들에 대한 의존성이 가장 높음)

## 변경 사항

### MarketApplication.java
- com.back.MarketApplication 메인 클래스 생성

### 패키지 복사
- boundedContext/market: Market 도메인 전체
- global: 전역 설정
- shared: 공유 DTO/이벤트
- standard: Util 클래스

### settings.gradle.kts
- include("market-service") 추가

### application.yml 설정
- 포트: 8084
- 애플리케이션명: market-service
- custom.market.product.payoutRate: 상품 판매 정산율 (기본값: 90%)

### .env.default
- MARKET_PRODUCT_PAYOUT_RATE 추가

---

# 0007 - member-service 모듈 분리

## 개요
Member(회원) 도메인을 독립적인 마이크로서비스로 분리
기존 src 디렉토리를 member-service로 이름 변경

## 분리 이유
- 모든 boundedContext 도메인을 독립적인 서비스로 운영
- src → member-service 리팩토링으로 일관된 프로젝트 구조

## 변경 사항

### src → member-service 이름 변경
- BackApplication → MemberApplication 이름 변경

### 불필요한 boundedContext 제거
- boundedContext/post, cash, market, payout 제거 (각 서비스에 이미 존재)

### build.gradle.kts 수정
- mainClass: com.back.MemberApplication
- bootJar: member-service.jar

### application.yml 설정
- 포트: 8080
- 애플리케이션명: member-service

### settings.gradle.kts
- include("member-service") 추가

---

# 0008 - common 모듈 추출

## 개요
공통 코드(global, shared, standard)를 common 모듈로 추출하여 중복 제거

## 변경 사항

### common 모듈 생성
- common/build.gradle.kts: java-library 플러그인 사용
- api 의존성으로 전이적 노출 (Spring Boot, Security, JPA, JWT 등)
- bootJar 비활성화 (라이브러리 모듈)

### 패키지 구조
- global: 전역 설정 (security, rq, exception, config)
- shared: 공유 DTO/이벤트
- standard: Util 클래스

### 서비스 모듈 변경
- 모든 서비스에서 global, shared, standard 패키지 제거
- implementation(project(":common")) 의존성 추가
- 중복 의존성 제거로 build.gradle.kts 간소화

### settings.gradle.kts
- include("common") 추가

---

# 0009 - Kafka 기반 서비스 간 이벤트 통신

## 개요
Spring Kafka를 도입하여 마이크로서비스 간 비동기 이벤트 통신 구현

## 도입 이유
- 기존 ApplicationEventPublisher는 동일 JVM 내에서만 동작
- 마이크로서비스 분리 후 서비스 간 이벤트 전파 불가
- Kafka를 통한 분산 이벤트 기반 아키텍처 구현

## 변경 사항

### common 모듈 - Kafka 인프라

#### build.gradle.kts
- spring-kafka 의존성 추가

#### KafkaConfig.java (com.back.global.kafka)
- @EnableKafka 설정
- ProducerFactory: JsonSerializer 사용
- ConsumerFactory: ErrorHandlingDeserializer + JsonDeserializer 사용
- KafkaTemplate, KafkaListenerContainerFactory 빈 등록

#### KafkaTopics.java
- 토픽 상수 정의:
  - member.joined, member.modified
  - post.created, post.comment.created
  - market.order.payment.requested, market.order.payment.completed
  - cash.order.payment.succeeded, cash.order.payment.failed
  - payout.completed

#### KafkaEventPublisher.java
- 이벤트 타입별 토픽 자동 매핑
- KafkaTemplate을 통한 이벤트 발행

#### EventPublisher.java 수정
- ApplicationEventPublisher (로컬) + KafkaEventPublisher (분산) 이중 발행

### 서비스별 KafkaListener

#### member-service
- MemberKafkaListener.java
- 수신: PostCreatedEvent, PostCommentCreatedEvent

#### post-service
- PostKafkaListener.java
- 수신: MemberJoinedEvent, MemberModifiedEvent

#### cash-service
- CashKafkaListener.java
- 수신: MemberJoinedEvent, MemberModifiedEvent, MarketOrderPaymentRequestedEvent, PayoutCompletedEvent

#### market-service
- MarketKafkaListener.java
- 수신: MemberJoinedEvent, MemberModifiedEvent, CashOrderPaymentSucceededEvent, CashOrderPaymentFailedEvent

#### payout-service
- PayoutKafkaListener.java
- 수신: MemberJoinedEvent, MemberModifiedEvent, MarketOrderPaymentCompletedEvent

### application.yml 설정
- 모든 서비스에 spring.kafka.bootstrap-servers 추가
- 환경변수: KAFKA_BOOTSTRAP_SERVERS (기본값: localhost:9092)

### Docker Compose 설정 (docker-compose.yml)

#### Redpanda (Kafka 호환 메시지 브로커)
- 이미지: redpandadata/redpanda:v24.1.1
- 포트: 9092 (외부), 29092 (내부)
- 경량 Kafka 대안으로 개발 환경에 적합

#### Redpanda Console (관리 UI)
- 이미지: redpandadata/console:v2.5.2
- 포트: 8090
- 토픽/메시지 모니터링 UI

#### 실행 방법
```bash
docker-compose up -d
```

## 이벤트 흐름도

```
[member-service] --MemberJoinedEvent--> [post, cash, market, payout]
[post-service] --PostCreatedEvent--> [member]
[market-service] --MarketOrderPaymentRequestedEvent--> [cash]
[market-service] --MarketOrderPaymentCompletedEvent--> [payout]
[cash-service] --CashOrderPaymentSucceededEvent--> [market]
[payout-service] --PayoutCompletedEvent--> [cash]
```

---

# 0010 - DTO/Event 클래스 record 변환

## 개요
Kafka JsonDeserializer 호환성을 위해 DTO와 Event 클래스를 Java record 타입으로 변환

## 변환 이유
- Lombok @AllArgsConstructor 클래스는 Jackson의 기본 생성자 요구사항 충족 불가
- Kafka 메시지 역직렬화 시 `Cannot construct instance of ... (no Creators, like default constructor)` 에러 발생
- Java record는 컴팩트 생성자를 통해 Jackson 역직렬화 자동 지원

## 변경 사항

### DTO 클래스 record 변환 (common 모듈)

| 패키지 | 클래스 | 변환 전 | 변환 후 |
|--------|--------|---------|---------|
| shared.member.dto | MemberDto | class + @Getter + @AllArgsConstructor | record |
| shared.cash.dto | CashMemberDto | class + @Getter + @AllArgsConstructor | record |
| shared.cash.dto | WalletDto | class + @Getter + @AllArgsConstructor | record |
| shared.market.dto | MarketMemberDto | class + @Getter + @AllArgsConstructor | record |
| shared.market.dto | OrderDto | class + @Getter + @AllArgsConstructor + implements HasModelTypeCode | record + implements HasModelTypeCode |
| shared.market.dto | OrderItemDto | class + @Getter + @AllArgsConstructor + implements HasModelTypeCode | record + implements HasModelTypeCode |
| shared.payout.dto | PayoutDto | class + @Getter + @AllArgsConstructor + implements HasModelTypeCode | record + implements HasModelTypeCode |
| shared.payout.dto | PayoutMemberDto | class + @Getter + @AllArgsConstructor | record |
| shared.post.dto | PostDto | class + @Getter + @AllArgsConstructor + implements HasModelTypeCode | record + implements HasModelTypeCode |
| shared.post.dto | PostCommentDto | class + @Getter + @AllArgsConstructor | record |

### Event 클래스 record 변환 (common 모듈)

| 패키지 | 클래스 | 변환 전 | 변환 후 |
|--------|--------|---------|---------|
| shared.member.event | MemberJoinedEvent | class + @Getter + @AllArgsConstructor | record |
| shared.member.event | MemberModifiedEvent | class + @Getter + @AllArgsConstructor | record |
| shared.cash.event | CashMemberCreatedEvent | class + @Getter + @AllArgsConstructor | record |
| shared.cash.event | CashOrderPaymentSucceededEvent | class + @Getter + @AllArgsConstructor | record |
| shared.cash.event | CashOrderPaymentFailedEvent | class + @Getter + @AllArgsConstructor + implements ResultType | record + implements ResultType |
| shared.market.event | MarketMemberCreatedEvent | class + @Getter + @AllArgsConstructor | record |
| shared.market.event | MarketOrderPaymentRequestedEvent | class + @Getter + @AllArgsConstructor | record |
| shared.market.event | MarketOrderPaymentCompletedEvent | class + @Getter + @AllArgsConstructor | record |
| shared.payout.event | PayoutCompletedEvent | class + @Getter + @AllArgsConstructor | record |
| shared.payout.event | PayoutMemberCreatedEvent | class + @Getter + @AllArgsConstructor | record |
| shared.post.event | PostCreatedEvent | class + @Getter + @AllArgsConstructor | record |
| shared.post.event | PostCommentCreatedEvent | class + @Getter + @AllArgsConstructor | record |

### 접근자 메서드 변경

record 타입 변환에 따라 getter 호출 방식 변경:

| 변환 전 | 변환 후 |
|---------|---------|
| `member.getId()` | `member.id()` |
| `member.getUsername()` | `member.username()` |
| `member.getNickname()` | `member.nickname()` |
| `event.getMember()` | `event.member()` |
| `event.getOrder()` | `event.order()` |
| `order.getCustomerId()` | `order.customerId()` |
| `payout.getPayeeId()` | `payout.payeeId()` |

### 변경된 서비스 파일

#### KafkaListener 파일
- member-service: MemberKafkaListener.java
- post-service: PostKafkaListener.java
- cash-service: CashKafkaListener.java
- market-service: MarketKafkaListener.java
- payout-service: PayoutKafkaListener.java

#### EventListener 파일
- market-service: MarketEventListener.java
- payout-service: PayoutEventListener.java

#### UseCase 파일
- post-service: PostSyncMemberUseCase.java
- cash-service: CashSyncMemberUseCase.java, CashCreateWalletUseCase.java, CashCompleteOrderPaymentUseCase.java, CashCompletePayoutUseCase.java
- market-service: MarketSyncMemberUseCase.java, MarketCreateCartUseCase.java
- payout-service: PayoutSyncMemberUseCase.java, PayoutAddPayoutCandidateItemsUseCase.java

#### common 모듈
- CustomAuthenticationFilter.java
