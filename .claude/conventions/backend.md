# 백엔드 코드 컨벤션

`.coderabbit.yaml` 리뷰 지침과 `common/` 패키지의 기존 코드를 기준으로 정리했습니다. 둘이 어긋나면 기존 코드를 우선 따르고 사용자에게 알립니다.

## 패키지 구조

```
com.byarrival
├── common/                 # 도메인에 종속되지 않는 공통 모듈만
│   ├── exception/          # ErrorCode, CommonErrorCode, BusinessException, GlobalExceptionHandler
│   └── response/           # ApiResponse
└── domain/<도메인>/
    ├── controller/
    ├── service/
    ├── repository/
    ├── entity/
    ├── dto/                # request/, response/ 로 나눠도 됨
    └── exception/          # <도메인>ErrorCode
```

- 도메인 이름은 명세 그룹을 따릅니다. 예: `user`, `onboarding`, `route`, `article`, `quiz`, `scrap`, `stat`, `feed`, `smalltalk`, `term`
- 특정 도메인에서만 쓰는 코드는 `common/`에 넣지 않습니다.

## 계층별 책임

### Controller
- 요청 검증(`@Valid`)과 응답 변환만 합니다. 비즈니스 로직은 Service에 둡니다.
- 응답은 항상 `ApiResponse.ok(data)` 또는 `ApiResponse.ok()`로 감쌉니다.
- Entity를 요청이나 응답에 직접 쓰지 않고 DTO를 씁니다.
- URI는 복수형 명사, 동작은 HTTP Method로 표현합니다. 예: `POST /api/routes`, `GET /api/routes/{routeId}/articles/{articleId}`

### Service
- 변경 메서드는 `@Transactional`, 조회 메서드는 `@Transactional(readOnly = true)`를 붙입니다.
- 트랜잭션 안에서 외부 API(AI, RSS, 소셜 로그인) 호출을 하지 않습니다. 호출은 트랜잭션 밖에서 하고 결과 저장만 트랜잭션으로 묶습니다.
- 다른 도메인의 Repository를 직접 부르지 않고 해당 도메인의 Service를 거칩니다.

### Repository
- 반복문 안에서 쿼리를 호출하지 않습니다.
- N+1이 생길 수 있는 조회는 fetch join이나 `@EntityGraph`를 씁니다.

### Entity
- `@Setter`를 쓰지 않고, 의도가 드러나는 메서드로 상태를 바꿉니다. 예: `route.complete()`, `article.markRecommendable()`
- 기본 생성자는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`로 제한합니다.
- 연관관계는 `fetch = FetchType.LAZY`를 씁니다.
- 상태 값은 `@Enumerated(EnumType.STRING)` enum으로 둡니다. 예: 노선 `CREATED / IN_PROGRESS / COMPLETED / EXPIRED`

### DTO
- 요청은 `*Request`, 응답은 `*Response`로 이름 짓고 `record`로 만듭니다.
- 정적 팩토리 메서드는 인자가 1개면 `from`, 2개 이상이면 `of`를 씁니다.

## 예외 처리

- `RuntimeException`이나 `IllegalArgumentException`을 직접 던지지 않고 `BusinessException`을 씁니다.
- 도메인 에러는 도메인별 `<도메인>ErrorCode` enum이 `ErrorCode`를 구현합니다. 응답 `code`에는 enum 상수 이름이 그대로 쓰입니다.

```java
@Getter
@RequiredArgsConstructor
public enum RouteErrorCode implements ErrorCode {

    ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "노선을 찾을 수 없습니다."),
    ROUTE_EXPIRED(HttpStatus.BAD_REQUEST, "만료된 노선입니다.");

    private final HttpStatus status;
    private final String message;
}
```

- 범용 에러(잘못된 입력, 인증, 권한, 리소스 없음)는 `CommonErrorCode`를 재사용합니다.
- 전역 처리는 `GlobalExceptionHandler`가 맡습니다. 컨트롤러에서 try-catch로 응답을 직접 만들지 않습니다.

## 로그

- `@Slf4j`를 씁니다. `System.out.println`과 `printStackTrace`는 남기지 않습니다.
- 4xx는 `warn`, 5xx는 `error`로 남깁니다. `GlobalExceptionHandler`의 `[예외이름] METHOD URI - 메시지` 형식을 따릅니다.
- 토큰, 소셜 인증 값, 개인정보는 로그에 남기지 않습니다.

## 시간과 날짜

- 서비스일은 **KST 자정 기준**입니다. 서버 기본 시간대에 의존하지 말고 `ZoneId.of("Asia/Seoul")`을 명시하거나 `Clock`을 주입받습니다.
- 노선 만료(생성일 다음 날 04:00), 수집 배치(23:45~23:59)처럼 시각이 걸린 로직은 `Clock`을 주입받아 테스트에서 시각을 고정할 수 있게 만듭니다.
- 비율, 허용 범위, 읽기 속도처럼 명세에서 "설정값으로 관리"라고 한 값은 하드코딩하지 않고 `@ConfigurationProperties`로 뺍니다.

## 포맷

- 들여쓰기 4칸, 연속 줄은 4칸 추가로 들여씁니다(기존 코드와 같게).
- import는 와일드카드 없이 정렬합니다.
- 클래스 Javadoc은 역할을 한 줄로 적습니다. 메서드 주석은 이유가 분명하지 않을 때만 답니다.

## 테스트

- 테스트 메서드 이름은 한글과 밑줄로 씁니다. 예: `노선은_서비스일당_한_번만_생성된다()`
- 성공 케이스와 함께 명세의 **예외 처리** 칸에 적힌 실패 케이스를 테스트합니다.
- 컨트롤러는 `MockMvc`로 응답의 `success`, `code`, `data`를 검증합니다(`GlobalExceptionHandlerTest` 참고).
- 랜덤이 들어가는 로직(노선 기사 추출)은 `Random`을 주입받아 테스트에서 고정합니다.
