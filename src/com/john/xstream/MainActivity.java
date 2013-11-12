package com.john.xstream;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.Assert;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.StateListDrawable;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import c7.Frame;

import com.crearo.mpu.sdk.CameraThread.H264FrameCallback;
import com.crearo.mpu.sdk.CameraThread.TakeShotCallback;

public class MainActivity extends Activity implements OnClickListener, H264FrameCallback, Callback {

	private static final String TAG = "XStream";
	private static final String KEY_RESOLUTION_IDX = "resolution_idx";
	public static XStream stream;
	private SurfaceView mSurfaceView;
	private SurfaceHolder mSurfaceHolder;
	private volatile MyPreviewRunnable mPreviewThread;
	private int mIdx = 0, mIdxAudio = 0;
	private com.gjfsoft.andaac.MainActivity mAudioThread;
	private WakeLock mWakeLock;
	CheckBox mLive;
	private ImageView mSnapshot, mSetting, mSwitch;
	private Dialog mSettingDlg;
	private int mSizeID;
	private int mCameraId = 0;
	private View mLiveFloatBar;

	private TextView mTimeInterval, mBitRate, mFrameRate;

	private AtomicInteger mTotalBits, mTotalFrames;
	private TakeShotCallback mTakeShotCb;
	private H264FrameCallback mPreviewThreadH264FrameCB;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);

		mSurfaceView = (SurfaceView) findViewById(R.id.sv_view);
		mSurfaceHolder = mSurfaceView.getHolder();
		mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		mSurfaceHolder.addCallback(this);

		mLive = (CheckBox) findViewById(R.id.preview_preview);
		mSnapshot = (ImageView) findViewById(R.id.preview_snapshot);
		mSetting = (ImageView) findViewById(R.id.preview_setting);
		mSwitch = (ImageView) findViewById(R.id.camera_switch);

		mLiveFloatBar = findViewById(R.id.float_bar);
		mTimeInterval = (TextView) mLiveFloatBar.findViewById(R.id.tv_time);
		mTimeInterval.setTag(0);
		mBitRate = (TextView) mLiveFloatBar.findViewById(R.id.tv_bitrate);
		mFrameRate = (TextView) mLiveFloatBar.findViewById(R.id.tv_frame_rate);
		mTotalBits = new AtomicInteger(0);
		mTotalFrames = new AtomicInteger(0);
		CRTimer t = new CRTimer(1000) {

			@Override
			protected void onTimer() {
				int current = (Integer) mTimeInterval.getTag();
				current += 1;
				mTimeInterval.setTag(current);
				int s = current / 60;
				int m = current % 60;
				mTimeInterval.setText(String.format("%02d:%02d", s, m));

				mFrameRate.setText(String.format("%4dfps", mTotalFrames.get()));
				mTotalFrames.set(0);
				mBitRate.setText(String.format("%4dkbps", mTotalBits.get() / 1024));
				mTotalBits.set(0);
			}
		};
		mLiveFloatBar.setTag(t);
		if (mLive.isChecked()) {
			t.start();
		}
		if (stream != null) {
			stream.setStartStreamCallback(this);
		}

		StateListDrawable drawable = new StateListDrawable();
		drawable.addState(new int[] { android.R.attr.state_checked },
				getResources().getDrawable(R.drawable.preview_pressed));
		drawable.addState(new int[] {}, getResources().getDrawable(R.drawable.preview));
		mLive.setButtonDrawable(drawable);
		mLive.setChecked(true);

		mLive.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				boolean isLive = isChecked;
				MyPreviewRunnable mpr = mPreviewThread;
				if (mpr != null) {
					if (!isLive) {
						mPreviewThreadH264FrameCB = mpr.getH264FrameCallback();
						mpr.setH264FrameCallback(null);
					} else {
						mpr.setH264FrameCallback(mPreviewThreadH264FrameCB);
						mpr.tryFeedCamera();
					}
				}
				if (!isLive) {
					mLiveFloatBar.setVisibility(View.GONE);
					CRTimer t = (CRTimer) mLiveFloatBar.getTag();
					t.stop();
				} else {
					mLiveFloatBar.setVisibility(View.VISIBLE);
					CRTimer t = (CRTimer) mLiveFloatBar.getTag();
					mTimeInterval.setTag(0);
					mTotalBits.set(0);
					mTotalFrames.set(0);
					t.start();
				}
			}
		});

		drawable = new StateListDrawable();
		drawable.addState(new int[] { android.R.attr.state_pressed },
				getResources().getDrawable(R.drawable.snapshot_pressed));
		drawable.addState(new int[] {}, getResources().getDrawable(R.drawable.snapshot));
		mSnapshot.setImageDrawable(drawable);

		mSnapshot.setOnClickListener(this);
		mSetting.setOnClickListener(this);
		mSwitch.setOnClickListener(this);
		mSettingDlg = new Dialog(
				this,
				Build.VERSION.SDK_INT > 10 ? android.R.style.Theme_Holo_Light_DialogWhenLarge_NoActionBar
						: android.R.style.Theme_Dialog);
		mSettingDlg.setContentView(R.layout.setting);
		Button ok = (Button) mSettingDlg.findViewById(R.id.ok);
		Button cancel = (Button) mSettingDlg.findViewById(R.id.cancel);
		ok.setOnClickListener(this);
		cancel.setOnClickListener(this);

		RadioGroup rg = (RadioGroup) mSettingDlg.findViewById(R.id.resulotions);
		int currentSizeID = 0;
		for (int i = 0; i < 3; i++) {
			RadioButton rb = (RadioButton) rg.getChildAt(i);
			rb.setId(i);
		}
		List<Size> rs = LoginActivity.sResolutions;
		int size = rs.size();
		if (size == 3) {
			for (int i = 0; i < 3; i++) {
				RadioButton rb = (RadioButton) rg.getChildAt(i);
				setRBTag(rb, rs.get(i));
			}
			currentSizeID = 1;
		} else if (size == 2) {
			rg.removeViewAt(1);
			for (int i = 0; i < 2; i++) {
				RadioButton rb = (RadioButton) rg.getChildAt(i);
				setRBTag(rb, rs.get(i));
			}
			currentSizeID = 0;
		} else if (size == 1) {
			rg.removeViewAt(2);
			rg.removeViewAt(0);
			RadioButton rb = (RadioButton) rg.getChildAt(0);
			setRBTag(rb, rs.get(0));
			currentSizeID = 0;
		} else if (size == 4 || size == 5) {
			for (int i = 0; i < 3; i++) {
				RadioButton rb = (RadioButton) rg.getChildAt(i);
				setRBTag(rb, rs.get(i + 1));
			}
			currentSizeID = 1;
		}
		if (size == 0) {
			finish();
			return;
		} else {
			for (int i = 0; i < 3; i++) {
				RadioButton rb = (RadioButton) rg.getChildAt(i);
				setRBTag(rb, rs.get(i * 2));
			}
			currentSizeID = 1;
		}
		mSizeID = getPreferences(MODE_PRIVATE).getInt(KEY_RESOLUTION_IDX, currentSizeID);

		RadioButton rb = (RadioButton) mSettingDlg.findViewById(mSizeID);
		if (rb != null)
			rb.setChecked(true);

		mTakeShotCb = new TakeShotCallback() {

			@Override
			public void onOneShot(Bitmap bmp) {
				if (bmp != null) {
					try {
						File f = new File(Environment.getExternalStorageDirectory(),
								XStreamApp.FILE_SNAPSHOT);
						SimpleDateFormat formater = new SimpleDateFormat("yy-MM-dd HH.mm.ss",
								Locale.US);
						String name = formater.format(new Date());
						f = new File(f, name + ".jpg");
						FileOutputStream fos = new FileOutputStream(f);
						if (bmp.compress(Bitmap.CompressFormat.JPEG, 100, fos)) {
							ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC,
									ToneGenerator.MAX_VOLUME);
							tone.startTone(ToneGenerator.TONE_PROP_BEEP);
						}
						bmp.recycle();
						fos.close();
						final String fName = f.getName();
						runOnUiThread(new Runnable() {

							@Override
							public void run() {
								Toast.makeText(MainActivity.this, "抓拍文件保存为 " + fName,
										Toast.LENGTH_SHORT).show();
							}
						});
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		};
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onDestroy()
	 */
	@Override
	protected void onDestroy() {
		if (stream != null) {
			stream.unreg();
			stream = null;
		}
		CRTimer t = (CRTimer) mLiveFloatBar.getTag();
		t.stop();
		mTakeShotCb = null;
		super.onDestroy();
	}

	@Override
	public void onBackPressed() {
		new AlertDialog.Builder(this).setTitle(R.string.app_name).setMessage("确定要退出吗？")
				.setPositiveButton("确定", new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						MainActivity.super.onBackPressed();
					}
				}).setNegativeButton("取消", null).show();
	}

	public synchronized void onStartStream(int arg0, int arg1) {
		MyPreviewRunnable mpr = mPreviewThread;
		if (mpr != null) {
			mIdx = 0;
			mpr.setH264FrameCallback(this);
			mpr.tryFeedCamera();
		}
		com.gjfsoft.andaac.MainActivity audioThread = mAudioThread;
		if (audioThread != null && !audioThread.isAlive()) {
			audioThread.start();
		}
	}

	// ui
	public void onStopStream() {
		Assert.assertEquals(Thread.currentThread(), Looper.getMainLooper().getThread());
		final MyPreviewRunnable pr = mPreviewThread;
		final com.gjfsoft.andaac.MainActivity audioThread = mAudioThread;

		mPreviewThread = null;
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
			pr.setH264FrameCallback(null);
			pr.stopCamera();
			pr.stopThread();
		}
	}

	@Override
	public synchronized void onFrameCallback(Thread context, Frame frame) {
		// frame带有私有数据
		final XStream stream = MainActivity.stream;
		boolean live = mLive.isChecked();
		if (live && stream != null) {
			int result = -1;
			while (result == -1) {
				if (mPreviewThread == null && frame.type == Frame.FRAME_TYPE_VIDEO) {
					break;
				} else if (mAudioThread == null && frame.type == Frame.FRAME_TYPE_AUDIO) {
					break;
				}

				if (frame.type == Frame.FRAME_TYPE_AUDIO) {
// return false;
				}
				if (frame.type == Frame.FRAME_TYPE_VIDEO) {
					mTotalFrames.addAndGet(1);
					mTotalBits.addAndGet(frame.length);
				}
				/*
				 * int offset = *pParams; int length = *(pParams + 1); int
				 * keyFrm = *(pParams + 2); int tmStamp = *(pParams + 3); int
				 * nIdx = *(pParams + 4);
				 */
// jint handle, jbyteArray jbaFrame, jintArray jiaParams
				result = stream.pumpFrame(frame.data, frame.type, frame.offset, frame.length,
						frame.keyFrmFlg, (int) System.currentTimeMillis(),
						frame.type == Frame.FRAME_TYPE_VIDEO ? mIdx++ : mIdxAudio++,
						frame.type == Frame.FRAME_TYPE_VIDEO ? frame.width : 0,
						frame.type == Frame.FRAME_TYPE_VIDEO ? frame.height : 0);
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
	}

	public void onChannelQuit(int channel) {
		Toast.makeText(this, "连接断开。", Toast.LENGTH_LONG).show();
		startActivity(new Intent(this, LoginActivity.class));
		finish();
	}

	public void onRequestKeyFrm() {
		final MyPreviewRunnable pr = mPreviewThread;
		if (pr != null) {
			pr.startKeyFrame(1);
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
	public void onClick(View v) {
		if (v == mSetting) {
			List<Size> rs = LoginActivity.sResolutions;
			RadioGroup rg = (RadioGroup) mSettingDlg.findViewById(R.id.resulotions);
			if (rs.isEmpty()) {
				return;
			}
			mSettingDlg.show();
			int resolutionIdx = getPreferences(MODE_PRIVATE).getInt(KEY_RESOLUTION_IDX, 1);
			RadioButton rb = (RadioButton) mSettingDlg.findViewById(resolutionIdx);
			rb.setChecked(true);
		} else if (v.getId() == R.id.ok) {
			RadioGroup rg = (RadioGroup) mSettingDlg.findViewById(R.id.resulotions);
			int id = rg.getCheckedRadioButtonId();
			RadioButton rb = (RadioButton) rg.findViewById(id);
			if (rb == null) {
				mSettingDlg.dismiss();
				return;
			}
			int resolutionIdx = getPreferences(MODE_PRIVATE).getInt(KEY_RESOLUTION_IDX, 1);
			if (id == resolutionIdx) {
				// 没有更改
			} else {
				getPreferences(MODE_PRIVATE).edit().putInt(KEY_RESOLUTION_IDX, id).apply();
				// 更改分辨率
				MyPreviewRunnable mpr = mPreviewThread;
				if (mpr != null) {
					mPreviewThread = null;
					mpr.pauseCamera();
					Camera.Size s = (Size) rb.getTag();
					mpr.setCameraSizeNoblock(s.width, s.height);
					mpr.resumeCamera();
					mPreviewThread = mpr;
				}
			}
			mSettingDlg.dismiss();
		} else if (v.getId() == R.id.cancel) {
			mSettingDlg.dismiss();
		} else if (v == mSwitch) {
			int num = Camera.getNumberOfCameras();
			if (num < 2) {
				Toast.makeText(this, "您的手机不支持摄像头切换功能", Toast.LENGTH_SHORT).show();
			}
			MyPreviewRunnable mpr = mPreviewThread;
			if (mpr != null) {
// int id = Camera.getNumberOfCameras() - (LoginActivity.sCameraId + 1);
// Camera c = LoginActivity.sCamera;
// mpr.switchCamera(id);
// mPreviewThread = mpr;
				mCameraId = num - 1 - mCameraId;
				mpr.switchCamera(mCameraId);
			}
		} else if (mSnapshot == v) {
			MyPreviewRunnable mpr = mPreviewThread;
			if (mpr != null) {
				mpr.talkOneShot(mTakeShotCb);
				mPreviewThreadH264FrameCB = mpr.getH264FrameCallback();
				if (mPreviewThreadH264FrameCB == null) {
					mpr.tryFeedCamera();
				}
			}
		}
	}

	private void setRBTag(RadioButton rb, Camera.Size s) {
		rb.setTag(s);
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		MyPreviewRunnable mpr = new MyPreviewRunnable();
		Camera.Size s = LoginActivity.sResolutions.get(mSizeID);
		mpr.setCameraSize(s.width, s.height);
		mpr.setVideoQuality(5);
		mpr.setFrameRate(20);
		mpr.setH264FrameCallback(null);
		mpr.startThread();
		mpr.startCamera(mSurfaceView, mCameraId);
		mPreviewThread = mpr;
		mAudioThread = new com.gjfsoft.andaac.MainActivity(this);
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		// TODO Auto-generated method stub

	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		onStopStream();
	}

}
