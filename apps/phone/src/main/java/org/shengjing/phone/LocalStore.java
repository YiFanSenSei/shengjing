package org.shengjing.phone;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.shengjing.core.EventHistory;
import org.shengjing.core.Policy;

/** App-private metadata only. Android backup is disabled in the manifest. */
final class LocalStore {
    private final SharedPreferences prefs;
    LocalStore(Context context) { prefs=context.getSharedPreferences("shengjing_local_v1",Context.MODE_PRIVATE); }
    void restoreSettings(Policy policy) {
        policy.setEnhanced(prefs.getBoolean("enhanced",false));
        policy.setQuiet(prefs.getBoolean("quiet",false));
        try { policy.setScene(Policy.Scene.valueOf(prefs.getString("scene","HOME"))); }
        catch(IllegalArgumentException e) { policy.setScene(Policy.Scene.HOME); }
        for(Policy.Kind kind:Policy.Kind.values()) policy.setEnabled(kind,prefs.getBoolean("enabled_"+kind.name(),true));
        for (Policy.Scene scene : Policy.Scene.values()) {
            for (Policy.Kind kind : Policy.Kind.values()) {
                policy.setLessFrequent(scene, kind, prefs.getBoolean("extended_" + scene + "_" + kind, false));
            }
        }
    }
    void saveSettings(Policy policy) {
        SharedPreferences.Editor edit=prefs.edit().putBoolean("enhanced",policy.isEnhanced())
            .putBoolean("quiet",policy.isQuiet()).putString("scene",policy.getScene().name());
        for(Policy.Kind kind:Policy.Kind.values()) edit.putBoolean("enabled_"+kind.name(),policy.isEnabled(kind));
        edit.apply();
    }
    EventHistory loadHistory() {
        List<EventHistory.Entry> entries=new ArrayList<>();
        try {
            JSONArray data=new JSONArray(prefs.getString("history","[]"));
            for(int i=Math.max(0,data.length()-EventHistory.LIMIT);i<data.length();i++) {
                try {
                    JSONObject e=data.getJSONObject(i);
                    entries.add(new EventHistory.Entry(e.getLong("id"),e.getLong("time"),Policy.Kind.valueOf(e.getString("kind")),
                        Policy.Scene.valueOf(e.getString("scene")),(float)e.getDouble("score"),e.getBoolean("simulated"),
                        EventHistory.Delivery.valueOf(e.getString("delivery")),EventHistory.Feedback.valueOf(e.getString("feedback"))));
                } catch(JSONException | IllegalArgumentException ignored) { /* Skip invalid metadata, never log user records. */ }
            }
        } catch(JSONException ignored) { /* Recovery leaves settings intact. */ }
        return new EventHistory(prefs.getLong("sequence",0),entries);
    }
    long consideredThrough(Policy.Scene scene, Policy.Kind kind) {
        return prefs.getLong("considered_" + scene + "_" + kind, 0L);
    }
    void recordChoice(Policy.Scene scene, Policy.Kind kind, boolean extended, long through) {
        prefs.edit().putBoolean("extended_" + scene + "_" + kind, extended)
            .putLong("considered_" + scene + "_" + kind, through).apply();
    }
    void saveHistory(EventHistory history) {
        JSONArray data=new JSONArray();
        for(EventHistory.Entry e:history.snapshot()) {
            try {
                data.put(new JSONObject().put("id",e.id).put("time",e.time).put("kind",e.kind.name()).put("scene",e.scene.name())
                    .put("score",(double)e.score).put("simulated",e.simulated).put("delivery",e.delivery.name()).put("feedback",e.feedback.name()));
            } catch(JSONException e1) { throw new IllegalStateException("Invalid local metadata",e1); }
        }
        prefs.edit().putLong("sequence",history.sequence()).putString("history",data.toString()).apply();
    }
}
