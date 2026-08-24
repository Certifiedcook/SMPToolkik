package io.github.xtx.smptoolkit.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class SMPCaseUpdateEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList();
    private final int caseId;
    private final String action;
    public SMPCaseUpdateEvent(int caseId,String action){this.caseId=caseId;this.action=action;}
    public int caseId(){return caseId;}
    public String action(){return action;}
    @Override public @NotNull HandlerList getHandlers(){return HANDLERS;}
    public static @NotNull HandlerList getHandlerList(){return HANDLERS;}
}
