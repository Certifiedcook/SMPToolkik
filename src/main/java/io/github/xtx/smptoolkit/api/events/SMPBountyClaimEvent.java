package io.github.xtx.smptoolkit.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class SMPBountyClaimEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList();
    private final int bountyId;
    private final UUID killer;
    private final UUID target;
    public SMPBountyClaimEvent(int bountyId,UUID killer,UUID target){this.bountyId=bountyId;this.killer=killer;this.target=target;}
    public int bountyId(){return bountyId;}
    public UUID killer(){return killer;}
    public UUID target(){return target;}
    @Override public @NotNull HandlerList getHandlers(){return HANDLERS;}
    public static @NotNull HandlerList getHandlerList(){return HANDLERS;}
}
