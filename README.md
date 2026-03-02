# RBS (Rental Box System)

## 무인 대여함 시스템

NFC를 활용한 무인 대여함 역할을 하는 IoT 시스템으로, Raspberry Pi 위에서 동작합니다.

---

## 1. 프로젝트 개요

| 항목 | 내용 |
|------|------|
| **목적** | NFC 카드 인증 기반의 무인 대여함 잠금/해제 시스템 |
| **실행 환경** | Raspberry Pi (Linux) |
| **언어** | Java 1.7 |
| **빌드 도구** | Maven |
| **하드웨어** | NFC 리더기, 스텝 모터, 적외선 센서 |

---

## 2. 시스템 아키텍처

```
┌──────────────────────────────────────────────────────┐
│                RentalBoxApplication (main)            │
│                                                      │
│   ┌──────────────┐         ┌────────────────────┐    │
│   │  NFCReader    │         │ CloseEventReceiver │    │
│   │  (Thread 1)   │         │ (Thread 2)         │    │
│   │ NFC 카드 UID  │         │ 적외선 센서 감지    │    │
│   │ 수신 대기     │         │ (닫힘 이벤트)       │    │
│   └──────┬───────┘         └────────┬───────────┘    │
│          │                          │                 │
│          ▼                          ▼                 │
│   ┌──────────────────────────────────────────┐       │
│   │         RentalBoxService (인터페이스)      │       │
│   │  processingAuth()  │  processingLock()    │       │
│   └──────────────────────────────────────────┘       │
│          │           RentalBoxServiceImpl             │
│          ▼                                            │
│   ┌──────────────────────────────────────────┐       │
│   │       RentalBoxRepository (인터페이스)     │       │
│   │  compareCardUID() │ operateMotor()        │       │
│   │  registerUsageHistory()                   │       │
│   └──────────────────────────────────────────┘       │
│                  RentalBoxRepositoryImpl              │
│                  ▼            ▼           ▼           │
│            [SRUS 서버]   [스텝 모터]  [SRUS 서버]     │
│            카드UID대조    GPIO 제어    이용내역 등록   │
└──────────────────────────────────────────────────────┘
```

---

## 3. 핵심 동작 흐름

### 3-1. 대여 (열림) — NFC 인증 흐름

```
1. NFCReader가 nfc-poll 명령어로 NFC 카드 UID를 수신
2. RentalBoxService.processingAuth(uid) 호출
3. RentalBoxRepository.compareCardUID(uid)로 SRUS 서버에 카드 UID 대조 요청 (HTTP GET)
4. 대조 결과가 true이면 → operateMotor(true)로 스텝 모터를 열림 방향으로 회전
5. 모터 동작 성공 시 → registerUsageHistory('O')로 이용내역(열림) 등록 (HTTP POST)
```

### 3-2. 반납 (닫힘) — 적외선 센서 감지 흐름

```
1. CloseEventReceiver의 적외선 센서(GPIO 7번)가 상태 변화 감지 (HIGH)
2. GpioPinListener.handleGpioPinDigitalStateChangeEvent() 이벤트 핸들러 실행
3. RentalBoxService.processingLock() 호출
4. RentalBoxRepository.operateMotor(false)로 스텝 모터를 잠금 방향으로 회전
5. 모터 동작 성공 시 → registerUsageHistory('C')로 이용내역(닫힘) 등록 (HTTP POST)
```

---

## 4. 클래스별 역할 정리

### `RentalBoxApplication` — 실행 진입점
- `main()` 메서드에서 **두 개의 스레드**를 생성하여 동시 실행
  - **Thread 1**: `NFCReader` — NFC 카드 UID 수신 대기
  - **Thread 2**: `CloseEventReceiver` — 적외선 센서 닫힘 이벤트 수신

### `NFCReader` — NFC 카드 UID 수신
- `Runnable` 구현체로, 무한 루프에서 `nfc-poll` 외부 프로세스를 실행
- `Runtime.getRuntime().exec("nfc-poll")`로 NFC 리더기에서 카드 UID 읽기
- 출력에서 `UID`로 시작하는 라인을 파싱하여 카드 고유 ID 추출
- 수신된 UID를 `RentalBoxService.processingAuth()`에 전달

### `CloseEventReceiver` — 닫힘 이벤트 수신 (적외선 센서)
- `Runnable` 구현체로, GPIO 7번 핀에 적외선 센서 연결
- **이벤트 리스너 패턴** 사용: `GpioPinListenerDigital` 내부 클래스로 센서 상태 변화 감지
- 센서 상태가 HIGH일 때 `RentalBoxService.processingLock()` 호출

