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
