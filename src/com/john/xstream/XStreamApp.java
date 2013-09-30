package com.john.xstream;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.app.Application;
import android.os.Environment;

public class XStreamApp extends Application {

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Application#onCreate()
	 */
	@Override
	public void onCreate() {
		super.onCreate();
		File f = new File(Environment.getExternalStorageDirectory(), "XStream");
		f.mkdirs();
		save2file("*****************************************************", true);
		save2file("******************开始启动****************************", true);
		save2file("*****************************************************", true);
		boolean debug = false;
		if (debug)
			Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {

				@Override
				public void uncaughtException(Thread thread, Throwable ex) {
					PrintStream err;
					OutputStream os;
					try {
						SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
								Locale.CHINA);
						String time = formatter.format(new java.util.Date());
						os = new FileOutputStream(String.format("%s/%s/log.txt", Environment
								.getExternalStorageDirectory().getPath(), "XStream"), true);
						err = new PrintStream(os);
						err.append(time);
						ex.printStackTrace(err);
						os.close();
						err.close();
					} catch (FileNotFoundException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					} catch (IOException e2) {
						// TODO Auto-generated catch block
						e2.printStackTrace();
					}

					System.exit(-1);
				}
			});
	}

	protected static void save2file(String content, boolean append) {
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(String.format("%s/%s/log.txt", Environment
					.getExternalStorageDirectory().getPath(), "XStream"), append);
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
			fos.write((formatter.format(new Date()) + "\r\n").getBytes());
			fos.write(content.getBytes());
			fos.write("\r\n".getBytes());
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
}
