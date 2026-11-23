package org.shengjing.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.shengjing.core.EventHistory;
import org.shengjing.core.Policy;

public final class HistoryActivity extends Activity {
    private App app;
    private LinearLayout records;
    private long shownRevision=-1;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable() { public void run() {
        if(shownRevision!=app.historyRevision) render();
        handler.postDelayed(this,1000);
    }};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);app=(App)getApplication();
        ScrollView scroll=new ScrollView(this);LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(32,60,32,48);scroll.addView(layout);setContentView(scroll);
        text(layout,"提醒记录",26);
        text(layout,"仅保存在这台手机，最多 100 条；不含录音、转写或位置。这里只记录达到提醒条件的事件，不是完整声音日志。\n“已请求通知”不代表手机已振动或手表已收到。反馈暂不自动训练模型。",16);
        button(layout,"返回监听页面",this::finish);
        button(layout,"清空全部提醒记录",()->new AlertDialog.Builder(this).setTitle("清空本机提醒记录？")
            .setMessage("删除全部记录和反馈，同时撤回本应用对应的提醒通知；设置和监听状态保留。此操作无法撤销。")
            .setNegativeButton("取消",null).setPositiveButton("清空",(dialog,which)->{app.clearHistory();render();}).show());
        records=new LinearLayout(this);records.setOrientation(LinearLayout.VERTICAL);layout.addView(records);render();
    }
    private void render() {
        records.removeAllViews();List<EventHistory.Entry> entries=app.history.snapshot();shownRevision=app.historyRevision;
        renderPreferences();
        if(entries.isEmpty()) { text(records,"暂无记录。开始监听或发送一次模拟提醒后，会显示在这里。",18);return; }
        DateFormat dates=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.MEDIUM);
        for(int i=entries.size()-1;i>=0;i--) {
            EventHistory.Entry e=entries.get(i);
            text(records,(e.simulated?"[模拟] ":"[识别] ")+e.kind.title+" · "+e.scene.title,20);
            text(records,dates.format(new Date(e.time))+"\n识别分数 "+String.format(Locale.ROOT,"%.3f",e.score)+"（不是准确率）\n"+
                deliveryText(e.delivery)+" · "+feedbackText(e.feedback),16);
            if(e.feedback==EventHistory.Feedback.PENDING) {
                button(records,"已看到",()->feedback(e.id,"ACK"));
                button(records,"声音认错了",()->feedback(e.id,"FALSE_ALARM"));
            }
            if (!e.simulated && e.delivery == EventHistory.Delivery.REQUESTED &&
                (e.feedback == EventHistory.Feedback.PENDING || e.feedback == EventHistory.Feedback.ACK)) {
                button(records,"声音认对了，但提醒太频繁",()->feedback(e.id,"TOO_FREQUENT"));
            }
        }
    }
    private static String deliveryText(EventHistory.Delivery value) {
        switch(value) { case BLOCKED:return "通知被系统设置阻止";case FAILED:return "通知发送失败";default:return "已请求通知"; }
    }
    static String feedbackText(EventHistory.Feedback value) {
        switch(value) {
            case ACK:return "已确认";
            case FALSE_ALARM:return "已标记误报";
            case TOO_FREQUENT:return "提醒太频繁";
            case ACK_TOO_FREQUENT:return "已确认，提醒太频繁";
            default:return "尚未反馈";
        }
    }
    private void renderPreferences() {
        for (Policy.Scene scene : Policy.Scene.values()) {
            for (Policy.Kind kind : Policy.Kind.values()) {
                if (kind == Policy.Kind.ALARM) continue;
                boolean active = app.policy.isLessFrequent(scene, kind);
                int count = app.frequentReports(scene, kind);
                if (!active && count < App.REPORTS_NEEDED) continue;
                long previous = Policy.defaultCooldown(scene, kind) / 1000;
                long extended = Policy.extendedCooldown(scene, kind) / 1000;
                String label = scene.title + " · " + kind.title;
                text(records, label, 20);
                if (active) {
                    text(records, "已按你的选择，将重复提醒间隔设为 " + extended + " 秒。", 16);
                    button(records, "恢复 " + previous + " 秒", () -> {app.chooseInterval(scene,kind,false);render();});
                } else {
                    text(records, "你对 " + count + " 条提醒反馈了“太频繁”。当前间隔 " + previous +
                        " 秒，可延长到 " + extended + " 秒。间隔内的新事件也可能被略过。", 16);
                    button(records, "采用 " + extended + " 秒间隔", () -> {app.chooseInterval(scene,kind,true);render();});
                    button(records, "保留当前间隔", () -> {app.chooseInterval(scene,kind,false);render();});
                }
            }
        }
    }
    private void feedback(long id,String action) {
        sendBroadcast(new Intent(this,FeedbackReceiver.class).setAction(action).putExtra("id",id));
    }
    private void text(LinearLayout parent,String content,int size) {
        TextView view=new TextView(this);view.setText(content);view.setTextSize(size);view.setPadding(0,12,0,12);parent.addView(view);
    }
    private void button(LinearLayout parent,String label,Runnable action) {
        Button view=new Button(this);view.setText(label);view.setAllCaps(false);parent.addView(view);view.setOnClickListener(v->action.run());
    }
    @Override public void onResume() { super.onResume();handler.post(refresh); }
    @Override public void onPause() { handler.removeCallbacks(refresh);super.onPause(); }
}
