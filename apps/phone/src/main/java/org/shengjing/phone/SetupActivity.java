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

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(state!=null)testId=state.getLong("watchTest",-1);
        heading(layout,"设置权限与手表",28);
        LinearLayout permissions=card();
        heading(permissions,"手机权限",22);
        permissionStatus=text(permissions,"",18);
        micButton=button(permissions,"允许使用麦克风",()->requestOrSettings(true));
        notificationButton=button(permissions,"允许发送通知",()->requestOrSettings(false));
        LinearLayout watch=card();
        heading(watch,"测试手表提醒",22);
        text(watch,"在 OPPO 健康的手表通知管理中，允许同步「声境·测试版」。戴好手表后，发送一条测试提醒。",18);
        button(watch,"发送手表测试提醒",this::sendTest);
        watchStatus=text(watch,"",17);
        confirm=button(watch,"我在手表上看到了",()->{
            if(validTest()){
                app.store.confirmWatch(System.currentTimeMillis());
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
        Alerts.show(this,app.policy.simulate(Policy.Kind.KNOCK,SystemClock.elapsedRealtime()));
        testId=app.latestId;
        refresh();
    }
    private boolean validTest(){
        EventHistory.Entry entry=app.history.find(testId);
        return entry!=null && entry.simulated && entry.kind==Policy.Kind.KNOCK &&
            entry.delivery==EventHistory.Delivery.REQUESTED;
    }
    @Override void refresh(){
        boolean mic=microphone(),notify=notifications();
        show(permissionStatus,"麦克风："+(mic?"已允许":"待开启")+"\n手机通知："+(notify?"已允许":"待开启"));
        micButton.setVisibility(mic?Button.GONE:Button.VISIBLE);
        notificationButton.setVisibility(notify?Button.GONE:Button.VISIBLE);
        boolean test=validTest();
        confirm.setVisibility(test?Button.VISIBLE:Button.GONE);
        if(test)show(watchStatus,"请打开手表通知列表，找到刚发出的「测试提醒：敲门」。");
        else if(testId!=-1)show(watchStatus,"测试记录不可确认。请检查系统中的敲门通知开关，再发送一次。");
        else if(app.store.watchConfirmedAt()>0)show(watchStatus,"上次手动确认："+MainActivity.date(app.store.watchConfirmedAt()));
        else show(watchStatus,"等待首次手表测试");
    }
    @Override protected void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);state.putLong("watchTest",testId);
    }
}
