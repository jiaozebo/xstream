package com.crearo.mpu.sdk;

import java.nio.ByteBuffer;

import junit.framework.Assert;
import vastorager.StreamWriter;
import android.util.AudioEncoderRunnable;
import android.util.Log;
import c7.BufferWithStatus;
import c7.DC7;
import c7.DCAssist;
import c7.Frame;

import com.crearo.audio.Speex;

/**
 * 输入音频处理线程，编码、打包、发送
 * 
 * @author John
 * @version 1.0
 * @date 2011-11-22
 */
public class AudioRunnable extends AudioEncoderRunnable {
	private static final String tag = "AudioRunnable";

	private static AudioRunnable _audioRunnable;

	private DC7 mDc;
	/**
	 * 发送出去的帧数目
	 */
	private int count;
	private StreamWriter mStreamWriter;

	private AudioRunnable() {
	}

	public static AudioRunnable singleton() {
		if (_audioRunnable == null) {
			_audioRunnable = new AudioRunnable();
		}
		return _audioRunnable;
	}

	public DC7 getDc() {
		return mDc;
	}

	public AudioRunnable setDc(DC7 dc) {
		this.mDc = dc;
		return this;
	}

	public int start() {
		final int result = super.start();
		if (result == 0) {
			count = 1;
		}
		return result;
	}

	public void stop() {
		super.stop();
		mDc = null;
		count = 0;
		_audioRunnable = null;
	}

	public void setRecorder(StreamWriter streamWriter) {
		this.mStreamWriter = streamWriter;
	}

	public StreamWriter getStreamWriter() {
		return mStreamWriter;
	}

	public void setEchoCanceller(Speex speex) {
		// mAudioReader.setEchoCanceller(speex);
	}

	@Override
	protected void handleAMRFrame(Frame frame) {
		if (mEncodeCallback != null) {
			mEncodeCallback.onEncodeFetched(frame);
		}
		BufferWithStatus bws = null;
		ByteBuffer bf = null;
		if (mDc != null) {
			frame.mFrameIdx = count++;
			try {
				bws = DCAssist.pumpFrame2DC(frame, mDc, true);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} else {
			frame.mFrameIdx = count++;
			bf = DCAssist.buildFrame(frame);
		}
		if (mStreamWriter != null) {
			Assert.assertTrue(bf != null || bws != null);
			if (bf == null) {
				bf = bws.buffer;
			}
			int nRet = mStreamWriter.pumpFrame(StreamWriter.FRAME_TYPE_IA, bf.array(),
					DCAssist.CHANNEL_HEAD_LENGTH, bf.capacity() - DCAssist.CHANNEL_HEAD_LENGTH);
			if (nRet != 0) {
				Log.e(tag, String.format("pump ia frame error!code=%d", nRet));
			}
		}
	}

	public interface EncodeCallback {
		public void onEncodeFetched(Frame frame);
	}

	private EncodeCallback mEncodeCallback;

	public void setEncoderCallback(EncodeCallback callback) {
		this.mEncodeCallback = callback;
	}
}