### `RentalBoxService` / `RentalBoxServiceImpl` — 비즈니스 로직
- **인터페이스-구현 분리** 패턴 적용
- `processingAuth(authKey)`: 카드 UID 대조 → 모터 열림 → 이용내역(O) 등록
- `processingLock()`: 모터 잠금 → 이용내역(C) 등록

### `RentalBoxRepository` / `RentalBoxRepositoryImpl` — 데이터/하드웨어 접근 계층
- **인터페이스-구현 분리** 패턴 적용
- `operateMotor(isOpen)`: Pi4J의 `GpioStepperMotorComponent`로 스텝 모터 제어
  - **더블 스텝 시퀀스** (0b0011 → 0b0110 → 0b1100 → 0b1001)로 모터 회전
  - 열림/닫힘 상태를 추적하여 중복 동작 방지
- `compareCardUID(authKey)`: OkHttp로 SRUS 서버에 GET 요청, Gson으로 JSON 응답 파싱
- `registerUsageHistory(division)`: OkHttp로 SRUS 서버에 POST 요청 (대여함 번호, 구분, 일시)

### `RentalBoxConfiguration` — 설정 관리
- `config.properties` 파일에서 설정값 로드 (대여함 번호, API URL, 모터 정보)
- 모터 상태 변경 시 설정 파일에 기록하여 **상태 영속화**

### `Motor` — 모터 상태 모델
- `Serializable` 구현, 모터의 열림 상태(`isOpened`), 스텝 수(`step`), 속도(`stepInterval`) 관리

### `CompareResult` — 카드 UID 대조 응답 모델
- `Serializable` 구현, SRUS 서버에서 받은 JSON 응답을 매핑 (`result: boolean`)

---

## 5. 사용 기술 스택

| 기술 | 용도 | 설명 |
|------|------|------|
| **Pi4J** | GPIO 제어 | Raspberry Pi의 GPIO 핀을 통해 스텝 모터, 적외선 센서 제어 |
| **OkHttp** | HTTP 통신 | SRUS 서버와의 REST API 통신 (카드 UID 대조, 이용내역 등록) |
| **Gson** | JSON 파싱 | 서버 응답 JSON을 `CompareResult` 객체로 변환 |
| **Java Properties** | 설정 관리 | `config.properties` 파일로 대여함 번호, URL, 모터 설정 관리 |
| **Java Thread** | 멀티스레딩 | NFC 수신과 센서 감지를 동시에 처리 |

---

## 6. 면접에서 설명할 수 있는 포인트

### 🔹 설계 패턴
- **Service-Repository 패턴**: 비즈니스 로직(`Service`)과 데이터/하드웨어 접근(`Repository`)을 분리하여 각 계층의 책임을 명확히 함
- **인터페이스-구현 분리**: `RentalBoxService`/`RentalBoxRepository`를 인터페이스로 정의하여 구현 교체 및 테스트 용이성 확보
- **이벤트 리스너 패턴**: 적외선 센서의 상태 변화를 `GpioPinListenerDigital` 리스너로 비동기 감지

### 🔹 멀티스레딩
- 두 개의 독립적인 스레드로 NFC 수신과 적외선 센서 감지를 **동시에** 처리
- 각 스레드가 `Runnable` 인터페이스를 구현하여 독립적으로 동작

### 🔹 하드웨어 제어 (스텝 모터)
- **더블 스텝 시퀀스**(4단계 바이트 배열)로 스텝 모터를 제어하여 잠금/해제 동작 구현
- 모터의 현재 상태(`isOpened`)를 추적하여 동일 방향 중복 동작을 방지하는 로직 적용
- 잠금 시 2초 지연(`Thread.sleep(2000)`)을 두어 물리적 동작 완료 대기

### 🔹 외부 프로세스 연동
- `Runtime.getRuntime().exec("nfc-poll")`로 NFC 리더기 프로세스를 실행하고, `BufferedReader`로 출력을 파싱하여 카드 UID를 추출

### 🔹 서버 통신
- **OkHttp**를 사용한 REST API 통신 (GET: 카드 UID 대조 / POST: 이용내역 등록)
- **Gson**을 사용한 JSON 역직렬화로 서버 응답 처리
- 리소스 누수 방지를 위해 `response.close()`를 `finally` 블록에서 호출

### 🔹 설정 외부화 및 상태 영속화
- `config.properties` 파일로 대여함 번호, API URL, 모터 설정 등을 외부화하여 코드 수정 없이 환경 변경 가능
- 모터 상태 변경 시 `Properties.store()`로 파일에 기록하여 프로그램 재시작 후에도 상태 유지
