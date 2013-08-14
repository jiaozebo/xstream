package com.crearo.mpu.sdk;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

import junit.framework.Assert;
import vastorager.StreamWriter;
import video.encoder.H264S2F;
import android.annotation.TargetApi;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnErrorListener;
import android.media.MediaRecorder.OnInfoListener;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.ACommonMethod;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import c7.BufferWithStatus;
import c7.DCAssist;
import c7.Frame;

/**
 * @author John
 */
public class RecordRunnable extends VideoRunnable implements OnInfoListener, OnErrorListener {

	public static class RecordHandler extends Handler {

		public static final int MSG_KEY_READ_STREAM = 0;
		public static final int MSG_KEY_HANDLE_STREAM = 1;
		private WeakReference<RecordRunnable> mRecordRunnableRef;
		/**
		 * 最近一个数据包的读取时间
		 */
		private long mLastReadInterval;
		/**
		 * 公用的数据包内存
		 */
		private byte[] mBuffer = null;
		Queue<Frame> mFrames = new LinkedList<Frame>();

		public RecordHandler(RecordRunnable rr) {
			super();
			mRecordRunnableRef = new WeakReference<RecordRunnable>(rr);
			mBuffer = new byte[rr.mDefaultBufferSize];
		}

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			RecordRunnable rr = mRecordRunnableRef.get();
			if (rr == null) {
				return;
			}

			switch (msg.what) {
			case MSG_KEY_READ_STREAM: {
				InputStream is = null;
				try {
					is = rr.receiver.getInputStream();
					long lastReadTime = System.currentTimeMillis();
					int size = 0, currentSize = 0;
					while (true) {
						currentSize = is.read(mBuffer, size, mBuffer.length - size);
						if (currentSize == -1) {
							return;
						}
						size += currentSize;
						// 如果未读满,说明一包数据读完了
						if (currentSize < mBuffer.length - size) {
							break;
						} else {
							byte[] oldBuffer = mBuffer;
							mBuffer = new byte[mBuffer.length * 2];
							Log.e(tag, String.format("buffer expand to %d", mBuffer.length));
							System.arraycopy(oldBuffer, 0, mBuffer, 0, size);
						}
					}
					mLastReadInterval = System.currentTimeMillis() - lastReadTime;
					msg = obtainMessage(MSG_KEY_HANDLE_STREAM, 0, size);
					msg.sendToTarget();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
				break;
			case MSG_KEY_HANDLE_STREAM: {
				int offset = msg.arg1;
				int size = msg.arg2;
				int nRet = rr.s2f.pushPacket(rr.handle, mBuffer, offset, size);
				if (nRet == 0) {
					byte[] frmType = new byte[1];
					int[] keyFrm = new int[1];
					nRet = 0;
					boolean needHandleStream = rr.needHandleStream();
					while (true) {
						if (rr.keyFrmCount-- > 0) {
							keyFrm[0] = 1;
						}
						int[] length = new int[] { mBuffer.length };
						nRet = rr.s2f.pop(rr.handle, mBuffer, length, frmType, keyFrm);
						if (nRet == 0 && needHandleStream) {
							byte[] data = new byte[length[0]];
							System.arraycopy(mBuffer, 0, data, 0, length[0]);
							Frame aFrame = new Frame(Frame.FRAME_TYPE_VIDEO, data, 0, length[0],
									(byte) keyFrm[0]);
							aFrame.mFrameIdx = rr.count++;
							mFrames.offer(aFrame);
						} else if (nRet == H264S2F.ERROR_TOO_SMALL_BUFFER) {
							Log.e(tag, String.format("popPackage error! code = %d", nRet));
							mBuffer = new byte[mBuffer.length * 2];
							Log.e(tag, String.format("buffer expand to %d", mBuffer.length));
							break;
						} else if (nRet == H264S2F.ERROR_WOULD_BLOCK) {
							break;
						}
					}
					if (!mFrames.isEmpty()) {
						rr.frmInterval = mLastReadInterval / mFrames.size();
						Boolean firstFramePumpSuccess = null;
						while (!mFrames.isEmpty()) {
							Frame frame = mFrames.poll();
							// rr.lastTmstamp += (rr.frmInterval == 0 ? 50 :
							// rr.frmInterval);
							rr.lastTmstamp += 1000l / rr.mFrameRate;
							frame.timeStamp = rr.lastTmstamp;
							Buffer byteBuffer = null;
							if (rr.dc != null) {
								if (firstFramePumpSuccess == null) {
									BufferWithStatus bws = DCAssist.pumpFrame2DC(frame, rr.dc,
											rr.getDCFrameCapacity());
									firstFramePumpSuccess = bws.status == BufferWithStatus.SUCCESS;
									byteBuffer = bws.buffer;
								} else if (firstFramePumpSuccess) {
									// 第一帧成功了，后面强制pump；
									BufferWithStatus bws = DCAssist.forcePumpFrame2DC(frame, rr.dc);
									byteBuffer = bws.buffer;
								} else {
									// 第一帧未成功，后面全部丢弃
									Log.e(tag, "first frame failed,throw all!");
								}
							}
							if (rr.streamWriter != null) {
								if (!rr.isFirstKeyfrmFethced) {
									rr.isFirstKeyfrmFethced = frame.keyFrmFlg == 1;
								}
								if (rr.isFirstKeyfrmFethced) {
									if (byteBuffer == null) {
										byteBuffer = DCAssist.buildFrame(frame);
									}
									byte[] data = ((ByteBuffer) byteBuffer).array();
									if (rr.streamWriter != null) {
										nRet = rr.streamWriter.pumpFrame(
												StreamWriter.FRAME_TYPE_IV, data,
												DCAssist.CHANNEL_HEAD_LENGTH, data.length
														- DCAssist.CHANNEL_HEAD_LENGTH);
										if (nRet != 0 && rr.mHandler != null) {
											msg = rr.mHandler.obtainMessage(
													MPUHandler.MSG_RECORD_ERROR, rr.streamWriter);
											msg.arg1 = nRet;
											msg.sendToTarget();
											rr.streamWriter = null;
										}
									}
								}
							}
						}
					}
				} else {
					Log.e(tag, String.format("pushPackage error! code = %d", nRet));
				}
				msg = obtainMessage(MSG_KEY_READ_STREAM);
				msg.sendToTarget();
			}
				break;
			default:
				break;
			}
		}

		public void quit() {
			mRecordRunnableRef.clear();
			getLooper().quit();
		}
	}

