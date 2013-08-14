package com.crearo.mpu.sdk.client;

import util.E;

public class ErrorCode {
	public static final int LOGIN_UNREACHABLE = 0x9000;// 地址不可到达
	public static final int LOGIN_UNKOWN_ERROR = 0x9001;// 未知错误
	public static final int LOGIN_PASSWORD_ERROR = 0x9002;// 密码错误
	public static final int LOGIN_PUID_NOTEXIST = 0x9003;// PUID 不存在
	public static final int LOGIN_PUID_DISABLE = 0x9004; // 平台返回PUID被禁用
	public static final int LOGIN_POST_INVALID = 0x9005; // post报文不正确
	public static final int LOGIN_PU_IS_FULL = 0x9006;// 设备已满
	public static final int LOGIN_PRODUCER_ID_INVALID = 0x9007; // 平台返回非法的厂商ID
	public static final int LOGIN_ALREADY_ONLINE = 0x9008; // 表示该PU已经上线
	public static final int LOGIN_DVCID_EXIST = 0x9009; // 表示该DevID已存在
	public static final int LOGIN_VERSION_INVALID = 0x9010; // 表示平台返回版本不正确
	public static final int LOGIN_REDIRECT_ERROR = 0x9011;// 重定向失败
	public static final int LOGIN_USER_INACTIVE = 0x9012;// 用户不使能
	public static final int LOGIN_USER_NOT_EXIST = 0x9013;// 用户不存在
	public static final int LOGIN_INVALID_ADDR = 0x9014;// 地址不合法
	public static final int LOGIN_CHANNEL_REBUILD = 0x9015;
	public static final int LOGIN_INVALID_TOKEN = 0x9016;
	public static final int LOGIN_CLOSE_BY_SERVER = 0x9100;
	public static final int NC_OFFSET = LOGIN_UNREACHABLE - E.LOGIN_UNREACHABLE;
	public static final int ERROR_REOURCE_IN_USE = 0x1805;
	public static final int ERROR_UNSUPPORT_OPERATION = 0x1806;

}
