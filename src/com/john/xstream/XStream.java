package com.john.xstream;

import java.lang.ref.WeakReference;

import junit.framework.Assert;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

public class XStream extends Handler {

	/**
	 * #define CMD_CMD_QUIT 0xe450 #define CMD_DATA_QUIT 0xe451 #define
	 * CMD_CMD_MESSAGE 0xe452 #define CMD_DATA_MESSAGE 0xe453 #define
	 * CMD_CMD_SEND 0xe4ff #define CMD_DATA_SEND 0xe4ff
	 * 
	 * #define ARG_CMD_KEEPALIVE 0x0e #define ARG_DATA_REQUEST_FRAME 0x05
	 * #define ARG_DATA_REQUEST_END_FRAME 0x5B
	 */
	private static final String TAG = "XStream";
	private static final int CMD_CMD_QUIT = 0xe450;
	private static final int CMD_DATA_QUIT = 0xe451;
	private static final int CMD_KEEPALIVE = 0x0E;
	// 表示发送了一帧数据
	private static final int CMD_CMD_SEND = 0xe4ff;
	private static final int CMD_DATA_SEND = 0xe500;

	private static final int CMD_CMD_MESSAGE = 0xe452;
	private static final int CMD_DATA_MESSAGE = 0xe453;

	private static final int ARG_DATA_REQUEST_FRAME = 0x05;
	private static final int ARG_DATA_REQUEST_END_FRAME = 0x5B;
	private int mHandle = 0;
	private MainActivity mActivity;
	static {
		System.loadLibrary("XStreamClient");
	}

	public XStream() {
		super();
		mHandle = native_setup(new WeakReference<XStream>(this));
	}

	public int reg(String addr, int port) {
		Assert.assertNotSame(0, mHandle);
		return reg2Server(mHandle, addr, port);
	}

	public void unreg() {
		Assert.assertEquals(Thread.currentThread(), Looper.getMainLooper().getThread());

		final int handle = mHandle;
		mHandle = 0;
		if (handle == 0) {
			return;
		}
		if (mActivity != null) {
			mActivity.onStopStream();
		}
		unreg(handle);
	}

	public int pumpFrame(byte[] frame, int... args) {
		return pumpFrame(mHandle, frame, args);
	}

	private native int reg2Server(int handle, String addr, int port);

	private native int pumpFrame(int handle, byte[] frame, int... args);

	private native void unreg(int handle);

	private native final int native_setup(Object weak_this);

	private static void sendMessageFromNative(Object weak_this, int what, int arg1, int arg2) {
		@SuppressWarnings("unchecked")
		WeakReference<XStream> reference = (WeakReference<XStream>) weak_this;
		XStream thiz = reference.get();
		if (thiz == null) {
			Log.e(TAG, "the object is already released!");
			return;
		} else {
		}
		if (thiz.mHandle == 0) {
			Log.e(TAG, "the object is already unreg!");
			return;
		}
		thiz.obtainMessage(what, arg1, arg2).sendToTarget();
	}

	@Override
	public void handleMessage(Message msg) {
		super.handleMessage(msg);
		Log.d(TAG, msg.toString());
		if (msg.what == CMD_CMD_QUIT) {
			Log.e(TAG, "cmd channel quit!");
			unreg();
			mActivity.onChannelQuit(CMD_CMD_QUIT);
		} else if (msg.what == CMD_DATA_QUIT) {
			Log.e(TAG, "data channel quit!");
			unreg();
			mActivity.onChannelQuit(CMD_DATA_QUIT);
		} else if (msg.what == CMD_CMD_MESSAGE) {
			if (msg.arg1 == CMD_KEEPALIVE) {
				Log.i(TAG, "cmd keepalive!");
			} else {
				Log.i(TAG, "cmd " + msg.arg1);
			}
		} else if (msg.what == CMD_DATA_MESSAGE) {
			if (ARG_DATA_REQUEST_FRAME == msg.arg1) {
				mActivity.onStartStream(msg.arg1, msg.arg2);
			} else if (ARG_DATA_REQUEST_END_FRAME == msg.arg1) {
				mActivity.onStopStream();
			} else if (0x0D == msg.arg1) {
				// key frm
				mActivity.onRequestKeyFrm();
			} else {
				Log.e(TAG, "data recvd " + msg.arg1);
			}
		}
	}

	public void setStartStreamCallback(MainActivity activity) {
		this.mActivity = activity;
	}

}
