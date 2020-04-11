package com.dizsun.timechain.constant;

import com.dizsun.timechain.util.DateUtil;
import com.dizsun.timechain.interfaces.ISubscriber;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 广播时间变化事件,相当于计时器
 */
public class Broadcaster {
    private Timer timer;
    private ArrayList<ISubscriber> subscribers;
    private DateUtil dateUtil;

    public Broadcaster() {
        timer = new Timer();
        subscribers = new ArrayList<>();
        dateUtil = DateUtil.getInstance();
        dateUtil.init();
    }

    public void broadcast() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if(dateUtil.getCurrentSecond() == 0){
                    for (ISubscriber s : subscribers) {
                        s.doPerHour45();
                    }
                }
                else if(dateUtil.getCurrentSecond() == 15){
                    for (ISubscriber s : subscribers) {
                        s.doPerHour59();
                    }
                }
                else if(dateUtil.getCurrentSecond() == 30){
                    for (ISubscriber s : subscribers) {
                        s.doPerHour00();
                    }
                }
                else if(dateUtil.getCurrentSecond() == 45){
                    for (ISubscriber s : subscribers) {
                        s.doPerHour01();
                    }
                }
            }
        }, 1, 1000);
    }

    public void subscribe(ISubscriber subscriber) {
        subscribers.add(subscriber);
    }

    public void unSubscribe(ISubscriber subscriber) {
        subscribers.remove(subscriber);
    }

}
