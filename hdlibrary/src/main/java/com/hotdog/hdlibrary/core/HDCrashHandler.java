package com.hotdog.hdlibrary.core;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.google.common.collect.Maps;
import com.hotdog.hdlibrary.utils.StorageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;


/**
 * UncaughtException处理类,当程序发生Uncaught异常的时候,有该类来接管程序,并记录发送错误报告.
 *
 * @author user
 */
public class HDCrashHandler implements UncaughtExceptionHandler {

	public static final String TAG = "CrashHandler";
	private static final String DIR = "log";

	private static HDCrashHandler INSTANCE = new HDCrashHandler();

	private Context mContext;

	private Thread.UncaughtExceptionHandler mDefaultHandler;
	private Map<String, String> infos = Maps.newHashMap();
	private DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault());

	private HDCrashHandler() {
	}

	/**
	 * 获取 CrashHandler 实例 ,单例模式
	 */
	public static HDCrashHandler getInstance() {
		return INSTANCE;
	}

	public void init(Context context) {
		mContext = context;
		mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler(this);
	}

	/**
	 * 当 UncaughtException 发生时会转入该函数来处理
	 */
	@Override
	public void uncaughtException(Thread thread, Throwable ex) {
		if (!handleException(ex) && mDefaultHandler != null) {
			// 如果用户没有处理则让系统默认的异常处理器来处理
			mDefaultHandler.uncaughtException(thread, ex);
		}
		else {
			try {
				Thread.sleep(3000);
			}
			catch (InterruptedException e) {
				Log.e(TAG, "error : ", e);
			}

			// 退出程序,注释下面的重启启动程序代码
			android.os.Process.killProcess(android.os.Process.myPid());
			System.exit(0);
		}
	}

	private boolean handleException(Throwable ex) {
		if (ex == null) {
			return false;
		}
		ex.printStackTrace();
		new Handler(Looper.getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(mContext, "很抱歉，程序出现异常，即将退出。", Toast.LENGTH_LONG).show();
			}
		});

		// 收集设备参数信息
		collectDeviceInfo(mContext);
		//if (MainApplication.isDebug) {
		// 保存日志文件
		saveCrashInfo2File(ex);
		//} else {
		// saveCrashInfo2Cloud(ex);
		//}
		return true;
	}

	/**
	 * 收集设备参数信息
	 *
	 * @param ctx
	 */
	public void collectDeviceInfo(Context ctx) {
		try {
			PackageManager pm = ctx.getPackageManager();
			PackageInfo pi = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_ACTIVITIES);

			if (pi != null) {
				String versionName = pi.versionName == null ? "null" : pi.versionName;
				String versionCode = pi.versionCode + "";
				infos.put("VERSION_NAME", versionName);
				infos.put("VERSION_CODE", versionCode);
			}
		}
		catch (NameNotFoundException e) {
			Log.e(TAG, "an error occured when collect package info", e);
		}

		Field[] fields = Build.class.getDeclaredFields();
		for (Field field : fields) {
			try {
				field.setAccessible(true);
				infos.put(field.getName(), field.get(null).toString());
				Log.d(TAG, field.getName() + " : " + field.get(null));
			}
			catch (Exception e) {
				Log.e(TAG, "an error occured when collect crash info", e);
			}
		}
	}

	/*
	 * /** 保存错误信息到文件中 *
	 * 
	 * @param ex
	 * 
	 * @return 返回文件名称,便于将文件传送到服务器
	 */

	private String saveCrashInfo2File(Throwable ex) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> entry : infos.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			sb.append(key).append("=").append(value).append("\n");
		}

		Writer writer = new StringWriter();
		PrintWriter printWriter = new PrintWriter(writer);
		ex.printStackTrace(printWriter);
		Throwable cause = ex.getCause();
		while (cause != null) {
			cause.printStackTrace(printWriter);
			cause = cause.getCause();
		}
		printWriter.close();

		String result = writer.toString();
		sb.append(result);
		try {
			long timestamp = System.currentTimeMillis();
			String time = formatter.format(new Date());
			String fileName = "crash-" + time + "-" + timestamp + ".log";

			FileOutputStream fos = new FileOutputStream(new File(StorageUtils.getStorageDirectory(mContext, DIR), fileName));
			fos.write(sb.toString().getBytes());
			fos.close();

			return fileName;
		}
		catch (Exception e) {
			Log.e(TAG, "an error occured while writing file...", e);
		}
		return null;
	}

}