package com.dizsun.timechain.interfaces;

import java.util.Date;

public interface JNative {
    /**
     * 设置系统时间
     * @param date Date
     */
    void setLocalTime(Date date);
}
