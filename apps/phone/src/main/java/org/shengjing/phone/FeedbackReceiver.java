package org.shengjing.phone;

import android.content.*;
import org.shengjing.core.EventHistory;

public final class FeedbackReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent) {
        String action = intent.getAction();
        if (!"ACK".equals(action) && !"FALSE_ALARM".equals(action) && !"TOO_FREQUENT".equals(action)) return;
        App app=(App)c.getApplicationContext();
        long id=intent.getLongExtra("id",-1);
        EventHistory.Entry alert=app.history.feedback(id,EventHistory.Feedback.valueOf(action));
        if(alert==null) { app.lastFeedback="该记录已处理、清空或超过保留数量"; return; }
        app.lastFeedback=(alert.simulated?"[模拟] ":"")+alert.kind.title+"："+HistoryActivity.feedbackText(alert.feedback);
        app.saveHistory();Alerts.cancel(c,id);
    }
}
