package org.shengjing.phone;

import android.app.*;
import android.content.*;
import org.shengjing.core.Policy;
import org.shengjing.core.EventHistory;

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
        NotificationChannel study=new NotificationChannel("study_knock","学习场景敲门提醒",NotificationManager.IMPORTANCE_HIGH);
        study.setSound(null,null);study.enableVibration(true);study.setVibrationPattern(new long[]{0,180});
        nm.createNotificationChannel(study);
    }
    static String channel(Policy.Kind kind,Policy.Scene scene) {
        return kind==Policy.Kind.KNOCK && scene==Policy.Scene.STUDY ? "study_knock" : kind.name();
    }
    static boolean available(Context c,String channel) {
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        if(android.os.Build.VERSION.SDK_INT>=33 && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) return false;
        NotificationChannel settings=manager.getNotificationChannel(channel);
        return manager.areNotificationsEnabled() && settings!=null && settings.getImportance()!=NotificationManager.IMPORTANCE_NONE;
    }
    static void cancel(Context c,long id) { c.getSystemService(NotificationManager.class).cancel("event:"+id,100); }
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
        String channel=channel(alert.kind,alert.scene);
        boolean allowed=available(c,channel);
        EventHistory.Entry entry=app.history.add(alert,System.currentTimeMillis(),allowed?EventHistory.Delivery.REQUESTED:EventHistory.Delivery.BLOCKED);
        app.policy.feedback(alert.id);
        app.latestId=entry.id;
        app.lastEvent=(alert.simulated?"[模拟] ":"[识别] ")+alert.kind.title+" · 分数 "+String.format(java.util.Locale.ROOT,"%.2f",alert.score);
        // Remove notifications whose records have been evicted; feedback must always address retained metadata.
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        for(android.service.notification.StatusBarNotification active:manager.getActiveNotifications()) {
            String tag=active.getTag();
            if(tag!=null && tag.startsWith("event:")) {
                try { if(app.history.find(Long.parseLong(tag.substring(6)))==null) manager.cancel(tag,active.getId()); }
                catch(NumberFormatException ignored) { /* Ignore unrelated tags. */ }
            }
        }
        if(!allowed) { app.lastEvent+=" · 通知被系统设置阻止，请检查权限和类别通知";app.saveHistory();return; }
        Notification.Builder b=new Notification.Builder(c,channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle((alert.simulated?"测试提醒：":"检测到可能的")+alert.kind.title)
            .setContentText(alert.scene.title+"场景 · 请结合实际环境确认").setContentIntent(open(c)).setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setOnlyAlertOnce(true);
        for(String action:new String[]{"ACK","FALSE_ALARM"}) {
            Intent intent=new Intent(c,FeedbackReceiver.class).setAction(action).putExtra("id",entry.id)
                .setData(android.net.Uri.parse("shengjing://feedback/"+entry.id+"/"+action));
            b.addAction(new Notification.Action.Builder(null,action.equals("ACK")?"已看到":"这是误报",
                PendingIntent.getBroadcast(c,0,intent,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT)).build());
        }
        app.saveHistory();
        try { manager.notify("event:"+entry.id,100,b.build()); }
        catch(RuntimeException e) { app.history.delivery(entry.id,EventHistory.Delivery.FAILED);app.lastEvent+=" · 通知发送失败";app.saveHistory(); }
    }
}