	RecordRunnable() {
	}

	public static final int ERROR_GET_OUTPUTSTREAM = 1000;
	public static final int ERROR_SUCCESS = 0;
	public static final int ERROR_READ_BUFFER = 1001;
	private static final String tag = "RecorderRunnable";

	private MediaRecorder recorder;
	private LocalSocket receiver;
	LocalServerSocket lss;
	private LocalSocket sender;
	private int handle;
	final H264S2F s2f = H264S2F.singleton();
	private long beginTime;
	private long lastTmstamp = beginTime;
	private long frmInterval = 0;// 帧的耗时间隔
	private RecordHandler mRecordHandler;
	private int mDefaultBufferSize = 50 * 1024;

	@Override
	public void run() {
		beginTime = System.currentTimeMillis();
		Looper.prepare();
		Log.e(tag, "Recorder begin");
		mDefaultBufferSize = mSize.width * mSize.height;
		mRecordHandler = new RecordHandler(this);
		mRecordHandler.sendEmptyMessage(RecordHandler.MSG_KEY_READ_STREAM);
		Looper.loop();
		Log.e(tag, "Recorder end");
	}

	@Override
	public void onError(MediaRecorder mr, int what, int extra) {
		Log.e(tag, "error ocurs! " + what + ", " + extra);
	}

	@Override
	public void onInfo(MediaRecorder mr, int what, int extra) {
		Log.d(tag, "info ocurs! " + what + ", " + extra);
	}

	@Override
	public int create(SurfaceView view, Camera camera) {
		String model = ACommonMethod.getModel();
		final PhoneType pt = PhoneType.fromString(model);
		Assert.assertTrue(pt.isRecorder());
		int nRet = initSenderReceiver();
		Assert.assertEquals(0, nRet);
		nRet = initCamera(view, camera);
		if (nRet != 0) {
			return nRet;
		}
		final int type = pt.type();
		handle = s2f.create(type, mSize.width, mSize.height);
		Assert.assertNotSame(0, handle);
		new Thread(this, "Record").start();
		return 0;
	}

