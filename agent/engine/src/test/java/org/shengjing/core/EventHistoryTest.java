package org.shengjing.core;

import java.util.ArrayList;
import java.util.List;

public final class EventHistoryTest {
    private static int checks;
    private static void check(boolean ok,String message) { checks++;if(!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        Policy policy=new Policy();policy.setScene(Policy.Scene.STUDY);
        EventHistory history=new EventHistory();
        EventHistory.Entry first=history.add(policy.simulate(Policy.Kind.KNOCK,0),1000,EventHistory.Delivery.REQUESTED);
        check(first.simulated && first.scene==Policy.Scene.STUDY,"retain simulation and scene");
        EventHistory.Entry second=history.add(policy.observe(Policy.Kind.ALARM,.8f,1).alert,1000,EventHistory.Delivery.BLOCKED);
        check(second.id>first.id && !second.simulated,"same wall clock unique ids and real event");
        check(second.delivery==EventHistory.Delivery.BLOCKED,"blocked alert not reported as delivered");
        EventHistory restored=new EventHistory(history.sequence(),history.snapshot());
        check(restored.feedback(first.id,EventHistory.Feedback.ACK)!=null,"old notification feedback after restart");
        check(restored.feedback(first.id,EventHistory.Feedback.FALSE_ALARM)==null,"feedback idempotent and cannot overwrite");
        check(restored.feedback(-1,EventHistory.Feedback.ACK)==null,"unknown notification ignored");
        check(restored.feedback(second.id,EventHistory.Feedback.PENDING)==null,"pending cannot be feedback");
        check(restored.feedback(second.id,EventHistory.Feedback.FALSE_ALARM).feedback==EventHistory.Feedback.FALSE_ALARM,"false alarm persisted");
        EventHistory restarted=new EventHistory(restored.sequence(),restored.snapshot());
        check(restarted.find(first.id).feedback==EventHistory.Feedback.ACK,"ack preserved across restart");
        check(restarted.find(second.id).feedback==EventHistory.Feedback.FALSE_ALARM,"false alarm preserved across restart");
        restarted.clear();
        EventHistory cleared=new EventHistory(restarted.sequence(),restarted.snapshot());
        EventHistory.Entry next=cleared.add(policy.simulate(Policy.Kind.KNOCK,2),100,EventHistory.Delivery.REQUESTED);
        check(next.id>second.id,"clear and clock rollback cannot reuse notification ids");
        check(cleared.feedback(first.id,EventHistory.Feedback.ACK)==null,"cleared old notification cannot affect new event");
        cleared.delivery(next.id,EventHistory.Delivery.FAILED);
        check(cleared.find(next.id).delivery==EventHistory.Delivery.FAILED,"failed notify persisted");
        cleared.snapshot().clear();check(cleared.find(next.id)!=null,"snapshot cannot mutate history");
        for(int i=0;i<105;i++) cleared.add(policy.simulate(Policy.Kind.KNOCK,i),2000+i,EventHistory.Delivery.REQUESTED);
        check(cleared.snapshot().size()==100,"bounded retention");
        check(cleared.find(next.id)==null,"oldest evicted");
        check(cleared.feedback(next.id,EventHistory.Feedback.ACK)==null,"evicted notification ignored");
        List<EventHistory.Entry> duplicates=new ArrayList<>(cleared.snapshot());duplicates.add(duplicates.get(0));
        EventHistory recovered=new EventHistory(0,duplicates);
        check(recovered.snapshot().size()==100,"duplicate IDs ignored on restore");
        long last=recovered.sequence();
        check(recovered.add(policy.simulate(Policy.Kind.KNOCK,3),10,EventHistory.Delivery.REQUESTED).id>last,"restore recovers sequence from entries");
        System.out.println("History tests passed: "+checks);
    }
}
