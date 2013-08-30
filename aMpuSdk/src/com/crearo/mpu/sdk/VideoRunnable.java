package com.crearo.mpu.sdk;

import vastorager.StreamWriter;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.view.SurfaceView;
import android.view.View;
import c7.DC7;
import c7.Frame;
import c7.IResType;

/**
 * @author John
 * @version 1.0
 * @date 2011-12-10
 */
public abstract class VideoRunnable implements Runnable {
	public static final int E0 = 0x1000;
	public static final int E1 = 0x1001;
	public static final int E2 = 0x1002;
	public static final int E3 = 0x1003;
	public static final int E4 = 0x1004;
	public static final int E5 = 0x1005;
	public static final int E6 = 0x1006;
	public static final int E7 = 0x1007;
	protected static VideoRunnable _runnable;
	protected final Size mSize = new Size();
	protected Camera mCamera;

	public static VideoRunnable singleton() {
		return _runnable;
	}

	protected Handler mHandler;
	protected StreamWriter streamWriter;
	protected boolean isFirstKeyfrmFethced;

	protected Thread mCurrentThread = null;

	/**
	 * 连续关键帧数目,在编码时据此来编出连续的若干个关键帧
	 */
	protected int keyFrmCount;
	/**
	 * 等待关键帧标志位
	 */
	protected boolean waitKeyFrm;
	protected int count;
	protected DC7 dc;
	/**
	 * 帧率
	 */
	protected int mFrameRate = 20;

	public void setHandler(MPUHandler handler) {
		mHandler = handler;
	}

	public void setVideoDC(DC7 dc) {
		this.dc = dc;
		if (dc != null) {
			startKeyFrame(1);
		}
	}

	public DC7 getVideoDC() {
		return dc;
	}

	/**
	 * 设置录像,设置录像时,应该等关键帧。
	 * 
	 * @param streamWriter
	 */
	public synchronized void setRecorder(StreamWriter streamWriter) {
		this.streamWriter = streamWriter;
		if (streamWriter != null) {
			isFirstKeyfrmFethced = false;
			startKeyFrame(1);
		}
	}

	public void startKeyFrame(int count) {
		keyFrmCount = count;
	}

	/**
	 * 用特定的参数初始化camera对象。
	 * 
	 * @param view
	 * @param camera
	 *            如果为null，表示反初始化
	 * @return
	 */
	public abstract int initCamera(SurfaceView view, Camera camera);

	public abstract int create(SurfaceView view, Camera camera);

	public void close() {
		setVideoDC(null);
		count = 0;
		synchronized (this) {
			if (streamWriter != null) {
				Message msg = mHandler.obtainMessage(MPUHandler.MSG_RECORD_ERROR);
				msg.obj = streamWriter;
				msg.sendToTarget();
				streamWriter = null;
			}
		}
		IResType.IV.mIsAlive = false;
		_runnable = null;
	}

	public boolean needHandleStream() {
		return streamWriter != null || dc != null;
	}

	public abstract void stopCamera();

	public abstract void autoFocus(View view);

	/**
	 * 设置图像尺寸
	 * 
	 * @param size
	 */
	public void setCameraSize(int width, int height) {
		mSize.width = width;
		mSize.height = height;
	}

	/**
	 * 获取DC通道的帧容量，超过这个帧容量后，就该考虑丢帧了。
	 * 
	 * @return
	 */
	public int getDCFrameCapacity() {
		if (mSize.width >= 480) {
			return 8;
		}
		return 16;
	}

	public void setFrameRate(int frameRate) {
		mFrameRate = frameRate;
	}

	protected FrameCallback mFrameCallback;

	/**
	 * 设置视频帧回调
	 * 
	 * @param callback
	 */
	public void setFrameCallback(FrameCallback callback) {
		this.mFrameCallback = callback;
	}

	public interface FrameCallback {
		/**
		 * C7 视频数据（即存储帧头之后的数据。一个完整帧，发送时需要分包发送）
		 * 
		 * @return true表示frame处理了，false表示frame丢弃了
		 */
		boolean onFrameFatched(Frame frame);
	}
}
