package cc.interstellarmc.curvature;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.sql.SQLException;

public class PlayerListeners implements Listener {
    private final CurvaturePlugin plugin;
    private final StateManager stateManager;

    public PlayerListeners(CurvaturePlugin plugin) {
        this.plugin = plugin;
        this.stateManager = plugin.getStateManager();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        try {
            PlayerState state = stateManager.getWithDecay(p.getUniqueId());
            long lastQuit = state.getLastQuitTs();
            long now = System.currentTimeMillis();
            long offline = now - lastQuit;
            long threshold = plugin.getConfig().getLong("restThresholdMillis", 600000);
            if (offline > threshold) {
                double bonus = plugin.getConfig().getDouble("restBonus", 10.0);
                plugin.applyInput("playtime", bonus, p.getUniqueId());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        try {
            PlayerState state = stateManager.getWithDecay(p.getUniqueId());
            state.setLastQuitTs(System.currentTimeMillis());
            stateManager.saveAll();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof Player)) return;
        Player killer = event.getEntity().getKiller();
        try {
            plugin.applyInput("kills", 1.0, killer.getUniqueId());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        // ignore if not block movement
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        // optionally ignore vehicle or flying movement
        boolean ignoreFlying = plugin.getConfig().getBoolean("movementTracking.ignoreWhileFlying", true);
        boolean ignoreInVehicle = plugin.getConfig().getBoolean("movementTracking.ignoreInVehicle", true);
        if ((ignoreFlying && (p.isFlying() || p.isGliding()))
                || (ignoreInVehicle && p.getVehicle() != null)) {
            return;
        }

        // calculate horizontal distance moved
        Vector from = event.getFrom().toVector().setY(0);
        Vector to   = event.getTo().toVector().setY(0);
        double distance = from.distance(to);

        // clamp per-move increment
        double minInc = plugin.getConfig().getDouble("movementTracking.minIncrementPerMove", 0.1);
        double maxInc = plugin.getConfig().getDouble("movementTracking.maxIncrementPerMove", 1.0);
        double inc = Math.max(minInc, Math.min(distance, maxInc));

        try {
            plugin.applyInput("movement", inc, p.getUniqueId());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
