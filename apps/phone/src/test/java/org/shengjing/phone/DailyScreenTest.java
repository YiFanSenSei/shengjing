package org.shengjing.phone;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static org.shengjing.phone.PreferenceFlowTest.findButton;

import android.Manifest;
import android.content.res.Configuration;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class DailyScreenTest {
    private final App app=(App) RuntimeEnvironment.getApplication();

    @Test public void firstStartDirectsToSetupBeforeStartingTheMicrophone() {
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO);
        try (var screen=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity home=screen.get();
            findButton(home.layout,"开始监听").performClick();
            assertEquals(SetupActivity.class.getName(),
                shadowOf(home).getNextStartedActivity().getComponent().getClassName());
            assertNull(shadowOf(app).getNextStartedService());
        }
    }

    @Test public void confirmingAWatchTestRequiresAnAvailableRecordAndExplicitTap() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        try (var screen=Robolectric.buildActivity(SetupActivity.class).setup()) {
            SetupActivity setup=screen.get();
            Button confirm=findButton(setup.layout,"我在手表上看到了");
            Button send=findButton(setup.layout,"发送手表测试提醒");
            assertEquals(View.GONE,confirm.getVisibility());
            send.performClick();
            assertEquals(View.VISIBLE,confirm.getVisibility());
            assertEquals(0,app.store.watchConfirmedAt());
            app.clearHistory();
            confirm.performClick();
            assertEquals(0,app.store.watchConfirmedAt());
            send.performClick();
            confirm.performClick();
            assertTrue(new LocalStore(app).watchConfirmedAt()>0);
            assertEquals(View.GONE,confirm.getVisibility());
        }
    }

    @Test public void deniedNotificationsCannotCreateAConfirmableWatchTest() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
        try (var screen=Robolectric.buildActivity(SetupActivity.class).setup()) {
            SetupActivity setup=screen.get();
            findButton(setup.layout,"发送手表测试提醒").performClick();
            assertTrue(app.history.snapshot().isEmpty());
            assertEquals(View.GONE,findButton(setup.layout,"我在手表上看到了").getVisibility());
        }
    }

    @Test public void smallScreenWithDoubleFontCanScrollToTheLastControl() {
        try (var screen=Robolectric.buildActivity(MainActivity.class).setup()) {
            Configuration large=new Configuration(screen.get().getResources().getConfiguration());
            large.fontScale=2f;
            screen.configurationChange(large);
            MainActivity home=screen.get();
            ScrollView scroll=(ScrollView) home.layout.getParent();
            int width=home.dp(320), height=home.dp(600);
            scroll.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
            scroll.layout(0,0,width,height);
            assertEquals(2f,home.getResources().getConfiguration().fontScale,0.01f);
            assertTrue(home.layout.getHeight()>height);
            Button last=findButton(home.layout,"诊断与测试工具");
            assertTrue(last.getHeight()>=home.dp(48));
            assertTrue(last.getLayout().getHeight()<=last.getHeight()-last.getCompoundPaddingTop()-last.getCompoundPaddingBottom());
            scroll.scrollTo(0,last.getBottom()-height+scroll.getPaddingBottom());
            assertTrue(scroll.getScrollY()>0);
            assertTrue(last.getBottom()<=scroll.getScrollY()+height-scroll.getPaddingBottom());
        }
    }
}
