package org.shengjing.phone;

import android.app.NotificationManager;
import android.content.*;
import org.shengjing.core.Policy;

public final class FeedbackReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent) {
        if(!"ACK".equals(intent.getAction()) && !"FALSE_ALARM".equals(intent.getAction())) return;
        App app=(App)c.getApplicationContext();
        long id=intent.getLongExtra("id",-1);
        Policy.Alert alert=app.policy.feedback(id);
        if(alert==null) { app.lastFeedback="该事件已处理或会话已结束"; return; }
        app.lastFeedback=(alert.simulated?"[模拟] ":"")+alert.kind.title+("ACK".equals(intent.getAction())?"：已确认":"：已标记误报（暂不自动调整模型）");
        c.getSystemService(NotificationManager.class).cancel((int)(id+100));
    }
}
