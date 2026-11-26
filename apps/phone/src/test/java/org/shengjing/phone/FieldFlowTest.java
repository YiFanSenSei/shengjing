package org.shengjing.phone;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static org.shengjing.phone.PreferenceFlowTest.findButton;
import android.Manifest;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.shengjing.core.Policy;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class FieldFlowTest {
    private final App app=(App)RuntimeEnvironment.getApplication();
    @Test public void liveRoundExcludesEarlierEventsAndFreezesManualObservation(){
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS,Manifest.permission.RECORD_AUDIO);
        Alerts.show(app,app.policy.simulate(Policy.Kind.KNOCK,0));
        try(var page=Robolectric.buildActivity(FieldDemoActivity.class).setup()){
            var screen=page.get();
            assertFalse(findButton(screen.layout,"开始新一轮").isEnabled());
            app.listening=true;screen.refresh();
            findButton(screen.layout,"开始新一轮").performClick();
            Alerts.show(app,app.policy.simulate(Policy.Kind.DOORBELL,1));
            Alerts.show(app,app.policy.observe(Policy.Kind.KNOCK,.9f,20000).alert);
            findButton(screen.layout,"本轮在手表看到了提醒").performClick();
            findButton(screen.layout,"结束本轮并保存摘要").performClick();
            String result=new FieldRound(app).summary(app.history);
            assertTrue(result.contains("敲门识别提醒：1"));
            assertTrue(result.contains("已请求通知 1 条"));
            assertTrue(result.contains("模拟提醒 1 条"));
            assertTrue(result.contains("佩戴者报告看到了提醒"));
            Alerts.show(app,app.policy.simulate(Policy.Kind.ALARM,2));
            assertEquals(result,app.fieldRound.summary(app.history));
            app.clearHistory();assertFalse(app.fieldRound.exists());
        }
    }
    @Test public void lockScreenTestUsesChosenSoundAndSupportsCancellation(){
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        try(var page=Robolectric.buildActivity(SetupActivity.class).setup()){
            var screen=page.get();
            findButton(screen.layout,"门铃").performClick();
            findButton(screen.layout,"10 秒后发送，可先锁屏").performClick();
            findButton(screen.layout,"取消待发送的测试").performClick();
            shadowOf(Looper.getMainLooper()).idleFor(11,TimeUnit.SECONDS);
            assertTrue(app.history.snapshot().isEmpty());
            findButton(screen.layout,"10 秒后发送，可先锁屏").performClick();
            shadowOf(Looper.getMainLooper()).idleFor(11,TimeUnit.SECONDS);
            screen.refresh();
            assertEquals(Policy.Kind.DOORBELL,app.history.find(app.latestId).kind);
            assertEquals(0,app.store.watchConfirmedAt());
            assertEquals(View.VISIBLE,findButton(screen.layout,"我在手表上看到了").getVisibility());
        }
    }
    @Test public void stalePauseActionCannotStartMicrophoneOrChangeSettings(){
        var controller=Robolectric.buildService(ListeningService.class).create();
        controller.get().onStartCommand(new Intent("PAUSE_ALERTS").putExtra("session",123L),0,1);
        assertFalse(app.policy.isQuiet());
        assertFalse(app.listening);
        assertEquals(ListeningSession.State.NONE,app.sessionReport.state);
        controller.destroy();
    }
    @Test public void homePauseUpdatesTheOngoingNotificationAndKeepsListening(){
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        app.listening=true;
        try(var page=Robolectric.buildActivity(MainActivity.class).setup()){
            var screen=page.get();
            findButton(screen.layout,"暂停真实提醒").performClick();
            assertTrue(app.listening);assertTrue(app.policy.isQuiet());
            var notification=app.getSystemService(NotificationManager.class).getActiveNotifications()[0].getNotification();
            assertEquals("恢复提醒",notification.actions[0].title);
            findButton(screen.layout,"恢复真实提醒").performClick();
            assertFalse(app.policy.isQuiet());
        }
    }
}
