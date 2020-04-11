package com.dizsun.timechain.util;

import com.dizsun.timechain.interfaces.JNative;
import com.dizsun.timechain.service.P2PService;
import org.apache.log4j.Logger;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LinuxSysTimeSet implements JNative {
    private Logger logger = Logger.getLogger(LinuxSysTimeSet.class);

    @Override
    public void setLocalTime(Date date) {
        if (date == null) {
            logger.error("获取时间失败！");
            return;
        }
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String dateNow = formatter.format(date);
        String[] command = new String[]{"sudo", "date", "-s", dateNow};
        try {
            Process process = Runtime.getRuntime().exec(command);
            StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), "ERROR");
            errorGobbler.start();
            StreamGobbler outGobbler = new StreamGobbler(process.getInputStream(), "STDOUT");
            outGobbler.start();
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class StreamGobbler extends Thread {
    InputStream is;
    String type;
    OutputStream os;

    StreamGobbler(InputStream is, String type) {
        this(is, type, null);
    }

    StreamGobbler(InputStream is, String type, OutputStream os) {
        this.is = is;
        this.type = type;
        this.os = os;
    }

    @Override
    public void run() {
        InputStreamReader isr = null;
        BufferedReader br = null;
        PrintWriter pw = null;
        try {
            if (os != null) {
                pw = new PrintWriter(os);
            }
            isr = new InputStreamReader(is);
            br = new BufferedReader(isr);
            String line = null;
            while ((line = br.readLine()) != null) {
                if (pw != null){
                    pw.println(line);
                }
                System.out.println(type + ">" + line);
            }
            if (pw != null) {
                pw.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
