package org.shengjing.phone;

import android.content.*;
import android.os.*;
import android.widget.*;
import org.shengjing.core.Policy;
public final class OptionsActivity extends PhoneScreen {
    private TextView sceneRules;
    private Switch quiet;
    @Override void refresh(){quiet.setChecked(app.policy.isQuiet());}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        heading(layout,"场景与提醒设置",28);
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
        quiet=new Switch(this); quiet.setText("暂停真实声音提醒（监听仍继续）"); layout.addView(quiet);
        quiet.setChecked(app.policy.isQuiet());
        quiet.setOnCheckedChangeListener((b,on)->app.setQuiet(on));
        for(Policy.Kind k:Policy.Kind.values()) {
            Switch enabled=new Switch(this); enabled.setText("关注："+k.title); enabled.setChecked(app.policy.isEnabled(k)); layout.addView(enabled);
            enabled.setOnCheckedChangeListener((b,on)->{app.policy.setEnabled(k,on);app.saveSettings();});
        }
        button(layout,"返回首页",this::finish);
        for(int i=0;i<layout.getChildCount();i++){
            if(layout.getChildAt(i) instanceof Switch toggle){
                toggle.setMinHeight(dp(52));toggle.setTextSize(18);toggle.setSingleLine(false);
            }
        }
        for(int i=0;i<scenes.getChildCount();i++){
            RadioButton choice=(RadioButton)scenes.getChildAt(i);
            choice.setMinHeight(dp(48));choice.setTextSize(18);
        }
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
}
