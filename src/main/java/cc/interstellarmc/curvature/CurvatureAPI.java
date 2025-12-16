package cc.interstellarmc.curvature;

import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicesManager;

/**
 * Convenience accessor for other plugins to retrieve the Curvature service.
 */
public final class CurvatureAPI {
    private CurvatureAPI() {}

    public static CurvatureService get() {
        ServicesManager sm = Bukkit.getServer().getServicesManager();
        return sm.load(CurvatureService.class);
    }
}
