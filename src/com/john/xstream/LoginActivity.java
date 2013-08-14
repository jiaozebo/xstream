package com.john.xstream;

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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class LoginActivity extends Activity {

	private static final String DEFAULT_ADDRESS = "210.51.52.34";
	private static final int DEFAULT_PORT = 8888;
	private String mAddress;
	private int mPort;
	private EditText mAddressView;
	private EditText mPortView;
	private View mLoginFormView;
	private View mLoginStatusView;
	private TextView mLoginStatusMessageView;
	private UserLoginTask mAuthTask;

	public static Camera sCamera;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_login);

		// Set up the login form.
		mAddress = DEFAULT_ADDRESS;
		mPort = DEFAULT_PORT;
		mAddressView = (EditText) findViewById(R.id.email);
		mAddressView.setText(mAddress);

		mPortView = (EditText) findViewById(R.id.password);
		mPortView.setText(String.valueOf(mPort));

		mLoginFormView = findViewById(R.id.login_form);
		mLoginStatusView = findViewById(R.id.login_status);
		mLoginStatusMessageView = (TextView) findViewById(R.id.login_status_message);

		findViewById(R.id.sign_in_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				attemptLogin();
			}
		});

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
		mAddressView.setError(null);
		mPortView.setError(null);

		// Store values at the time of the login attempt.
		mAddress = mAddressView.getText().toString();

		boolean cancel = false;
		View focusView = null;
		// Check for a valid password.
		if (TextUtils.isEmpty(mPortView.getText())) {
			mPortView.setError("非法值！");
			focusView = mPortView;
			cancel = true;
		}

		mPort = Integer.parseInt(mPortView.getText().toString());

		// Check for a valid email address.
		if (TextUtils.isEmpty(mAddress)) {
			mAddressView.setError("非法值！");
			focusView = mAddressView;
			cancel = true;
		}

		if (cancel) {
			// There was an error; don't attempt login and focus the first
			// form field with an error.
			focusView.requestFocus();
		} else {
			// Show a progress spinner, and kick off a background task to
			// perform the user login attempt.
			mLoginStatusMessageView.setText("正在登录");
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

	/**
	 * Represents an asynchronous login/registration task used to authenticate
	 * the user.
	 */
	public class UserLoginTask extends AsyncTask<Void, Void, Integer> {
		XStream stream = new XStream();

		@Override
		protected Integer doInBackground(Void... params) {

			if (sCamera == null) {
				sCamera = Camera.open(0);
			}

			int result = stream.reg(mAddress, mPort);
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
