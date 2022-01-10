package com.serenegiant.xiaxl.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class TimeUtil {

    /**
     * 格式化的 当前时间
     *
     * @return 字符串 yyyyMMdd_HHmmss
     */
    public static String getFormatCurrTime() {
        Date currentTime = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String dateString = formatter.format(currentTime);
        return dateString;
    }
}
