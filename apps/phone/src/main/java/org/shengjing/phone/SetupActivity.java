package org.shengjing.phone;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.*;
import org.shengjing.core.EventHistory;
import org.shengjing.core.Policy;

public final class SetupActivity extends PhoneScreen {
    private TextView permissionStatus, watchStatus;
    private Button micButton, notificationButton, confirm;
    private long testId=-1;
    private Policy.Kind selected=Policy.Kind.KNOCK;
    private TextView channelStatus;
    private long delayedGeneration=-1;
    private Button cancelDelayed;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(state!=null){
            testId=state.getLong("watchTest",-1);
            delayedGeneration=state.getLong("delayedGeneration",-1);
            selected=Policy.Kind.valueOf(state.getString("watchKind","KNOCK"));
        }
        heading(layout,"设置权限与手表",28);
        LinearLayout permissions=card();
        heading(permissions,"手机权限",22);
        permissionStatus=text(permissions,"",18);
        micButton=button(permissions,"允许使用麦克风",()->requestOrSettings(true));
        notificationButton=button(permissions,"允许发送通知",()->requestOrSettings(false));
        LinearLayout watch=card();
        heading(watch,"测试手表提醒",22);
        text(watch,"在 OPPO 健康的手表通知管理中，允许同步「声境·测试版」。戴好手表后，发送一条测试提醒。",18);
        RadioGroup sounds=new RadioGroup(this);
        watch.addView(sounds);
        for(Policy.Kind kind:Policy.Kind.values()){
            RadioButton choice=new RadioButton(this);
            choice.setText(kind.title);choice.setTextSize(18);choice.setMinHeight(dp(48));
            choice.setId(android.view.View.generateViewId());sounds.addView(choice);
            choice.setChecked(selected==kind);
            choice.setOnCheckedChangeListener((v,checked)->{
                if(checked && selected!=kind){
                    selected=kind;testId=-1;cancelOwnTimer();refresh();
                }
            });
        }
        channelStatus=text(watch,"",17);
        button(watch,"调整这类声音的系统通知",()->channelSettings(Alerts.channel(selected,app.policy.getScene())));
        button(watch,"发送手表测试提醒",this::sendTest);
        button(watch,"10 秒后发送，可先锁屏",()->{
            if(!notifications()){requestOrSettings(false);return;}
            app.delayedTest.schedule(selected);
            delayedGeneration=app.delayedTest.generation;testId=-1;refresh();
        });
        cancelDelayed=button(watch,"取消待发送的测试",()->{cancelOwnTimer();refresh();});
        watchStatus=text(watch,"",17);
        confirm=button(watch,"我在手表上看到了",()->{
            if(validTest()){
                app.store.confirmWatch(selected,System.currentTimeMillis());
                testId=-1;
                refresh();
            }
        });
        button(layout,"打开系统通知设置",()->systemSettings(true));
        button(layout,"打开应用权限与电池设置",()->systemSettings(false));
        button(layout,"返回首页",this::finish);
    }
    private void requestOrSettings(boolean mic){
        String permission=mic?Manifest.permission.RECORD_AUDIO:Manifest.permission.POST_NOTIFICATIONS;
        if(!mic && Build.VERSION.SDK_INT<33){systemSettings(true);return;}
        if(!mic && checkSelfPermission(permission)==android.content.pm.PackageManager.PERMISSION_GRANTED){
            systemSettings(true);return;
        }
        if(app.store.asked(permission) && !shouldShowRequestPermissionRationale(permission)){
            systemSettings(!mic);return;
        }
        app.store.markAsked(permission);
        requestPermissions(new String[]{permission},mic?1:2);
    }
    private void sendTest(){
        if(!notifications()){requestOrSettings(false);return;}
        cancelOwnTimer();
        Alerts.show(this,app.policy.simulate(selected,SystemClock.elapsedRealtime()));
        testId=app.latestId;
        refresh();
    }
    private void cancelOwnTimer(){
        if(delayedGeneration==app.delayedTest.generation && app.delayedTest.isPending())app.delayedTest.cancel();
        delayedGeneration=-1;
    }
    private boolean validTest(){
        EventHistory.Entry entry=app.history.find(testId);
        return entry!=null && entry.simulated && entry.kind==selected &&
            entry.delivery==EventHistory.Delivery.REQUESTED;
    }
    @Override void refresh(){
        boolean mic=microphone(),notify=notifications();
        show(permissionStatus,"麦克风："+(mic?"已允许":"待开启")+"\n手机通知："+(notify?"已允许":"待开启"));
        micButton.setVisibility(mic?Button.GONE:Button.VISIBLE);
        notificationButton.setVisibility(notify?Button.GONE:Button.VISIBLE);
        boolean ownTimer=delayedGeneration==app.delayedTest.generation;
        if(ownTimer && app.delayedTest.sentId!=-1){
            testId=app.delayedTest.sentId;
            delayedGeneration=-1;ownTimer=false;
        }
        cancelDelayed.setVisibility(ownTimer && app.delayedTest.isPending()?Button.VISIBLE:Button.GONE);
        boolean test=validTest();
        confirm.setVisibility(test?Button.VISIBLE:Button.GONE);
        String channel=Alerts.channel(selected,app.policy.getScene());
        android.app.NotificationChannel config=getSystemService(android.app.NotificationManager.class).getNotificationChannel(channel);
        String settings=config==null || !Alerts.available(this,channel)?"通知被阻止":config.shouldVibrate()?"通知和振动已开启":"通知已开启，振动已关闭";
        show(channelStatus,app.policy.getScene().title+" · "+selected.title+"："+settings);
        if(ownTimer)show(watchStatus,app.delayedTest.status);
        else if(test)show(watchStatus,"请在手表通知列表中核对刚发出的「测试提醒："+selected.title+"」。");
        else if(testId!=-1)show(watchStatus,"本次测试无法确认。请检查通知设置后再发送。");
        else if(app.store.watchConfirmedAt(selected)>0)show(watchStatus,selected.title+"上次手动确认："+MainActivity.date(app.store.watchConfirmedAt(selected)));
        else show(watchStatus,selected.title+"还没有手动确认记录");
    }
    @Override protected void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);state.putLong("watchTest",testId);state.putString("watchKind",selected.name());
        state.putLong("delayedGeneration",delayedGeneration);
    }
}
