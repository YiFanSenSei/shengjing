package org.shengjing.phone;

import android.app.Application;
import org.shengjing.core.Policy;
import org.shengjing.core.EventHistory;

public final class App extends Application {
    public final Policy policy=new Policy();
    LocalStore store;
    EventHistory history;
    long historyRevision;
    public volatile boolean listening=false;
    public volatile String status="尚未开始监听";
    public volatile String lastEvent="暂无事件";
    public volatile String lastFeedback="暂无反馈";
    public volatile long latestId=-1;
    public final DelayedTestReminder delayedTest=new DelayedTestReminder(this);
    public volatile long audioSession;
    public volatile long diagnosticsUpdatedAt;
    public volatile String diagnostics="尚无采集数据，请开始监听";
    @Override public void onCreate() {
        super.onCreate();store=new LocalStore(this);store.restoreSettings(policy);history=store.loadHistory();
        java.util.List<EventHistory.Entry> entries=history.snapshot();
        if(!entries.isEmpty()) latestId=entries.get(entries.size()-1).id;
        Alerts.channels(this);
    }
    void saveSettings() { store.saveSettings(policy); }
    void saveHistory() { store.saveHistory(history);historyRevision++; }
    void clearHistory() {
        for(EventHistory.Entry e:history.snapshot()) Alerts.cancel(this,e.id);
        history.clear();saveHistory();latestId=-1;lastEvent="暂无事件";lastFeedback="本机提醒记录已清空";
    }
}
