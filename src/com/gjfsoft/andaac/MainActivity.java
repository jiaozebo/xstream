package com.gjfsoft.andaac;

import java.io.File;
import java.nio.ByteBuffer;

import util.CommonMethod;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.os.Environment;
import android.util.Log;
import c7.Frame;

import com.crearo.mpu.sdk.VideoRunnable.FrameCallback;

public class MainActivity extends Thread {
	private static final int _4096 = 4096;
	private boolean mWorking;

	FrameCallback mCallback;

	public MainActivity(FrameCallback callback) {
		mCallback = callback;
	}

	public void run() {
		int Fr = 32000;
		int CC = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
		int audioBuffer = AudioRecord.getMinBufferSize(Fr, CC, AudioFormat.ENCODING_PCM_16BIT);
		int minBufferSize = 4096;
		if (minBufferSize < audioBuffer) {
			minBufferSize = audioBuffer;
		}
		AudioRecord ar = new AudioRecord(AudioSource.DEFAULT, Fr, CC,
				AudioFormat.ENCODING_PCM_16BIT, minBufferSize);
		ar.startRecording();
		minBufferSize = 4096;
		long encHandle = NativeEncodeOpen(2, Fr, 2, 32000 * 2);
		byte[] readBuf = new byte[minBufferSize];
		ByteBuffer buffer = ByteBuffer.allocate(minBufferSize + 17);
		int read = 0;
		while (mWorking) {
			int cread = ar.read(readBuf, read, readBuf.length - read);
			if (cread < 0)
				break;
			read += cread;
			if (read < readBuf.length) {
				continue;
			}
			read = 0;
			byte[] outBuf = new byte[minBufferSize];
			int result = NativeEncodeFrame(encHandle, readBuf, readBuf.length / 2, outBuf,
					minBufferSize);
			if (result < 0) {
				continue;
			}
			buffer.clear();
			buffer.position(17);
			buffer.put(outBuf, 0, result);
			buffer.flip();
			Frame frame = new Frame();
			frame.timeStamp = (int) System.currentTimeMillis();
			frame.data = buffer.array();
			frame.offset = 17;
			frame.length = buffer.remaining() - 17;
			frame.keyFrmFlg = 1;
			frame.type = Frame.FRAME_TYPE_AUDIO;
			if (mCallback != null) {
				mCallback.onFrameFatched(frame);
			}

			// DCAssist.pumpFrame2DC(frame, loopCount, dc, -1);

		}
		ar.release();
		// dc.close();
	}

	public void run2() {
		int Fr = 32000;
		int CC = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
		final int minBufferSize = AudioRecord.getMinBufferSize(Fr, CC,
				AudioFormat.ENCODING_PCM_16BIT);
		AudioRecord ar = new AudioRecord(AudioSource.DEFAULT, Fr, CC,
				AudioFormat.ENCODING_PCM_16BIT, minBufferSize);
		ar.startRecording();

		byte[] readBuf = new byte[minBufferSize + 17];
		int read = 17;
		while (mWorking) {
			int cread = ar.read(readBuf, read, readBuf.length - read);
			if (cread < 0)
				break;
			read += cread;
			if (read < readBuf.length) {
				continue;
			}
			read = 17;
			Frame frame = new Frame();
			frame.timeStamp = (int) System.currentTimeMillis();
			frame.data = readBuf;
			frame.offset = 17;
			frame.length = readBuf.length - 17;
			frame.keyFrmFlg = 1;
			frame.type = Frame.FRAME_TYPE_AUDIO;
			if (mCallback != null) {
				mCallback.onFrameFatched(frame);
			}
			// DCAssist.pumpFrame2DC(frame, loopCount, dc, -1);

		}
		ar.release();
		// dc.close();
	}

	@Override
	public synchronized void start() {
		mWorking = true;
		super.start();
	}

	public void terminate() throws InterruptedException {
		mWorking = false;
		interrupt();
		join();
	}

	public native long NativeEncodeOpen(int aac_type, int samplerate, int channels, int bit_rate);

	public native int NativeEncodeFrame(long handle, byte[] inbuf, int inlen, byte[] outbuf,
			int outlen);

	public native int NativeEncodeClose(long handle);

	public native long NativeDecodeOpen(int aac_type, int samplerate, int channels);

	public native int NativeDecodeFrame(long handle, byte[] inbuf, int inlen, byte[] outbuf,
			int outlen);

	public native int NativeDecodeClose(long handle);

	static {
		System.loadLibrary("andaac");
	}
}
