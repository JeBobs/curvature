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

    private void tickOnlinePlaytime() {
        if (stateManager == null) return;
        double inc = playtimeIncrementPerSecond;
        if (inc <= 0) return;
        for (Player player : getServer().getOnlinePlayers()) {
            try {
                PlayerState state = stateManager.getWithDecay(player.getUniqueId());
                state.setX("playtime", state.getX("playtime") + inc);
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
