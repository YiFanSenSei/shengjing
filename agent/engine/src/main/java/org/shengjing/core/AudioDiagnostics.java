package org.shengjing.core;

import java.util.Arrays;
import java.util.Locale;

/** Bounded, in-memory measurements. Never retains audio samples. */
public final class AudioDiagnostics {
    private final float[] peaks=new float[Policy.Kind.values().length];
    private long frames;
    public AudioDiagnostics() { Arrays.fill(peaks,Float.NaN); }

    public static double rmsDbfs(float[] samples) {
        if(samples.length==0) throw new IllegalArgumentException("Empty audio window");
        double energy=0;
        for(float sample:samples) {
            if(!Float.isFinite(sample) || Math.abs(sample)>1) throw new IllegalArgumentException("Invalid PCM value");
            energy+=(double)sample*sample;
        }
        return energy==0 ? Double.NEGATIVE_INFINITY : 10*Math.log10(energy/samples.length);
    }

    public String update(float[] samples,float[] scores,String[] reasons,String topLabel,float topScore,long inferenceMs) {
        return updateLevel(rmsDbfs(samples),scores,reasons,topLabel,topScore,inferenceMs);
    }

    public String updateLevel(double dbfs,float[] scores,String[] reasons,String topLabel,float topScore,long inferenceMs) {
        return updateLevel(dbfs,scores,reasons,topLabel,topScore,inferenceMs,false);
    }
    public String updateLevel(double dbfs,float[] scores,String[] reasons,String topLabel,float topScore,long inferenceMs,boolean enhanced) {
        if(scores.length!=peaks.length || reasons.length!=peaks.length) throw new IllegalArgumentException("Wrong class count");
        if(Double.isNaN(dbfs) || dbfs>0) throw new IllegalArgumentException("Invalid digital level");
        frames++;
        StringBuilder text=new StringBuilder("有效识别窗口：").append(frames)
            .append(" · 推理耗时 ").append(inferenceMs).append(" ms\n输入电平：")
            .append(Double.isInfinite(dbfs) ? "全零音频" : String.format(Locale.ROOT,"%.1f dBFS",dbfs))
            .append("（数字电平，不是环境分贝）\n模型最高分类：")
            .append(topLabel==null ? "未返回有效分类" : topLabel+" · "+scoreText(topScore))
            .append("\n模式：").append(enhanced?"增强（试验）":"标准")
            .append(" · 敲门/闹钟阈值：").append(String.format(Locale.ROOT,"%.2f",enhanced?Policy.ENHANCED_THRESHOLD:Policy.THRESHOLD))
            .append(" · 门铃阈值：0.60")
            .append("（分数不等于准确率）");
        for(Policy.Kind kind:Policy.Kind.values()) {
            int i=kind.ordinal();
            if(validScore(scores[i]) && (!validScore(peaks[i]) || scores[i]>peaks[i])) peaks[i]=scores[i];
            text.append('\n').append(kind.title).append("：当前 ").append(scoreText(scores[i]))
                .append(" / 本次峰值 ").append(scoreText(peaks[i])).append(" · ").append(reasonText(reasons[i]));
        }
        return text.toString();
    }

    public float peak(Policy.Kind kind) { return peaks[kind.ordinal()]; }
    public long frames() { return frames; }
    private static boolean validScore(float value) { return Float.isFinite(value) && value>=0 && value<=1; }
    private static String scoreText(float value) { return validScore(value) ? String.format(Locale.ROOT,"%.3f",value) : "未返回"; }
    public static String reasonText(String reason) {
        if(reason==null) return "未返回目标类别";
        switch(reason) {
            case "new_event": return "已请求提醒";
            case "below_threshold": return "低于阈值";
            case "same_event": return "连续事件，避免重复";
            case "cooldown": return "提醒冷却中";
            case "disabled": return "类别已关闭";
            case "quiet_mode": return "真实提醒已暂停";
            default: return "无有效结果";
        }
    }
}
