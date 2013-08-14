package com.crearo.mpu.sdk;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;

import junit.framework.Assert;
import c7.DC7;
import c7.DCAssist;
import c7.Frame;
import android.content.Context;

public class GPSHandler implements BDLocationListener {
	static final String tag = "GPSHandler";
	private DC7 dc;
	private int count = 1;
	private Context mContext;
	private LocationClient locationClient;

	/**
	 * 主线程中调用
	 * 
	 * @param context
	 *            全进程有效的context,推荐用getApplicationConext获取全进程有效的context
	 * @param dc
	 */
	public GPSHandler(Context context, DC7 dc) {
		this.mContext = context;
		this.dc = dc;
		locationClient = new LocationClient(mContext);
		locationClient.registerLocationListener(this);

		LocationClientOption option = new LocationClientOption();
		option.setOpenGps(true); // 打开gps
		option.setScanSpan(5 * 1000); // 设置定位模式，小于1秒则一次定位;大于等于1秒则定时定位

		/*
		 * 我们支持返回若干种坐标系，包括国测局坐标系、百度坐标系，需要更多坐标系请联系我们，需要深度合作。目前这些参数的代码为。因此需要在请求时指定类型
		 * ，如果不指定，默认返回百度坐标系。注意当仅输入IP时，不会返回坐标。目前这些参数的代码为
		 * 
		 * 返回国测局经纬度坐标系 coor=gcj02 返回百度墨卡托坐标系 coor=bd09 返回百度经纬度坐标系 coor=bd09ll
		 * 
		 * 百度手机地图对外接口中的坐标系默认是bd09ll，如果配合百度地图产品的话，需要注意坐标系对应问题。
		 */
		option.setCoorType("gcj02");
		locationClient.setLocOption(option);
		locationClient.start();
		BDLocation bdLocation = locationClient.getLastKnownLocation();

		handleLocation(bdLocation);
	}

	/**
	 * 注意：要在主线程中调用
	 * 
	 * @param loc
	 * @param dc
	 * @return
	 */

	private void handleLocation(BDLocation bdLocation) {
		Assert.assertNotNull(dc);
		byte[] frame = new byte[Common.GPS_RAW_LENGTH];
		if (Common.parseGPSDataFromLocation(frame, bdLocation)) {
			Frame aFrame = new Frame(Frame.FRAME_TYPE_GPS, frame, 0, Common.GPS_RAW_LENGTH,
					(byte) 1);
			aFrame.mFrameIdx = count++;
			DCAssist.pumpFrame2DC(aFrame, dc, -1);
		}
	}

	public DC7 getDC() {
		return dc;
	}

	@Override
	public void onReceiveLocation(BDLocation bdLocation) {
		handleLocation(bdLocation);
	}

	public void close() {
		if (locationClient != null) {
			locationClient.stop();
			locationClient.unRegisterLocationListener(this);
			locationClient = null;
		}
	}

	@Override
	public void onReceivePoi(BDLocation arg0) {

	}
}
