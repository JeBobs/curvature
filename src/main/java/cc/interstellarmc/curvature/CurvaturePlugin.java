package cc.interstellarmc.curvature;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class CurvaturePlugin extends JavaPlugin {
    private static final long DECAY_INTERVAL_SECONDS = 10;

    private Connection conn;
    private StateManager stateManager;
    private final Map<String, ICurve> curves = new HashMap<>();
    private final Map<String, Double> decayRatesPerSecond = new HashMap<>();
    private final Map<String, java.util.List<CurveBinding>> inputBindings = new HashMap<>();
    private double playtimeIncrementPerSecond = 1.0 / 60.0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadCurves();
        if (curves.isEmpty()) {
            getLogger().severe("No curves configured; disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        loadBindings();

        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + getDataFolder() + "/income.db");
            stateManager = new StateManager(conn, getLogger(), decayRatesPerSecond);
        } catch (SQLException e) {
            getLogger().severe("Failed to init database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new PlayerListeners(this), this);
        getServer().getScheduler()
                .runTaskTimer(
                        this,
                        stateManager::decayAll,
                        DECAY_INTERVAL_SECONDS * 20,
                        DECAY_INTERVAL_SECONDS * 20
                );
        getServer().getScheduler()
                .runTaskTimer(
                        this,
                        this::tickOnlinePlaytime,
                        20L,
                        20L
                );

        // expose API for other plugins
        getServer().getServicesManager().register(
                CurvatureService.class,
                new CurvatureServiceImpl(this),
                this,
                ServicePriority.Normal
        );
        new CurvatureCommand(this);
        new CurvaturePlaceholderExpansion(this).register();
    }

    @Override
    public void onDisable() {
        if (stateManager == null || conn == null) return;
        try {
            stateManager.saveAll();
            conn.close();
        } catch (Exception e) {
            getLogger().warning("Error while shutting down: " + e.getMessage());
        }
    }

    void reloadPluginConfig() {
        reloadConfig();
        loadCurves();
        loadBindings();
        if (stateManager != null) {
            stateManager.updateDecayRates(decayRatesPerSecond);
        }
    }

    private void loadCurves() {
        playtimeIncrementPerSecond = getConfig().getDouble("playtimeTracking.incrementPerSecond", 1.0 / 60.0);
        curves.clear();
        decayRatesPerSecond.clear();
        ConfigurationSection sec = getConfig().getConfigurationSection("curves");
        if (sec == null) {
            getLogger().severe("Missing 'curves' section in config.yml.");
            return;
        }
        for (String name : sec.getKeys(false)) {
            ConfigurationSection c = sec.getConfigurationSection(name);
            if (c == null) {
                getLogger().warning("Skipping curve '" + name + "' because its config section is missing.");
                continue;
            }
            ICurve base;
            switch (c.getString("type")) {
                case "logistic":
                    base = new LogisticCurve(
                            c.getDouble("params.L"),
                            c.getDouble("params.k"),
                            c.getDouble("params.x0")
                    );
                    break;
                case "exponential":
                    base = new ExponentialCurve(c.getDouble("params.base"));
                    break;
                case "polynomial":
                    base = new PolynomialCurve(
                            c.getDouble("params.a"),
                            c.getDouble("params.b"),
                            c.getDouble("params.c")
                    );
                    break;
                case "sampled":
                    SampledCurve.Interpolation mode;
                    String interp = c.getString("interpolate", "linear").toUpperCase();
                    try {
                        mode = SampledCurve.Interpolation.valueOf(interp);
                    } catch (IllegalArgumentException ex) {
                        getLogger().warning("Invalid interpolation '" + interp + "' for curve " + name + ", defaulting to LINEAR");
                        mode = SampledCurve.Interpolation.LINEAR;
                    }
                    java.util.List<SampledCurve.Point> pts = new java.util.ArrayList<>();
                    for (java.util.Map<?, ?> m : c.getMapList("points")) {
                        if (m == null) continue;
                        Object ox = m.get("x");
                        Object oy = m.get("y");
                        Double px = ox instanceof Number ? ((Number) ox).doubleValue() : null;
                        Double py = oy instanceof Number ? ((Number) oy).doubleValue() : null;
                        SampledCurve.Point p = SampledCurve.Point.of(px, py);
                        if (p == null) {
                            getLogger().warning("Skipping invalid point in curve " + name + ": " + m);
                            continue;
                        }
                        pts.add(p);
                    }
                    if (pts.size() < 2) {
                        getLogger().warning("Curve " + name + " has fewer than 2 points; skipping.");
                        continue;
                    }
                    base = new SampledCurve(pts, mode);
                    break;
                default:
                    continue;
            }
            // wrap with limits if configured
            double minY = c.getDouble("limits.minY", Double.NEGATIVE_INFINITY);
            double maxY = c.getDouble("limits.maxY", Double.POSITIVE_INFINITY);
            ICurve curve = base;
            if (!Double.isInfinite(minY) || !Double.isInfinite(maxY)) {
                curve = new BoundedCurve(base, minY, maxY);
            }
            curves.put(name, curve);

            double dpm = c.getDouble("decayPerMinute", 0.0);
            decayRatesPerSecond.put(name, dpm / 60.0);
        }
    }

    private void loadBindings() {
        inputBindings.clear();
        ConfigurationSection inputs = getConfig().getConfigurationSection("inputs");
        if (inputs != null) {
            for (String metric : inputs.getKeys(false)) {
                ConfigurationSection metricSec = inputs.getConfigurationSection(metric);
                if (metricSec == null) continue;
                java.util.List<Map<?, ?>> targets = metricSec.getMapList("targets");
                java.util.List<CurveBinding> bindings = new java.util.ArrayList<>();
                for (Map<?, ?> t : targets) {
                    String curveName = t.get("curve") != null ? t.get("curve").toString() : null;
                    if (curveName == null || !curves.containsKey(curveName)) {
                        getLogger().warning("Skipping input target for metric '" + metric + "' due to missing or unknown curve: " + curveName);
                        continue;
                    }
                    double weight = 1.0;
                    Object w = t.get("weight");
                    if (w instanceof Number) {
                        weight = ((Number) w).doubleValue();
                    }
                    bindings.add(new CurveBinding(curveName, weight));
                }
                if (!bindings.isEmpty()) {
                    inputBindings.put(metric, bindings);
                }
            }
        }
        // fallback defaults: bind metric to curve of same name if not configured
        for (String curveName : curves.keySet()) {
            if (!inputBindings.containsKey(curveName)) {
                inputBindings.put(curveName, java.util.Collections.singletonList(new CurveBinding(curveName, 1.0)));
            }
        }
    }

    public void applyInput(String metric, double amount, java.util.UUID playerId) throws SQLException {
        java.util.List<CurveBinding> bindings = inputBindings.get(metric);
        if (bindings == null || bindings.isEmpty()) return;
        PlayerState state = stateManager.getWithDecay(playerId);
        for (CurveBinding b : bindings) {
            double current = state.getX(b.curveName());
            state.setX(b.curveName(), current + amount * b.weight());
        }
    }

    private record CurveBinding(String curveName, double weight) { }

    private void tickOnlinePlaytime() {
        if (stateManager == null) return;
        double inc = playtimeIncrementPerSecond;
        if (inc <= 0) return;
        for (Player player : getServer().getOnlinePlayers()) {
            try {
                applyInput("playtime", inc, player.getUniqueId());
            } catch (SQLException e) {
                getLogger().warning("Failed to tick playtime for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    public StateManager getStateManager() {
        return stateManager;
    }

    public Map<String, ICurve> getCurves() {
        return curves;
    }
}
