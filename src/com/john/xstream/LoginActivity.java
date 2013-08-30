package com.john.xstream;

import java.util.List;

import junit.framework.Assert;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
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

	public static int sCameraId = 0;
	public static Camera sCamera;
	public static List<Camera.Size> sResolutions;
	public static int sCurrentResolution;

	public static void openCamera(int id) {
		Assert.assertNull(sCamera);
		Camera c = Camera.open(id);
		sResolutions = c.getParameters().getSupportedPreviewSizes();
		int size = sResolutions.size();
		if (size > 3) {
			sCurrentResolution = 3;
		} else if (size == 1) {
			sCurrentResolution = 0;
		} else if (size == 2) {
			sCurrentResolution = 1;
		}
		sCamera = c;
		sCameraId = id;
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

			if (sCamera == null) {
				openCamera(0);
			}
			if (sResolutions == null || sResolutions.isEmpty()) {
				return 1000;
			}
			int result = stream.reg(DEFAULT_ADDRESS, DEFAULT_PORT, mUserName, mPassword,
					DEFAULT_WIDTH, DEFAULT_HEIGHT);
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
