package org.shengjing.phone;

import android.app.Application;
import org.shengjing.core.Policy;

public final class App extends Application {
    public final Policy policy=new Policy();
    public volatile boolean listening=false;
    public volatile String status="尚未开始监听";
    public volatile String lastEvent="暂无事件";
    public volatile String lastFeedback="暂无反馈";
    public volatile long latestId=-1;
    public final DelayedTestReminder delayedTest=new DelayedTestReminder(this);
    @Override public void onCreate() { super.onCreate(); Alerts.channels(this); }
}
