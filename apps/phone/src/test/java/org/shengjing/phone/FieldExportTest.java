package org.shengjing.phone;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.Manifest;
import android.os.Looper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.shengjing.core.Policy;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class FieldExportTest {
    private final App app=(App)RuntimeEnvironment.getApplication();
    @Test public void freezesRelativeEventsAndOmitsWatchAssumptions() throws Exception {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        app.fieldRound.begin(app.history,app.policy);
        Alerts.show(app,app.policy.simulate(Policy.Kind.DOORBELL,0));
        shadowOf(Looper.getMainLooper()).idleFor(1,TimeUnit.SECONDS);
        Alerts.show(app,app.policy.observe(Policy.Kind.KNOCK,.9f,1000).alert);
        app.fieldRound.observeWatch(true);
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
        shadowOf(Looper.getMainLooper()).idleFor(1,TimeUnit.SECONDS);
        Alerts.show(app,app.policy.observe(Policy.Kind.ALARM,.9f,2000).alert);
        shadowOf(Looper.getMainLooper()).idleFor(1,TimeUnit.SECONDS);
        app.fieldRound.complete(app.history);
        String frozen=new FieldRound(app).exportJson();
        JSONObject doc=new JSONObject(frozen);
        assertEquals(1,doc.getInt("simulated_excluded"));
        assertEquals(2,doc.getJSONArray("phone").length());
        JSONObject first=doc.getJSONArray("phone").getJSONObject(0);
        assertEquals("knock",first.getString("label"));
        assertFalse(first.has("wrist"));
        assertEquals("requested",first.getString("delivery"));
        assertEquals("blocked",doc.getJSONArray("phone").getJSONObject(1).getString("delivery"));
        String fixture=System.getenv("FIELD_EXPORT_FIXTURE");
        if(fixture!=null)Files.writeString(Path.of(fixture),frozen);
        app.clearHistory();
        assertNull(app.fieldRound.exportJson());
    }
    @Test public void interruptionAndOverLimitCannotBecomeCompleteExports(){
        app.fieldRound.begin(app.history,app.policy);
        new FieldRound(app).finish(app.history,"中断");
        assertNull(app.fieldRound.exportJson());
        app.fieldRound.begin(app.history,app.policy);
        for(int i=0;i<101;i++){
            Alerts.show(app,app.policy.observe(Policy.Kind.KNOCK,.9f,i*20000L).alert);
        }
        shadowOf(Looper.getMainLooper()).idleFor(1,TimeUnit.SECONDS);
        app.fieldRound.complete(app.history);
        assertNull(app.fieldRound.exportJson());
        assertTrue(app.fieldRound.summary(app.history).contains("超过 100 条"));
    }
}
