package com.crearo.mpu.sdk;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import libs.LngLatOffset;
import util.CommonMethod;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.ACommonMethod;
import c7.PUParam;

import com.baidu.location.BDLocation;

/**
 * @author John
 * @version 1.0
 * @date 2011-6-14
 */
public class Common {
	// public final static int DEVICE_TYPE_ = H264S2F.TYPE_P1000;
	public static final String PUBLIC_PREFERENCE = "mpu_informations";
	public static final String PU_NAME = "pu_name";
	public static final String PU_DESC = "pu_desc";
	public static final String PUID = "key_puid";
	public static final String CAMERA_NAME = "camera_name";
	public static final String CAMERA_DESC = "camera_desc";
	public static final String ADDRESS = "address";
	public static final String PORT = "port";
	public static final String PASSWORD = "password";
	public static final String IV_MODE_CLEAR = "iv mode clear";
	public static final String FIX_ADD = "fix_address";
	public static final String VIDEO_SIZE_INDEX = "video_size_index";
	public static final String VIDEO_QUANT_INDEX = "video_quant_index";
	public static final int MPEG4 = 1;
	public static final int H264 = 0;
	public static final String KEY_RECORD_SUPPORT = "key_record_support";
	public static final int GPS_RAW_LENGTH = 40;
	/**
	 * 关键帧间隔
	 */
	// public static final String PHONE_GT_P1000 = "GT-P1000";
	// public static final String PHONE_GT_P100 = "GT-P100";
	// public static final String PHONE_SCH_I909 = "SCH-i909";
	// public static final String PHONE_SCH_I889 = "SCH-i889";
	// public static final String PHONE_HS_U8 = "HS-U8";
	// public static final String PHONE_Coolpad_9900 = "9900";
	public static final String SWITCH2WORK = "switch2work";
	public static final String KEY_GLOBAL_INITED = "global_inited";

	public static final String PATH_LOG = Environment.getExternalStorageDirectory().getPath()
			+ "/aMPU/Log";
	public static final String PATH_STORAGE = Environment.getExternalStorageDirectory().getPath()
			+ "/aMPU/Storage";
	public static final String PATH_STORAGE_SNAPSHOT = PATH_STORAGE + "/Snapshot";
	public static final String PATH_STORAGE_RECORD = PATH_STORAGE + "/Record";
	public static final String PATH_AUTHORIZATION_OLD = Environment.getExternalStorageDirectory()
			.getPath() + "/aMPU/accredit";

	public static final int DEFAULT_RECORD_MINUTES = 15;
	public static final String MULTIP = "×";
	public static final String KEY_QUALITY = "key_quality";
	public static final String KEY_RESOLUTION = "key_resolution";
	public static final String KEY_PUNAME = "key_puName";
	public static final String KEY_PUDESC = "key_desc";
	public static final String KEY_PUID = "key_puid";
	public static final String KEY_CAMNAME = "key_cam_name";
	public static final String KEY_CAMDESC = "key_cam_desc";

	public static final int MAX_FPS_SCALE = 10;
	public static final int MAX_KBPS_SCALE = 10;
	public static final int BIT_RATE_BASE = 200;
	public static final String KEY_FRAME_RATE = "key_frameRate";
	public static final String KEY_VIDEO_PORTRAIT = "key_video_portrait";

	// private static List<Size> previewSizes;
	// private static List<Integer> frameRates;
	private static String sCameraName = "android camera";
	private static String sCameraDesc = "android camera";
	private static List<Size> sPreviewSizes;

	public static final String getDeviceId(Context context) {
		TelephonyManager tm = (TelephonyManager) context
				.getSystemService(Context.TELEPHONY_SERVICE);
		String dvcId = tm.getDeviceId();
		if (dvcId == null) {
			// 若获取不到设备ID，则将设备ID设置为 "000" + 设备MAC地址
			String mac = "000000000000";
			WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
			WifiInfo info = wifi.getConnectionInfo();
			String addr = info.getMacAddress();
			String[] macs = addr.split(":");
			if (macs.length != 0) {
				String newmac = "";
				for (int i = 0; i < macs.length; i++) {
					newmac += macs[i];
				}
				mac = newmac;
			}
			dvcId = "000" + mac.toUpperCase();
		}
		return dvcId;
	}

