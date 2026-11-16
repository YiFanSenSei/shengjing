package org.shengjing.phone;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioRecord;
import android.os.*;
import org.shengjing.core.Policy;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.Classifications;
import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.support.label.Category;

public final class ListeningService extends Service {
    private volatile boolean running;
    private Thread worker;
    private App app;
    private final Handler main=new Handler(Looper.getMainLooper());
    @Override public void onCreate(){super.onCreate();app=(App)getApplication();}
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null && "STOP".equals(intent.getAction())) { stopSelf();return START_NOT_STICKY; }
        if(running) return START_NOT_STICKY;
        try {
            if(Build.VERSION.SDK_INT>=30) startForeground(Alerts.SERVICE_ID,Alerts.listening(this),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            else startForeground(Alerts.SERVICE_ID,Alerts.listening(this));
            running=true;app.listening=true;app.policy.resetListeningSession();app.status="正在加载离线模型…";
            worker=new Thread(this::listen,"shengjing-audio");worker.start();
        } catch(RuntimeException e) { app.status="监听启动失败，请检查麦克风权限及前台状态";stopSelf(); }
        return START_NOT_STICKY;
    }
    private void listen() {
        AudioClassifier classifier=null;AudioRecord recorder=null;
        try {
            if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED) throw new SecurityException();
            classifier=AudioClassifier.createFromFile(this,"yamnet.tflite");
            TensorAudio tensor=classifier.createInputTensorAudio();
            recorder=classifier.createAudioRecord();recorder.startRecording();
            if(recorder.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException();
            app.status="正在本机监听：敲门 / 门铃 / 闹钟";
            while(running) {
                tensor.load(recorder);
                if(!running) break;
                for(Classifications group:classifier.classify(tensor)) for(Category category:group.getCategories()) {
                    for(Policy.Kind kind:Policy.Kind.values()) if(kind.modelLabel.equals(category.getLabel())) {
                        Policy.Decision decision=app.policy.observe(kind,category.getScore(),SystemClock.elapsedRealtime());
                        if(decision.alert!=null) main.post(()->{if(running) Alerts.show(this,decision.alert);});
                    }
                }
                Thread.sleep(250);
            }
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
        catch(Exception | LinkageError e) { app.status="识别停止：模型、音频设备或权限不可用；可先测试模拟提醒"; }
        finally {
            if(recorder!=null){try{recorder.stop();}catch(RuntimeException ignored){}recorder.release();}
            if(classifier!=null)classifier.close();
            running=false;app.listening=false;main.post(this::stopSelf);
        }
    }
    @Override public void onDestroy() {
        running=false;app.listening=false;
        if(worker!=null)worker.interrupt();
        if(app.status.startsWith("正在"))app.status="已停止监听";
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
