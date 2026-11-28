package org.shengjing.core;

import java.util.List;

/**
 * Reproducible policy table for scene, personal interval and enhanced mode.
 * Reads no audio, connects to no device and does not replace field evidence.
 */
public final class PolicyReplay {
    private record Event(int second, Policy.Kind kind, float score, String situation, boolean wanted) {}
    private record Scenario(String name, Policy.Scene scene, boolean lessFrequent,
                            boolean enhanced, List<Event> events, String[] expected) {}

    private static final List<Event> BASE = List.of(
        new Event(0, Policy.Kind.KNOCK, .80f, "第一位来客敲门", true),
        new Event(1, Policy.Kind.KNOCK, .82f, "同一串敲击", false),
        new Event(20, Policy.Kind.KNOCK, .76f, "同一位来客再敲", false),
        new Event(40, Policy.Kind.KNOCK, .76f, "第二位来客敲门", true),
        new Event(42, Policy.Kind.DOORBELL, .84f, "门铃响", true),
        new Event(45, Policy.Kind.ALARM, .77f, "闹钟响", true),
        new Event(61, Policy.Kind.KNOCK, .80f, "第三位来客敲门", true),
        new Event(85, Policy.Kind.KNOCK, .35f, "桌面轻响", false)
    );

    private static final List<Event> ENHANCED = List.of(
        new Event(0, Policy.Kind.KNOCK, .45f, "0.45 的敲门", true),
        new Event(1, Policy.Kind.KNOCK, .45f, "同一串敲击", false),
        new Event(42, Policy.Kind.DOORBELL, .45f, "0.45 的门铃", false)
    );

    public static void main(String[] args) {
        List<Scenario> scenarios = List.of(
            new Scenario("居家·默认", Policy.Scene.HOME, false, false, BASE, new String[]{
                "new_event", "same_event", "new_event", "new_event",
                "new_event", "new_event", "new_event", "below_threshold"}),
            new Scenario("学习·默认", Policy.Scene.STUDY, false, false, BASE, new String[]{
                "new_event", "same_event", "cooldown", "cooldown",
                "new_event", "new_event", "new_event", "below_threshold"}),
            new Scenario("居家·已采纳 60 秒", Policy.Scene.HOME, true, false, BASE, new String[]{
                "new_event", "same_event", "cooldown", "cooldown",
                "new_event", "new_event", "new_event", "below_threshold"}),
            new Scenario("学习·已采纳 120 秒", Policy.Scene.STUDY, true, false, BASE, new String[]{
                "new_event", "same_event", "cooldown", "cooldown",
                "new_event", "new_event", "cooldown", "below_threshold"}),
            new Scenario("居家·增强 0.40", Policy.Scene.HOME, false, true, ENHANCED, new String[]{
                "new_event", "same_event", "below_threshold"})
        );

        System.out.println("同一串事件的策略结果（固定分数、固定时间，不读音频）");
        for (Scenario scenario : scenarios) {
            System.out.println();
            System.out.println(scenario.name());
            Policy policy = new Policy();
            policy.setScene(scenario.scene());
            if (scenario.lessFrequent()) policy.setLessFrequent(scenario.scene(), Policy.Kind.KNOCK, true);
            policy.setEnhanced(scenario.enhanced());
            int wanted = 0, delivered = 0, extra = 0;
            for (int i = 0; i < scenario.events().size(); i++) {
                Event event = scenario.events().get(i);
                Policy.Decision decision = policy.observe(event.kind(), event.score(), event.second() * 1000L);
                String expected = scenario.expected()[i];
                if (!expected.equals(decision.reason) || (decision.alert != null) != expected.equals("new_event"))
                    throw new AssertionError(scenario.name() + " @ " + event.second() + "s: " + decision.reason);
                if (event.wanted()) wanted++;
                if (decision.alert != null) {
                    if (event.wanted()) delivered++;
                    else extra++;
                }
                System.out.printf("%3ds  %-14s  %-8s  %s%n", event.second(), event.situation(),
                    reasonLabel(decision.reason), AudioDiagnostics.reasonText(decision.reason));
            }
            System.out.printf("需要提醒命中 %d/%d，额外提醒 %d 次%n", delivered, wanted, extra);
        }

        checkPreferences();
        System.out.println();
        System.out.println("偏好只作用于所选场景和声音；闹钟不能采纳；检查通过。");
    }

