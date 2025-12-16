package cc.interstellarmc.curvature;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerState {
    private final UUID uuid;
    private final Map<String, Double> xValues = new HashMap<>();
    private long lastQuitTs;
    private long lastDecayMs;

    public PlayerState(UUID uuid) {
        this.uuid = uuid;
        this.lastDecayMs = System.currentTimeMillis();
    }

    public double getX(String curve) {
        return xValues.getOrDefault(curve, 0.0);
    }

    public void setX(String curve, double value) {
        xValues.put(curve, value);
    }

    public long getLastQuitTs() {
        return lastQuitTs;
    }

    public void setLastQuitTs(long ts) {
        lastQuitTs = ts;
    }

    public UUID getUuid() { return uuid; }

    public long getLastDecayMs() {
        return lastDecayMs;
    }

    public void setLastDecayMs(long lastDecayMs) {
        this.lastDecayMs = lastDecayMs;
    }
}
