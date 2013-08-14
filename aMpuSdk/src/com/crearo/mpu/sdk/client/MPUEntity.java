package com.crearo.mpu.sdk.client;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.os.Build;
import android.view.SurfaceView;
import c7.DC7;
import c7.IResType;
import c7.LoginInfo;
import c7.PUParam;

import com.crearo.mpu.sdk.AudioRunnable;
import com.crearo.mpu.sdk.MPUHandler;
import com.crearo.mpu.sdk.OAudioRunnable;
import com.crearo.mpu.sdk.PreviewRunnable;
import com.crearo.mpu.sdk.VideoRunnable;

/**
 * MPU服务类
 */
@SuppressLint("NewApi")
public class MPUEntity extends MPUHandler {

	public MPUEntity(Context context) {
		super(context);
	}

	/**
	 * 登录服务器
	 * 
	 * @param addr
	 *            地址
	 * @param port
	 *            端口
	 * @param password
	 *            密码
	 * @param info
	 *            设备信息
	 * @return 0说明登录成功，否则为{@link ErrorCode 错误码}里的值。
	 */
	public int login(String addr, int port, String password, PUInfo info) {
		LoginInfo li = new LoginInfo();
		li.addr = addr;
		li.port = port;
		li.password = password;
		li.param = new PUParam();
		li.param.ProducerID = "00005";
		li.param.PUID = info.puid;
		li.param.DevID = info.puid.substring(3);
		li.param.HardwareVer = info.hardWareVer;
		li.param.SoftwareVer = info.softWareVer;
		li.param.puname = info.name;
		li.param.pudesc = info.name;
		li.param.mCamName = info.cameraName;
		li.param.mMicName = info.mMicName;
		li.param.mSpeakerName = info.mSpeakerName;
		li.param.mGPSName = info.mGPSName;
		return login(li);
	}

	/**
	 * 登出服务器
	 */
	public void logout() {
		close();
	}

	/**
	 * 启动视频服务，通常在登录成功后调用该函数。这样客户端就可以申请到视频了
	 * 
	 * @param sf
	 *            显示摄像头视频的SurfaceView
	 * @param param
	 *            视频信息，该参数包含帧高度、宽度，视频质量
	 * @return 0为成功，-1表示无摄像头或者初始化摄像头失败。其他为错误码。
	 */
	public int start(SurfaceView sf, VideoParam param) {
		PreviewRunnable pr = (PreviewRunnable) VideoRunnable.createInstance(false);
		pr.setCameraSize(param.width, param.height);
		pr.setVideoQuality(param.quality);
		Camera camera = null;
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO) {
			int cameraNum = Camera.getNumberOfCameras();
			if (cameraNum < 1) {
				return -1;
			}
			CameraInfo cameraInfo = new CameraInfo();
			Camera.getCameraInfo(0, cameraInfo);
			camera = Camera.open(0);
		} else {
			camera = Camera.open();
		}
		if (camera == null) {
			return -1;
		}
		return pr.create(sf, camera);
	}

	/**
	 * 终止视频服务，在退出之前要手动调用该函数以防止内存泄漏
	 */
	public void stop() {
		VideoRunnable vr = VideoRunnable.singleton();
		if (vr != null) {
			DC7 dc7 = vr.getVideoDC();
			if (dc7 != null) {
				dc7.close();
			}
			vr.close();
		}
		DC7 dc7 = AudioRunnable.singleton().getDc();
		if (dc7 != null) {
			dc7.close();
		}
		AudioRunnable.singleton().stop();
		dc7 = OAudioRunnable.singleton().getDC();
		if (dc7 != null) {
			dc7.close();
		}
		OAudioRunnable.singleton().stop();
		if (gpsHandler != null) {
			dc7 = gpsHandler.getDC();
			if (dc7 != null) {
				dc7.close();
			}
			gpsHandler.close();
		}
	}

	/**
	 * 设置回调
	 * 
	 * @param callback
	 *            实现该回调以接收各种流的状态
	 */
	public void setCallback(final MPUCallback callback) {
		setRendCallback(new RendCallback() {

			@Override
			public void onRendStatusFetched(IResType type, byte status) {
				switch (type) {
				case IV:
					callback.onStatusFetched(MPUCallback.TYPE_IV, status);
					break;
				case GPS:
					break;
				case IA:
					callback.onStatusFetched(MPUCallback.TYPE_PLAY_AUDIO, status);
					break;
				case OA:
					final boolean rending = (status & STT_REND_BEGIN) != 0;
					// 通过status获取到oaType
					byte oa_type = (byte) (status & ~(rending ? STT_REND_BEGIN : STT_REND_END));
					int sdkType = oa_type != 0 ? MPUCallback.TYPE_TALK : MPUCallback.TYPE_CALL;
					callback.onStatusFetched(sdkType, rending ? MPUCallback.STT_START
							: MPUCallback.STT_STOP);
					break;
				default:
					break;
				}
			}
		});
	}

	/**
	 * 获取SDK版本号
	 * 
	 * @return 版本号字符串
	 */
	public static final String getVersion() {
		return "7.0.13.221";
	}
}
