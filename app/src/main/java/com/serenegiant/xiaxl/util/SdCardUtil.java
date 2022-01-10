package com.serenegiant.xiaxl.util;


import android.content.Context;
import android.os.Environment;

import java.io.File;

/**
 * 内部存储
 * /data/data/包名/files
 * context.getFilesDir().getPath()
 * /data/data/包名/cache
 * context.getCacheDir().getPath()
 * <p>
 * 外部存储
 * /sdcard/Android/data/包名/cache/dir
 * context.getExternalFilesDir("dir").getPath()
 * /sdcard/Android/data/包名/cache
 * context.getExternalCacheDir().getPath()
 */
public class SdCardUtil {


    /**
     * 获取应用私有file目录
     * <p>
     * /sdcard/Android/data/包名/file/dir
     *
     * @param dir The type of files directory to return. May be {@code null}
     *            for the root of the files directory or one of the following
     *            constants for a subdirectory:
     *            {@link android.os.Environment#DIRECTORY_MUSIC},
     *            {@link android.os.Environment#DIRECTORY_PODCASTS},
     *            {@link android.os.Environment#DIRECTORY_RINGTONES},
     *            {@link android.os.Environment#DIRECTORY_ALARMS},
     *            {@link android.os.Environment#DIRECTORY_NOTIFICATIONS},
     *            {@link android.os.Environment#DIRECTORY_PICTURES}, or
     *            {@link android.os.Environment#DIRECTORY_MOVIES}.
     */
    public static String getPrivateFilePath(Context context, String dir) {
        File file = context.getExternalFilesDir(dir);
        //先判断外部存储是否可用
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) && file != null) {
            return file.getAbsolutePath();
        } else {
            return context.getFilesDir() + File.separator + dir;
        }
    }


    /**
     * 获取应用私有cache目录
     * <p>
     * /sdcard/Android/data/包名/cache
     */
    public static String getPrivateCachePath(Context context) {
        File file = context.getExternalCacheDir();
        //先判断外部存储是否可用
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) && file != null) {
            return file.getAbsolutePath();
        } else {
            return context.getCacheDir().getAbsolutePath();
        }
    }
}


