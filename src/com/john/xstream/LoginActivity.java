package com.john.xstream;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class LoginActivity extends Activity {

	private static final String KEY_USR = "key_usr";
	private static final String DEFAULT_ADDRESS = "210.51.52.34";
	private static final int DEFAULT_PORT = 8888;
	private static final String RESOLUTION_LOW = "key_low";
	private static final String RESOLUTION_HIGH = "key_high";

	private String mUserName;
	private String mPassword;
	private EditText mUser;
	private EditText mPwd;
	private View mLoginFormView;
	private View mLoginStatusView;
	private TextView mLoginStatusMessageView;
	private UserLoginTask mAuthTask;

// public static Camera sCamera;
	public static List<Camera.Size> sResolutions;

	public static void getCameraResolutions(int id) {
		try {
			int num = Camera.getNumberOfCameras();
			if (num < 1) {
				return;
			}
			Camera c = Camera.open(id);
			sResolutions = c.getParameters().getSupportedPreviewSizes();
			int size = sResolutions.size();
			c.release();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.login);

		// Set up the login form.
		mUserName = getPreferences(MODE_PRIVATE).getString(KEY_USR, "");
		mUser = (EditText) findViewById(R.id.login_user);
		mUser.setText(mUserName);

		mPwd = (EditText) findViewById(R.id.login_pwd);
		mPwd.setText(mPassword);

		mLoginFormView = findViewById(R.id.login_form);
		mLoginStatusView = findViewById(R.id.login_status);
		mLoginStatusMessageView = (TextView) findViewById(R.id.login_status_message);

		findViewById(R.id.sign_in_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				attemptLogin();
			}
		});
		findViewById(R.id.sign_in_button).setFocusable(true);
		findViewById(R.id.sign_in_button).requestFocus();
	}

	/**
	 * Attempts to sign in or register the account specified by the login form.
	 * If there are form errors (invalid email, missing fields, etc.), the
	 * errors are presented and no actual login attempt is made.
	 */
	public void attemptLogin() {
		if (mAuthTask != null) {
			return;
		}
		// Reset errors.
		mUser.setError(null);
		mPwd.setError(null);

		// Store values at the time of the login attempt.
		mUserName = mUser.getText().toString();

		boolean cancel = false;

		mPassword = mPwd.getText().toString();

		// Check for a valid email address.
		if (TextUtils.isEmpty(mUserName)) {
			mUser.setError("请输入用户名");
			cancel = true;
		}

		if (cancel) {
		} else {
			// Show a progress spinner, and kick off a background task to
			// perform the user login attempt.
			mLoginStatusMessageView.setText("正在登录");

			getPreferences(MODE_PRIVATE).edit().putString(KEY_USR, mUserName).apply();
			showProgress(true);
			mAuthTask = new UserLoginTask();
			mAuthTask.execute((Void) null);
		}
	}

	/**
	 * Shows the progress UI and hides the login form.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
	private void showProgress(final boolean show) {
		// On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
		// for very easy animations. If available, use these APIs to fade-in
		// the progress spinner.
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
			int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

			mLoginStatusView.setVisibility(View.VISIBLE);
			mLoginStatusView.animate().setDuration(shortAnimTime).alpha(show ? 1 : 0)
					.setListener(new AnimatorListenerAdapter() {
						@Override
						public void onAnimationEnd(Animator animation) {
							mLoginStatusView.setVisibility(show ? View.VISIBLE : View.GONE);
						}
					});

			mLoginFormView.setVisibility(View.VISIBLE);
			mLoginFormView.animate().setDuration(shortAnimTime).alpha(show ? 0 : 1)
					.setListener(new AnimatorListenerAdapter() {
						@Override
						public void onAnimationEnd(Animator animation) {
							mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
						}
					});
		} else {
			// The ViewPropertyAnimator APIs are not available, so simply show
			// and hide the relevant UI components.
			mLoginStatusView.setVisibility(show ? View.VISIBLE : View.GONE);
			mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
		}
	}

	private static final int DEFAULT_HEIGHT = 240;
	private static final int DEFAULT_WIDTH = 320;

	/**
	 * Represents an asynchronous login/registration task used to authenticate
	 * the user.
	 */
	public class UserLoginTask extends AsyncTask<Void, Void, Integer> {

		XStream stream = new XStream();

		@Override
		protected Integer doInBackground(Void... params) {

			getCameraResolutions(0);
			if (sResolutions == null || sResolutions.isEmpty()) {
				return 1000;
			}
			String addr = DEFAULT_ADDRESS;
			int port = DEFAULT_PORT;
			try {
				FileInputStream fis = new FileInputStream(new File(Environment.getExternalStorageDirectory(), "server.txt"));
				BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
				addr = reader.readLine();
				port = Integer.parseInt(reader.readLine());
				reader.close();
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}

			int result = stream
					.reg(addr, port, mUserName, mPassword, DEFAULT_WIDTH, DEFAULT_HEIGHT);
			return result;
		}

		@Override
		protected void onPostExecute(final Integer result) {
			mAuthTask = null;
			showProgress(false);

			if (result == 0) {
				MainActivity.stream = stream;
				startActivity(new Intent(LoginActivity.this, MainActivity.class));
				finish();
			} else if (result == 1000) {
				Toast.makeText(LoginActivity.this, String.format("启动摄像头失败", result),
						Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(LoginActivity.this, String.format("login failed(%d)！", result),
						Toast.LENGTH_SHORT).show();
			}
		}

		@Override
		protected void onCancelled() {
			mAuthTask = null;
			showProgress(false);
		}
	}

}
