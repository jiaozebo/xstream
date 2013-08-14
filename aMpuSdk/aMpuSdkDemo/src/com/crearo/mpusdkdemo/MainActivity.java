package com.crearo.mpusdkdemo;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.Menu;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import com.crearo.mpu.sdk.client.MPUCallback;
import com.crearo.mpu.sdk.client.MPUEntity;
import com.crearo.mpu.sdk.client.PUInfo;
import com.crearo.mpu.sdk.client.VideoParam;

public class MainActivity extends Activity implements OnClickListener {

	final MPUEntity mEntity = new MPUEntity(this);
	private ProgressDialog mPDlg;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		findViewById(R.id.btn_start).setOnClickListener(this);
		findViewById(R.id.tv_iaudio).setVisibility(View.GONE);
		findViewById(R.id.tv_oaudio).setVisibility(View.GONE);
		findViewById(R.id.tv_video).setVisibility(View.GONE);
		findViewById(R.id.tv_talk).setVisibility(View.GONE);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	@Override
	public void onClick(View v) {
		if (v.getId() == R.id.btn_start) {
			final Button button = (Button) v;
			if (button.getText().equals("启动")) {
				if (mPDlg == null) {
					mPDlg = new ProgressDialog(this);
				}
				mPDlg.setMessage("正在启动服务");
				mPDlg.setCancelable(false);
				mPDlg.show();
				// 注意登录在子线程中进行
				new Thread() {
					@Override
					public void run() {
						super.run();
						PUInfo info = new PUInfo();
						// 使用版本号作为设备的名称
						info.name = String.format("MPUSDK(%s)", MPUEntity.getVersion()) ;
						info.puid = "151123456789123456";
						info.cameraName = "测试摄像头";
						info.mMicName = "测试mic";
						info.mSpeakerName = "测试speaker";
						info.mGPSName = null;// 暂时不支持GPS，在这里设置为null
						final int result1 = mEntity.login("www.crearo.com", 28866, "", info);
						mPDlg.dismiss();
						runOnUiThread(new Runnable() {

							@Override
							public void run() {
								int result = result1;
								if (result == 0) {
									button.setText("终止");
									VideoParam param = new VideoParam();
									param.width = 352;// 视频宽度
									param.height = 288;// 视频高度
									param.quality = 3;// 1~5;

									mEntity.setCallback(new MPUCallback() {

										@Override
										public void onStatusFetched(int type, final int status) {
											int id = 0;
											switch (type) {
											case TYPE_IV:
												id = R.id.tv_video;
												break;
											case TYPE_PLAY_AUDIO:
												id = R.id.tv_iaudio;
												break;
											case TYPE_CALL:
												id = R.id.tv_oaudio;
												break;
											case TYPE_TALK:
												id = R.id.tv_talk;
												break;
											default:
												break;
											}
											if (id != 0) {
												final int fid = id;

												runOnUiThread(new Runnable() {

													@Override
													public void run() {
														if (status == STT_START) {
															findViewById(fid).setVisibility(
																	View.VISIBLE);
														} else {
															findViewById(fid).setVisibility(
																	View.GONE);
														}
													}
												});

											}
										}
									});

									result = mEntity.start(
											(SurfaceView) findViewById(R.id.sv_video_area), param);
									if (result != 0) {
										Toast.makeText(MainActivity.this,
												"启动视频服务未成功！错误码：" + result, Toast.LENGTH_SHORT)
												.show();
									}
								} else {
									// 错误码见 com.crearo.mpu.sdk.client.ErrorCode
									// 里的定义
									Toast.makeText(MainActivity.this, "登录服务器未成功！错误码：" + result,
											Toast.LENGTH_SHORT).show();
								}
							}
						});
					}

				}.start();

			} else {
				mEntity.logout();
				findViewById(R.id.tv_iaudio).setVisibility(View.GONE);
				findViewById(R.id.tv_oaudio).setVisibility(View.GONE);
				findViewById(R.id.tv_video).setVisibility(View.GONE);
				findViewById(R.id.tv_talk).setVisibility(View.GONE);
				button.setText("启动");
			}
		}
	}

	@Override
	public void onBackPressed() {

		Button button = (Button) findViewById(R.id.btn_start);
		if (button.getText().equals("终止")) {
			mEntity.logout();
		}
		super.onBackPressed();

	}

}
