package kr.co.rbs.rentalbox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

// NFC 수신 클래스
public class NFCReader implements Runnable {
	@Override
	public void run() {
		RentalBoxService rentalBoxService = new RentalBoxServiceImpl();

		while (true) {
			StringBuilder uid = null;
			// 만약 카드 UID를 수신하면
			if ((uid = readUID()) != null) {
				// 수신한 카드 UID를 이용해 인증 처리를 한다.
				rentalBoxService.processingAuth(uid.toString());
			}
		}
	}

	// 카드 UID를 수신한다.
	private StringBuilder readUID() {
		StringBuilder uid = null;

		String message[] = null;

		Process process = null;

		BufferedReader in = null;

		try {
			process = Runtime.getRuntime().exec("nfc-poll");

			in = new BufferedReader(new InputStreamReader(process.getInputStream()));

			String line = null;
			while ((line = in.readLine()) != null) {
				line = line.trim();
				if (line.startsWith("UID")) {
					uid = new StringBuilder("");
					message = line.split("\\s");

					for (int i = 2; i < message.length; i++) {
						uid.append(message[i]);
					}
				}
			}

			process.waitFor();
			process.destroy();
			System.out.println("수신한 카드 UID : " + uid);
		} catch (Exception e) {
			System.out.println("카드 UID 수신 오류");
			e.printStackTrace();
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return uid;
	}
}
