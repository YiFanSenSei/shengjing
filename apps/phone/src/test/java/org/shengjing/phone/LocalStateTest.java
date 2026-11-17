package org.shengjing.phone;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
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
public final class LocalStateTest {
    private App app() { return (App)RuntimeEnvironment.getApplication(); }
    @Test public void settingsSurviveReloadWithoutRestartingMicrophone() {
        App app=app();app.policy.setEnhanced(true);app.policy.setQuiet(true);app.policy.setScene(Policy.Scene.STUDY);
        app.policy.setEnabled(Policy.Kind.ALARM,false);app.saveSettings();
        Policy restored=new Policy();new LocalStore(app).restoreSettings(restored);
        assertTrue(restored.isEnhanced());assertTrue(restored.isQuiet());
        assertEquals(Policy.Scene.STUDY,restored.getScene());assertFalse(restored.isEnabled(Policy.Kind.ALARM));
        assertTrue(restored.isEnabled(Policy.Kind.DOORBELL));assertFalse(app.listening);
    }
    @Test public void notificationActionSurvivesHistoryReloadAndPolicyReset() throws Exception {
        App app=app();shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        Alerts.show(app,app.policy.simulate(Policy.Kind.KNOCK,0));long id=app.latestId;
        NotificationManager nm=app.getSystemService(NotificationManager.class);
        Notification notice=nm.getActiveNotifications()[0].getNotification();
        app.history=new LocalStore(app).loadHistory();app.policy.resetListeningSession();
        notice.actions[0].actionIntent.send();shadowOf(Looper.getMainLooper()).idle();
        assertEquals(EventHistory.Feedback.ACK,app.history.find(id).feedback);
        assertEquals(EventHistory.Feedback.ACK,new LocalStore(app).loadHistory().find(id).feedback);
        assertEquals(0,nm.getActiveNotifications().length);
        notice.actions[1].actionIntent.send();shadowOf(Looper.getMainLooper()).idle();
        assertEquals(EventHistory.Feedback.ACK,app.history.find(id).feedback);
    }
    @Test public void blockedPermissionIsRecordedWithoutPretendingDelivery() {
        App app=app();shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
        Alerts.show(app,app.policy.simulate(Policy.Kind.ALARM,0));
        assertEquals(EventHistory.Delivery.BLOCKED,new LocalStore(app).loadHistory().find(app.latestId).delivery);
        assertEquals(0,app.getSystemService(NotificationManager.class).getActiveNotifications().length);
    }
    @Test public void clearRetractsNotificationsAndOldFeedbackCannotAffectNextRecord() {
        App app=app();shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        Alerts.show(app,app.policy.simulate(Policy.Kind.DOORBELL,0));long old=app.latestId;
        app.policy.setScene(Policy.Scene.STUDY);app.saveSettings();app.clearHistory();
        assertTrue(new LocalStore(app).loadHistory().snapshot().isEmpty());
        assertEquals(0,app.getSystemService(NotificationManager.class).getActiveNotifications().length);
        Alerts.show(app,app.policy.simulate(Policy.Kind.KNOCK,1));long current=app.latestId;
        new FeedbackReceiver().onReceive(app,new Intent().setAction("FALSE_ALARM").putExtra("id",old));
        assertNotEquals(old,current);assertEquals(EventHistory.Feedback.PENDING,app.history.find(current).feedback);
        assertEquals("study_knock",app.getSystemService(NotificationManager.class).getActiveNotifications()[0].getNotification().getChannelId());
        Policy restored=new Policy();new LocalStore(app).restoreSettings(restored);assertEquals(Policy.Scene.STUDY,restored.getScene());
    }
    @Test public void damagedHistoryDoesNotDiscardValidSettings() {
        App app=app();app.policy.setEnhanced(true);app.saveSettings();
        app.getSharedPreferences("shengjing_local_v1",Context.MODE_PRIVATE).edit().putString("history","[{}]").apply();
        assertTrue(new LocalStore(app).loadHistory().snapshot().isEmpty());
        Policy restored=new Policy();new LocalStore(app).restoreSettings(restored);assertTrue(restored.isEnhanced());
    }
    @Test public void screensOpenWithStoredStudySceneAndHistory() {
        App app=app();app.policy.setScene(Policy.Scene.STUDY);app.saveSettings();
        Alerts.show(app,app.policy.simulate(Policy.Kind.KNOCK,0));
        try(var main=Robolectric.buildActivity(MainActivity.class).setup();var history=Robolectric.buildActivity(HistoryActivity.class).setup()) {
            assertNotNull(main.get());assertNotNull(history.get());assertFalse(app.listening);
        }
    }
}
