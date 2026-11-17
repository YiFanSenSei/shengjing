package org.shengjing.core;

public final class PolicyTest {
    private static int checks;
    private static void check(boolean ok,String message) {
        checks++; if(!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Policy p=new Policy();
        check(p.observe(Policy.Kind.KNOCK,Float.NaN,0).alert==null,"reject NaN");
        check(p.observe(Policy.Kind.KNOCK,.2f,0).alert==null,"reject low score");
        Policy.Alert a=p.observe(Policy.Kind.KNOCK,.9f,100).alert;
        check(a!=null,"first alert");
        check(p.feedback(a.id)!=null,"ack existing");
        check(p.feedback(a.id)==null,"feedback idempotent");
        check(p.observe(Policy.Kind.KNOCK,.9f,500).alert==null,"no repeat after ack");
        check(p.observe(Policy.Kind.KNOCK,.9f,4000).reason.equals("cooldown"),"cooldown");
        check(p.observe(Policy.Kind.DOORBELL,.9f,5000).alert!=null,"independent classes");
        check(p.observe(Policy.Kind.KNOCK,.9f,16000).alert!=null,"new event after gap");
        for(long t=17000;t<40000;t+=1000) check(p.observe(Policy.Kind.KNOCK,.9f,t).alert==null,"continuous sound");
        p.setQuiet(true);
        check(p.observe(Policy.Kind.ALARM,.9f,40000).reason.equals("quiet_mode"),"quiet is explicit for all classes");
        p.setQuiet(false); p.setEnabled(Policy.Kind.ALARM,false);
        check(p.observe(Policy.Kind.ALARM,.9f,44000).reason.equals("disabled"),"disable class");
        check(p.observe(Policy.Kind.KNOCK,.9f,10).reason.equals("invalid_event"),"out of order");
        p.resetListeningSession();
        check(p.simulate(Policy.Kind.KNOCK,0).simulated,"simulation tagged");
        check(p.observe(Policy.Kind.KNOCK,.9f,1).alert!=null,"simulation leaves live state alone");
        Policy candidate=new Policy();
        check(candidate.observe(Policy.Kind.KNOCK,.5f,0).alert==null,"standard rejects moderate score");
        candidate.setEnhanced(true);
        check(candidate.observe(Policy.Kind.KNOCK,.4f,1).alert!=null,"enhanced knock boundary");
        check(candidate.observe(Policy.Kind.ALARM,.4f,2).alert!=null,"enhanced alarm boundary");
        check(candidate.observe(Policy.Kind.DOORBELL,.5f,3).alert==null,"doorbell unchanged");
        candidate.setEnhanced(false);candidate.setEnhanced(true);
        check(candidate.observe(Policy.Kind.KNOCK,.5f,4).alert==null,"mode toggle preserves dedupe");
        candidate.resetListeningSession();candidate.setQuiet(true);
        check(candidate.observe(Policy.Kind.KNOCK,.5f,5).reason.equals("quiet_mode"),"enhanced respects pause");
        candidate.setQuiet(false);candidate.setEnabled(Policy.Kind.ALARM,false);
        check(candidate.observe(Policy.Kind.ALARM,.5f,6).reason.equals("disabled"),"enhanced respects disabled class");
        candidate.setEnhanced(false);
        check(candidate.threshold(Policy.Kind.KNOCK)==.6f,"standard restored");
        Policy scenes=new Policy();
        scenes.setScene(Policy.Scene.STUDY);
        Policy.Alert study=scenes.observe(Policy.Kind.KNOCK,.8f,0).alert;
        check(study!=null && study.scene==Policy.Scene.STUDY,"scene captured in alert");
        check(scenes.observe(Policy.Kind.KNOCK,.8f,16000).reason.equals("cooldown"),"study knock repeats less frequently");
        check(scenes.observe(Policy.Kind.ALARM,.8f,16001).alert!=null,"study alarm available");
        check(scenes.observe(Policy.Kind.ALARM,.8f,31001).alert!=null,"study alarm keeps 15 seconds");
        check(scenes.cooldown(Policy.Kind.DOORBELL)==15000,"doorbell unchanged in study");
        check(scenes.observe(Policy.Kind.KNOCK,.8f,60000).alert!=null,"study boundary allows next knock");
        scenes.setScene(Policy.Scene.HOME);
        check(scenes.observe(Policy.Kind.KNOCK,.8f,60001).reason.equals("same_event"),"scene switching retains dedupe");
        check(scenes.observe(Policy.Kind.KNOCK,.8f,64000).reason.equals("cooldown"),"scene switching retains last alert");
        check(scenes.observe(Policy.Kind.KNOCK,.8f,75000).alert!=null,"home restores 15 seconds");
        check(study.scene==Policy.Scene.STUDY,"existing event keeps original scene");
        scenes.setQuiet(true);scenes.setScene(Policy.Scene.STUDY);
        check(scenes.observe(Policy.Kind.ALARM,.8f,90000).reason.equals("quiet_mode"),"scene cannot override pause");
        scenes.setQuiet(false);scenes.setEnabled(Policy.Kind.KNOCK,false);scenes.setScene(Policy.Scene.HOME);
        check(scenes.observe(Policy.Kind.KNOCK,.8f,100000).reason.equals("disabled"),"scene cannot override user category");
        System.out.println("Policy tests passed: "+checks);
    }
}
