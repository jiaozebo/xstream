package com.crearo.mpu.sdk;

import video.encoder.H264S2F;

/**
 * 手机类型
 * 
 * @author John
 * @version 1.0
 * @date 2012-3-13
 */
public enum PhoneType {
	/*
	 * #define PT_COMMON 0 //目前不支持COMMON类型 #define PT_SAMSUNG_GT_P1000 1
	 * //SAMSUNG，GT-P1000 , 兼容i909 #define PT_MOTO_XT800 2 #define PT_HS_U8 3
	 * #define PT_CP_9900 4 //Cool Pad 9900 #define PT_MOTO_XT882 5 //MOTO XT882
	 * #define PT_NUM 6
	 */
	PHONE_GT_P1000("GT-P1000"), PHONE_GT_P100("GT-P100"), PHONE_GT_I8530("GT-I8530"), PHONE_SCH_I909(
			"SCH-i909"), PHONE_SCH_I889("SCH-i889"), PHONE_HS_U8("HS-U8"), PHONE_Coolpad_9900(
			"9900"), PHONE_MI_ONE("MI-ONE Plus"), PHONE_MEIZU_MX("MEIZU MX"), PHONE_MEIZU_MX_(
			"M030"),
	// PHONE_MOTO_XT882("XT882"), // 这个硬编码效果不好，使用软编码得了
	PHONE_OTHER("PHONE_OTHER");

	String value;

	/**
	 * int type = H264S2F.TYPE_P1000; if (model.equals(Common.PHONE_HS_U8)) {
	 * type = H264S2F.TYPE_HS_U8; } else if
	 * (model.equals(Common.PHONE_Coolpad_9900)) { type =
	 * H264S2F.TYPE_COOLPAD_9900; }
	 */
	/**
	 * @return
	 */
	public int type() {
		switch (this) {
		case PHONE_GT_P1000:
		case PHONE_GT_P100:
		case PHONE_SCH_I909:
		case PHONE_SCH_I889:
			return H264S2F.TYPE_P1000;
		case PHONE_HS_U8:
			return H264S2F.TYPE_HS_U8;
		case PHONE_Coolpad_9900:
			return H264S2F.TYPE_COOLPAD_9900;
		case PHONE_MI_ONE:
			return H264S2F.TYPE_MI_ONE_PLUS;
		case PHONE_MEIZU_MX:
		case PHONE_MEIZU_MX_:
			return H264S2F.TYPE_MEIZU_MX;
			// case PHONE_MOTO_XT882:
			// return H264S2F.TYPE_XT882;
		case PHONE_GT_I8530:
			return H264S2F.PT_SAMSUNG_GT_I8530;
		default:
			return H264S2F.TYPE_COMMON;
		}

	}

	private PhoneType(String value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return value;
	}

	public static PhoneType fromString(String value) {
		for (PhoneType pt : PhoneType.values()) {
			if (pt.value.equals(value)) {
				return pt;
			}
		}
		return PHONE_OTHER;
	}

	public boolean isRecorder() {
		return this != PHONE_OTHER;
	}
}
