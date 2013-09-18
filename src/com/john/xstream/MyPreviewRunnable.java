package com.john.xstream;

import android.os.Process;
import android.view.SurfaceView;
import c7.DC7;

import com.crearo.mpu.sdk.PreviewThread;

public class MyPreviewRunnable extends PreviewThread {

	public MyPreviewRunnable() {
		super();
		setVideoDC(new DC7());
		USE_HW_ENCODE = false;
	}

	@Override
	public void run() {
		Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
		super.run();
	}

	/* (non-Javadoc)
	 * @see com.crearo.mpu.sdk.CameraThread#startThread()
	 */
	@Override
	protected void startThread() {
		// TODO Auto-generated method stub
		super.startThread();
	}

	/* (non-Javadoc)
	 * @see com.crearo.mpu.sdk.CameraThread#startCamera(android.view.SurfaceView, int)
	 */
	@Override
	protected void startCamera(SurfaceView sf, int cameraId) {
		// TODO Auto-generated method stub
		super.startCamera(sf, cameraId);
	}

	/* (non-Javadoc)
	 * @see com.crearo.mpu.sdk.CameraThread#resumeCamera()
	 */
	@Override
	protected void resumeCamera() {
		// TODO Auto-generated method stub
		super.resumeCamera();
	}

	/* (non-Javadoc)
	 * @see com.crearo.mpu.sdk.CameraThread#pauseCamera()
	 */
	@Override
	protected void pauseCamera() {
		// TODO Auto-generated method stub
		super.pauseCamera();
	}

	/* (non-Javadoc)
	 * @see com.crearo.mpu.sdk.CameraThread#switchCamera(int)
	 */
	@Override
	protected void switchCamera(int newId) {
		// TODO Auto-generated method stub
		super.switchCamera(newId);
	}
 
}
