import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.shengjing.core.Policy;

/** Consume time_ms, knock, doorbell, alarm scores. Emit only policy alerts. */
public final class ContinuousReplay {
    public static void main(String[] args) throws Exception {
        Policy policy = new Policy();
        policy.setScene(Policy.Scene.valueOf(args[0]));
        policy.setEnhanced(Boolean.parseBoolean(args[1]));
        var reader = new BufferedReader(new InputStreamReader(System.in));
        var kinds = new Policy.Kind[]{Policy.Kind.KNOCK, Policy.Kind.DOORBELL, Policy.Kind.ALARM};
        for (String line; (line = reader.readLine()) != null;) {
            String[] fields = line.split("\t");
            long time = Long.parseLong(fields[0]);
            for (int i = 0; i < kinds.length; i++) {
                float score = Float.parseFloat(fields[i+1]);
                var decision = policy.observe(kinds[i], score, time);
                if (decision.reason.equals("invalid_event")) throw new IllegalArgumentException("Invalid window");
                if (decision.alert != null) {
                    System.out.println(time + "\t" + kinds[i] + "\t" + score);
                    policy.feedback(decision.alert.id);
                }
            }
        }
    }
}
