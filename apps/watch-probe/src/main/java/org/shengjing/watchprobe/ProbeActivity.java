package org.shengjing.watchprobe;

import android.app.Activity;
import android.os.*;
import android.widget.*;

public final class ProbeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);box.setPadding(35,45,35,45);scroll.addView(box);setContentView(scroll);
        TextView status=new TextView(this);status.setTextSize(18);
        status.setText("声境手表验证\n安装与启动已成功\nAndroid API "+Build.VERSION.SDK_INT+"\n本应用暂不连接手机");box.addView(status);
        Button test=new Button(this);test.setText("测试两次震动");box.addView(test);
        test.setOnClickListener(v->{
            Vibrator vibrator=(Vibrator)getSystemService(VIBRATOR_SERVICE);
            if(vibrator==null||!vibrator.hasVibrator()){status.setText("系统未报告可用振动器");return;}
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,180,100,180},-1));
            status.setText("已请求两次震动\n请确认实际是否感受到");
        });
    }
}
