package org.shengjing.phone;

import android.app.*;
import android.content.*;
import org.shengjing.core.Policy;

final class Alerts {
    static final int SERVICE_ID=1;
    static void channels(Context c) {
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        NotificationChannel service=new NotificationChannel("listening","监听状态",NotificationManager.IMPORTANCE_LOW);
        service.setSound(null,null); service.enableVibration(false); nm.createNotificationChannel(service);
        for(Policy.Kind k:Policy.Kind.values()) {
            NotificationChannel channel=new NotificationChannel(k.name(),k.title+"提醒",NotificationManager.IMPORTANCE_HIGH);
            channel.setSound(null,null); channel.enableVibration(true);
            channel.setVibrationPattern(k==Policy.Kind.KNOCK ? new long[]{0,180,100,180} :
                k==Policy.Kind.DOORBELL ? new long[]{0,400,150,200} : new long[]{0,500,200,500});
            nm.createNotificationChannel(channel);
        }
    }
    static PendingIntent open(Context c) {
        return PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    }
    static Notification listening(Context c) {
        Intent stop=new Intent(c,ListeningService.class).setAction("STOP");
        return new Notification.Builder(c,"listening").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("声境正在本机监听").setContentText("不保存或上传录音；点停止可关闭")
            .setContentIntent(open(c)).setOngoing(true).setLocalOnly(true)
            .addAction(new Notification.Action.Builder(null,"停止",PendingIntent.getService(c,0,stop,PendingIntent.FLAG_IMMUTABLE)).build()).build();
    }
    static void show(Context c,Policy.Alert alert) {
        App app=(App)c.getApplicationContext();
        if(android.os.Build.VERSION.SDK_INT>=33 && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            app.status="通知权限已关闭，无法发出提醒";return;
        }
        app.latestId=alert.id;
        app.lastEvent=(alert.simulated?"[模拟] ":"[识别] ")+alert.kind.title+" · 分数 "+String.format(java.util.Locale.ROOT,"%.2f",alert.score);
        Notification.Builder b=new Notification.Builder(c,alert.kind.name())
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle((alert.simulated?"测试提醒：":"检测到可能的")+alert.kind.title)
            .setContentText("请结合实际环境确认").setContentIntent(open(c)).setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setOnlyAlertOnce(true);
        for(String action:new String[]{"ACK","FALSE_ALARM"}) {
            Intent intent=new Intent(c,FeedbackReceiver.class).setAction(action).putExtra("id",alert.id);
            b.addAction(new Notification.Action.Builder(null,action.equals("ACK")?"已看到":"这是误报",
                PendingIntent.getBroadcast(c,(int)(alert.id*2+(action.equals("ACK")?0:1)),intent,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT)).build());
        }
        c.getSystemService(NotificationManager.class).notify((int)(alert.id+100),b.build());
    }
}
