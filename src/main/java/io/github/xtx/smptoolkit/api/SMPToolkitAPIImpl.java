package io.github.xtx.smptoolkit.api;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.data.Database;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SMPToolkitAPIImpl implements SMPToolkitAPI {
    private final SMPToolkitPlugin plugin;
    public SMPToolkitAPIImpl(SMPToolkitPlugin plugin){this.plugin=plugin;}
    @Override public String version(){return plugin.getPluginMeta().getVersion();}
    @Override public boolean isFeatureEnabled(String key){return plugin.featureEnabled(key);}
    @Override public boolean isFrozen(UUID playerId){return plugin.staff()!=null&&plugin.staff().isFrozen(playerId);}
    @Override public boolean isVanished(UUID playerId){return plugin.staff()!=null&&plugin.staff().isVanished(playerId);}
    @Override public String activeSeason(){return plugin.platform()==null?"":plugin.platform().seasons().activeId();}
    @Override public CompletableFuture<Database.Stats> stats(UUID playerId){return plugin.database().stats(playerId);}
    @Override public CompletableFuture<Integer> openReportCount(UUID playerId){return plugin.platform()==null?CompletableFuture.completedFuture(0):plugin.platform().database().openReportCount(playerId);}
}
