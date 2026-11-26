package org.shengjing.phone;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static org.shengjing.phone.PreferenceFlowTest.findButton;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.RadioButton;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.shengjing.core.EventHistory;
import org.shengjing.core.Policy;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class NotificationJourneyTest {
    private final App app=(App)RuntimeEnvironment.getApplication();
    @Test public void olderNotificationOpensAndAcknowledgesOnlyItsOwnRecord() throws Exception {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        app.policy.setScene(Policy.Scene.STUDY);
        Alerts.show(app,app.policy.simulate(Policy.Kind.KNOCK,1));
        long first=app.latestId;
        PendingIntent tap=app.getSystemService(NotificationManager.class).getActiveNotifications()[0].getNotification().contentIntent;
        app.policy.setScene(Policy.Scene.HOME);
        Alerts.show(app,app.policy.simulate(Policy.Kind.ALARM,2));
        long second=app.latestId;
        tap.send();shadowOf(Looper.getMainLooper()).idle();
        Intent detail=shadowOf(app).getNextStartedActivity();
        assertEquals(first,detail.getLongExtra("eventId",-1));
        assertEquals(HistoryActivity.class.getName(),detail.getComponent().getClassName());
        try(var page=Robolectric.buildActivity(HistoryActivity.class,detail).setup()){
            var screen=page.get();
            findButton(screen.layout,"检查这类声音的通知设置").performClick();
            assertEquals("study_knock",shadowOf(screen).getNextStartedActivity().getStringExtra(Settings.EXTRA_CHANNEL_ID));
            findButton(screen.layout,"已看到").performClick();shadowOf(Looper.getMainLooper()).idle();
            assertEquals(EventHistory.Feedback.ACK,app.history.find(first).feedback);
            assertEquals(EventHistory.Feedback.PENDING,app.history.find(second).feedback);
            app.clearHistory();
            Alerts.show(app,app.policy.simulate(Policy.Kind.DOORBELL,3));
            screen.refresh();
            assertNull(findButton(screen.layout,"已看到"));
        }
    }
    @Test public void watchConfirmationsAreSeparateAndOldKnockTimeSurvivesUpgrade(){
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        app.getSharedPreferences("shengjing_local_v1",Context.MODE_PRIVATE).edit().putLong("watch_confirmed_at",42).apply();
        try(var page=Robolectric.buildActivity(SetupActivity.class).setup()){
            var screen=page.get();
            findButton(screen.layout,"发送手表测试提醒").performClick();
            ((RadioButton)findButton(screen.layout,"门铃")).performClick();
            assertEquals(View.GONE,findButton(screen.layout,"我在手表上看到了").getVisibility());
            findButton(screen.layout,"发送手表测试提醒").performClick();
            assertEquals(Policy.Kind.DOORBELL,app.history.find(app.latestId).kind);
            findButton(screen.layout,"我在手表上看到了").performClick();
            LocalStore store=new LocalStore(app);
            assertTrue(store.watchConfirmedAt(Policy.Kind.DOORBELL)>42);
            assertEquals(42,store.watchConfirmedAt(Policy.Kind.KNOCK));
            assertEquals(0,store.watchConfirmedAt(Policy.Kind.ALARM));
        }
    }
    @Test public void pendingFilterOnlyShowsUnansweredRealEvents(){
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        Alerts.show(app,app.policy.simulate(Policy.Kind.ALARM,1));
        try(var page=Robolectric.buildActivity(HistoryActivity.class).setup()){
            var screen=page.get();
            findButton(screen.layout,"只看待反馈的真实提醒").performClick();
            assertNull(findButton(screen.layout,"已看到"));
            Alerts.show(app,app.policy.observe(Policy.Kind.KNOCK,.9f,20000).alert);
            screen.refresh();
            findButton(screen.layout,"已看到").performClick();
            shadowOf(Looper.getMainLooper()).idle();
            screen.refresh();
            assertNull(findButton(screen.layout,"已看到"));
        }
    }
}
