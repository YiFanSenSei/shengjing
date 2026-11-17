package org.shengjing.phone;

import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import org.shengjing.core.Policy;

/** One user-triggered, bounded screen-off delivery test. All methods run on the main thread. */
final class DelayedTestReminder {
    private final App app;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;
    private Runnable pending;
    volatile String status="延迟测试：尚未安排";

    DelayedTestReminder(App app) { this.app=app; }

    void schedule() {
        clear();
        try {
            PowerManager power=app.getSystemService(PowerManager.class);
            wakeLock=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"shengjing:delayed-test");
            wakeLock.acquire(20_000L);
            pending=()-> {
                try {
                    Alerts.show(app,app.policy.simulate(Policy.Kind.KNOCK,SystemClock.elapsedRealtime()));
                    status="延迟测试：已请求发送模拟敲门，请检查手表";
                } catch(RuntimeException e) {
                    status="延迟测试：发送失败，请检查通知权限后重试";
                } finally { clear(); }
            };
            if(!handler.postDelayed(pending,10_000L)) throw new IllegalStateException();
            status="延迟测试：已安排，10 秒后发送；现在可以锁屏";
        } catch(RuntimeException e) {
            clear();
            status="延迟测试：未能安排，请重新打开应用后重试";
        }
    }

    void cancel() {
        boolean scheduled=pending!=null;
        clear();
        status=scheduled ? "延迟测试：已取消" : "延迟测试：没有待发送提醒";
    }

    private void clear() {
        if(pending!=null) { handler.removeCallbacks(pending); pending=null; }
        if(wakeLock!=null) {
            if(wakeLock.isHeld()) wakeLock.release();
            wakeLock=null;
        }
    }
}
