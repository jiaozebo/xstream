package com.john.xstream;

import android.os.Process;
import c7.DC7;

import com.crearo.mpu.sdk.PreviewRunnable;

public class MyPreviewRunnable extends PreviewRunnable {

	public MyPreviewRunnable() {
		super();
		setVideoDC(new DC7());
	}

	@Override
	public void run() {
		Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
		super.run();
	}

}
