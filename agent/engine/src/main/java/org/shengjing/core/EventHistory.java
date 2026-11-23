package org.shengjing.core;

import java.util.ArrayList;
import java.util.List;

/** Bounded local metadata. IDs survive process restarts and clearing history. No audio or text transcripts. */
public final class EventHistory {
    public static final int LIMIT=100;
    public enum Feedback { PENDING, ACK, FALSE_ALARM, TOO_FREQUENT, ACK_TOO_FREQUENT }
    public enum Delivery { REQUESTED, BLOCKED, FAILED }
    public static final class Entry {
        public final long id, time;
        public final Policy.Kind kind;
        public final Policy.Scene scene;
        public final float score;
        public final boolean simulated;
        public final Delivery delivery;
        public final Feedback feedback;
        public Entry(long id,long time,Policy.Kind kind,Policy.Scene scene,float score,boolean simulated,Delivery delivery,Feedback feedback) {
            if(id<=0 || time<0 || kind==null || scene==null || delivery==null || feedback==null || !Float.isFinite(score) || score<0 || score>1)
                throw new IllegalArgumentException("Invalid history entry");
            this.id=id;this.time=time;this.kind=kind;this.scene=scene;this.score=score;
            this.simulated=simulated;this.delivery=delivery;this.feedback=feedback;
        }
        Entry withFeedback(Feedback value) { return new Entry(id,time,kind,scene,score,simulated,delivery,value); }
        Entry withDelivery(Delivery value) { return new Entry(id,time,kind,scene,score,simulated,value,feedback); }
    }
    private final List<Entry> entries=new ArrayList<>();
    private long sequence;
    public EventHistory(long sequence,List<Entry> restored) {
        this.sequence=Math.max(0,sequence);
        for(Entry entry:restored) {
            if(find(entry.id)!=null) continue;
            entries.add(entry);this.sequence=Math.max(this.sequence,entry.id);
        }
        trim();
    }
    public EventHistory() { this(0,new ArrayList<>()); }
    public synchronized Entry add(Policy.Alert alert,long wallTime,Delivery delivery) {
        long id=Math.max(Math.addExact(sequence,1),wallTime);
        Entry entry=new Entry(id,wallTime,alert.kind,alert.scene,alert.score,alert.simulated,delivery,Feedback.PENDING);
        sequence=id;entries.add(entry);trim();return entry;
    }
    private void trim() { while(entries.size()>LIMIT) entries.remove(0); }
    public synchronized Entry find(long id) {
        for(Entry e:entries) if(e.id==id) return e;
        return null;
    }
    public synchronized Entry feedback(long id,Feedback value) {
        if(value==null || value==Feedback.PENDING || value==Feedback.ACK_TOO_FREQUENT) return null;
        for(int i=0;i<entries.size();i++) {
            Entry e=entries.get(i);
            if (e.id == id && value == Feedback.TOO_FREQUENT) {
                if (e.simulated || e.delivery != Delivery.REQUESTED ||
                    (e.feedback != Feedback.PENDING && e.feedback != Feedback.ACK)) return null;
                Entry updated = e.withFeedback(e.feedback == Feedback.ACK ? Feedback.ACK_TOO_FREQUENT : value);
                entries.set(i, updated);
                return updated;
            }
            if(e.id==id && e.feedback==Feedback.PENDING) {
                Entry updated=e.withFeedback(value);entries.set(i,updated);return updated;
            }
        }
        return null;
    }
    public synchronized int frequentReports(Policy.Scene scene, Policy.Kind kind, long afterId) {
        int count = 0;
        for (Entry entry : entries) {
            if (entry.scene == scene && entry.kind == kind && entry.id > afterId &&
                !entry.simulated && entry.delivery == Delivery.REQUESTED &&
                (entry.feedback == Feedback.TOO_FREQUENT || entry.feedback == Feedback.ACK_TOO_FREQUENT)) count++;
        }
        return count;
    }
    public synchronized void delivery(long id,Delivery value) {
        for(int i=0;i<entries.size();i++) if(entries.get(i).id==id) { entries.set(i,entries.get(i).withDelivery(value));return; }
    }
    public synchronized List<Entry> snapshot() { return new ArrayList<>(entries); }
    public synchronized long sequence() { return sequence; }
    public synchronized void clear() { entries.clear(); }
}
