package kr.co.rbs.rentalbox;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.google.gson.Gson;
import com.pi4j.component.motor.impl.GpioStepperMotorComponent;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

// 무인 대여함 리포지토리
public class RentalBoxRepositoryImpl implements RentalBoxRepository {
	private static int RENTALBOX_NO;

	private static String URL_REGISTER_USAGE_HISTORY;

	private static String URL_COMPARE_CARD_UID;

	private static Motor motorInfo;

	public static RentalBoxConfiguration config;
	// GPIO 컨트롤러 생성
	private static GpioController gpio = GpioFactory.getInstance();

	// GPIO 0, 1, 2, 3번 -- 스텝 모터
	private static GpioPinDigitalOutput[] pins = { gpio.provisionDigitalOutputPin(RaspiPin.GPIO_00, PinState.LOW),
			gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01, PinState.LOW),
			gpio.provisionDigitalOutputPin(RaspiPin.GPIO_02, PinState.LOW),
			gpio.provisionDigitalOutputPin(RaspiPin.GPIO_03, PinState.LOW) };

	static {
		config = new RentalBoxConfiguration();

		try {
			// 설정 파일을 불러온다.
			config.load();
			// 무인 대여함 번호를 가져온다.
			RENTALBOX_NO = config.getRentalBoxNo();
			// 카드 UID 대조 URL을 가져온다.
			URL_COMPARE_CARD_UID = config.getCompareCardUidURL();
			// 이용내역 등록 URL을 가져온다.
			URL_REGISTER_USAGE_HISTORY = config.getRegisterUsageHistoryURL();
			// 모터의 정보를 가져온다.
			motorInfo = config.getMotor();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// 모터 작동
	@Override
	public boolean operateMotor(boolean isOpen) throws InterruptedException {
		boolean result = false;
		//gpio.setShutdownOptions(true, PinState.LOW, pins);

		GpioStepperMotorComponent motor = new GpioStepperMotorComponent(pins);

		byte[] double_step_sequence = new byte[4];
		double_step_sequence[0] = (byte) 0b0011;
		double_step_sequence[1] = (byte) 0b0110;
		double_step_sequence[2] = (byte) 0b1100;
		double_step_sequence[3] = (byte) 0b1001;

		// 모터 속도 조절
		motor.setStepInterval(motorInfo.getStepInterval());
		// 더블 스텝 시퀀스
		motor.setStepSequence(double_step_sequence);
		
		if (motorInfo.isOpened() && !isOpen) {
			// 닫힘
			Thread.sleep(2000);
			motor.step(-motorInfo.getStep());
			motorInfo.setOpened(false);
			config.setStatus(false);
			result = true;
		} else if (!motorInfo.isOpened() && isOpen) {
			// 열림
			motor.step(motorInfo.getStep());
			motorInfo.setOpened(true);
			config.setStatus(true);
			result = true;
		}

		motor.stop();

		return result;
	}

	// 이용내역 등록
	@Override
	public void registerUsageHistory(char division) {
		OkHttpClient client = new OkHttpClient();

		FormBody body = new FormBody.Builder().add("rentalBoxNo", String.valueOf(RENTALBOX_NO))
				.add("division", String.valueOf(division))
				.add("usageDate",
						LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd kk:mm:ss")).toString())
				.build();

		Request request = new Request.Builder().post(body).url(URL_REGISTER_USAGE_HISTORY).build();

		Response response = null;
		try {
			System.out.println("이용내역 등록 요청");
			System.out.println(": " + request.url());

			response = client.newCall(request).execute();
		} catch (IOException e) {
			System.out.println("이용내역 등록 요청 오류");
			e.printStackTrace();
		} finally {
			if (response != null) {
				response.close();
			}
		}
	}

	// 카드 UID (인증키 대조)
	@Override
	public boolean compareCardUID(String authKey) {
		OkHttpClient client = new OkHttpClient();

		Request request = new Request.Builder().get()
				.url(URL_COMPARE_CARD_UID + "?no=" + RENTALBOX_NO + "&authKey=" + authKey).build();

		Response response = null;
		String responseBody = null;
		try {
			System.out.println("카드 UID 인증키 대조 요청");
			System.out.println(": " + request.url());

			response = client.newCall(request).execute();

			responseBody = response.body().string();
		} catch (IOException e) {
			System.out.println("카드 UID 인증키 대조 요청 오류");
			e.printStackTrace();
		} finally {
			if (response != null) {
				response.close();
			}
		}

		if (responseBody == null) {
			System.out.println("카드 UID 인증키 대조 응답 없음");
			return false;
		}

		Gson gson = new Gson();
		CompareResult result = gson.fromJson(responseBody, CompareResult.class);

		return result.getResult();
	}
}
