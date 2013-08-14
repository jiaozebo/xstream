package com.crearo.mpu.sdk;

import java.io.IOException;
import java.lang.ref.WeakReference;

import junit.framework.Assert;
import vastorager.StreamWriter;
import video.encoder.CRVideoConverter;
import video.encoder.CRVideoEncoder;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import c7.DC7;
import c7.Frame;

/**
 * 处理软编码视频数据，被动停止。
 * 
 * @author John
 * @version 1.0
 * @date 2011-11-17
 */
public class PreviewRunnable extends VideoRunnable implements PreviewCallback {
	public static class PreviewHandler extends Handler {

		byte[] mBufferReuse = null;
		private long mLastRequestFrameTime;
		final int[] outLen = new int[1];
		private WeakReference<PreviewRunnable> mPrevRunnableRef;

		public PreviewHandler(PreviewRunnable pr) {
			super();
			mPrevRunnableRef = new WeakReference<PreviewRunnable>(pr);
		}

		public void quit() {
			mPrevRunnableRef.clear();
			getLooper().quit();
		}

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			PreviewRunnable pr = mPrevRunnableRef.get();
			if (pr == null) {
				return;
			}
			if (msg.what == MSG_PREVIEW_FRAME_FETCHED) {
				Frame frame = (Frame) msg.obj;
				int[] keyFrm = new int[1];
				if (pr.keyFrmCount-- > 0) {
					keyFrm[0] = 1;
				} else if (pr.waitKeyFrm) {
					pr.waitKeyFrm = false;
					keyFrm[0] = 1;
				}
				if (mBufferReuse == null) {
					mBufferReuse = new byte[frame.length];
				}
				pr.cvt.convert(frame.data, pr.mSize.width, pr.mSize.height, 1, mBufferReuse);
				int nRet = 0;
				if (keyFrm[0] == 1) {
					Log.e(tag, "request key frm");
				}
				if (pr.mRotate) {
					yuvRotate90(frame.data, mBufferReuse, pr.mSize.width, pr.mSize.height);
					nRet = pr.mEncoder.encode(pr.hEncoder, mBufferReuse, pr.mSize.height,
							pr.mSize.width, keyFrm, frame.data, outLen);
					frame.width = pr.mSize.height;
					frame.height = pr.mSize.width;
				} else {
					nRet = pr.mEncoder.encode(pr.hEncoder, mBufferReuse, pr.mSize.width,
							pr.mSize.height, keyFrm, frame.data, outLen);
				}
				if (nRet == 0) {
					// VideoData 前面的信息以及私有数据
					frame.offset = 16 + 12;
					frame.length = outLen[0] - 28;
					frame.keyFrmFlg = (byte) keyFrm[0];
					if (pr.mFrameCallback != null) {
						pr.mFrameCallback.onFrameFatched(frame);
					}
				}
				long delay = 0l;
				msg = obtainMessage(MSG_PREVIEW_FRAME_REQUESTED);
				msg.obj = frame.data;
				sendMessageDelayed(msg, delay);
			} else if (msg.what == MSG_PREVIEW_FRAME_REQUESTED) {
				synchronized (pr) {
					if (pr.mCamera != null) {
						mLastRequestFrameTime = System.currentTimeMillis();
						byte[] data = (byte[]) msg.obj;
						pr.mCamera.addCallbackBuffer(data);
					}
				}
			}
		}
	}

	private static final String tag = "Camera";

	protected static final int MSG_PREVIEW_FRAME_FETCHED = 1000;

	protected static final int MSG_PREVIEW_FRAME_REQUESTED = 1001;

	CRVideoEncoder mEncoder;
	private int hEncoder;

	private CRVideoConverter cvt;
	private PreviewHandler mPreviewHandler;
	byte[] mCameraDataBufferReuse = null;
	private int mQuality = -1;
	private boolean mRotate = false;

	private byte[] mRotateBuffer;

	@Override
	public int create(SurfaceView view, Camera camera) {
		Assert.assertNull(mEncoder);
		Assert.assertEquals(0, hEncoder);
		mEncoder = new CRVideoEncoder();
		hEncoder = mEncoder.create();
		if (hEncoder == 0) {
			return E7;
		}
		cvt = new CRVideoConverter();
		int[] config = new int[6];
		mEncoder.getConfig(hEncoder, config);
		// H264,取28~38.
		SharedPreferences spf = PreferenceManager.getDefaultSharedPreferences(view.getContext());
		if (mQuality == -1) {
			mQuality = spf.getInt(Common.KEY_QUALITY, 5);
		}
		int quant = mQuality;
		quant = 5 - quant;
		{
			quant = 28 + quant * 2;
		}
		mEncoder.setConfig(hEncoder, config[0], mFrameRate, config[2], quant, quant);
		mFrameRate = spf.getInt(Common.KEY_FRAME_RATE, 20);
		mRotate = spf.getBoolean(Common.KEY_VIDEO_PORTRAIT, false);
		int nRet = initCamera(view, camera);
		if (nRet != 0) {
			stopCamera();
		} else {
			new Thread(this, "Preview").start();
		}
		return nRet;
	}

	public void close() {
		super.close();
		boolean threadRun = false;
		synchronized (this) {
			if (mCurrentThread != null) {
				// 已经跑起来了
				threadRun = true;
			} else {
				mCurrentThread = Thread.currentThread();
			}
		}
		stopCamera();
		if (threadRun) {
			if (mPreviewHandler != null) {
				mPreviewHandler.quit();
				mPreviewHandler = null;
				try {
					mCurrentThread.interrupt();
					mCurrentThread.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
					mCurrentThread.interrupt();
				}
			}
		}
		mCurrentThread = null;
		if (mEncoder != null) {
			Assert.assertNotSame(0, hEncoder);
			mEncoder.close(hEncoder);
			hEncoder = 0;
			mEncoder = null;
		}
		waitKeyFrm = false;
	}

	@Override
	public void setVideoDC(DC7 dc) {
		super.setVideoDC(dc);
		if (dc != null) {
			startFrame();
		} else {
			if (streamWriter == null) {
				mCameraDataBufferReuse = null;
			}
		}
	}

	@Override
	public void setRecorder(StreamWriter streamWriter) {
		super.setRecorder(streamWriter);
		if (streamWriter != null) {
			startFrame();
		} else {
			if (dc == null) {
				mCameraDataBufferReuse = null;
			}
		}
	}

	/**
	 * 调用该接口回调视频帧
	 */
	private void startFrame() {
		if (mCamera != null && mPreviewHandler != null) {
			if (mCameraDataBufferReuse == null && (dc != null || streamWriter != null)) {
				Size mSize = mCamera.getParameters().getPreviewSize();
				int format = mCamera.getParameters().getPreviewFormat();
				int bpp = ImageFormat.getBitsPerPixel(format);
				mCameraDataBufferReuse = new byte[mSize.width * mSize.height * bpp / 8];
				Message msg = mPreviewHandler.obtainMessage(MSG_PREVIEW_FRAME_REQUESTED);
				msg.obj = mCameraDataBufferReuse;
				msg.sendToTarget();
			}
		}
	}

	@Override
	public void run() {
		synchronized (this) {
			if (mCurrentThread != null) {
				return;
			} else {
				mCurrentThread = Thread.currentThread();
			}
		}
		Looper.prepare();
		mPreviewHandler = new PreviewHandler(this);
		startFrame();
		Looper.loop();
	}

	private static void rotatRect90(byte[] src, byte[] dst, int offset, int width, int height) {
		int n = 0;
		for (int i = 0; i < width; i++) {
			for (int j = height - 1; j > -1; j--) {
				dst[offset + n++] = src[offset + j * width + i];
			}
		}
	}

	private static void yuvRotate90(byte[] src, byte[] dst, int width, int height) {
		int offset = 0;
		rotatRect90(src, dst, offset, width, height);
		offset += (width * height);
		rotatRect90(src, dst, offset, width / 2, height / 2);
		offset += width * height / 4;
		rotatRect90(src, dst, offset, width / 2, height / 2);
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		if (mPreviewHandler != null) {
			Frame frame = new Frame(Frame.FRAME_TYPE_VIDEO, data, 0, data.length, (byte) 0);
			frame.timeStamp = (int) System.currentTimeMillis();
			Message msg = mPreviewHandler.obtainMessage(MSG_PREVIEW_FRAME_FETCHED);
			msg.obj = frame;
			msg.sendToTarget();
		}
	}

	public void startKeyFrame(int count) {
		keyFrmCount = count;
	}

	@Override
	public int initCamera(SurfaceView view, Camera camera) {
		mCamera = camera;
		if (mCamera == null) {
			stopCamera();
			return E0;
		}
		SurfaceHolder holder = view.getHolder();
		Parameters param = mCamera.getParameters();
		if (mSize.width == 0 || mSize.height == 0) {
			return E1;
		} else {
			param.setPreviewSize(mSize.width, mSize.height);
			// param.setPreviewFormat(arg0)
			try {
				param.setZoom(2);
				mCamera.setParameters(param);
				Log.i(tag, "size : " + mSize.width + ", " + mSize.height);
			} catch (Exception e) {
				e.printStackTrace();
				Size size = param.getPreviewSize();
				Log.e(tag, "size : " + size.width + ", " + size.height);
				String strRes = size.width + Common.MULTIP + size.height;
				Common.commitDefaultString(view.getContext(), Common.KEY_RESOLUTION, strRes);
			}
		}
		setCameraDisplayOrientation((Activity) view.getContext(), 0, camera);
		try {
			mCamera.setPreviewDisplay(holder);
		} catch (IOException e) {
			e.printStackTrace();
			mCamera = null;
			return E2;
		}
		mCamera.setPreviewCallbackWithBuffer(this);
		mCamera.startPreview();
		// 原则上讲，这里不应该调用，因为这时候还没有初始化Handler, 且DC、StreamWriter等变量没有设置。
		// 但是在切换摄像头时，如果在这里不调用，run函数就不会循环了。
		startFrame();
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.crearo.mpu.sdk.VideoRunnable#setCameraSize(int, int)
	 */
	@Override
	public void setCameraSize(int width, int height) {
		// TODO Auto-generated method stub
		super.setCameraSize(width, height);
		mRotateBuffer = new byte[width * height];
	}

	@Override
	public void stopCamera() {
		synchronized (this) {
			if (mCamera != null) {
				mCamera.stopPreview();
				mCamera.release();
				mCamera = null;
			}
		}
		mCameraDataBufferReuse = null;
	}

	@Override
	public void autoFocus(final View view) {
		synchronized (this) {
			if (mCamera != null) {
				mCamera.autoFocus(new AutoFocusCallback() {
					@Override
					public void onAutoFocus(boolean success, Camera camera) {
						view.setClickable(true);
					}
				});
			}
		}
	}

	public void setVideoQuality(int quality) {
		mQuality = quality;
	}

	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	public static void setCameraDisplayOrientation(Activity activity, int cameraId,
			android.hardware.Camera camera) {
		android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
		android.hardware.Camera.getCameraInfo(cameraId, info);
		int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
		int degrees = 0;
		switch (rotation) {
		case Surface.ROTATION_0:
			degrees = 0;
			break;
		case Surface.ROTATION_90:
			degrees = 90;
			break;
		case Surface.ROTATION_180:
			degrees = 180;
			break;
		case Surface.ROTATION_270:
			degrees = 270;
			break;
		}

		int result;
		if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
			result = (info.orientation + degrees) % 360;
			result = (360 - result) % 360; // compensate the mirror
		} else { // back-facing
			result = (info.orientation - degrees + 360) % 360;
		}
		camera.setDisplayOrientation(result);
	}
}
