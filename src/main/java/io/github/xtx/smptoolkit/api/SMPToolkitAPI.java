package io.github.xtx.smptoolkit.api;

import io.github.xtx.smptoolkit.data.Database;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface SMPToolkitAPI {
    String version();
    boolean isFeatureEnabled(String key);
    boolean isFrozen(UUID playerId);
    boolean isVanished(UUID playerId);
    String activeSeason();
    CompletableFuture<Database.Stats> stats(UUID playerId);
    CompletableFuture<Integer> openReportCount(UUID playerId);
}
