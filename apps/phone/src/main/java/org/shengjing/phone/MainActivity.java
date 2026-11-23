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
    private TextView diagnostics;
    private TextView readiness;
    private TextView sceneRules;
    private Button historyButton;
    private long renderedHistoryRevision = -1;
    private App app;
    private final Runnable refresh=new Runnable() { public void run() {
        status.setText(app.status+"\n\n"+app.lastEvent+"\n\n"+app.lastFeedback+"\n\n"+app.delayedTest.status);
        readiness.setText(readinessText());
        if (renderedHistoryRevision != app.historyRevision) {
            int suggestions = 0;
            for (Policy.Scene scene : Policy.Scene.values()) {
                for (Policy.Kind kind : Policy.Kind.values()) {
                    if (kind != Policy.Kind.ALARM && !app.policy.isLessFrequent(scene, kind) &&
                        app.frequentReports(scene,kind) >= App.REPORTS_NEEDED) suggestions++;
                }
            }
            historyButton.setText(suggestions == 0 ? "查看提醒记录与反馈" : "查看提醒记录与反馈（"+suggestions+" 条建议）");
            renderedHistoryRevision = app.historyRevision;
        }
        long age=app.diagnosticsUpdatedAt==0 ? 0 : Math.max(0,(SystemClock.elapsedRealtime()-app.diagnosticsUpdatedAt)/1000);
        diagnostics.setText(app.diagnostics+(app.listening && age>=3 ? "\n已 "+age+" 秒未更新采集诊断" : ""));
        handler.postDelayed(this,1000);
    }};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); app=(App)getApplication();
        ScrollView scroll=new ScrollView(this); LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(32,60,32,48); scroll.addView(layout); setContentView(scroll);
        text(layout,"声境",30);
        text(layout,"重要声音，看得见，也能感受到",18);
        text(layout,"0.5.1 开发测试版 · 离线识别\n不保存或上传录音。本机保留最近 100 条提醒摘要，可随时清空。不能作为安全报警保障。",16);
        status=text(layout,"",18);
        readiness=text(layout,"",16);
        historyButton=button(layout,"查看提醒记录与反馈",()->startActivity(new Intent(this,HistoryActivity.class)));
        button(layout,"开始监听",()-> {
            if(!permissions()) return;
            try { startForegroundService(new Intent(this,ListeningService.class)); }
            catch(RuntimeException e) { app.status="无法启动监听，请保持应用在前台并检查麦克风权限"; }
        });
        button(layout,"停止监听",()->stopService(new Intent(this,ListeningService.class)));
        text(layout,"使用场景（手动选择，自动执行对应规则）",20);
        RadioGroup scenes=new RadioGroup(this);layout.addView(scenes);
        for(Policy.Scene scene:Policy.Scene.values()) {
            RadioButton choice=new RadioButton(this);choice.setText(scene.title);choice.setId(android.view.View.generateViewId());scenes.addView(choice);
            choice.setChecked(app.policy.getScene()==scene);
            choice.setOnCheckedChangeListener((view,checked)-> {
                if(checked) { app.policy.setScene(scene);app.saveSettings();updateSceneRules(); }
            });
        }
        sceneRules=text(layout,"",16);updateSceneRules();
        button(layout,"预览当前场景的敲门策略",this::previewKnocks);
        Switch enhanced=new Switch(this); enhanced.setText("敲门 / 闹钟增强模式（试验）");layout.addView(enhanced);
        enhanced.setChecked(app.policy.isEnhanced());
        enhanced.setOnCheckedChangeListener((button,on)->{app.policy.setEnhanced(on);app.saveSettings();});
        text(layout,"增强模式更容易触发提醒，也可能增加误报。场景、增强模式、关注类别和暂停状态会保存在本机。门铃保持原阈值。",14);
        Switch showDiagnostics=new Switch(this); showDiagnostics.setText("显示识别诊断（仅本机内存）"); layout.addView(showDiagnostics);
        diagnostics=text(layout,app.diagnostics,14); diagnostics.setVisibility(android.view.View.GONE);
        showDiagnostics.setOnCheckedChangeListener((button,on)->diagnostics.setVisibility(on ? android.view.View.VISIBLE : android.view.View.GONE));
        Switch quiet=new Switch(this); quiet.setText("暂停真实声音提醒（监听仍继续）"); layout.addView(quiet);
        quiet.setChecked(app.policy.isQuiet());
        quiet.setOnCheckedChangeListener((b,on)->{app.policy.setQuiet(on);app.saveSettings();});
        for(Policy.Kind k:Policy.Kind.values()) {
            Switch enabled=new Switch(this); enabled.setText("关注："+k.title); enabled.setChecked(app.policy.isEnabled(k)); layout.addView(enabled);
            enabled.setOnCheckedChangeListener((b,on)->{app.policy.setEnabled(k,on);app.saveSettings();});
        }
        button(layout,"打开系统通知设置",()->openSettings(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName())));
        button(layout,"打开应用权限与电池设置",()->openSettings(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.parse("package:"+getPackageName()))));
        text(layout,"设备连通测试（下列按钮不代表识别成功）",16);
        for(Policy.Kind k:Policy.Kind.values()) button(layout,"发送模拟"+k.title,()-> {
            if(!notificationPermission()) return;
            Alerts.show(this,app.policy.simulate(k,SystemClock.elapsedRealtime()));
        });
        button(layout,"10 秒后发送模拟敲门（锁屏测试）",()-> {
            if(notificationPermission()) app.delayedTest.schedule();
        });
        button(layout,"取消延迟测试",()->app.delayedTest.cancel());
        text(layout,"锁屏测试：点击延迟按钮后手动锁屏，观察手表。测试会短暂保持处理器运行，最多 20 秒，不点亮屏幕；不代表长期后台监听已通过。",14);
        button(layout,"确认最近一次提醒",()->sendBroadcast(new Intent(this,FeedbackReceiver.class).setAction("ACK").putExtra("id",app.latestId)));
        button(layout,"最近一次是误报",()->sendBroadcast(new Intent(this,FeedbackReceiver.class).setAction("FALSE_ALARM").putExtra("id",app.latestId)));
        text(layout,"手表：先在配套健康应用中允许同步声境通知。能否显示操作按钮、回传确认、保留震动模式，均需 Watch X2 实测。",16);
    }
    private void updateSceneRules() {
        if(sceneRules==null) return;
        StringBuilder rules = new StringBuilder("两次同类提醒的最短间隔");
        for (Policy.Kind kind : Policy.Kind.values()) {
            rules.append("\n").append(kind.title).append("  ").append(app.policy.cooldown(kind)/1000).append(" 秒");
            if (app.policy.isLessFrequent(app.policy.getScene(), kind)) rules.append("（个人偏好）");
        }
        sceneRules.setText(rules.toString());
    }
    private void previewKnocks() {
        Policy preview = new Policy();
        Policy.Scene scene = app.policy.getScene();
        Policy.Kind kind = Policy.Kind.KNOCK;
        preview.setScene(scene);
        preview.setLessFrequent(scene, kind, app.policy.isLessFrequent(scene, kind));
        preview.setQuiet(app.policy.isQuiet());
        preview.setEnabled(kind, app.policy.isEnabled(kind));
        preview.setEnhanced(app.policy.isEnhanced());
        int[] seconds = {0, 1, 20, 40, 65, 125};
        String[] situations = {"首次敲门", "连续敲击", "原来客再敲", "另一位来客", "再次敲门", "再次敲门"};
        StringBuilder lines = new StringBuilder("示例事件使用固定识别分数 0.80。来客身份由示例设定。\n");
        for (int i=0; i<seconds.length; i++) {
            Policy.Decision decision=preview.observe(kind,.80f,seconds[i]*1000L);
            lines.append("\n").append(seconds[i]).append(" 秒 · ").append(situations[i]).append(" · ")
                .append(org.shengjing.core.AudioDiagnostics.reasonText(decision.reason));
        }
        new android.app.AlertDialog.Builder(this).setTitle(scene.title+" · 离线策略预览")
            .setMessage(lines.toString()).setPositiveButton("关闭",null).show();
    }
    private String readinessText() {
        StringBuilder text=new StringBuilder("当前场景：").append(app.policy.getScene().title)
            .append(" · ").append(app.policy.isEnhanced()?"增强模式":"标准模式")
            .append("\n真实提醒：").append(app.policy.isQuiet()?"已暂停（重启后仍保留暂停）":"已开启")
            .append("\n麦克风权限：").append(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED?"已允许":"未允许");
        android.app.NotificationManager nm=getSystemService(android.app.NotificationManager.class);
        int enabled=0;
        for(Policy.Kind kind:Policy.Kind.values()) {
            if(!app.policy.isEnabled(kind)) continue;
            enabled++;
            String channel=Alerts.channel(kind,app.policy.getScene());
            text.append("\n").append(kind.title).append("通知：");
            if(!Alerts.available(this,channel)) text.append("被阻止，请检查系统通知设置");
            else {
                android.app.NotificationChannel settings=nm.getNotificationChannel(channel);
                text.append(settings.shouldVibrate()?"已允许振动":"振动关闭");
            }
        }
        if(enabled==0) text.append("\n所有声音类别均已关闭，不会产生真实提醒");
        PowerManager power=getSystemService(PowerManager.class);
        text.append("\n系统电池优化：").append(power.isIgnoringBatteryOptimizations(getPackageName())?"已豁免":"仍受优化，长时间锁屏需验证");
        text.append("\n手表同步状态无法自动确认，请在配套健康应用中检查。");
        return text.toString();
    }
    private void openSettings(Intent intent) {
        try { startActivity(intent); }
        catch(ActivityNotFoundException e) { app.status="请手动进入系统设置 → 应用 → 声境"; }
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
    private Button button(LinearLayout parent,String title,Runnable action) {
        Button b=new Button(this); b.setText(title);b.setAllCaps(false);parent.addView(b);b.setOnClickListener(v->action.run());
        return b;
    }
    @Override public void onResume(){super.onResume();updateSceneRules();handler.post(refresh);}
    @Override public void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}
