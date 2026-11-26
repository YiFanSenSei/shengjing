package org.shengjing.phone;

import android.content.Context;
import android.content.SharedPreferences;
import org.shengjing.core.EventHistory;
import org.shengjing.core.Policy;

/** One local rehearsal. Only the sequence boundary and aggregate result are retained. */
final class FieldRound {
    private final SharedPreferences prefs;
    FieldRound(Context context) {
        prefs=context.getSharedPreferences("field_round",Context.MODE_PRIVATE);
    }
    boolean active(){return prefs.getBoolean("active",false);}
    boolean exists(){return prefs.contains("after");}
    void begin(EventHistory history, Policy.Scene scene){
        if(active())return;
        prefs.edit().clear().putBoolean("active",true).putLong("after",history.sequence())
            .putString("scene",scene.title).apply();
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
    void finish(EventHistory history,String reason){
        if(active())prefs.edit().putString("result",reason+"\n"+summary(history)).putBoolean("active",false).apply();
    }
    void clear(){prefs.edit().clear().apply();}
}
