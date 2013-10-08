package com.john.xstream;

import junit.framework.Assert;
import android.os.Handler;

/**
 * 定时器
 * 
 * @author John
 * 
 */
public abstract class CRTimer implements Runnable {

	private int mInterval = 5000;
	private Handler mHandler;
	private boolean mAlive = false;

	/**
	 * 应该在handle线程中调用
	 * 
	 * @param mInterval
	 *            定时间隔
	 */
	public CRTimer(int mInterval) {
		this();
		this.mInterval = mInterval;
	}

	/**
	 * 应该在handle线程中调用
	 */
	public CRTimer() {
		super();
		mHandler = new Handler();
	}

	public void setInterval(int interval) {
		mInterval = interval;
	}

	public int getInterval() {
		return mInterval;
	}

	public void start() {
		startDelay(0);
	}

	public void stop() {
		mAlive = false;
		mHandler.removeCallbacks(this);
	}

	public void startDelay(int delay) {
		Assert.assertFalse("CRTimer already start!", mAlive);
		mAlive = true;
		mHandler.postDelayed(this, delay);
	}

	/**
	 * 立即执行一次
	 * <p>
	 * 执行过后，时间点重新从当前时间开始
	 */
	public void excuteNow() {
		stop();
		start();
	}

	@Override
	public void run() {
		mHandler.postDelayed(this, mInterval);
		onTimer();
	}

	protected abstract void onTimer();
}
