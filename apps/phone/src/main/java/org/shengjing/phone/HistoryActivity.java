package org.shengjing.phone;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.shengjing.core.EventHistory;
import org.shengjing.core.Policy;

public final class HistoryActivity extends PhoneScreen {
    private LinearLayout records;
    private long shownRevision=-1;
    private boolean pendingOnly;
    @Override void refresh(){
        if(shownRevision!=app.historyRevision) render();
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(getIntent().hasExtra("eventId")){
            heading(layout,"这条提醒",28);
            records=new LinearLayout(this);records.setOrientation(LinearLayout.VERTICAL);layout.addView(records);
            button(layout,"查看全部提醒记录",()->{go(HistoryActivity.class);finish();});
            button(layout,"返回监听页面",this::returnHome);
            render();
            return;
        }
        heading(layout,"提醒记录",28);
        text(layout,"最近 100 条提醒保存在本机。可在记录上确认声音或反馈提醒太频繁。",17);
        button(layout,"返回监听页面",this::returnHome);
        pendingOnly=state!=null && state.getBoolean("pendingOnly");
        android.widget.CheckBox filter=new android.widget.CheckBox(this);
        filter.setText("只看待反馈的真实提醒");filter.setMinHeight(dp(48));filter.setTextSize(18);
        filter.setChecked(pendingOnly);layout.addView(filter);
        filter.setOnCheckedChangeListener((v,checked)->{pendingOnly=checked;render();});
        button(layout,"清空全部提醒记录",()->new AlertDialog.Builder(this).setTitle("清空本机提醒记录？")
            .setMessage("删除全部记录和反馈，同时撤回本应用对应的提醒通知；设置和监听状态保留。此操作无法撤销。")
            .setNegativeButton("取消",null).setPositiveButton("清空",(dialog,which)->{app.clearHistory();render();}).show());
        records=new LinearLayout(this);records.setOrientation(LinearLayout.VERTICAL);layout.addView(records);render();
    }
    private void render() {
        records.removeAllViews();shownRevision=app.historyRevision;
        if(getIntent().hasExtra("eventId")){
            EventHistory.Entry selected=app.history.find(getIntent().getLongExtra("eventId",-1));
            if(selected==null)text(records,"这条提醒已清空或已超过最近 100 条的保留范围。",18);
            else renderEntry(selected);
            return;
        }
        List<EventHistory.Entry> entries=app.history.snapshot();
        renderPreferences();
        if(entries.isEmpty()) { text(records,"暂无记录。开始监听或发送一次模拟提醒后，会显示在这里。",18);return; }
        boolean any=false;
        for(int i=entries.size()-1;i>=0;i--) {
            EventHistory.Entry e=entries.get(i);
            if(pendingOnly && (e.simulated || e.feedback!=EventHistory.Feedback.PENDING))continue;
            renderEntry(e);any=true;
        }
        if(!any)text(records,"当前没有待反馈的真实提醒。",18);
    }
    @Override protected void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);state.putBoolean("pendingOnly",pendingOnly);
    }
    private void returnHome(){
        startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
    private void renderEntry(EventHistory.Entry e){
        DateFormat dates=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.MEDIUM);
        LinearLayout item=card(records);
        heading(item,(e.simulated?"模拟 · ":"识别 · ")+e.kind.title+" · "+e.scene.title,20);
        text(item,dates.format(new Date(e.time))+"\n"+
            deliveryText(e.delivery)+" · "+feedbackText(e.feedback),16);
        if(!e.simulated)text(item,"模型分数 "+String.format(Locale.ROOT,"%.3f",e.score),16);
        if(e.feedback==EventHistory.Feedback.PENDING) {
            button(item,"已看到",()->feedback(e.id,"ACK"));
            button(item,"声音认错了",()->feedback(e.id,"FALSE_ALARM"));
        }
        if (!e.simulated && e.delivery == EventHistory.Delivery.REQUESTED &&
            (e.feedback == EventHistory.Feedback.PENDING || e.feedback == EventHistory.Feedback.ACK)) {
            button(item,"声音认对了，但提醒太频繁",()->feedback(e.id,"TOO_FREQUENT"));
        }
        if(e.delivery==EventHistory.Delivery.BLOCKED || getIntent().hasExtra("eventId")){
            button(item,"检查这类声音的通知设置",()->channelSettings(Alerts.channel(e.kind,e.scene)));
        }
    }
    static String deliveryText(EventHistory.Delivery value) {
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
}
