package kr.co.rbs.rentalbox;

public class RentalBoxServiceImpl implements RentalBoxService {
	private RentalBoxRepository rentalBoxRepository;

	public RentalBoxServiceImpl() {
		this.rentalBoxRepository = new RentalBoxRepositoryImpl();
	}

	// 잠금 처리
	@Override
	public void processingLock() {
		boolean result = false;

		try {
			// 모터를 잠금 방향으로 동작시킨다.
			result = rentalBoxRepository.operateMotor(false);

			if (result) {
				// 이용내역(구분 = 'C'[닫힘])을 등록한다.
				rentalBoxRepository.registerUsageHistory('C');
			}
		} catch (InterruptedException e) {
			System.out.println("잠금 처리 중 모터 동작 인터럽트 오류 발생");
			e.printStackTrace();
		}
	}

	// 인증 처리
	@Override
	public void processingAuth(String authKey) {
		// SRUS 서버에게 카드 UID 대조 요청을 보낸다.
		boolean isMatched = rentalBoxRepository.compareCardUID(authKey);

		// 인증키 대조 결과가 참이면
		if (isMatched) {
			try {
				// 모터를 잠금해제 방향으로 동작시킨다.
				boolean result = rentalBoxRepository.operateMotor(true);

				if (result) {
					// 이용내역(구분 = 'O'[열림])을 등록한다.
					rentalBoxRepository.registerUsageHistory('O');
				}
			} catch (InterruptedException e) {
				System.out.println("인증 처리 중 모터 동작 인터럽트 오류 발생");
				e.printStackTrace();
			}
		}
	}
}
