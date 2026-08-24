package io.github.xtx.smptoolkit.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class SMPSeasonChangeEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList();
    private final String seasonId;
    private final String displayName;
    private final boolean started;
    public SMPSeasonChangeEvent(String seasonId,String displayName,boolean started){this.seasonId=seasonId;this.displayName=displayName;this.started=started;}
    public String seasonId(){return seasonId;}
    public String displayName(){return displayName;}
    public boolean started(){return started;}
    @Override public @NotNull HandlerList getHandlers(){return HANDLERS;}
    public static @NotNull HandlerList getHandlerList(){return HANDLERS;}
}
