package org.shengjing.phone;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.widget.*;
import org.shengjing.core.Policy;

public final class MainActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status;
    private App app;
    private final Runnable refresh=new Runnable() { public void run() {
        status.setText(app.status+"\n\n"+app.lastEvent+"\n\n"+app.lastFeedback);
        handler.postDelayed(this,500);
    }};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); app=(App)getApplication();
        ScrollView scroll=new ScrollView(this); LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(32,60,32,48); scroll.addView(layout); setContentView(scroll);
        text(layout,"声境",30);
        text(layout,"重要声音，看得见，也能感受到",18);
        text(layout,"开发测试版 · 离线识别\n声音仅在内存处理，不保存录音。模型尚未针对你的环境校准，不能作为安全报警保障。",16);
        status=text(layout,"",18);
        button(layout,"开始监听",()-> {
            if(!permissions()) return;
            try { startForegroundService(new Intent(this,ListeningService.class)); }
            catch(RuntimeException e) { app.status="无法启动监听，请保持应用在前台并检查麦克风权限"; }
        });
        button(layout,"停止监听",()->stopService(new Intent(this,ListeningService.class)));
        Switch quiet=new Switch(this); quiet.setText("暂停真实声音提醒（监听仍继续）"); layout.addView(quiet);
        quiet.setChecked(app.policy.isQuiet());
        quiet.setOnCheckedChangeListener((b,on)->app.policy.setQuiet(on));
        for(Policy.Kind k:Policy.Kind.values()) {
            Switch enabled=new Switch(this); enabled.setText("关注："+k.title); enabled.setChecked(app.policy.isEnabled(k)); layout.addView(enabled);
            enabled.setOnCheckedChangeListener((b,on)->app.policy.setEnabled(k,on));
        }
        text(layout,"设备连通测试（下列按钮不代表识别成功）",16);
        for(Policy.Kind k:Policy.Kind.values()) button(layout,"发送模拟"+k.title,()-> {
            if(!notificationPermission()) return;
            Alerts.show(this,app.policy.simulate(k,SystemClock.elapsedRealtime()));
        });
        button(layout,"确认最近一次提醒",()->sendBroadcast(new Intent(this,FeedbackReceiver.class).setAction("ACK").putExtra("id",app.latestId)));
        button(layout,"最近一次是误报",()->sendBroadcast(new Intent(this,FeedbackReceiver.class).setAction("FALSE_ALARM").putExtra("id",app.latestId)));
        text(layout,"手表：先在配套健康应用中允许同步声境通知。能否显示操作按钮、回传确认、保留震动模式，均需 Watch X2 实测。",16);
    }
    private boolean notificationPermission() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1); app.status="授权通知后，请重新点击操作"; return false;
        }
        if(!getSystemService(android.app.NotificationManager.class).areNotificationsEnabled()) {
            app.status="系统通知已关闭，请在应用通知设置中开启"; return false;
        }
        return true;
    }
    private boolean permissions() {
        if(!notificationPermission()) return false;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},2); app.status="授权麦克风后，请再次点击开始监听"; return false;
        }
        return true;
    }
    private TextView text(LinearLayout parent,String value,int size) {
        TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setPadding(0,12,0,12);parent.addView(v);return v;
    }
    private void button(LinearLayout parent,String title,Runnable action) {
        Button b=new Button(this); b.setText(title);b.setAllCaps(false);parent.addView(b);b.setOnClickListener(v->action.run());
    }
    @Override public void onResume(){super.onResume();handler.post(refresh);}
    @Override public void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}
