package com.dizsun.timechain.util;

import com.dizsun.timechain.constant.Config;
import com.dizsun.timechain.constant.R;
import com.dizsun.timechain.interfaces.JNative;
import com.dizsun.timechain.service.NTPClient;
import org.apache.commons.net.ntp.TimeStamp;

import java.io.DataInputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtil {
    private Date date;
    private SimpleDateFormat sdf;
    private String time;
    private JNative jNative;

    private DateUtil() {
    }

    private static class Holder {
        private static DateUtil dateUtil = new DateUtil();
    }

    public static DateUtil getInstance() {
        return Holder.dateUtil;
    }

    public void init() {
        sdf = new SimpleDateFormat(R.NTP_DATE_FORMAT);
        date = new Date();
        time = sdf.format(date);
        jNative = NativeFactory.newNative();
    }

    public int getCurrentMinute(){
        sdf = new SimpleDateFormat("mm");
        date = new Date();
        return Integer.parseInt(sdf.format(date));
    }

    public int getCurrentSecond(){
        sdf = new SimpleDateFormat("ss");
        date = new Date();
        return Integer.parseInt(sdf.format(date));
    }

//    public int getCurrentTime() {
//
//    }

    public String getTime() {
        time = TimeStamp.getNtpTime(System.currentTimeMillis()).toDateString();
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getTimeFromRC(){
        Config config = Config.getInstance();
        try {
            NTPClient ntpClient = new NTPClient(config.getNtpReqTimeout(), config.getTimeCenterIp());
            date = ntpClient.getNTPTime();
            boolean flag = jNative.setLocalTime(date);
            if (!flag) {
                return "获取授时失败！";
            }
            sdf = new SimpleDateFormat(R.NTP_DATE_FORMAT);
            time = sdf.format(date);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return time;
    }
}
