package com.crearo.mpu.sdk.client;

import com.crearo.mpu.sdk.MPUHandler.RendCallback;

/**
 * 
 * MPU的回调函数，给上层提供各种流的状态
 * 
 */
public interface MPUCallback {

	/**
	 * 预览视频
	 */
	int TYPE_IV = 0;
	/**
	 * 播放声音
	 */
	int TYPE_PLAY_AUDIO = 1;
	/**
	 * 喊话
	 */
	int TYPE_CALL = 2;
	/**
	 * 对讲
	 */
	int TYPE_TALK = 3;

	int STT_START = RendCallback.STT_REND_BEGIN;
	int STT_STOP = RendCallback.STT_REND_END;

	public void onStatusFetched(int type, int status);

}