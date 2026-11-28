package org.shengjing.phone;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import android.os.Bundle;
import android.os.PowerManager;
import android.widget.*;
import org.shengjing.core.Policy;

public final class FieldDemoActivity extends PhoneScreen {
    private TextView readiness, result;
    private Button listen, begin, finish, copy, channel, options, setup;
    private LinearLayout wrist;
    private Button export;
    private String pendingRound;
    private String blockedChannel;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(state!=null)pendingRound=state.getString("pendingRound");
        heading(layout,"现场演示与锁屏核对",28);
        LinearLayout device=card();
        heading(device,"设备准备",22);
        readiness=text(device,"",18);
        setup=button(device,"设置手机权限与手表",()->go(SetupActivity.class));
        options=button(device,"调整场景与关注声音",()->go(OptionsActivity.class));
        channel=button(device,"打开受阻声音的通知设置",()->channelSettings(blockedChannel));
        listen=button(device,"开始监听",this::toggleListening);
        LinearLayout round=card();
        heading(round,"本轮演示",22);
        result=text(round,"",18);
        begin=button(round,"开始新一轮",()->{app.fieldRound.begin(app.history,app.policy);refresh();});
        wrist=new LinearLayout(this);wrist.setOrientation(LinearLayout.VERTICAL);round.addView(wrist);
        button(wrist,"本轮在手表看到了提醒",()->{app.fieldRound.observeWatch(true);refresh();});
        button(wrist,"本轮在手表未看到提醒",()->{app.fieldRound.observeWatch(false);refresh();});
        finish=button(round,"结束本轮并保存摘要",()->{app.fieldRound.complete(app.history);refresh();});
        copy=button(round,"复制本轮摘要",()->{
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("声境现场演示",app.fieldRound.summary(app.history)));
            Toast.makeText(this,"已复制本轮摘要",Toast.LENGTH_SHORT).show();
        });
        export=button(round,"保存本轮手机事件文件",this::chooseDestination);
        button(layout,"查看监听连续性",()->go(DiagnosticsActivity.class));
        button(layout,"返回首页",this::finish);
    }
    @Override void refresh(){
        boolean permissions=microphone() && notifications();
        boolean quiet=app.policy.isQuiet();
        int enabled=0;
        blockedChannel=null;
        StringBuilder check=new StringBuilder(app.status);
        if(!permissions)check.append("\n请开启麦克风和手机通知权限。");
        if(quiet)check.append("\n真实声音提醒已暂停。");
        for(Policy.Kind kind:Policy.Kind.values()){
            if(!app.policy.isEnabled(kind))continue;
            enabled++;
            String id=Alerts.channel(kind,app.policy.getScene());
            if(permissions && !Alerts.available(this,id)){
                check.append("\n").append(kind.title).append("的系统通知已关闭。");
                if(blockedChannel==null)blockedChannel=id;
            }
        }
        if(enabled==0)check.append("\n请至少关注一种声音。");
        PowerManager power=getSystemService(PowerManager.class);
        if(power.isPowerSaveMode())check.append("\n系统处于省电模式，请在锁屏测试后核对监听连续性。");
        if(app.store.watchConfirmedAt()==0)check.append("\n手表尚未手动核对，可先发送一次测试提醒。");
        show(readiness,check.toString());
        setup.setVisibility(!permissions || app.store.watchConfirmedAt()==0?Button.VISIBLE:Button.GONE);
        options.setVisibility(quiet || enabled==0?Button.VISIBLE:Button.GONE);
        channel.setVisibility(blockedChannel==null?Button.GONE:Button.VISIBLE);
        show(listen,app.listening?"停止监听":"开始监听");
        boolean active=app.fieldRound.active();
        begin.setEnabled(app.listening && !quiet && enabled>0 && permissions);
        begin.setVisibility(active?Button.GONE:Button.VISIBLE);
        wrist.setVisibility(active?Button.VISIBLE:Button.GONE);
        finish.setVisibility(active?Button.VISIBLE:Button.GONE);
        copy.setVisibility(!active && app.fieldRound.exists()?Button.VISIBLE:Button.GONE);
        export.setVisibility(app.fieldRound.exportJson()!=null?Button.VISIBLE:Button.GONE);
        show(result,(active?app.fieldRound.progress()+"\n":"")+app.fieldRound.summary(app.history));
    }
    private void chooseDestination(){
        pendingRound=app.fieldRound.roundCode();
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json").putExtra(Intent.EXTRA_TITLE,"shengjing-"+pendingRound+".json");
        try{startActivityForResult(intent,1);}
        catch(ActivityNotFoundException e){Toast.makeText(this,"没有可用的文件保存位置",Toast.LENGTH_LONG).show();}
    }
    @Override protected void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);state.putString("pendingRound",pendingRound);
    }
    @Override protected void onActivityResult(int request,int code,Intent data){
        super.onActivityResult(request,code,data);
        if(request!=1 || code!=RESULT_OK || data==null || data.getData()==null)return;
        String json=app.fieldRound.exportJson();
        if(json==null || !app.fieldRound.roundCode().equals(pendingRound)){
            Toast.makeText(this,"场次已变更，请重新选择",Toast.LENGTH_LONG).show();return;
        }
        var uri=data.getData();
        new Thread(()->{
            String message;
            try(OutputStream stream=getContentResolver().openOutputStream(uri,"wt")){
                if(stream==null)throw new IOException();
                stream.write(json.getBytes(StandardCharsets.UTF_8));
                message="手机事件文件已保存";
            }catch(IOException | RuntimeException e){message="保存失败，请重新选择位置";}
            String outcome=message;
            runOnUiThread(()->Toast.makeText(getApplicationContext(),outcome,Toast.LENGTH_LONG).show());
        },"field-export").start();
    }
}
