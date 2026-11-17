package org.shengjing.phone;

import android.content.*;
import org.shengjing.core.EventHistory;

public final class FeedbackReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent) {
        if(!"ACK".equals(intent.getAction()) && !"FALSE_ALARM".equals(intent.getAction())) return;
        App app=(App)c.getApplicationContext();
        long id=intent.getLongExtra("id",-1);
        EventHistory.Entry alert=app.history.feedback(id,"ACK".equals(intent.getAction())?EventHistory.Feedback.ACK:EventHistory.Feedback.FALSE_ALARM);
        if(alert==null) { app.lastFeedback="该记录已处理、清空或超过保留数量"; return; }
        app.lastFeedback=(alert.simulated?"[模拟] ":"")+alert.kind.title+("ACK".equals(intent.getAction())?"：已确认":"：已标记误报（暂不自动调整模型）");
        app.saveHistory();Alerts.cancel(c,id);
    }
}
