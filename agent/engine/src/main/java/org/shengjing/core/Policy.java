package org.shengjing.core;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Deterministic notification policy. All times are monotonic milliseconds. */
public final class Policy {
    public enum Scene {
        HOME("居家"), STUDY("学习");
        public final String title;
        Scene(String title) { this.title=title; }
    }
    public enum Kind {
        KNOCK("敲门", "Knock"), DOORBELL("门铃", "Doorbell"), ALARM("闹钟", "Alarm clock");
        public final String title;
        public final String modelLabel;
        Kind(String title, String label) { this.title = title; this.modelLabel = label; }
    }
    public static final class Alert {
        public final long id, detectedAt;
        public final Kind kind;
        public final boolean simulated;
        public final float score;
        public final Scene scene;
        Alert(long id, Kind kind, float score, long now, boolean simulated, Scene scene) {
            this.id=id; this.kind=kind; this.score=score; this.detectedAt=now; this.simulated=simulated;
            this.scene=scene;
        }
    }
    public static final class Decision {
        public final Alert alert;
        public final String reason;
        Decision(Alert alert, String reason) { this.alert=alert; this.reason=reason; }
    }
    private static final class State { long lastSeen=-1, lastAlert=-1; }
    private final Map<Kind, State> states = new EnumMap<>(Kind.class);
    private final Map<Long, Alert> pending = new HashMap<>();
    private final Map<Kind, Boolean> enabled = new EnumMap<>(Kind.class);
    private long sequence=0, clock=-1;
    private boolean quiet=false;
    private boolean enhanced=false;
    private Scene scene=Scene.HOME;
    public synchronized void setScene(Scene value) { scene=java.util.Objects.requireNonNull(value); }
    public synchronized Scene getScene() { return scene; }
    public synchronized long cooldown(Kind kind) { return scene==Scene.STUDY && kind==Kind.KNOCK ? 60000 : COOLDOWN_MS; }
    public static final long SILENCE_GAP_MS=2500, COOLDOWN_MS=15000;
    public static final float THRESHOLD=0.60f;
    public static final float ENHANCED_THRESHOLD=0.40f;
    public synchronized void setEnhanced(boolean value) { enhanced=value; }
    public synchronized boolean isEnhanced() { return enhanced; }
    public synchronized float threshold(Kind kind) {
        return enhanced && kind!=Kind.DOORBELL ? ENHANCED_THRESHOLD : THRESHOLD;
    }

    public Policy() { for (Kind k:Kind.values()) { states.put(k,new State()); enabled.put(k,true); } }
    public synchronized void setQuiet(boolean value) { quiet=value; }
    public synchronized void setEnabled(Kind kind, boolean value) { enabled.put(kind,value); }
    public synchronized boolean isQuiet() { return quiet; }
    public synchronized boolean isEnabled(Kind kind) { return enabled.get(kind); }
    public synchronized Decision observe(Kind kind, float score, long now) {
        if (kind==null || !Float.isFinite(score) || score<0 || score>1 || now<0 || now<clock)
            return new Decision(null,"invalid_event");
        clock=now;
        if (score<threshold(kind)) return new Decision(null,"below_threshold");
        State s=states.get(kind);
        boolean continuing=s.lastSeen>=0 && now-s.lastSeen<=SILENCE_GAP_MS;
        s.lastSeen=now;
        if (!enabled.get(kind)) return new Decision(null,"disabled");
        if (quiet) return new Decision(null,"quiet_mode");
        if (continuing) return new Decision(null,"same_event");
        if (s.lastAlert>=0 && now-s.lastAlert<cooldown(kind)) return new Decision(null,"cooldown");
        s.lastAlert=now;
        return new Decision(add(kind,score,now,false),"new_event");
    }
    public synchronized Alert simulate(Kind kind, long now) {
        // Simulation never touches live classification cooldown state.
        return add(kind,1f,now,true);
    }
    private Alert add(Kind kind,float score,long now,boolean simulated) {
        if (pending.size()>=100) pending.clear(); // bounded in-memory prototype state
        Alert alert=new Alert(++sequence,kind,score,now,simulated,scene);
        pending.put(alert.id,alert);
        return alert;
    }
    public synchronized Alert feedback(long id) { return pending.remove(id); }
    public synchronized void resetListeningSession() {
        clock=-1;
        for (Kind k:Kind.values()) states.put(k,new State());
        pending.clear();
    }
}
