package org.shengjing.phone;

import android.content.*;
import android.os.*;
import android.widget.*;
import org.shengjing.core.Policy;
public final class DiagnosticsActivity extends PhoneScreen {
    private TextView diagnostics, delayed, access, sessionSummary;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        heading(layout,"诊断与测试工具",28);
        LinearLayout session=card();
        heading(session,"最近一次监听",22);
        sessionSummary=text(session,"",18);
        diagnostics=text(layout,"",16);
        access=text(layout,"",16);
        delayed=text(layout,"",16);
        text(layout,"设备连通测试（下列按钮不代表识别成功）",16);
        for(Policy.Kind k:Policy.Kind.values()) button(layout,"发送模拟"+k.title,()-> {
            if(!notificationsOrSetup()) return;
            Alerts.show(this,app.policy.simulate(k,SystemClock.elapsedRealtime()));
        });
        button(layout,"10 秒后发送模拟敲门（锁屏测试）",()-> {
            if(notificationsOrSetup()) app.delayedTest.schedule();
        });
        button(layout,"取消延迟测试",()->app.delayedTest.cancel());
        text(layout,"锁屏测试：点击延迟按钮后手动锁屏，观察手表。测试会短暂保持处理器运行，最多 20 秒，不点亮屏幕；不代表长期后台监听已通过。",14);
        button(layout,"确认最近一次提醒",()->sendBroadcast(new Intent(this,FeedbackReceiver.class).setAction("ACK").putExtra("id",app.latestId)));
        button(layout,"最近一次是误报",()->sendBroadcast(new Intent(this,FeedbackReceiver.class).setAction("FALSE_ALARM").putExtra("id",app.latestId)));
        button(layout,"返回首页",this::finish);
    }
    private boolean notificationsOrSetup(){
        if(notifications())return true;
        go(SetupActivity.class);return false;
    }
    @Override void refresh(){
        show(sessionSummary,app.sessionReport.summary(SystemClock.elapsedRealtime()));
        long age=(SystemClock.elapsedRealtime()-app.diagnosticsUpdatedAt)/1000;
        show(diagnostics,app.status+"\n"+app.diagnostics+(app.listening && age>=3?"\n采集诊断已 "+age+" 秒未更新":""));
        show(delayed,app.delayedTest.status+"\n"+app.lastFeedback);
        StringBuilder report=new StringBuilder("版本 0.7.0");
        for(Policy.Kind k:Policy.Kind.values()){
            String channel=Alerts.channel(k,app.policy.getScene());
            android.app.NotificationChannel config=getSystemService(android.app.NotificationManager.class).getNotificationChannel(channel);
            report.append("\n").append(k.title).append("：").append(!Alerts.available(this,channel)?"系统通知被阻止":
                config!=null && config.shouldVibrate()?"允许振动":"振动关闭");
        }
        PowerManager power=getSystemService(PowerManager.class);
        report.append("\n电池优化：").append(power.isIgnoringBatteryOptimizations(getPackageName())?"已豁免":"受系统优化");
        show(access,report.toString());
    }
}
