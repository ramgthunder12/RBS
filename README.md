# RBS

## 무인 대여함 시스템
* NFC를 활용한 무인 대여함 역할을 하는 시스템

## 인증 로직 흐름

### 전체 구조

본 시스템은 Raspberry Pi 위에서 동작하며, NFC 카드 인증을 통해 대여함의 잠금을 해제하고, 적외선 센서를 통해 닫힘을 감지하여 자동 잠금을 수행합니다.

```
[NFC 카드 태그] → NFCReader → RentalBoxService.processingAuth()
                                  ├─ RentalBoxRepository.compareCardUID()  (서버 인증)
                                  ├─ RentalBoxRepository.operateMotor()    (잠금 해제)
                                  └─ RentalBoxRepository.registerUsageHistory() (이용내역 등록)

[적외선 센서 감지] → CloseEventReceiver → RentalBoxService.processingLock()
                                            ├─ RentalBoxRepository.operateMotor()    (잠금)
                                            └─ RentalBoxRepository.registerUsageHistory() (이용내역 등록)
```

### 인증 처리 상세 흐름

1. **NFC 카드 UID 수신** (`NFCReader`)
   - `nfc-poll` 명령어를 실행하여 NFC 카드의 UID를 읽어옵니다.
   - 카드 UID가 수신되면 `RentalBoxService.processingAuth(uid)`를 호출합니다.

2. **카드 UID 서버 대조** (`RentalBoxRepositoryImpl.compareCardUID()`)
   - OkHttp 클라이언트를 사용하여 SRUS 서버에 GET 요청을 보냅니다.
   - 요청 URL: `{compareCardUidURL}?no={대여함번호}&authKey={카드UID}`
   - 서버 응답(JSON)을 Gson으로 파싱하여 `CompareResult` 객체의 `result` 값(boolean)을 반환합니다.

3. **인증 성공 시 잠금 해제** (`RentalBoxServiceImpl.processingAuth()`)
   - 서버 대조 결과가 `true`이면 `operateMotor(true)`를 호출하여 스텝 모터를 잠금 해제 방향으로 동작시킵니다.
   - 모터 동작이 성공하면 `registerUsageHistory('O')`를 호출하여 열림(Open) 이용내역을 서버에 등록합니다.

4. **닫힘 감지 및 자동 잠금** (`CloseEventReceiver`)
   - GPIO 7번 핀에 연결된 적외선 센서의 상태 변화를 감지합니다.
   - 센서가 HIGH 상태가 되면 `RentalBoxService.processingLock()`을 호출합니다.
   - `operateMotor(false)`로 스텝 모터를 잠금 방향으로 동작시키고, 성공 시 `registerUsageHistory('C')`로 닫힘(Close) 이용내역을 등록합니다.

### 클래스별 역할

| 클래스 | 역할 |
|---|---|
| `RentalBoxApplication` | 메인 실행 클래스. NFCReader와 CloseEventReceiver 스레드를 시작합니다. |
| `NFCReader` | NFC 카드 UID를 수신하여 인증 처리를 시작합니다. |
| `CloseEventReceiver` | 적외선 센서로 닫힘 이벤트를 감지하여 잠금 처리를 수행합니다. |
| `RentalBoxService` | 인증 처리(`processingAuth`)와 잠금 처리(`processingLock`) 인터페이스를 정의합니다. |
| `RentalBoxServiceImpl` | 인증 처리와 잠금 처리의 비즈니스 로직을 구현합니다. |
| `RentalBoxRepository` | 모터 작동, 이용내역 등록, 카드 UID 대조 인터페이스를 정의합니다. |
| `RentalBoxRepositoryImpl` | 서버 통신(OkHttp), 모터 제어(GPIO), 설정 관리를 구현합니다. |
| `RentalBoxConfiguration` | `config.properties` 파일에서 대여함 번호, 서버 URL, 모터 설정을 로드합니다. |
| `CompareResult` | 서버의 카드 UID 대조 응답을 담는 데이터 클래스입니다. |
| `Motor` | 스텝 모터의 상태(열림/닫힘), 스텝 수, 속도 정보를 담는 데이터 클래스입니다. |

### 설정 파일 (`config.properties`)

| 속성 | 설명 |
|---|---|
| `rentalBoxNo` | 무인 대여함 번호 |
| `compareCardUidURL` | 카드 UID 대조 서버 URL |
| `registerUsageHistoryURL` | 이용내역 등록 서버 URL |
| `isOpened` | 현재 모터(대여함) 열림/닫힘 상태 |
| `step` | 모터 스텝 이동 수 |
| `stepInterval` | 모터 이동 속도 |
