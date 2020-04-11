package com.dizsun.timechain.util;

import com.dizsun.timechain.constant.Config;
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
        time = TimeStamp.getNtpTime(System.currentTimeMillis()).toDateString();
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

    public String getTime() {
        time = TimeStamp.getNtpTime(System.currentTimeMillis()).toDateString();
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public void getTimeFromRC(){
        Config config = Config.getInstance();
        try {
            NTPClient ntpClient = new NTPClient(config.getNtpReqTimeout(), config.getTimeCenterIp());
            jNative.setLocalTime(ntpClient.getNTPTime());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }
}
