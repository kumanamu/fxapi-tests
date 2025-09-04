
# fxapi-test (API 테스트 전용)
외부망 없이 MockWebServer로 응답을 모킹해 **Frankfurter(ECB)** / **ExchangeRate.host** 파싱과
회원/비회원 분기 로직을 검증합니다.

## 실행
```
./gradlew test
```
테스트가 모두 통과하면, `application.properties`의 base-url을 실제 API로 바꿔 연결하세요.
