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
                state.setX("playtime", state.getX("playtime") + bonus);
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
            PlayerState state = stateManager.getWithDecay(killer.getUniqueId());
            state.setX("kills", state.getX("kills") + 1);
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
        boolean ignoreFlying = plugin.getConfig().getBoolean("movement.ignoreWhileFlying", true);
        boolean ignoreInVehicle = plugin.getConfig().getBoolean("movement.ignoreInVehicle", true);
        if ((ignoreFlying && (p.isFlying() || p.isGliding()))
                || (ignoreInVehicle && p.getVehicle() != null)) {
            return;
        }

        // calculate horizontal distance moved
        Vector from = event.getFrom().toVector().setY(0);
        Vector to   = event.getTo().toVector().setY(0);
        double distance = from.distance(to);

        // clamp per-move increment
        double minInc = plugin.getConfig().getDouble("movement.minIncrementPerMove", 0.1);
        double maxInc = plugin.getConfig().getDouble("movement.maxIncrementPerMove", 1.0);
        double inc = Math.max(minInc, Math.min(distance, maxInc));

        try {
            PlayerState state = stateManager.getWithDecay(p.getUniqueId());
            state.setX("movement", state.getX("movement") + inc);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
