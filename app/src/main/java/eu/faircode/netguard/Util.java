package eu.faircode.netguard;

/*
    This file is part of NetGuard.

    NetGuard is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    NetGuard is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with NetGuard.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2015 by Marcel Bokhorst (M66B)
*/

import android.app.ApplicationErrorReport;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.util.Set;

public class Util {
    public static String getSelfVersionName(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException ex) {
            return ex.toString();
        }
    }

    public static boolean isRoaming(Context context) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return tm.isNetworkRoaming();
    }

    public static boolean isWifiActive(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return (ni != null && ni.getType() == ConnectivityManager.TYPE_WIFI);

    }

    public static boolean isMetered(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.isActiveNetworkMetered();
    }

    public static boolean isInteractive(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm.isInteractive();
    }

    public static boolean isPackageInstalled(String packageName, Context context) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    public static void toast(final String text, final int length, final Context context) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, text, length).show();
            }
        });
    }

    public static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if (bitmapDrawable.getBitmap() != null)
                return bitmapDrawable.getBitmap();
        }

        Bitmap bitmap;
        if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0)
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        else
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    public static boolean hasValidFingerprint(String tag, Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            String pkg = context.getPackageName();
            PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
            byte[] cert = info.signatures[0].toByteArray();
            MessageDigest digest = MessageDigest.getInstance("SHA1");
            byte[] bytes = digest.digest(cert);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < bytes.length; ++i)
                sb.append(Integer.toString(bytes[i] & 0xff, 16).toLowerCase());
            String calculated = sb.toString();
            String expected = context.getString(R.string.fingerprint);
            return calculated.equals(expected);
        } catch (Throwable ex) {
            Log.e(tag, ex.toString() + "\n" + Log.getStackTraceString(ex));
            return false;
        }
    }

    public static void logExtras(String tag, Intent intent) {
        if (intent != null)
            logBundle(tag, intent.getExtras());
    }

    public static void logBundle(String tag, Bundle data) {
        if (data != null) {
            Set<String> keys = data.keySet();
            StringBuilder stringBuilder = new StringBuilder();
            for (String key : keys)
                stringBuilder.append(key).append("=").append(data.get(key)).append("\r\n");
            Log.d(tag, stringBuilder.toString());
        }
    }

    public static void sendCrashReport(Throwable ex, Context context) {
        ApplicationErrorReport report = new ApplicationErrorReport();
        report.packageName = report.processName = context.getPackageName();
        report.time = System.currentTimeMillis();
        report.type = ApplicationErrorReport.TYPE_CRASH;
        report.systemApp = false;

        ApplicationErrorReport.CrashInfo crash = new ApplicationErrorReport.CrashInfo();
        crash.exceptionClassName = ex.getClass().getSimpleName();
        crash.exceptionMessage = ex.getMessage();

        StringWriter writer = new StringWriter();
        PrintWriter printer = new PrintWriter(writer);
        ex.printStackTrace(printer);

        crash.stackTrace = writer.toString();

        StackTraceElement stack = ex.getStackTrace()[0];
        crash.throwClassName = stack.getClassName();
        crash.throwFileName = stack.getFileName();
        crash.throwLineNumber = stack.getLineNumber();
        crash.throwMethodName = stack.getMethodName();

        report.crashInfo = crash;

        Intent intent = new Intent(Intent.ACTION_APP_ERROR);
        intent.putExtra(Intent.EXTRA_BUG_REPORT, report);
        context.startActivity(intent);
    }

    public static void sendLogcat(final String tag, final Context context) {
        AsyncTask task = new AsyncTask<Object, Object, Intent>() {
            @Override
            protected Intent doInBackground(Object... objects) {
                PackageInfo pInfo;
                try {
                    pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                } catch (PackageManager.NameNotFoundException ex) {
                    Log.e(tag, ex.toString() + "\n" + Log.getStackTraceString(ex));
                    return null;
                }

                StringBuilder sb = new StringBuilder();
                sb.insert(0, "\r\n");
                sb.insert(0, "Please decribe your problem:\r\n");
                sb.insert(0, "\r\n");
                sb.insert(0, String.format("VPN dialogs: %b\r\n", isPackageInstalled("com.android.vpndialogs", context)));
                sb.insert(0, String.format("Id: %s\r\n", Build.ID));
                sb.insert(0, String.format("Display: %s\r\n", Build.DISPLAY));
                sb.insert(0, String.format("Host: %s\r\n", Build.HOST));
                sb.insert(0, String.format("Device: %s\r\n", Build.DEVICE));
                sb.insert(0, String.format("Product: %s\r\n", Build.PRODUCT));
                sb.insert(0, String.format("Model: %s\r\n", Build.MODEL));
                sb.insert(0, String.format("Manufacturer: %s\r\n", Build.MANUFACTURER));
                sb.insert(0, String.format("Brand: %s\r\n", Build.BRAND));
                sb.insert(0, "\r\n");
                sb.insert(0, String.format("Android: %s (SDK %d)\r\n", Build.VERSION.RELEASE, Build.VERSION.SDK_INT));
                sb.insert(0, String.format("NetGuard: %s\r\n", pInfo.versionName + "/" + pInfo.versionCode));

                Intent sendEmail = new Intent(Intent.ACTION_SEND);
                sendEmail.setType("message/rfc822");
                sendEmail.putExtra(Intent.EXTRA_EMAIL, new String[]{"marcel+netguard@faircode.eu"});
                sendEmail.putExtra(Intent.EXTRA_SUBJECT, "NetGuard " + pInfo.versionName + " logcat");
                sendEmail.putExtra(Intent.EXTRA_TEXT, sb.toString());

                File logcatFolder = context.getExternalCacheDir();
                logcatFolder.mkdirs();
                File logcatFile = new File(logcatFolder, "logcat.txt");
                Log.i(tag, "Writing " + logcatFile);
                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(logcatFile);
                    fos.write(getLogcat(tag).toString().getBytes());
                } catch (Throwable ex) {
                    Log.e(tag, ex.toString() + "\n" + Log.getStackTraceString(ex));
                } finally {
                    if (fos != null)
                        try {
                            fos.close();
                        } catch (IOException ignored) {
                        }
                }
                logcatFile.setReadable(true);

                sendEmail.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(logcatFile));

                return sendEmail;
            }

            @Override
            protected void onPostExecute(Intent sendEmail) {
                if (sendEmail != null)
                    try {
                        context.startActivity(sendEmail);
                    } catch (Throwable ex) {
                        Log.e(tag, ex.toString() + "\n" + Log.getStackTraceString(ex));
                    }
            }
        };
        task.execute();
    }

    private static StringBuilder getLogcat(String tag) {
        String pid = Integer.toString(android.os.Process.myPid());
        StringBuilder builder = new StringBuilder();
        try {
            String[] command = new String[]{"logcat", "-d", "-v", "threadtime"};
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = bufferedReader.readLine()) != null)
                if (line.contains(pid)) {
                    builder.append(line);
                    builder.append("\r\n");
                }
        } catch (IOException ex) {
            Log.e(tag, ex.toString() + "\n" + Log.getStackTraceString(ex));
        }
        return builder;
    }
}