	public static final String getPeerUnitName() {
		return android.os.Build.MODEL;
	}

	public static final String getPuid(Context context) {
		char[] arrTemp = getDeviceId(context).toCharArray();
		char[] arr = new char[15];
		for (int i = 0; i < arr.length; i++) {
			arr[i] = '0';
		}
		if (arrTemp.length == 15) {
			arr = arrTemp;
		} else {
			System.arraycopy(arrTemp, 0, arr, 0, arrTemp.length > 15 ? 15 : arrTemp.length);
		}
		for (int i = 0; i < arr.length; i++) {
			int b = Integer.valueOf("" + arr[i], 16);
			if (b > 9) {
				b -= 10; // 为保证PUID为数字，将'A'-'F'的字符减10处理
				arr[i] = (char) (b + 48);
			}
		}

		return "151" + new String(arr);
	}

	public static String getVersion(Context context) {
		return ACommonMethod.getVersion(context);
	}

	public static int getSdkVersion() {
		return Integer.parseInt(Build.VERSION.SDK);
	}

	/**
	 * 获取支持的分辨率
	 * <p>
	 * 有可能为null，此时打开摄像头出错
	 * </p>
	 * 
	 * @return
	 */
	public static List<Size> getCameraPreviewSizes(Camera camera) {
		if (sPreviewSizes != null) {
			return sPreviewSizes;
		}
		try {
			Camera.Parameters parameters = camera.getParameters();
			sPreviewSizes = parameters.getSupportedPreviewSizes();

			// frameRates = parameters.getSupportedPreviewFrameRates();
			// Collections.sort(frameRates);
			// camera.release();
			return sPreviewSizes;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	// public static int getFrameRate(int fps_scale) {
	// if (frameRates != null) {
	// int size = frameRates.size();
	// if (size > 0) {
	// int index = (size * fps_scale) / MAX_FPS_SCALE;
	// return frameRates.get(index);
	// }
	// }
	// return -1;
	// }

	public static String getCameraDesc(Context context) {
		return sCameraDesc;
	}

	public static String getCameraName(Context context) {
		// return String.format("%s %s", android.os.Build.MODEL,
		// context.getString(R.string.camera));
		return sCameraName;
	}

	public static String getPeerUnitDesc() {
		return android.os.Build.MANUFACTURER;
	}

	public static void initParam(PUParam param, Context context) {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		param.PUID = preferences.getString(Common.PUID,
				Common.getPuid(context.getApplicationContext()));//
		param.DevID = param.PUID.substring(3);
		param.HardwareVer = android.os.Build.MODEL + " " + android.os.Build.VERSION.RELEASE;
		param.SoftwareVer = Common.getVersion(context);// 发布的时候要记得改一下.
		param.puname = preferences.getString(KEY_PUNAME.toString(), Common.getPeerUnitName());
		param.pudesc = preferences.getString(KEY_PUDESC.toString(), Common.getPeerUnitDesc());
		param.mCamName = preferences.getString(KEY_CAMNAME.toString(),
				Common.getCameraName(context));
		param.mSpeakerName = "Android speaker";
		param.mMicName = "Android mic";
		param.mGPSName = "Android gps";
	}

	public static String getPhoneModel() {
		String model = ACommonMethod.getModel();
		return model;
	}

	public static void commitBoolean(Context context, String key, boolean value) {
		SharedPreferences preference = context.getSharedPreferences(Common.PUBLIC_PREFERENCE,
				Context.MODE_PRIVATE);
		Editor edit = preference.edit();
		edit.putBoolean(key, value);
		edit.commit();
	}

	public static void commitDefaultBoolean(Context context, String key, boolean value) {
		SharedPreferences preference = PreferenceManager.getDefaultSharedPreferences(context);
		Editor edit = preference.edit();
		edit.putBoolean(key, value);
		edit.commit();
	}

	public static void commitDefaultString(Context context, String key, String value) {
		SharedPreferences preference = PreferenceManager.getDefaultSharedPreferences(context);
		Editor edit = preference.edit();
		edit.putString(key, value);
		edit.commit();
	}

	public static boolean parseGPSDataFromLocation(byte[] gpsData, BDLocation loc) {
		if (loc != null && gpsData.length == GPS_RAW_LENGTH) {
			gpsData[0] = 1; // 2字节的GPS采样点个数.
			float lat = (float) loc.getLatitude();
			float lon = (float) loc.getLongitude();
			float[] lnglat = new float[2];
			lnglat[0] = lon;
			lnglat[1] = lat;

			LngLatOffset.getOffset(lnglat);
			lon -= lnglat[0];
			lat -= lnglat[1];

			float bea = (float) 0;
			float spe = (float) ((float) loc.getSpeed() * 3.6);
			float alt = (float) loc.getAltitude();
			int tim = (int) (System.currentTimeMillis() / 1000);

			// float []lons = new float[1];
			// lons[0] = lon;
			// float []lats = new float[1];
			// lats[0] = lat;
			// sConverter.OffSetGPSData(lons, lats);
			// lon = lons[0];
			// lat = lats[0];

			System.arraycopy(CommonMethod.IntToBytes(Float.floatToIntBits(lat), true), 0, gpsData,
					4, 4); // 纬度
			System.arraycopy(CommonMethod.IntToBytes(Float.floatToIntBits(lon), true), 0, gpsData,
					8, 4); // 经度
			System.arraycopy(CommonMethod.IntToBytes(Float.floatToIntBits(bea), true), 0, gpsData,
					12, 4); // 方向
			System.arraycopy(CommonMethod.IntToBytes(Float.floatToIntBits(spe), true), 0, gpsData,
					16, 4); // 速度
			System.arraycopy(CommonMethod.IntToBytes(Float.floatToIntBits(alt), true), 0, gpsData,
					20, 4); // 海拔
			System.arraycopy(CommonMethod.IntToBytes(tim, true), 0, gpsData, 24, 4); // 时间
			return true;
		} else {
			return false;
		}
	}

	/**
	 * 获取当前分辨率。
	 * 
	 * @param context
	 * @param camera
	 * @return null表示“默认分辨率”，否则返回当前分辨率
	 */
	private static Size getCurrentPreviewSize(Context context, Camera camera) {
		SharedPreferences preference = PreferenceManager.getDefaultSharedPreferences(context);
		String value = preference.getString(Common.KEY_RESOLUTION, null);
		Camera.Size size = null;
		if (null == value) {
			List<Camera.Size> sizes = Common.getCameraPreviewSizes(camera);
			for (int i = 0; i < sizes.size(); i++) {
				size = sizes.get(i);
				if ((size.width == 320 && size.height == 240)
						|| (size.width == 352 && size.height == 288)
						|| (size.width == 640 && size.height == 480)) {
					break;
				}
			}
		} else {
			try {
				size = camera.new Size(320, 240);
				String[] strSize = value.split(Common.MULTIP);
				size.width = Integer.parseInt(strSize[0]);
				size.height = Integer.parseInt(strSize[1]);

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return size;
	}

	public static boolean saveBmp2File(Bitmap bmp, File file) {
		try {
			file.createNewFile();
			FileOutputStream fos = new FileOutputStream(file);
			bmp.compress(Bitmap.CompressFormat.JPEG, 100, fos);
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	// public static boolean getDefaultBoolean(String key, Context context,
	// boolean defaultValue) {
	// return ACommonMethod.getDefaultBoolean(key, context, defaultValue);
	// }
	//
	// public static int getDefaultInt(String key, Context context, int
	// defValue) {
	// return ACommonMethod.getDefaultInt(key, context, defValue);
	// }
	//
	// public static String getDefaultString(String key, Context context, String
	// defValue) {
	// return ACommonMethod.getDefaultString(key, context, defValue);
	// }
}
