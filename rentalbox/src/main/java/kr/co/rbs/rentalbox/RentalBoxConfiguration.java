package kr.co.rbs.rentalbox;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

// 무인 대여함 관련 설정 클래스
public class RentalBoxConfiguration {
	private final static String CONFIG_FILE_NAME = "config.properties";

	private Properties config;
	// 무인 대여함 번호
	private int rentalBoxNo;
	// 이용내역 등록 URL
	private String registerUsageHistoryURL;
	// 인증키 대조 URL
	private String compareCardUidURL;
	//모터 객체
	private Motor motor;
	
	// config.propertiesF 에서 설정 정보를 가져온다.
	public void load() throws IOException {
		FileInputStream in = null;

		try {
			in = new FileInputStream(new File("/home/pi/java/resources/").getAbsoluteFile() + File.separator + CONFIG_FILE_NAME);
			Properties config = new Properties();
			config.load(in);

			this.rentalBoxNo = Integer.parseInt(config.getProperty("rentalBoxNo"));

			this.registerUsageHistoryURL = config.getProperty("registerUsageHistoryURL");

			this.compareCardUidURL = config.getProperty("compareCardUidURL");
			
			motor = new Motor();
			
			this.motor.setOpened(Boolean.parseBoolean(config.getProperty("isOpened")));
			
			this.motor.setStep(Long.parseLong(config.getProperty("step")));
			
			this.motor.setStepInterval(Long.parseLong(config.getProperty("stepInterval")));
			
			this.config = config;
		} catch (IOException e) {
			throw e;
		} finally {
			if (in != null) {
				in.close();
			}
		}
	}
	
	public void setStatus(boolean isOpened) {
		FileOutputStream out = null;
		try {
			config.setProperty("isOpened", String.valueOf(isOpened));
			out = new FileOutputStream(new File("/home/pi/java/resources/").getAbsoluteFile() + File.separator + CONFIG_FILE_NAME);
			config.store(out, "");
		} catch (IOException e) {
			System.out.println("설정 파일 저장 오류");
			e.printStackTrace();
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public Properties getConfig() {
		return config;
	}

	public void setConfig(Properties config) {
		this.config = config;
	}

	public int getRentalBoxNo() {
		return rentalBoxNo;
	}

	public void setRentalBoxNo(int rentalBoxNo) {
		this.rentalBoxNo = rentalBoxNo;
	}

	public String getRegisterUsageHistoryURL() {
		return registerUsageHistoryURL;
	}

	public void setRegisterUsageHistoryURL(String registerUsageHistoryURL) {
		this.registerUsageHistoryURL = registerUsageHistoryURL;
	}

	public String getCompareCardUidURL() {
		return compareCardUidURL;
	}

	public void setCompareCardUidURL(String compareCardUidURL) {
		this.compareCardUidURL = compareCardUidURL;
	}

	public Motor getMotor() {
		return motor;
	}

	public void setMotor(Motor motor) {
		this.motor = motor;
	}

	public static String getConfigFileName() {
		return CONFIG_FILE_NAME;
	}

}
