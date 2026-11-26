package org.shengjing.phone;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static org.shengjing.phone.ListeningSession.State.*;
import static org.shengjing.phone.PreferenceFlowTest.findButton;

import android.Manifest;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class ListeningRecoveryTest {
    private final App app = (App) RuntimeEnvironment.getApplication();

    @Test public void processLossRestoresOnlyLastSavedProgressAndDoesNotListen() {
        ListeningSession session = app.sessionReport;
        assertEquals(NONE, session.state);
        session.begin(1000);
        session.completedWindow(2000);
        session.completedWindow(33000);
        session.completedWindow(34000);
        assertEquals(6000, session.sinceLastWindow(40000));
        app.sessionReport = new ListeningSession(app);
        ListeningSession restored = app.sessionReport;
        assertEquals(INTERRUPTED, restored.state);
        assertEquals(2, restored.windows);
        assertEquals(32000, restored.lastWindowMs);
        assertEquals(31000, restored.largestGapMs);
        assertEquals(restored.summary(90000), restored.summary(1));
        try (var screen = Robolectric.buildActivity(MainActivity.class).setup()) {
            var recovery = findButton(screen.get().layout, "检查设备设置");
            assertEquals(View.VISIBLE, recovery.getVisibility());
            recovery.performClick();
            assertEquals(SetupActivity.class.getName(),
                shadowOf(screen.get()).getNextStartedActivity().getComponent().getClassName());
            assertNull(shadowOf(app).getNextStartedService());
            assertFalse(app.listening);
        }
    }

    @Test public void failureSurvivesTeardownAndRetryStartsFresh() {
        ListeningSession session = app.sessionReport;
        session.begin(5000);
        session.completedWindow(6000);
        session.finish(SILENCED, 7000);
        session.completedWindow(8000);
        session.finish(INTERRUPTED, 9000);
        session = new ListeningSession(app);
        assertEquals(SILENCED, session.state);
        assertEquals(2000, session.elapsedMs);
        assertEquals(1, session.windows);
        session.begin(10000);
        assertEquals(0, session.windows);
        assertEquals(0, session.largestGapMs);
        session.finish(STOPPED, 10100);
        assertEquals(STOPPED, new ListeningSession(app).state);
    }

    @Test public void notificationStopDuringStartupWinsOverQueuedWorkerCallbacks() {
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO);
        var controller = Robolectric.buildService(ListeningService.class).create();
        ListeningService service = controller.get();
        service.onStartCommand(new Intent(), 0, 1);
        assertEquals(RUNNING, app.sessionReport.state);
        service.onStartCommand(new Intent("STOP"), 0, 2);
        controller.destroy();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(STOPPED, new ListeningSession(app).state);
        assertFalse(app.listening);
    }

    @Test public void serviceWithoutMicrophonePermissionKeepsSpecificRecoveryHint() throws Exception {
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO);
        var controller = Robolectric.buildService(ListeningService.class).create();
        try {
            controller.get().onStartCommand(new Intent(), 0, 1);
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(4);
            while (app.sessionReport.state == RUNNING && System.nanoTime() < deadline) {
                Thread.sleep(10);
                shadowOf(Looper.getMainLooper()).idle();
            }
            assertEquals(PERMISSION, app.sessionReport.state);
        } finally {
            controller.destroy();
        }
        assertEquals(PERMISSION, new ListeningSession(app).state);
        assertEquals(PERMISSION.title, app.status);
    }
}
