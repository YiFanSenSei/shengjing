package org.shengjing.phone;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioRecord;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.content.res.AssetFileDescriptor;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import android.os.*;
import java.util.Arrays;
import org.shengjing.core.AudioDiagnostics;
import org.shengjing.core.Policy;
import org.tensorflow.lite.Interpreter;
import org.shengjing.phone.ListeningSession.State;

public final class ListeningService extends Service {
    private volatile boolean running;
    private boolean stopRequested;
    private Thread worker;
    private long session;
    private App app;
    private final Handler main=new Handler(Looper.getMainLooper());
    @Override public void onCreate(){super.onCreate();app=(App)getApplication();}
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        String action=intent==null?null:intent.getAction();
        if("RESUME_ALERTS".equals(action) || "PAUSE_ALERTS".equals(action)){
            if(running && session==intent.getLongExtra("session",-1))app.setQuiet("PAUSE_ALERTS".equals(action));
            if(worker==null)stopSelf(startId);
            return START_NOT_STICKY;
        }
        if(intent!=null && "STOP".equals(intent.getAction())) {
            stopRequested=true;running=false;stopSelf();return START_NOT_STICKY;
        }
        if(worker!=null) return START_NOT_STICKY;
        session=++app.audioSession;
        app.sessionReport.begin(SystemClock.elapsedRealtime());
        try {
            if(Build.VERSION.SDK_INT>=30) startForeground(Alerts.SERVICE_ID,Alerts.listening(this),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            else startForeground(Alerts.SERVICE_ID,Alerts.listening(this));
            running=true;app.listening=true;app.policy.resetListeningSession();app.status="正在加载离线模型…";
            app.diagnostics="正在准备模型和音频输入…";app.diagnosticsUpdatedAt=SystemClock.elapsedRealtime();
            long token=session;
            worker=new Thread(()->listen(token),"shengjing-audio");worker.start();
        } catch(RuntimeException e) { recordFailure(State.FOREGROUND);stopSelf(); }
        return START_NOT_STICKY;
    }
    private boolean active(long token) { return running && app.audioSession==token; }
    private void recordFailure(State cause) {
        app.sessionReport.finish(cause,SystemClock.elapsedRealtime());
        app.status=cause.title;
        app.diagnostics=cause.title;
    }
    private boolean inputSilenced(AudioRecord recorder) {
        if(getSystemService(AudioManager.class).isMicrophoneMute()) return true;
        if(Build.VERSION.SDK_INT>=29) {
            AudioRecordingConfiguration config=recorder.getActiveRecordingConfiguration();
            return config!=null && config.isClientSilenced();
        }
        return false;
    }
    private void publish(long token,String detail) {
        main.post(()-> {
            if(active(token)) { app.diagnostics=detail;app.diagnosticsUpdatedAt=SystemClock.elapsedRealtime(); }
        });
    }
    private void listen(long token) {
        Interpreter classifier=null;AudioRecord recorder=null;
        State stage=State.PERMISSION;
        try {
            if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED) throw new SecurityException();
            stage=State.MODEL;
            try (AssetFileDescriptor asset=getAssets().openFd("yamnet.tflite");
                 FileInputStream stream=asset.createInputStream()) {
                classifier=new Interpreter(stream.getChannel().map(FileChannel.MapMode.READ_ONLY,
                    asset.getStartOffset(),asset.getLength()),new Interpreter.Options().setNumThreads(2));
            }
            String[] labels;
            try (BufferedReader reader=new BufferedReader(new InputStreamReader(
                    getAssets().open("yamnet_label_list.txt"),StandardCharsets.UTF_8))) {
                labels=reader.lines().toArray(String[]::new);
            }
            if (!Arrays.equals(classifier.getInputTensor(0).shape(),new int[]{15600}) ||
                !Arrays.equals(classifier.getOutputTensor(0).shape(),new int[]{1,labels.length}))
                throw new IllegalStateException("Model shape mismatch");
            int[] targetIndices=new int[Policy.Kind.values().length];
            for (Policy.Kind kind:Policy.Kind.values()) {
                int index=Arrays.asList(labels).indexOf(kind.modelLabel);
                if(index<0) throw new IllegalStateException("Missing label");
                targetIndices[kind.ordinal()]=index;
            }
            if(!active(token)) return;
            stage=State.MICROPHONE;
            int minimum=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(minimum<=0) throw new IllegalStateException("Unsupported microphone format");
            recorder=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,Math.max(minimum,32000));
            recorder.startRecording();
            if(recorder.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException();
            main.post(()->{if(active(token))app.status="正在本机监听：敲门 / 门铃 / 闹钟";});
            AudioDiagnostics diagnostics=new AudioDiagnostics();
            int filled=0;
            float[] samples=new float[15600];
            float[][] output=new float[1][labels.length];
            short[] chunk=new short[4000];
            int pending=0;
            long lastAudio=SystemClock.elapsedRealtime();
            long nextInputCheck=lastAudio, nextEmptyWarning=lastAudio+3000;
            while(active(token)) {
                stage=State.INPUT;
                long now=SystemClock.elapsedRealtime();
                if(now>=nextInputCheck) {
                    if(inputSilenced(recorder)) {
                        stage=State.SILENCED;
                        throw new IllegalStateException();
                    }
                    nextInputCheck=now+1000;
                }
                int captured=recorder.read(chunk,pending,chunk.length-pending,AudioRecord.READ_NON_BLOCKING);
                if(!active(token)) break;
                if(captured<0) throw new IllegalStateException("Audio read failed");
                if(captured==0) {
                    long emptyMs=now-lastAudio;
                    if(emptyMs>=15000) throw new IllegalStateException();
                    if(now>=nextEmptyWarning) {
                        publish(token,"连续 "+emptyMs/1000+" 秒没有新音频；请检查系统麦克风开关及其他应用占用");
                        nextEmptyWarning=now+1000;
                    }
                    Thread.sleep(25);continue;
                }
                lastAudio=SystemClock.elapsedRealtime();
                nextEmptyWarning=lastAudio+3000;
                pending+=captured;
                if(pending<chunk.length) continue;
                pending=0;
                System.arraycopy(samples,chunk.length,samples,0,samples.length-chunk.length);
                for(int i=0;i<chunk.length;i++) samples[samples.length-chunk.length+i]=chunk[i]/32768f;
                filled=Math.min(samples.length,filled+chunk.length);
                if(filled<samples.length) {
                    publish(token,"正在填充首个音频窗口："+filled+" / "+samples.length+" 个采样值");
                    continue;
                }
                final double dbfs=AudioDiagnostics.rmsDbfs(samples);
                stage=State.INFERENCE;
                long started=SystemClock.elapsedRealtime();
                classifier.run(samples,output);
                long at=SystemClock.elapsedRealtime(),inferenceMs=at-started;
                if(!active(token)) break;
                float[] scores=new float[Policy.Kind.values().length];Arrays.fill(scores,Float.NaN);
                String highestLabel=null;float highestScore=-1;
                for(int i=0;i<labels.length;i++) {
                    float score=output[0][i];
                    if(!Float.isFinite(score) || score<0 || score>1) continue;
                    if(score>highestScore) { highestScore=score;highestLabel=labels[i]; }
                }
                for(Policy.Kind kind:Policy.Kind.values()) {
                    float value=output[0][targetIndices[kind.ordinal()]];
                    if(Float.isFinite(value) && value>=0 && value<=1) scores[kind.ordinal()]=value;
                }
                final String topLabel=highestLabel;final float topScore=highestScore;
                // Only diagnostics cross to the UI; audio remains on this worker.
                main.post(()-> {
                    if(!active(token)) return;
                    app.sessionReport.completedWindow(at);
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
            }
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
        catch(Exception | LinkageError e) {
            final State failure=stage;
            main.post(()-> { if(active(token)) recordFailure(failure); });
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
            app.fieldRound.finish(app.history,"监听已结束");
            app.sessionReport.finish(stopRequested?State.STOPPED:State.INTERRUPTED,SystemClock.elapsedRealtime());
            ++app.audioSession;app.listening=false;
            if(app.sessionReport.state.needsAttention()) app.status=app.sessionReport.state.title;
            else if(app.status.startsWith("正在"))app.status="已停止监听";
        }
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
