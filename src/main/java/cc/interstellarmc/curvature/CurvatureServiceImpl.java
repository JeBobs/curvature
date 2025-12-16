package cc.interstellarmc.curvature;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

class CurvatureServiceImpl implements CurvatureService {
    private final CurvaturePlugin plugin;

    CurvatureServiceImpl(CurvaturePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Set<String> getCurveNames() {
        return plugin.getCurves().keySet();
    }

    @Override
    public double getMultiplier(UUID playerId, String curve) throws SQLException {
        ICurve c = plugin.getCurves().get(curve);
        if (c == null) return 0.0;
        PlayerState s = plugin.getStateManager().getWithDecay(playerId);
        return c.apply(s.getX(curve));
    }

    @Override
    public double getRawX(UUID playerId, String curve) throws SQLException {
        PlayerState s = plugin.getStateManager().getWithDecay(playerId);
        return s.getX(curve);
    }

    @Override
    public Map<String, Double> getAllMultipliers(UUID playerId) throws SQLException {
        PlayerState s = plugin.getStateManager().getWithDecay(playerId);
        Map<String, Double> out = new HashMap<>();
        for (Map.Entry<String, ICurve> e : plugin.getCurves().entrySet()) {
            out.put(e.getKey(), e.getValue().apply(s.getX(e.getKey())));
        }
        return out;
    }

    @Override
    public Map<String, Double> getAllX(UUID playerId) throws SQLException {
        PlayerState s = plugin.getStateManager().getWithDecay(playerId);
        Map<String, Double> out = new HashMap<>();
        for (String name : plugin.getCurves().keySet()) {
            out.put(name, s.getX(name));
        }
        return out;
    }
}
