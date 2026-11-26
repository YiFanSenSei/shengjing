package org.shengjing.phone;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.widget.*;
import org.shengjing.core.Policy;

public final class FieldDemoActivity extends PhoneScreen {
    private TextView readiness, result;
    private Button listen, begin, finish, copy, channel, options, setup;
    private LinearLayout wrist;
    private String blockedChannel;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        heading(layout,"现场演示与锁屏核对",28);
        LinearLayout device=card();
        heading(device,"设备准备",22);
        readiness=text(device,"",18);
        setup=button(device,"设置手机权限与手表",()->go(SetupActivity.class));
        options=button(device,"调整场景与关注声音",()->go(OptionsActivity.class));
        channel=button(device,"打开受阻声音的通知设置",()->channelSettings(blockedChannel));
        listen=button(device,"开始监听",this::toggleListening);
        LinearLayout round=card();
        heading(round,"本轮演示",22);
        result=text(round,"",18);
        begin=button(round,"开始新一轮",()->{app.fieldRound.begin(app.history,app.policy.getScene());refresh();});
        wrist=new LinearLayout(this);wrist.setOrientation(LinearLayout.VERTICAL);round.addView(wrist);
        button(wrist,"本轮在手表看到了提醒",()->{app.fieldRound.observeWatch(true);refresh();});
        button(wrist,"本轮在手表未看到提醒",()->{app.fieldRound.observeWatch(false);refresh();});
        finish=button(round,"结束本轮并保存摘要",()->{app.fieldRound.finish(app.history,"本轮已结束");refresh();});
        copy=button(round,"复制本轮摘要",()->{
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("声境现场演示",app.fieldRound.summary(app.history)));
            Toast.makeText(this,"已复制本轮摘要",Toast.LENGTH_SHORT).show();
        });
        button(layout,"查看监听连续性",()->go(DiagnosticsActivity.class));
        button(layout,"返回首页",this::finish);
    }
    @Override void refresh(){
        boolean permissions=microphone() && notifications();
        boolean quiet=app.policy.isQuiet();
        int enabled=0;
        blockedChannel=null;
        StringBuilder check=new StringBuilder(app.status);
        if(!permissions)check.append("\n请开启麦克风和手机通知权限。");
        if(quiet)check.append("\n真实声音提醒已暂停。");
        for(Policy.Kind kind:Policy.Kind.values()){
            if(!app.policy.isEnabled(kind))continue;
            enabled++;
            String id=Alerts.channel(kind,app.policy.getScene());
            if(permissions && !Alerts.available(this,id)){
                check.append("\n").append(kind.title).append("的系统通知已关闭。");
                if(blockedChannel==null)blockedChannel=id;
            }
        }
        if(enabled==0)check.append("\n请至少关注一种声音。");
        PowerManager power=getSystemService(PowerManager.class);
        if(power.isPowerSaveMode())check.append("\n系统处于省电模式，请在锁屏测试后核对监听连续性。");
        if(app.store.watchConfirmedAt()==0)check.append("\n手表尚未手动核对，可先发送一次测试提醒。");
        show(readiness,check.toString());
        setup.setVisibility(!permissions || app.store.watchConfirmedAt()==0?Button.VISIBLE:Button.GONE);
        options.setVisibility(quiet || enabled==0?Button.VISIBLE:Button.GONE);
        channel.setVisibility(blockedChannel==null?Button.GONE:Button.VISIBLE);
        show(listen,app.listening?"停止监听":"开始监听");
        boolean active=app.fieldRound.active();
        begin.setEnabled(app.listening && !quiet && enabled>0 && permissions);
        begin.setVisibility(active?Button.GONE:Button.VISIBLE);
        wrist.setVisibility(active?Button.VISIBLE:Button.GONE);
        finish.setVisibility(active?Button.VISIBLE:Button.GONE);
        copy.setVisibility(!active && app.fieldRound.exists()?Button.VISIBLE:Button.GONE);
        show(result,app.fieldRound.summary(app.history));
    }
}
