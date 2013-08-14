package com.example.mpurecorddemo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import android.app.Activity;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnErrorListener;
import android.media.MediaRecorder.OnInfoListener;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Bundle;
import android.view.Menu;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

public class MainActivity extends Activity implements OnClickListener, OnInfoListener,
		OnErrorListener, Callback {

	private static final String TAG = "MPURecordDemo";
	Button mRecord3GP, mRecordTS;
	Spinner mResolutions;
	// Camera mCamera;
	private MediaRecorder mRecorder;
	private LocalSocket mReceiver;
	private LocalSocket sender;
	private LocalServerSocket mLocalServerSocket;
	private Camera mCamera;
	private SurfaceView vSurfaceView;
	private SurfaceHolder sv;
	private Thread mReceivingThread;

	class MySize extends Size {
		public MySize(Camera camera, Size size) {
			camera.super(size.width, size.height);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return String.format("%d*%d", width, height);
		}

	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		mRecord3GP = (Button) findViewById(R.id.bt_record_3gp);
		mRecordTS = (Button) findViewById(R.id.bt_record_ts);
		mRecord3GP.setOnClickListener(this);
		mRecordTS.setOnClickListener(this);
		mResolutions = (Spinner) findViewById(R.id.sp_resolutions);
		mCamera = Camera.open();

		List<Size> resolutions = mCamera.getParameters().getSupportedPreviewSizes();
		final int size = resolutions.size();
		for (int i = 0; i < size; i++) {
			Camera.Size sz = resolutions.get(i);
			MySize ms = new MySize(mCamera, sz);
			resolutions.set(i, ms);
		}
		mResolutions.setAdapter(new ArrayAdapter<Size>(this, R.layout.spinner_item, R.id.tv_name,
				resolutions));
		vSurfaceView = (SurfaceView) findViewById(R.id.sv_view);
		sv = vSurfaceView.getHolder();
		sv.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		sv.addCallback(this);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public void onClick(View v) {
		if (mRecord3GP == v) {
			startOrStop((SurfaceView) findViewById(R.id.sv_view), true);
		} else if (mRecordTS == v) {
			startOrStop((SurfaceView) findViewById(R.id.sv_view), false);
		}
	}

	private int initSenderReceiver() {
		mReceiver = new LocalSocket();
		try {
			mLocalServerSocket = new LocalServerSocket(TAG);
			mReceiver.connect(mLocalServerSocket.getLocalSocketAddress());
			sender = new LocalSocket();
			sender = mLocalServerSocket.accept();
			mReceivingThread = new Thread() {
				private byte[] mStreamBuffer = new byte[50 * 1024];
				ByteBuffer lengthBuffer = ByteBuffer.allocate(4);

				/*
				 * (non-Javadoc)
				 * 
				 * @see java.lang.Thread#run()
				 */
				@Override
				public void run() {
					super.run();
					InputStream is = null;
					int[] length = new int[1];
					length[0] = 50 * 1024;
					FileOutputStream fos = null;
					try {
						is = mReceiver.getInputStream();
						fos = new FileOutputStream("/sdcard/test.ts");
						int size = 0;
						while (true) {
							final int request = mStreamBuffer.length - size;
							int currentSize = is.read(mStreamBuffer, size, request);
							if (currentSize == -1) {
								break;
							}
							size += currentSize;
							// 如果未读满,说明一包数据读完了
							if (currentSize < request) {
								lengthBuffer.putInt(0, size);
								fos.write(lengthBuffer.array());
								fos.write(mStreamBuffer, 0, size);
								size = 0;
							} else {
								byte[] oldBuffer = mStreamBuffer;
								mStreamBuffer = new byte[mStreamBuffer.length * 2];
								System.arraycopy(oldBuffer, 0, mStreamBuffer, 0, size);
							}
						}
					} catch (IOException e) {
						e.printStackTrace();
					} finally {
						if (fos != null)
							try {
								fos.close();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						if (is != null) {
							try {
								is.close();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}
				}

			};
			mReceivingThread.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}

	private void deleteSenderReceiver() {
		if (mReceiver != null) {
			try {
				sender.close();
				mReceiver.close();
				mLocalServerSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			mReceiver = null;
			mLocalServerSocket = null;
			sender = null;
		}
	}

	public int startOrStop(SurfaceView view, boolean record3GP) {
		if (mRecorder == null) {
			try {
				mRecorder = new MediaRecorder();// 创建mediarecorder对象
				mCamera.unlock();
				mRecorder.setCamera(mCamera);
				// 设置录制视频源为Camera(相机)
				mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
				mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
				MySize size = (MySize) mResolutions.getSelectedItem();
				mRecorder.setVideoSize(size.width, size.height);
				mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
				mRecorder.setVideoFrameRate(20);
				mRecorder.setVideoEncodingBitRate(400 * 1000);
				mRecorder.setPreviewDisplay(sv.getSurface());
				// 设置视频文件输出的路径
			} catch (Exception e) {
				e.printStackTrace();
				mRecorder = null;
				return -1;
			}
			if (record3GP) {
				mRecorder.setOutputFile("sdcard/test.3gp");
			} else {
				initSenderReceiver();
				mRecorder.setOutputFile(sender.getFileDescriptor());

			}

			// recorder.setOutputFile();
			try {
				mRecorder.prepare();
				mRecorder.start();
			} catch (Exception e) {
				e.printStackTrace();
				mRecorder.release();
				mRecorder = null;
				return -2;
			}
		} else {
			mRecorder.stop();
			mRecorder.release();
			try {
				mCamera.reconnect();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			mRecorder = null;
			if (!record3GP) {
				deleteSenderReceiver();
			}
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onBackPressed()
	 */
	@Override
	public void onBackPressed() {
		if (mRecorder != null) {
			startOrStop(vSurfaceView, mReceiver == null);
		}
		mCamera.release();
		mCamera = null;
		super.onBackPressed();
	}

	@Override
	public void onError(MediaRecorder mr, int what, int extra) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onInfo(MediaRecorder mr, int what, int extra) {
		// TODO Auto-generated method stub

	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		sv = holder;
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		// TODO Auto-generated method stub

	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		sv = null;
	}
}
