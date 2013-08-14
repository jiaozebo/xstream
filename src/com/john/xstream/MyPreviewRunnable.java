package com.john.xstream;

import c7.DC7;

import com.crearo.mpu.sdk.PreviewRunnable;

public class MyPreviewRunnable extends PreviewRunnable {

	public MyPreviewRunnable() {
		super();
		setVideoDC(new DC7());
	}

	@Override
	public void run() {
		super.run();
	}

	@Override
	public void stopCamera() {
		if (mCamera != null) {
			mCamera.stopPreview();
			mCamera = null;
		}
	}

}
