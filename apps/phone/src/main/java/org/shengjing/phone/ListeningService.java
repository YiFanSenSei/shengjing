package org.shengjing.phone;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioRecord;
import android.os.*;
import java.util.Arrays;
import java.util.List;
import org.shengjing.core.AudioDiagnostics;
import org.shengjing.core.Policy;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.Classifications;
import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.support.label.Category;

public final class ListeningService extends Service {
    private volatile boolean running;
    private Thread worker;
    private long session;
    private App app;
    private final Handler main=new Handler(Looper.getMainLooper());
    @Override public void onCreate(){super.onCreate();app=(App)getApplication();}
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null && "STOP".equals(intent.getAction())) { stopSelf();return START_NOT_STICKY; }
        if(worker!=null) return START_NOT_STICKY;
        try {
            if(Build.VERSION.SDK_INT>=30) startForeground(Alerts.SERVICE_ID,Alerts.listening(this),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            else startForeground(Alerts.SERVICE_ID,Alerts.listening(this));
            session=++app.audioSession;
            running=true;app.listening=true;app.policy.resetListeningSession();app.status="正在加载离线模型…";
            app.diagnostics="正在准备模型和音频输入…";app.diagnosticsUpdatedAt=SystemClock.elapsedRealtime();
            long token=session;
            worker=new Thread(()->listen(token),"shengjing-audio");worker.start();
        } catch(RuntimeException e) { app.status="监听启动失败，请检查麦克风权限及前台状态";stopSelf(); }
        return START_NOT_STICKY;
    }
    private boolean active(long token) { return running && app.audioSession==token; }
    private void publish(long token,String detail) {
        main.post(()-> {
            if(active(token)) { app.diagnostics=detail;app.diagnosticsUpdatedAt=SystemClock.elapsedRealtime(); }
        });
    }
    private void listen(long token) {
        AudioClassifier classifier=null;AudioRecord recorder=null;
        String stage="麦克风权限";
        try {
            if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED) throw new SecurityException();
            stage="离线模型加载";
            AudioClassifier.AudioClassifierOptions options=AudioClassifier.AudioClassifierOptions.builder()
                .setMaxResults(-1).setScoreThreshold(0f).build();
            classifier=AudioClassifier.createFromFileAndOptions(this,"yamnet.tflite",options);
            if(!active(token)) return;
            TensorAudio tensor=classifier.createInputTensorAudio();
            stage="麦克风启动";
            recorder=classifier.createAudioRecord();recorder.startRecording();
            if(recorder.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException();
            main.post(()->{if(active(token))app.status="正在本机监听：敲门 / 门铃 / 闹钟";});
            AudioDiagnostics diagnostics=new AudioDiagnostics();
            int filled=0;
            long lastAudio=SystemClock.elapsedRealtime();
            while(active(token)) {
                stage="音频采集";
                int captured=tensor.load(recorder);
                if(!active(token)) break;
                if(captured<=0) {
                    long emptyMs=SystemClock.elapsedRealtime()-lastAudio;
                    if(emptyMs>=3000) publish(token,"连续 "+emptyMs/1000+" 秒没有新音频；请检查系统麦克风开关及其他应用占用");
                    Thread.sleep(250);continue;
                }
                lastAudio=SystemClock.elapsedRealtime();
                float[] samples=tensor.getTensorBuffer().getFloatArray();
                filled=Math.min(samples.length,filled+captured);
                if(filled<samples.length) {
                    publish(token,"正在填充首个音频窗口："+filled+" / "+samples.length+" 个采样值");
                    Thread.sleep(250);continue;
                }
                final double dbfs=AudioDiagnostics.rmsDbfs(samples);
                stage="模型推理";
                long started=SystemClock.elapsedRealtime();
                List<Classifications> results=classifier.classify(tensor);
                long at=SystemClock.elapsedRealtime(),inferenceMs=at-started;
                if(!active(token)) break;
                float[] scores=new float[Policy.Kind.values().length];Arrays.fill(scores,Float.NaN);
                String highestLabel=null;float highestScore=-1;
                for(Classifications group:results) for(Category category:group.getCategories()) {
                    float score=category.getScore();
                    if(!Float.isFinite(score) || score<0 || score>1) continue;
                    if(score>highestScore) { highestScore=score;highestLabel=category.getLabel(); }
                    for(Policy.Kind kind:Policy.Kind.values()) if(kind.modelLabel.equals(category.getLabel())) {
                        int i=kind.ordinal();
                        if(!Float.isFinite(scores[i]) || score>scores[i])scores[i]=score;
                    }
                }
                final String topLabel=highestLabel;final float topScore=highestScore;
                // Only diagnostics cross to the UI; audio remains on this worker.
                main.post(()-> {
                    if(!active(token)) return;
                    String[] reasons=new String[scores.length];
                    for(Policy.Kind kind:Policy.Kind.values()) {
                        int i=kind.ordinal();
                        if(!Float.isFinite(scores[i])) continue;
                        Policy.Decision decision=app.policy.observe(kind,scores[i],at);reasons[i]=decision.reason;
                        if(decision.alert!=null) Alerts.show(this,decision.alert);
                    }
                    app.diagnostics=diagnostics.updateLevel(dbfs,scores,reasons,topLabel,topScore,inferenceMs,app.policy.isEnhanced());
                    app.diagnosticsUpdatedAt=at;
                });
                Thread.sleep(250);
            }
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
        catch(Exception | LinkageError e) {
            final String failure="识别停止："+stage+"失败（"+e.getClass().getSimpleName()+"）";
            main.post(()-> { if(app.audioSession==token) { app.status=failure;app.diagnostics=failure+"；请停止后重试"; } });
        }
        finally {
            if(recorder!=null){try{recorder.stop();}catch(RuntimeException ignored){}try{recorder.release();}catch(RuntimeException ignored){}}
            if(classifier!=null){try{classifier.close();}catch(RuntimeException ignored){}}
            main.post(()-> {
                if(app.audioSession==token) { running=false;app.listening=false;stopSelf(); }
            });
        }
    }
    @Override public void onDestroy() {
        running=false;
        if(worker!=null)worker.interrupt();
        if(app.audioSession==session) {
            ++app.audioSession;app.listening=false;
            if(app.status.startsWith("正在"))app.status="已停止监听";
        }
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
