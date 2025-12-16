package cc.interstellarmc.curvature;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Public-facing service for other plugins to query Curvature multipliers.
 */
public interface CurvatureService {
    Set<String> getCurveNames();

    double getMultiplier(UUID playerId, String curve) throws SQLException;

    double getRawX(UUID playerId, String curve) throws SQLException;

    Map<String, Double> getAllMultipliers(UUID playerId) throws SQLException;

    Map<String, Double> getAllX(UUID playerId) throws SQLException;
}
