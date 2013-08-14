package com.crearo.mpu.sdk;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import junit.framework.Assert;
import util.CommonMethod;
import vastorager.StreamWriter;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Message;
import android.util.CRAudioTrack;
import android.util.Log;
import audio.decoder.CRAudioDecoder;
import c7.DC7;
import c7.DCAssist;
import c7.Frame;

/**
 * 处理输出音频线程，拼包，解码，播放
 * 
 * @author John
 * 
 */
public class OAudioRunnable implements Runnable {
	private static final String tag = "OAudioRunnable";
	private static final int ERROR_CREATE_DECODER = 0x6000;
	public static final int ERROR_SIZE_UNSUPPORT = 0x6001;
	public static final int ERROR_INIT_AUDIO_TRACK = 0x6002;
	public static final int ERROR_PLAY = 0x6003;
	private static final int ERROR_ALREADY_START = 0x6004;
	private static OAudioRunnable _audioRunnable;
	private CRAudioDecoder decoder;
	private CRAudioTrack mAt;
	public BlockingQueue<Frame> frames = null;
	int hDecoder = 0;
	private DC7 dc;
	// private AudioWriter audioWriter;
	private StreamWriter mStreamWrither;
	/**
	 * 表示耳机是否插入
	 */
	private boolean mEarPhoneIn = true;

	class PeekValue {
		public short mValue;
		public long mCurrentTime;
	}

	public void setEarphoneInsertion(boolean isInserted) {
		mEarPhoneIn = isInserted;
	}

	/**
	 * 相对于IV的时间戳差值，从DC接收到的OA数据的时间戳减去该值，为修正过的时间戳
	 */
	private long mTimeStampOffset;
	private Thread mCurrentThread;
	private byte[] mBufferReuse = null;
	private Handler mHandler;
	// private final PeekValue mPeekValue = new PeekValue();

	/**
	 * 写入数据标志位，0表示不写,否则表示写
	 */
	private volatile int mWriteFlg = 1;

	private OAudioRunnable() {
	}

	public void setHandler(Handler handler) {
		mHandler = handler;
	}

	private int create() {
		if (mCurrentThread != null) {
			return ERROR_ALREADY_START;
		}
		int sampleRateInHz = 8000;
		int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO;
		int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
		int bfSize = AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
		if (bfSize == AudioTrack.ERROR_BAD_VALUE || bfSize == AudioTrack.ERROR) {
			Log.e(tag, "bufSize unsuport:" + bfSize);
			return ERROR_SIZE_UNSUPPORT;
		}
		try {
			mAt = new CRAudioTrack(AudioManager.STREAM_MUSIC, sampleRateInHz, channelConfig,
					audioFormat, bfSize, AudioTrack.MODE_STREAM);
		} catch (Exception e) {
			return ERROR_INIT_AUDIO_TRACK;
		}
		Assert.assertEquals(0, hDecoder);
		decoder = new CRAudioDecoder();
		hDecoder = decoder.create();
		if (hDecoder == 0) {
			return ERROR_CREATE_DECODER;
		}
		mBufferReuse = new byte[10 * 1024];
		frames = new ArrayBlockingQueue<Frame>(10);
		return 0;
	}

	@Override
	public void run() {
		Assert.assertEquals("use start to start thread!", mCurrentThread, Thread.currentThread());
		mAt.play();
		while (mCurrentThread != null) {
			Frame data = null;
			try {
				data = frames.take();
			} catch (InterruptedException e) {
				e.printStackTrace();
				Thread.currentThread().interrupt();
				continue;
			}
			byte[] frame = new byte[data.length];
			System.arraycopy(data.data, data.offset, frame, 0, data.length);
			int[] outLen = new int[1];
			if (data.length * 2 > mBufferReuse.length) {
				mBufferReuse = new byte[data.length * 10];
			}
			outLen[0] = mBufferReuse.length;
			int nRet = decoder.decode(hDecoder, frame, mBufferReuse, outLen);
			if (nRet != 0) {
				Log.e(tag, "decoder decode error! code:" + nRet);
				continue;
			} else {
				if (mDecodeCallback != null) {
					mDecodeCallback.onDecodeFetched(mBufferReuse, 0, outLen[0]);
				}
				nRet = mAt.write(mBufferReuse, 0, outLen[0]);
				if (nRet == AudioTrack.ERROR_BAD_VALUE
						|| nRet == AudioTrack.ERROR_INVALID_OPERATION) {
					if (mHandler != null) {
						Message msg = mHandler.obtainMessage();
						msg.what = MPUHandler.MSG_REND_ERROR;
						msg.arg1 = nRet;
						msg.obj = dc;
						msg.sendToTarget();
						synchronized (this) {
							try {
								wait();
							} catch (InterruptedException e) {
								e.printStackTrace();
								Thread.currentThread().interrupt();
							}
						}
					} else {
						// 直接退出线程即可。
						break;
					}
				}
			}
		}
		mAt.release();
		mAt = null;
	}