    private static String reasonLabel(String reason) {
        return switch (reason) {
            case "new_event" -> "提醒";
            case "same_event" -> "同串";
            case "cooldown" -> "冷却";
            case "below_threshold" -> "低分";
            case "disabled" -> "已关闭";
            case "quiet_mode" -> "已暂停";
            default -> "无效";
        };
    }

    private static void checkPreferences() {
        Policy home = new Policy();
        home.setLessFrequent(Policy.Scene.HOME, Policy.Kind.KNOCK, true);
        check(home.isLessFrequent(Policy.Scene.HOME, Policy.Kind.KNOCK), "居家敲门偏好");
        check(home.cooldown(Policy.Kind.KNOCK) == 60000, "居家敲门 60 秒");
        check(!home.isLessFrequent(Policy.Scene.STUDY, Policy.Kind.KNOCK), "偏好不跨场景");
        check(home.cooldown(Policy.Kind.DOORBELL) == 15000, "门铃不受敲门偏好影响");
        check(home.observe(Policy.Kind.KNOCK, .8f, 0).alert != null, "采纳后首次提醒");
        check(home.observe(Policy.Kind.KNOCK, .8f, 59999).reason.equals("cooldown"), "采纳后 60 秒内冷却");
        Policy homeBoundary = new Policy();
        homeBoundary.setLessFrequent(Policy.Scene.HOME, Policy.Kind.KNOCK, true);
        check(homeBoundary.observe(Policy.Kind.KNOCK, .8f, 0).alert != null, "采纳后 60 秒边界首次提醒");
        check(homeBoundary.observe(Policy.Kind.KNOCK, .8f, 60000).alert != null, "采纳后 60 秒边界");

        Policy study = new Policy();
        study.setScene(Policy.Scene.STUDY);
        study.setLessFrequent(Policy.Scene.STUDY, Policy.Kind.KNOCK, true);
        check(study.cooldown(Policy.Kind.KNOCK) == 120000, "学习敲门 120 秒");
        check(study.observe(Policy.Kind.KNOCK, .8f, 0).alert != null, "学习首次提醒");
        check(study.observe(Policy.Kind.KNOCK, .8f, 119999).reason.equals("cooldown"), "学习 120 秒内冷却");
        Policy studyBoundary = new Policy();
        studyBoundary.setScene(Policy.Scene.STUDY);
        studyBoundary.setLessFrequent(Policy.Scene.STUDY, Policy.Kind.KNOCK, true);
        check(studyBoundary.observe(Policy.Kind.KNOCK, .8f, 0).alert != null, "学习 120 秒边界首次提醒");
        check(studyBoundary.observe(Policy.Kind.KNOCK, .8f, 120000).alert != null, "学习 120 秒边界");

        Policy bell = new Policy();
        bell.setLessFrequent(Policy.Scene.HOME, Policy.Kind.DOORBELL, true);
        check(bell.cooldown(Policy.Kind.DOORBELL) == 60000, "门铃 60 秒");
        bell.setLessFrequent(Policy.Scene.HOME, Policy.Kind.ALARM, true);
        check(!bell.isLessFrequent(Policy.Scene.HOME, Policy.Kind.ALARM), "闹钟不能采纳");
        check(bell.cooldown(Policy.Kind.ALARM) == 15000, "闹钟仍为 15 秒");

        Policy visitors = new Policy();
        check(visitors.observe(Policy.Kind.KNOCK, .8f, 0).alert != null, "居家第一位来客");
        check(visitors.observe(Policy.Kind.KNOCK, .8f, 20000).alert != null, "居家第二位来客");
        Policy studyVisitors = new Policy();
        studyVisitors.setScene(Policy.Scene.STUDY);
        check(studyVisitors.observe(Policy.Kind.KNOCK, .8f, 0).alert != null, "学习第一位来客");
        check(studyVisitors.observe(Policy.Kind.KNOCK, .8f, 20000).reason.equals("cooldown"), "学习压掉第二位来客");
        Policy adoptedVisitors = new Policy();
        adoptedVisitors.setLessFrequent(Policy.Scene.HOME, Policy.Kind.KNOCK, true);
        check(adoptedVisitors.observe(Policy.Kind.KNOCK, .8f, 0).alert != null, "采纳后第一位来客");
        check(adoptedVisitors.observe(Policy.Kind.KNOCK, .8f, 20000).reason.equals("cooldown"), "采纳后压掉第二位来客");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
