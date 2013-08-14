package com.crearo.mpu.sdk.client;

/**
 * 
 * MPU的设备信息
 */
public class PUInfo {

	/**
	 * 当前的程序硬件版本号，通常填手机的型号
	 */
	public String hardWareVer;

	/**
	 * 当前软件版本号，通常填发布时的版本
	 */
	public String softWareVer;

	/**
	 * MPU的唯一ID,18位的数字，通常取手机串号，前面加“151”
	 */
	public String puid;

	/**
	 * MPU的名称，可在客户端显示
	 */
	public String name;

	/**
	 * 摄像头名称，为null表示不支持该资源
	 */
	public String cameraName = "Android camera";

	/**
	 * mic名称，为null表示不支持该资源
	 */
	public String mMicName = "Android mic";
	/**
	 * 扬声器名称，为null表示不支持该资源
	 */
	public String mSpeakerName = "Android speaker";

	/**
	 * GPS名称，为null表示不支持该资源
	 */
	public String mGPSName = "Android GPS";
}