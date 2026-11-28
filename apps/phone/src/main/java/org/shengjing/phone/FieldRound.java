package org.shengjing.phone;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.util.Locale;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.shengjing.core.EventHistory;
import org.shengjing.core.Policy;

/** One local rehearsal. Detailed relative times are frozen only on an explicit finish. */
final class FieldRound {
    private final SharedPreferences prefs;
    private JSONArray events=new JSONArray();
    private long start=-1;
    private int simulated;
    private boolean overflow;
    FieldRound(Context context) {
        prefs=context.getSharedPreferences("field_round",Context.MODE_PRIVATE);
    }
    boolean active(){return prefs.getBoolean("active",false);}
    boolean exists(){return prefs.contains("after");}
    void begin(EventHistory history, Policy policy){
        if(active())return;
        events=new JSONArray();start=SystemClock.elapsedRealtime();simulated=0;overflow=false;
        prefs.edit().clear().putBoolean("active",true).putLong("after",history.sequence())
            .putString("round",UUID.randomUUID().toString().substring(0,8))
            .putString("scene",policy.getScene().title).putBoolean("enhanced",policy.isEnhanced()).apply();
    }
    void observeWatch(boolean seen){
        if(active())prefs.edit().putString("wrist",seen?"佩戴者报告看到了提醒":"佩戴者报告未看到提醒").apply();
    }
    String summary(EventHistory history){
        if(!exists())return "开启监听后开始一轮，随后可锁屏并触发真实声音。";
        if(!active())return prefs.getString("result","");
        int[] sounds=new int[Policy.Kind.values().length];
        int requests=0, blocked=0, failed=0, feedback=0, simulated=0;
        long after=prefs.getLong("after",0);
        var entries=history.snapshot();
        for(EventHistory.Entry entry:entries){
            if(entry.id<=after)continue;
            if(entry.simulated){simulated++;continue;}
            sounds[entry.kind.ordinal()]++;
            switch(entry.delivery){
                case REQUESTED: requests++;break;
                case BLOCKED: blocked++;break;
                case FAILED: failed++;break;
            }
            if(entry.feedback!=EventHistory.Feedback.PENDING)feedback++;
        }
        StringBuilder result=new StringBuilder("开始场景 · ").append(prefs.getString("scene",""));
        for(Policy.Kind kind:Policy.Kind.values())result.append("\n").append(kind.title).append("识别提醒：").append(sounds[kind.ordinal()]);
        return result+"\n真实提醒中，已请求通知 "+requests+" 条，系统阻止 "+blocked+" 条，发送失败 "+failed+" 条。"
            +"\n已反馈 "+feedback+" 条 · 另有模拟提醒 "+simulated+" 条"
            +"\n腕上观察："+prefs.getString("wrist","尚未填写")
            +(entries.size()==EventHistory.LIMIT && entries.get(0).id>after?"\n仅统计仍保留的最近 100 条记录。":"");
    }
    void record(EventHistory.Entry e,long at){
        if(start<0 || !active())return;
        if(e.simulated){simulated++;return;}
        if(events.length()>=EventHistory.LIMIT){overflow=true;return;}
        try{
            events.put(new JSONObject().put("n",events.length()+1).put("at_s",(at-start)/1000.0)
                .put("scene",e.scene.title)
                .put("label",e.kind.name().toLowerCase(Locale.ROOT))
                .put("delivery",e.delivery.name().toLowerCase(Locale.ROOT)));
        }catch(JSONException impossible){throw new IllegalStateException(impossible);}
    }
    String roundCode(){return prefs.getString("round","");}
    String progress(){
        return active() && start>=0?"场次 "+roundCode()+" · 已开始 "+(SystemClock.elapsedRealtime()-start)/1000+" 秒":"";
    }
    String exportJson(){return prefs.getString("export",null);}
    void complete(EventHistory history){
        if(!active())return;
        long duration=SystemClock.elapsedRealtime()-start;
        SharedPreferences.Editor edit=prefs.edit();
        if(start>=0 && duration>0 && !overflow){
            try{
                JSONObject data=new JSONObject().put("schema","shengjing.phone.v1")
                    .put("round",roundCode()).put("duration_s",duration/1000.0)
                    .put("scene_at_start",prefs.getString("scene","")).put("simulated_excluded",simulated)
                    .put("enhanced_at_start",prefs.getBoolean("enhanced",false))
                    .put("phone",events);
                edit.putString("export",data.toString(2));
            }catch(JSONException impossible){throw new IllegalStateException(impossible);}
        }
        close(history,"本轮已结束"+(overflow?" · 真实提醒超过 100 条，无法导出完整明细":""),edit);
    }
    private void close(EventHistory history,String reason,SharedPreferences.Editor edit){
        edit.putString("result",reason+"\n"+summary(history)).putBoolean("active",false).apply();
        start=-1;events=new JSONArray();
    }
    void finish(EventHistory history,String reason){
        if(active())close(history,reason,prefs.edit());
    }
    void clear(){prefs.edit().clear().apply();start=-1;events=new JSONArray();}
}