	public int start() {
		int result = create();
		if (result == 0) {
			Assert.assertNull("thread is already start!", mCurrentThread);
			mCurrentThread = new Thread(this, "OAudioRunnable");
			mCurrentThread.start();
		}
		return result;
	}

	public void stop() {
		if (mCurrentThread != null) {
			final Thread theThread = mCurrentThread;
			mCurrentThread = null;
			theThread.interrupt();
			try {
				theThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		if (decoder != null) {
			decoder.close(hDecoder);
			hDecoder = 0;
			decoder = null;
		}
		if (frames != null) {
			frames.clear();
			frames = null;
		}
		mBufferReuse = null;
		dc = null;
		_audioRunnable = null;
	}

	public boolean isActive() {
		return mCurrentThread != null;
	}

	public static OAudioRunnable singleton() {
		if (_audioRunnable == null) {
			_audioRunnable = new OAudioRunnable();
		}
		return _audioRunnable;
	}

	public DC7 getDC() {
		return dc;
	}

	public OAudioRunnable setDc(DC7 dc) {
		this.dc = dc;
		return this;
	}

	/**
	 * 
	 * @param data
	 *            从存储帧头开始算起
	 * @param offset
	 * @param length
	 */
	public void pumpAudioFrame(byte[] data, int offset, int length) {
		if (mStreamWrither != null) {
			synchronized (this) {
				// 由于OA的时间戳是从DC获取来的，与IA、IV不一致，因此需要修正。
				// 如果是第一次pump，那么获取与IV之间的偏移值。
				if (mTimeStampOffset == 0) {
					long tmOff = mStreamWrither.getLastTimeStamp(StreamWriter.FRAME_TYPE_IV);

					if (tmOff != 0) {
						long tm = CommonMethod.bytesToUnsignedInt(data, offset + 4, true);
						mTimeStampOffset = tm - tmOff;
					}
				}
				if (mTimeStampOffset != 0) {
					long timeStamp = CommonMethod.bytesToUnsignedInt(data, offset + 4, true);
					long fixedTimeStamp = timeStamp - mTimeStampOffset;
					CommonMethod.int2Bytes4((int) fixedTimeStamp, true, data, offset + 4);
					int nRet = mStreamWrither.pumpFrame(StreamWriter.FRAME_TYPE_OA, data, offset,
							length);

					Log.d(tag, String.format("pumpOAFrame return %d", nRet));
				}
			}
		}
		Frame frame = new Frame(data, offset + DCAssist.STORAGE_HEAD_LENGTH, length
				- +DCAssist.STORAGE_HEAD_LENGTH);
		frame.timeStamp = System.currentTimeMillis();
		boolean bRet = false;
		do {
			if (mWriteFlg != 0) {
				bRet = frames.offer(frame);
				if (!bRet) {
					frames.poll();
				}
			} else {
				break;
			}
		} while (!bRet);
	}

	public StreamWriter getStreamWriter() {
		return mStreamWrither;
	}

	public void setRecorder(StreamWriter sw) {
		if (sw == null && mStreamWrither != null) {
			synchronized (this) {
				mStreamWrither = sw;
			}
		} else {
			mStreamWrither = sw;
		}
		mTimeStampOffset = 0l;
	}

	public void pause() {
		mWriteFlg = 0;
		frames.clear();
	}

	public void resume() {
		mWriteFlg = 1;
	}

	public interface DecodeCallback {
		public void onDecodeFetched(byte[] data, int offset, int length);
	}

	private DecodeCallback mDecodeCallback;

	public void setDecoderCallback(DecodeCallback callback) {
		this.mDecodeCallback = callback;
	}
}