	public void close() {
		super.close();
		final Camera c = mCamera;
		if (recorder != null) {
			recorder.stop();
			recorder.release();
			recorder = null;
			if (c != null) {
				try {
					c.reconnect();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		mCamera = null;
		mRecordHandler.quit();
		deleteSenderReceiver();
		s2f.close(handle);
	}

	private int initSenderReceiver() {
		receiver = new LocalSocket();
		try {
			lss = new LocalServerSocket(tag);
			receiver.connect(lss.getLocalSocketAddress());
			sender = new LocalSocket();
			sender = lss.accept();
		} catch (IOException e) {
			e.printStackTrace();
			try {
				lss.close();
				receiver.close();
				receiver = null;
				sender.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			return E2;
		}
		return 0;
	}

	private void deleteSenderReceiver() {
		if (receiver != null) {
			try {
				sender.close();
				receiver.close();
				lss.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			receiver = null;
			lss = null;
			sender = null;
		}
	}

	public int initCamera(SurfaceView view, Camera camera) {
		if (camera == null) {
			// stopCamera();
			return E0;
		}
		mCamera = camera;
		SurfaceHolder holder = view.getHolder();
		if (recorder == null) {
			try {
				recorder = new MediaRecorder();
				recorder.setOnInfoListener(this);
				recorder.setOnErrorListener(this);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
					Parameters p = mCamera.getParameters();
					mCamera.setParameters(p);
				}
				mCamera.unlock();
				recorder.setCamera(mCamera);
				recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
				setRecordParams();
				recorder.setPreviewDisplay(holder.getSurface());
			} catch (Exception e) {
				e.printStackTrace();
				recorder = null;
				deleteSenderReceiver();
				mCamera = null;
				return E6;
			}
			recorder.setOutputFile(sender.getFileDescriptor());
			// recorder.setOutputFile("sdcard/test.3gp");
			try {
				recorder.prepare();
				recorder.start();
			} catch (Exception e) {
				e.printStackTrace();
				recorder.release();
				recorder = null;
				deleteSenderReceiver();
				mCamera = null;
				return E3;
			}
			startKeyFrame(1);
		} else {
			// recorder.reset();
			// recorder.setCamera(mCamera);
			// try {
			// // recorder.prepare();
			// recorder.start();
			// } catch (Exception e) {
			// // TODO Auto-generated catch block
			// e.printStackTrace();
			// recorder.release();
			// recorder = null;
			// mCamera = null;
			// deleteSenderReceiver();
			// return E5;
			// }
		}
		return 0;
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setRecordParams() {
		String model = ACommonMethod.getModel();
		final PhoneType pt = PhoneType.fromString(model);

		if (mSize.width == 720 && pt == PhoneType.PHONE_GT_I8530) { // 定制的GT-I8530、GT-I9100
			recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
			recorder.setVideoFrameRate(20);
			mFrameRate = 20;
			recorder.setVideoSize(mSize.width, mSize.height);
			recorder.setVideoEncodingBitRate((int) (mSize.width * mSize.height * 3.5));
			recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
		} else {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
				CamcorderProfile profile = CamcorderProfile
						.get(mSize.height == 480 ? CamcorderProfile.QUALITY_480P
								: CamcorderProfile.QUALITY_QVGA);
				mFrameRate = profile.videoFrameRate;
			} else {
				mFrameRate = 20;
			}
			recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
			recorder.setVideoSize(mSize.width, mSize.height);
			recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
		}
	}

	@Override
	public void stopCamera() {
		if (mCamera != null) {
			mCamera = null;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.crearo.mpu.sdk.VideoRunnable#setFrameRate(int)
	 */
	@Override
	public void setFrameRate(int frameRate) {
		// TODO Auto-generated method stub
		Assert.assertTrue(false);
	}

	@Override
	public void autoFocus(View view) {
		Assert.assertTrue(false);
	}
}
