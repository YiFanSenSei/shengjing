import java.util.List;
import org.shengjing.core.Policy;

/** Compare both shipped policies with exactly the same scripted classifier outputs. */
public final class SceneReplay {
    record Observation(int seconds, Policy.Kind kind, float score, String situation,
                       boolean wantsAlert, String homeReason, String studyReason) {}

    public static void main(String[] args) {
        var knock = Policy.Kind.KNOCK;
        var events = List.of(
            new Observation(0, knock, .80f, "第一位来客敲门", true, "new_event", "new_event"),
            new Observation(1, knock, .82f, "同一串敲击", false, "same_event", "same_event"),
            new Observation(20, knock, .76f, "同一来客再次敲门", false, "new_event", "cooldown"),
            new Observation(40, knock, .76f, "第二位来客敲门", true, "new_event", "cooldown"),
            new Observation(42, Policy.Kind.DOORBELL, .84f, "门铃响", true, "new_event", "new_event"),
            new Observation(45, Policy.Kind.ALARM, .77f, "闹钟响", true, "new_event", "new_event"),
            new Observation(61, knock, .80f, "第三位来客敲门", true, "new_event", "new_event"),
            new Observation(85, knock, .35f, "桌面轻响", false, "below_threshold", "below_threshold"));
        for (Policy.Scene scene : Policy.Scene.values()) {
            Policy policy = new Policy();
            policy.setScene(scene);
            int wanted = 0, delivered = 0, extra = 0;
            System.out.println("\n" + scene.title + "（固定分数回放，标准模式）");
            for (Observation event : events) {
                var result = policy.observe(event.kind(), event.score(), event.seconds() * 1000L);
                String expected = scene == Policy.Scene.HOME ? event.homeReason() : event.studyReason();
                if (!result.reason.equals(expected) || (result.alert != null) != expected.equals("new_event"))
                    throw new AssertionError(scene + " @ " + event.seconds() + "s: " + result.reason);
                if (event.wantsAlert()) wanted++;
                if (result.alert != null) {
                    if (event.wantsAlert()) delivered++;
                    else extra++;
                }
                System.out.printf("%2ds  %-14s  %s%n", event.seconds(), event.situation(), result.reason);
            }
            System.out.printf("预设需要的提醒命中 %d/%d，额外提醒 %d 次%n", delivered, wanted, extra);
        }
    }
}
