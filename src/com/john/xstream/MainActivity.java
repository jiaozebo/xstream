package com.john.xstream;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;
import c7.Frame;

import com.crearo.mpu.sdk.VideoRunnable.FrameCallback;

public class MainActivity extends Activity implements FrameCallback {

	private static final String TAG = "XStream";
	public static XStream stream;
	private SurfaceView mSurfaceView;
	private SurfaceHolder mSurfaceHolder;
	private volatile MyPreviewRunnable mpr;
	private int mIdx = 0, mIdxAudio = 0;
	private com.gjfsoft.andaac.MainActivity mAudioThread;
	private WakeLock mWakeLock;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (LoginActivity.sCamera == null) {
			Toast.makeText(this, "摄像头初始化失败。", Toast.LENGTH_SHORT).show();
			finish();
			return;
		}
		setContentView(R.layout.activity_main);
		if (stream != null) {
			stream.setStartStreamCallback(this);
		}

		mSurfaceView = (SurfaceView) findViewById(R.id.sv_view);
		mSurfaceHolder = mSurfaceView.getHolder();
		mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

	@Override
	protected void onResume() {
		super.onResume();
		// 启用屏幕常亮功能
		if (mWakeLock == null) {
			PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
			mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
					| PowerManager.ON_AFTER_RELEASE, TAG);

		}
		mWakeLock.acquire(); // ... wl.release(); }

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.crearo.lib.map.CRMapActivity#onPause()
	 */
	@Override
	protected void onPause() {
		if (mWakeLock != null)
			mWakeLock.release();
		mWakeLock = null;
		super.onPause();
	}

	@Override
	public void onBackPressed() {
		new AlertDialog.Builder(this).setTitle(R.string.app_name).setMessage("确定要退出吗？")
				.setPositiveButton("确定", new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						onStopStream();
						if (stream != null) {
							stream.unreg();
							stream = null;
						}
						MainActivity.super.onBackPressed();
					}
				}).setNegativeButton("取消", null).show();
	}

	public void onStartStream(int arg0, int arg1) {
		if (mpr == null) {
			mIdx = 0;
			mpr = new MyPreviewRunnable();
			mpr.setCameraSize(320, 240);
			int result = mpr.create(mSurfaceView, LoginActivity.sCamera);
			if (result != 0) {
				mpr.close();
				mpr = null;
			} else {
				if (arg1 == 2) {
					mAudioThread = new com.gjfsoft.andaac.MainActivity(this);
					mAudioThread.start();
				}
				mpr.setFrameCallback(this);
			}
		}
	}

	public void onStopStream() {
		final MyPreviewRunnable pr = mpr;
		final com.gjfsoft.andaac.MainActivity audioThread = mAudioThread;

		mpr = null;
		mAudioThread = null;
		if (audioThread != null) {
			try {
				audioThread.terminate();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if (pr != null) {
			pr.close();
		}
	}

	@Override
	public synchronized boolean onFrameFatched(Frame frame) {
		// frame带有私有数据
		final XStream stream = MainActivity.stream;
		if (stream != null) {
			int result = -1;
			while (result == -1) {
				if (mpr == null && frame.type == Frame.FRAME_TYPE_VIDEO) {
					break;
				} else if (mAudioThread == null && frame.type == Frame.FRAME_TYPE_AUDIO) {
					break;
				}

				if (frame.type == Frame.FRAME_TYPE_AUDIO) {
// return false;
				}
				if (frame.type == Frame.FRAME_TYPE_VIDEO) {

				}
				/*
				 * int offset = *pParams; int length = *(pParams + 1); int
				 * keyFrm = *(pParams + 2); int tmStamp = *(pParams + 3); int
				 * nIdx = *(pParams + 4);
				 */

				result = stream.pumpFrame(frame.data, frame.type, frame.offset, frame.length,
						frame.keyFrmFlg, (int) System.currentTimeMillis(),
						frame.type == Frame.FRAME_TYPE_VIDEO ? mIdx++ : mIdxAudio++);
				if (result == -1) {
					try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						Thread.currentThread().interrupt();
						break;
					}
				}
			}
		}
		return false;
	}

	public void onChannelQuit(int channel) {
		Toast.makeText(this, "连接断开。", Toast.LENGTH_LONG).show();
		startActivity(new Intent(this, LoginActivity.class));
		finish();
	}

	public void onRequestKeyFrm() {
		final MyPreviewRunnable pr = mpr;
		if (pr != null) {
			pr.startKeyFrame(1);
		}
	}

}
