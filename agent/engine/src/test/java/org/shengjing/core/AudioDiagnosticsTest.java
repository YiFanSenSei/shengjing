package org.shengjing.core;

public final class AudioDiagnosticsTest {
    private static int checks;
    private static void check(boolean value,String message) { checks++;if(!value)throw new AssertionError(message); }
    public static void main(String[] args) {
        check(AudioDiagnostics.rmsDbfs(new float[]{0,0})==Double.NEGATIVE_INFINITY,"silence has no finite level");
        check(Math.abs(AudioDiagnostics.rmsDbfs(new float[]{1,-1}))<0.0001,"full scale");
        check(Math.abs(AudioDiagnostics.rmsDbfs(new float[]{.5f,-.5f})+6.0206)<0.0001,"half amplitude is -6 dBFS");
        boolean rejected=false;
        try { AudioDiagnostics.rmsDbfs(new float[]{Float.NaN}); } catch(IllegalArgumentException e) { rejected=true; }
        check(rejected,"invalid PCM rejected");
        rejected=false;
        try { AudioDiagnostics.rmsDbfs(new float[0]); } catch(IllegalArgumentException e) { rejected=true; }
        check(rejected,"empty input rejected");
        AudioDiagnostics d=new AudioDiagnostics();
        float[] scores={.2f,Float.NaN,.7f};
        d.update(new float[]{.2f},scores,new String[]{"below_threshold",null,"new_event"},"Alarm clock",.7f,12);
        scores[0]=.9f;
        check(Math.abs(d.peak(Policy.Kind.KNOCK)-.2f)<.0001,"caller cannot mutate captured peak");
        check(Float.isNaN(d.peak(Policy.Kind.DOORBELL)),"missing label is not zero score");
        d.update(new float[]{0},new float[]{.1f,.4f,.8f},new String[]{"below_threshold","below_threshold","same_event"},"Alarm clock",.8f,8);
        check(Math.abs(d.peak(Policy.Kind.KNOCK)-.2f)<.0001,"lower score preserves peak");
        check(Math.abs(d.peak(Policy.Kind.ALARM)-.8f)<.0001,"higher score advances peak");
        check(d.frames()==2,"counts valid analyzed windows");
        check(new AudioDiagnostics().frames()==0,"new session resets count");
        check(Float.isNaN(new AudioDiagnostics().peak(Policy.Kind.ALARM)),"new session resets peaks");
        System.out.println("Audio diagnostics tests passed: "+checks);
    }
}
