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
        System.out.println("Policy tests passed: "+checks);
    }
}
