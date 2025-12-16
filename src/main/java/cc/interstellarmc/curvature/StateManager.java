package cc.interstellarmc.curvature;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class StateManager {
    private final Connection conn;
    private final Logger log;
    private final Map<UUID, PlayerState> cache = new HashMap<>();
    private volatile Map<String, Double> decayRatesPerSecond;

    public StateManager(Connection conn, Logger log, Map<String, Double> decayRatesPerSecond) throws SQLException {
        this.conn = conn;
        this.log = log;
        this.decayRatesPerSecond = new HashMap<>(decayRatesPerSecond);
        try (Statement s = conn.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS player_income(" +
                    "uuid TEXT PRIMARY KEY, playtime REAL, kills REAL, movement REAL, last_quit BIGINT)");
        }
    }

    public PlayerState get(UUID uuid) throws SQLException {
        if (cache.containsKey(uuid)) return cache.get(uuid);
        PlayerState state = new PlayerState(uuid);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT playtime,kills,movement,last_quit FROM player_income WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    state.setX("playtime", rs.getDouble("playtime"));
                    state.setX("kills", rs.getDouble("kills"));
                    state.setX("movement", rs.getDouble("movement"));
                    state.setLastQuitTs(rs.getLong("last_quit"));
                }
            }
        }
        state.setLastDecayMs(System.currentTimeMillis());
        cache.put(uuid, state);
        return state;
    }

    public void saveAll() throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "REPLACE INTO player_income(uuid,playtime,kills,movement,last_quit) VALUES(?,?,?,?,?)")) {
            for (PlayerState s : cache.values()) {
                ps.setString(1, s.getUuid().toString());
                ps.setDouble(2, s.getX("playtime"));
                ps.setDouble(3, s.getX("kills"));
                ps.setDouble(4, s.getX("movement"));
                ps.setLong(5, s.getLastQuitTs());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void decayAll() {
        Map<String, Double> rates = this.decayRatesPerSecond;
        if (rates.isEmpty()) return;

        long now = System.currentTimeMillis();
        for (PlayerState state : cache.values()) {
            applyElapsedDecay(state, now, rates);
        }
    }

    public void updateDecayRates(Map<String,Double> newRates) {
        this.decayRatesPerSecond = new HashMap<>(newRates);
    }

    public PlayerState getWithDecay(UUID uuid) throws SQLException {
        PlayerState state = get(uuid);
        applyElapsedDecay(state, System.currentTimeMillis(), decayRatesPerSecond);
        return state;
    }

    private void applyElapsedDecay(PlayerState state, long now, Map<String, Double> rates) {
        long last = state.getLastDecayMs();
        long deltaMs = now - last;
        if (deltaMs <= 0) return;
        double seconds = deltaMs / 1000.0;
        for (Map.Entry<String, Double> entry : rates.entrySet()) {
            String curve = entry.getKey();
            double decayPerSecond = entry.getValue();
            if (decayPerSecond <= 0) continue;
            double x = state.getX(curve);
            double newX = Math.max(0, x - decayPerSecond * seconds);
            state.setX(curve, newX);
        }
        state.setLastDecayMs(now);
    }
}
