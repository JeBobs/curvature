package cc.interstellarmc.curvature;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class CurvaturePlaceholderExpansion extends PlaceholderExpansion {
    private final CurvaturePlugin plugin;

    public CurvaturePlaceholderExpansion(CurvaturePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String getIdentifier() {
        return "curvature";
    }

    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().isEmpty() ? "" : plugin.getDescription().getAuthors().get(0);
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        String[] parts = identifier.split("_");
        if (parts.length != 2 || !"multiplier".equals(parts[1])) return null;
        String curve = parts[0];
        PlayerState state;
        try {
            state = plugin.getStateManager().getWithDecay(player.getUniqueId());
        } catch (Exception e) {
            return null;
        }
        ICurve c = plugin.getCurves().get(curve);
        if (c == null) return null;
        double x = state.getX(curve);
        double y = c.apply(x);
        return String.format("%.2f", y);
    }
}
