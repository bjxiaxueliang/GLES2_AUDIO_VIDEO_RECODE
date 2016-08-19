package com.serenegiant.xiaxl;

import android.util.Log;

/**
 * 系统的日志处理类，对android默认日志做了封装，根据系统配置的日志级别打印日志
 */
public class LogUtils {
    private static final int LOG_LEVEL_MIN = Log.VERBOSE;

    private static final String SNS_TAG = "xiaxl ";

    /**
     *
     * @param msg
     * @return
     */
    private static String getStr(String msg) {
        return msg == null ? "null" : msg;
    }

    /**
     * 获取tag,加入了类名\方法名\行号
     *
     * @param msg
     * @return
     */
    private static String getTAG(String msg) {
        /**
         * 获取类名\方法名\行号\
         */
        //
        StackTraceElement caller = Thread.currentThread().getStackTrace()[4];
        // className
        //String className = caller.getClassName();
        //className = className.substring(className.lastIndexOf(".") + 1);
        // methodName
        String methodName = caller.getMethodName();
        // lineNumber
        int lineNumber = caller.getLineNumber();

        // -------------------------
        StringBuffer sb = new StringBuffer();
        sb.append(SNS_TAG);
        sb.append(": ");
        sb.append(msg);
        //sb.append(" ");
        //sb.append(className);
        sb.append(".");
        sb.append(methodName);
        sb.append(":");
        sb.append(lineNumber);
        return sb.toString();
    }

    public static void i(String tag, String s) {
        if (LOG_LEVEL_MIN <= Log.INFO) {
            Log.i(getTAG(tag), getStr(s));
        }
    }

    public static void e(String tag, Object s) {
        if (null != s) {
            e(tag, String.valueOf(s));
        }
    }

    public static void e(String tag, String s) {
        if (LOG_LEVEL_MIN <= Log.ERROR) {
            Log.e(getTAG(tag), getStr(s));
        }
    }

    public static void e(String tag, String s, Throwable tr) {
        if (LOG_LEVEL_MIN <= Log.ERROR) {
            Log.e(getTAG(tag), getStr(s), tr);
        }
    }

    public static void d(String tag, String s) {
        if (LOG_LEVEL_MIN <= Log.DEBUG) {
            Log.d(getTAG(tag), getStr(s));
        }
    }

    public static void w(String tag, String s) {
        if (LOG_LEVEL_MIN <= Log.WARN) {
            Log.w(getTAG(tag), getStr(s));
        }
    }

    public static void w(String tag, String s, Throwable tr) {
        if (LOG_LEVEL_MIN <= Log.WARN) {
            Log.w(getTAG(tag), getStr(s), tr);
        }
    }

    public static void v(String tag, String s) {
        if (LOG_LEVEL_MIN <= Log.VERBOSE) {
            Log.v(getTAG(tag), getStr(s));
        }
    }

    public static void v(String tag, String s, Throwable tr) {
        if (LOG_LEVEL_MIN <= Log.VERBOSE) {
            Log.v(getTAG(tag), getStr(s), tr);
        }
    }
}
