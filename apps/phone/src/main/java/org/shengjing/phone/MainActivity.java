package org.shengjing.phone;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.*;
import java.text.DateFormat;
import java.util.Date;
import org.shengjing.core.EventHistory;
import org.shengjing.core.Policy;

public final class MainActivity extends PhoneScreen {
    private TextView status, mode, latest, watch;
    private Button listen, history, setup, recovery, quiet, detail;
    private long displayedRevision=-1;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        heading(layout,"声境",32);
        LinearLayout monitor=card();
        status=heading(monitor,"",26);
        mode=text(monitor,"",18);
        listen=button(monitor,"开始监听",this::toggleListening);primary(listen);
        quiet=button(monitor,"暂停真实提醒",()->{app.setQuiet(!app.policy.isQuiet());refresh();});
        recovery=button(monitor,"检查设备设置",()->go(SetupActivity.class));
        LinearLayout recent=card();
        heading(recent,"最近提醒",22);
        latest=text(recent,"",19);
        detail=button(recent,"查看这条提醒",()->startActivity(new Intent(this,HistoryActivity.class).putExtra("eventId",app.latestId)));
        history=button(recent,"查看提醒记录与反馈",()->go(HistoryActivity.class));
        LinearLayout devices=card();
        heading(devices,"手机与手表",22);
        watch=text(devices,"",17);
        setup=button(devices,"设置权限与手表",()->go(SetupActivity.class));
        button(layout,"场景与提醒设置",()->go(OptionsActivity.class));
        button(layout,"现场演示与锁屏核对",()->go(FieldDemoActivity.class));
        button(layout,"诊断与测试工具",()->go(DiagnosticsActivity.class));
        text(layout,"声音在本机处理，不保存录音。声境不能作为安全报警保障。",16);
    }
    @Override void refresh(){
        show(status,app.status.startsWith("正在本机监听：")?"正在监听":app.status);
        show(listen,app.listening?"停止监听":"开始监听");
        quiet.setVisibility(app.listening || app.policy.isQuiet()?Button.VISIBLE:Button.GONE);
        show(quiet,app.policy.isQuiet()?"恢复真实提醒":"暂停真实提醒");
        recovery.setVisibility(app.sessionReport.state.needsAttention()?Button.VISIBLE:Button.GONE);
        int enabled=0,available=0;
        for(Policy.Kind k:Policy.Kind.values()){
            if(!app.policy.isEnabled(k))continue;
            enabled++;
            if(Alerts.available(this,Alerts.channel(k,app.policy.getScene())))available++;
        }
        String delivery=app.policy.isQuiet()?"真实提醒已暂停":enabled==0?"所有声音类别已关闭":
            available==0?"声音通知被阻止":available<enabled?"部分声音通知被阻止":"已关注 "+enabled+" 类声音";
        long age=app.sessionReport.sinceLastWindow(SystemClock.elapsedRealtime());
        String stalled=app.listening && app.status.startsWith("正在本机监听") && age>=5000
            ?"\n已有 "+age/1000+" 秒没有新的声音处理结果，可打开诊断查看":"";
        show(mode,app.policy.getScene().title+"场景 · "+delivery+stalled);
        boolean ready=microphone() && notifications();
        long confirmed=app.store.watchConfirmedAt();
        String lastTest=confirmed==0?"手表同步待测试":"上次手动确认手表收到测试提醒："+date(confirmed);
        show(watch,(ready?"手机权限已开启":"手机权限待设置")+"\n"+lastTest);
        show(setup,ready && confirmed>0?"检查权限与手表":"设置权限与手表");
        if(displayedRevision==app.historyRevision)return;
        displayedRevision=app.historyRevision;
        EventHistory.Entry e=app.history.find(app.latestId);
        detail.setVisibility(e==null?Button.GONE:Button.VISIBLE);
        show(latest,e==null?"还没有提醒记录":(e.simulated?"模拟 · ":"识别 · ")+e.kind.title+"\n"+date(e.time)+" · "+
            HistoryActivity.deliveryText(e.delivery)+" · "+HistoryActivity.feedbackText(e.feedback));
        int count=0;
        for(Policy.Scene scene:Policy.Scene.values())for(Policy.Kind kind:Policy.Kind.values())
            if(kind!=Policy.Kind.ALARM && !app.policy.isLessFrequent(scene,kind) && app.frequentReports(scene,kind)>=App.REPORTS_NEEDED)count++;
        show(history,count==0?"查看提醒记录与反馈":"查看提醒记录与反馈（"+count+" 条建议）");
    }
    static String date(long time){return DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(time));}
}
