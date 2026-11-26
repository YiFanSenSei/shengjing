package org.shengjing.phone;

import android.content.Context;
import android.content.SharedPreferences;

/** Latest run only. Lifecycle and completed-window updates are delivered on the main thread. */
final class ListeningSession {
    enum State {
        NONE("尚无监听记录"), RUNNING("正在监听"), STOPPED("已手动停止"),
        INTERRUPTED("上次监听中断。请检查电池设置，再点开始监听。"),
        PERMISSION("无法使用麦克风。请在设备引导中开启权限，再开始监听。"),
        MODEL("离线模型加载失败。请重新打开应用后再试。"),
        MICROPHONE("麦克风启动失败。请检查麦克风开关和占用情况，再开始监听。"),
        SILENCED("系统已将麦克风静音。请检查麦克风开关和其他录音应用，再开始监听。"),
        INPUT("音频输入中断。请检查麦克风开关和其他录音应用，再开始监听。"),
        INFERENCE("声音处理停止。请重新开始监听。"),
        FOREGROUND("监听服务启动失败。请回到首页，检查权限后再试。");

        final String title;
        State(String title) { this.title = title; }
        boolean needsAttention() { return this != NONE && this != RUNNING && this != STOPPED; }
    }

    private final SharedPreferences prefs;
    State state;
    long windows, lastWindowMs, largestGapMs, elapsedMs;
    private long startedAt, savedAt;

    ListeningSession(Context context) {
        prefs = context.getSharedPreferences("listening_session", Context.MODE_PRIVATE);
        state = State.valueOf(prefs.getString("state", State.NONE.name()));
        windows = prefs.getLong("windows", 0);
        lastWindowMs = prefs.getLong("lastWindowMs", 0);
        largestGapMs = prefs.getLong("largestGapMs", 0);
        elapsedMs = prefs.getLong("elapsedMs", 0);
        if (state == State.RUNNING) {
            // A new process cannot know when the previous one ended.
            state = State.INTERRUPTED;
            save();
        }
    }

    void begin(long now) {
        startedAt = savedAt = now;
        elapsedMs = windows = lastWindowMs = largestGapMs = 0;
        state = State.RUNNING;
        save();
    }

    void completedWindow(long now) {
        if (state != State.RUNNING) return;
        long offset = now - startedAt;
        if (windows > 0) largestGapMs = Math.max(largestGapMs, offset - lastWindowMs);
        windows++;
        lastWindowMs = elapsedMs = offset;
        if (windows == 1 || now - savedAt >= 30000) {
            savedAt = now;
            save();
        }
    }

    void finish(State result, long now) {
        if (state != State.RUNNING) return;
        elapsedMs = now - startedAt;
        state = result;
        save();
    }

    long sinceLastWindow(long now) {
        return now - startedAt - lastWindowMs;
    }

    String summary(long now) {
        if (state == State.NONE) return state.title;
        String duration = state == State.RUNNING ? "已启动 " : state == State.INTERRUPTED ? "最近记录到 " : "本次历时 ";
        long elapsed = state == State.RUNNING ? now - startedAt : elapsedMs;
        String gap = windows < 2 ? "尚无两次处理结果" : largestGapMs + " 毫秒";
        return state.title + "\n" + duration + elapsed / 60000 + " 分 " + elapsed / 1000 % 60 + " 秒"
            + " · 已处理 " + windows + " 个声音窗口"
            + (windows == 0 ? "" : "\n最后一个窗口在启动后 " + lastWindowMs / 1000 + " 秒")
            + "\n相邻处理结果最长间隔：" + gap;
    }

    private void save() {
        prefs.edit().putString("state", state.name()).putLong("windows", windows)
            .putLong("lastWindowMs", lastWindowMs).putLong("largestGapMs", largestGapMs)
            .putLong("elapsedMs", elapsedMs).apply();
    }
}
