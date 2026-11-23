package org.shengjing.phone;

import android.app.Activity;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.Manifest;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.net.Uri;
import android.view.View;
import android.view.Gravity;
import android.widget.*;

abstract class PhoneScreen extends Activity {
    static final int INK=0xff142d29, GREEN=0xff15584b, PAPER=0xfff6f5ef;
    App app;
    LinearLayout layout;
    final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){refresh();handler.postDelayed(this,1000);}};

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        app=(App)getApplication();
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(PAPER);
        if(Build.VERSION.SDK_INT>=30){
            getWindow().setDecorFitsSystemWindows(false);
            scroll.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets edges=insets.getInsets(android.view.WindowInsets.Type.systemBars() |
                    android.view.WindowInsets.Type.displayCutout());
                v.setPadding(edges.left,edges.top,edges.right,edges.bottom);
                return insets;
            });
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20),dp(16),dp(20),dp(28));
        scroll.addView(layout);
        setContentView(scroll);
    }
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    GradientDrawable shape(int color,int radius){
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(color);bg.setCornerRadius(dp(radius));return bg;
    }
    LinearLayout card(){
        return card(layout);
    }
    LinearLayout card(LinearLayout parent){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16),dp(10),dp(16),dp(12));
        box.setBackground(shape(0xffffffff,16));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.setMargins(0,dp(8),0,dp(4));
        parent.addView(box,params);
        return box;
    }
    TextView text(LinearLayout parent,String value,int size){
        TextView view=new TextView(this);
        view.setText(value);view.setTextSize(size);view.setTextColor(INK);
        view.setPadding(0,dp(5),0,dp(5));view.setTextIsSelectable(false);
        if(size>=22)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        parent.addView(view);return view;
    }
    TextView heading(LinearLayout parent,String value,int size){
        TextView view=text(parent,value,size);
        if(Build.VERSION.SDK_INT>=28)view.setAccessibilityHeading(true);
        return view;
    }
    Button button(LinearLayout parent,String title,Runnable action){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setSingleLine(false);
        b.setTextSize(18);b.setMinHeight(dp(52));b.setTextColor(GREEN);
        b.setGravity(Gravity.CENTER);b.setPadding(dp(12),dp(9),dp(12),dp(9));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2215584b),shape(0xffe4eeea,10),null));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(4),0,dp(4));
        parent.addView(b,p);b.setOnClickListener(v->action.run());return b;
    }
    void primary(Button b){
        b.setTextColor(0xffffffff);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44ffffff),shape(GREEN,10),null));
    }
    void show(TextView view,String value){if(!value.contentEquals(view.getText()))view.setText(value);}
    void go(Class<? extends Activity> screen){startActivity(new Intent(this,screen));}
    boolean microphone(){return checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;}
    boolean notifications(){return getSystemService(android.app.NotificationManager.class).areNotificationsEnabled()
        && (Build.VERSION.SDK_INT<33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED);}
    void systemSettings(boolean notification){
        Intent intent=notification?new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName())
            :new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()));
        try{startActivity(intent);}catch(ActivityNotFoundException e){Toast.makeText(this,"请在系统设置中打开声境的应用详情",Toast.LENGTH_LONG).show();}
    }
    void refresh(){}
    @Override protected void onResume(){super.onResume();tick.run();}
    @Override protected void onPause(){handler.removeCallbacks(tick);super.onPause();}
}
