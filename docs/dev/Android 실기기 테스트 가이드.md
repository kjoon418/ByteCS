# Android 실기기 테스트 가이드

에뮬레이터가 아닌 **실제 안드로이드 폰**에 앱을 설치해 로컬 백엔드와 함께 돌려 보는 절차다.

> iOS 실기기는 이 문서의 범위가 아니다. iOS 빌드는 Xcode(macOS 전용)가 필요해 Windows 개발 호스트에서 수행할 수 없다.

## 왜 설정이 필요한가

에뮬레이터는 호스트 PC의 `localhost`를 **`10.0.2.2`** 라는 특수 주소로 볼 수 있게 안드로이드가 마련해 둔 통로가 있고,
`platformApiBaseUrl()`의 기본값이 바로 이 주소다. 하지만 실기기에는 그런 통로가 없다.
폰 입장에서 `10.0.2.2`도 `localhost`도 **폰 자신**을 가리키므로, 아무것도 하지 않으면 앱은 시작하자마자
게스트 토큰 발급(`POST /api/guests`)에 실패한다.

그래서 두 가지가 필요하다.

1. **폰 → PC 통로**: `adb reverse`가 USB 케이블 위로 터널을 놓아, 폰의 `localhost:8080`을 PC의 `localhost:8080`으로 넘긴다.
2. **앱이 그 주소를 보게 하기**: 기본값이 `10.0.2.2`이므로 `localhost`로 덮어써야 한다.

`adb reverse`를 쓰면 네트워크를 타지 않으므로 **방화벽 설정도, 같은 와이파이도, 공유기 IP 확인도 필요 없다.**

## 준비 (최초 1회)

### 1. 폰에서 USB 디버깅 켜기

1. `설정 → 휴대전화 정보 → 소프트웨어 정보`에서 **빌드 번호**를 7번 연타 → 개발자 모드 활성화
2. `설정 → 개발자 옵션 → USB 디버깅` 켜기
3. USB 케이블로 PC에 연결 → 폰에 뜨는 **"USB 디버깅을 허용하시겠습니까?"** 에서 허용

### 2. 앱이 볼 API 주소 지정

저장소 루트의 `local.properties`(git이 무시하는 개인 설정 파일)에 한 줄 추가한다.

```properties
bytecs.apiBaseUrl=http://localhost:8080
```

이 값은 빌드 시점에 `ANDROID_API_BASE_URL` 상수로 구워져 `platformApiBaseUrl()`이 반환한다
(구현: `app/shared/build.gradle.kts`의 `generateApiConfig` 태스크).
**줄을 지우면 기본값 `http://10.0.2.2:8080`으로 돌아가 에뮬레이터가 그대로 동작한다.**
개인 IP나 머신별 설정이 커밋되지 않는 게 이 방식의 요점이다.

## 매번 실행할 때

`adb`는 PATH에 없을 수 있다. 없으면 전체 경로를 쓴다:
`C:\Users\<사용자>\AppData\Local\Android\Sdk\platform-tools\adb.exe`

### 1. 백엔드 띄우기

```powershell
./gradlew :server:bootRun --args='--spring.profiles.active=local'
```

`local` 프로파일은 인메모리 H2를 쓰고 기동 시 CS 문제 **7개**를 시딩한다.
세션 크기 기본값은 10이지만 문제가 부족하면 있는 만큼만 배정하므로(`SessionCreator.assignProblemIds`),
**오늘의 한입이 7문제로 뜨는 것이 정상**이다. 데이터는 인메모리라 서버를 내리면 계정·세션·스트릭이 모두 사라진다.

### 2. 폰 연결 확인

```powershell
adb devices
```

폰의 시리얼이 `device` 상태로 보여야 한다. `unauthorized`면 폰 화면의 USB 디버깅 허용 팝업을 확인한다.
에뮬레이터가 같이 떠 있으면 목록에 둘 다 나오므로, 아래 명령에 `-s <시리얼>`로 대상을 지정한다.

### 3. 터널 놓기

```powershell
adb reverse tcp:8080 tcp:8080
```

**폰을 다시 꽂을 때마다 실행해야 한다** (재부팅·케이블 분리 시 터널이 사라진다).
`adb reverse --list`로 확인할 수 있다.

### 4. 설치 후 실행

```powershell
./gradlew :app:androidApp:installDebug
```

반드시 **debug** 빌드여야 한다. 평문 HTTP 허용은 `src/debug` 소스셋 전용이며
(`app/androidApp/src/debug/res/xml/network_security_config.xml`), 릴리스는 HTTPS를 강제하므로
릴리스 빌드로는 로컬 백엔드에 붙지 못한다.

## 확인해 볼 흐름

1. **첫 실행** — 게스트 토큰이 자동 발급되고 홈('오늘의 한입')이 뜬다
2. **세션 풀이** — 오답을 내도 빨간색·경고가 없는지(무낙인), 오답 1회 뒤에만 '정답 보기'가 열리는지(no-leak)
3. **정답 공개 후** — 공개된 답을 직접 타이핑해야 다음으로 넘어가는지
4. **완료** — 문제 수 count-up·컨페티·햅틱이 한 번만 울리는지, 스트릭이 긍정 톤인지
5. **중단·재개** — 앱을 죽였다 켜도 풀던 위치가 유지되는지
6. **가입 승계** — 게스트로 몇 문제 푼 뒤 가입해도 진행이 유지되는지(동일 userId 승계)
7. **계정 삭제** — 위험(danger) 빨강이 여기서만 쓰이는지

## 문제 해결

| 증상 | 원인 |
|---|---|
| 첫 화면부터 로드 실패 | 서버 미기동, 또는 `adb reverse` 미실행 |
| 터널을 놨는데도 실패 | `local.properties`에 `bytecs.apiBaseUrl` 누락 → 앱이 아직 `10.0.2.2`를 봄. 다시 설치 필요 |
| 설정 바꿨는데 그대로 | 상수는 빌드 시점에 구워진다. `installDebug` 재실행 |
| `adb devices`에 폰 없음 | USB 디버깅 미허용, 또는 케이블이 충전 전용 |
| 릴리스 빌드로 붙지 않음 | 정상. 평문 허용은 debug 전용 |

## LAN(와이파이)으로 붙고 싶다면

케이블 없이 쓰려면 `adb reverse` 대신 PC의 공유기 내부 IP로 직접 붙어야 하고, 추가 작업이 필요하다.

1. `local.properties`를 `bytecs.apiBaseUrl=http://<PC의 LAN IP>:8080`으로
2. `network_security_config.xml`의 평문 허용 목록에 그 IP 추가 (이 파일은 CIDR·IP 대역을 지원하지 않아 IP를 직접 적어야 한다)
3. Windows 방화벽에 8080 인바운드 규칙 추가
4. 폰과 PC가 같은 와이파이에 연결

**대신 같은 네트워크의 다른 사용자에게 개발 서버(H2 콘솔 포함)가 노출된다.** 공용 와이파이에서는 권장하지 않는다.
IP는 공유기가 재배정하면 바뀌므로 1·2번을 다시 해야 한다.
