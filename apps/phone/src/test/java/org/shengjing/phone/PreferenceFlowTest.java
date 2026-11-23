package org.shengjing.phone;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import java.util.concurrent.TimeUnit;
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
public final class PreferenceFlowTest {
    private final App app = (App) RuntimeEnvironment.getApplication();
    private static final Policy.Scene HOME = Policy.Scene.HOME;
    private static final Policy.Kind KNOCK = Policy.Kind.KNOCK;

    private long add(long time, boolean simulated, EventHistory.Delivery delivery) {
        Policy.Alert alert = simulated ? app.policy.simulate(KNOCK,time) : app.policy.observe(KNOCK,.8f,time).alert;
        EventHistory.Entry entry = app.history.add(alert,time,delivery);
        app.saveHistory();
        return entry.id;
    }
    private void feedback(long id, String action) {
        new FeedbackReceiver().onReceive(app,new Intent(action).putExtra("id",id));
    }
    static Button findButton(View view, String label) {
        if (view instanceof Button && label.contentEquals(((Button) view).getText())) return (Button)view;
        if (view instanceof ViewGroup group) {
            for (int i=0;i<group.getChildCount();i++) {
                Button found=findButton(group.getChildAt(i),label);
                if (found!=null) return found;
            }
        }
        return null;
    }
    @Test public void feedbackToConfirmedIntervalToUndoThroughHistoryScreen() {
        long first=add(0,false,EventHistory.Delivery.REQUESTED);
        feedback(first,"ACK");feedback(first,"TOO_FREQUENT");feedback(first,"TOO_FREQUENT");
        assertEquals(EventHistory.Feedback.ACK_TOO_FREQUENT,app.history.find(first).feedback);
        assertEquals(1,app.frequentReports(HOME,KNOCK));
        assertFalse(app.chooseInterval(HOME,KNOCK,true));
        long second=add(20000,false,EventHistory.Delivery.REQUESTED);
        try(var screen=Robolectric.buildActivity(HistoryActivity.class).setup()) {
            View root=screen.get().getWindow().getDecorView();
            findButton(root,"声音认对了，但提醒太频繁").performClick();
            shadowOf(Looper.getMainLooper()).idleFor(1100,TimeUnit.MILLISECONDS);
            assertEquals(EventHistory.Feedback.TOO_FREQUENT,new LocalStore(app).loadHistory().find(second).feedback);
            assertEquals(15000,app.policy.cooldown(KNOCK));
            findButton(root,"采用 60 秒间隔").performClick();
            assertEquals("cooldown",app.policy.observe(KNOCK,.8f,40000).reason);
            Policy restored=new Policy();
            new LocalStore(app).restoreSettings(restored);
            assertEquals(60000,restored.cooldown(KNOCK));
            assertEquals(15000,restored.cooldown(Policy.Kind.DOORBELL));
            assertFalse(restored.isLessFrequent(Policy.Scene.STUDY,KNOCK));
            app.clearHistory();
            shadowOf(Looper.getMainLooper()).idleFor(1100,TimeUnit.MILLISECONDS);
            findButton(root,"恢复 15 秒").performClick();
            new LocalStore(app).restoreSettings(restored);
            assertEquals(15000,restored.cooldown(KNOCK));
            assertNotNull(app.policy.observe(KNOCK,.8f,45000).alert);
        }
    }
    @Test public void filtersAndDismissalSurviveReload() {
        long simulation=add(0,true,EventHistory.Delivery.REQUESTED);
        long blocked=add(1,false,EventHistory.Delivery.BLOCKED);
        feedback(simulation,"TOO_FREQUENT");feedback(blocked,"TOO_FREQUENT");
        long wrong=add(20000,false,EventHistory.Delivery.REQUESTED);
        feedback(wrong,"FALSE_ALARM");feedback(wrong,"TOO_FREQUENT");
        assertEquals(0,app.frequentReports(HOME,KNOCK));
        feedback(add(40000,false,EventHistory.Delivery.REQUESTED),"TOO_FREQUENT");
        feedback(add(60000,false,EventHistory.Delivery.REQUESTED),"TOO_FREQUENT");
        assertEquals(2,app.frequentReports(HOME,KNOCK));
        try(var screen=Robolectric.buildActivity(HistoryActivity.class).setup()) {
            findButton(screen.get().getWindow().getDecorView(),"保留当前间隔").performClick();
        }
        app.store=new LocalStore(app);app.history=app.store.loadHistory();
        assertEquals(0,app.frequentReports(HOME,KNOCK));
        assertEquals(15000,app.policy.cooldown(KNOCK));
        feedback(add(80000,false,EventHistory.Delivery.REQUESTED),"TOO_FREQUENT");
        assertEquals(1,app.frequentReports(HOME,KNOCK));
        assertFalse(app.chooseInterval(HOME,Policy.Kind.ALARM,true));
    }
    @Test public void previewDoesNotConsumeLiveCooldownOrCreateHistory() {
        try(var screen=Robolectric.buildActivity(OptionsActivity.class).setup()) {
            findButton(screen.get().getWindow().getDecorView(),"预览当前场景的敲门策略").performClick();
            assertTrue(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().isShowing());
            assertTrue(app.history.snapshot().isEmpty());
            assertNotNull(app.policy.observe(KNOCK,.8f,0).alert);
            assertFalse(app.listening);
        }
    }
}
