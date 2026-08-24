package io.github.xtx.smptoolkit.modules.chatgames;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import io.github.xtx.smptoolkit.core.Module;
import io.github.xtx.smptoolkit.core.Text;
import io.github.xtx.smptoolkit.data.Database;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class ChatGamesModule implements Module, Listener {
    private final SMPToolkitPlugin plugin;
    private final List<GamePrompt> prompts = new ArrayList<>();
    private volatile ActiveGame active;
    private BukkitTask cycleTask;
    private BukkitTask expiryTask;

    public ChatGamesModule(SMPToolkitPlugin plugin) { this.plugin = plugin; seedPrompts(); }
    @Override public String name() { return "chat-games"; }
    @Override public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        int minutes = Math.max(1, plugin.getConfig().getInt("chat-games.interval-minutes", 15));
        cycleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (active == null && plugin.getConfig().getBoolean("chat-games.enabled", true)) startRandom(null); }, minutes * 1200L, minutes * 1200L);
    }
    @Override public void disable() { if (cycleTask != null) cycleTask.cancel(); if (expiryTask != null) expiryTask.cancel(); active = null; }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        ActiveGame game = active; if (game == null) return;
        String rawAnswer = Text.plain(event.message());
        String answer = game.exact() ? normalizeExact(rawAnswer) : normalize(rawAnswer);
        if (!game.answers().contains(answer)) return;
        event.setCancelled(true); Player winner = event.getPlayer();
        synchronized (this) { if (active != game) return; active = null; if (expiryTask != null) { expiryTask.cancel(); expiryTask = null; } }
        plugin.sync(() -> {
            Bukkit.broadcast(Text.mm("<green><bold>Chat Game:</bold></green> <white>" + Text.escapeMini(winner.getName()) + "</white> <gray>won in</gray> <yellow>" + String.format(Locale.ROOT, "%.2fs", (System.currentTimeMillis()-game.startedAt())/1000.0) + "</yellow><gray>!</gray>"));
            reward(winner);
            if (plugin.stats() != null) plugin.stats().incrementChatWin(winner.getUniqueId());
            if (plugin.platform() != null) { plugin.platform().progression().recordChatWin(winner); plugin.platform().seasons().recordChatWin(winner); }
        });
    }

    public boolean handle(CommandSender sender, String label, String[] args) {
        if (!label.equalsIgnoreCase("chatgames")) return false;
        if (args.length == 0) { sender.sendMessage(Text.mm("<gold>Chat Games</gold> <gray>— /chatgames stats [player], /chatgames top</gray>")); if (active != null) sender.sendMessage(Text.mm("<yellow>An active game is currently running.</yellow>")); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> { if (!sender.hasPermission("smp.events.admin")) return deny(sender); String type = args.length > 1 ? args[1] : null; if (!startRandom(type)) sender.sendMessage(Text.mm("<red>A game is already active or that type is unknown.</red>")); return true; }
            case "stop" -> { if (!sender.hasPermission("smp.events.admin")) return deny(sender); stopGame("<gray>The active chat game was stopped.</gray>"); return true; }
            case "stats" -> { if (args.length == 1) { if (!(sender instanceof Player p)) { sender.sendMessage("Use /chatgames stats <player>"); return true; } showStats(sender, p.getUniqueId(), p.getName()); } else resolve(args[1], sender, (id,name) -> showStats(sender,id,name)); return true; }
            case "top" -> { plugin.database().leaderboard("chat_wins",10).thenAccept(rows -> plugin.sync(() -> { sender.sendMessage(Text.mm("<gold><bold>Top Chat Game Wins</bold></gold>")); int i=1; for (Database.LeaderboardEntry row: rows) sender.sendMessage(Text.mm("<gray>"+(i++)+".</gray> <white>"+Text.escapeMini(row.name())+"</white> <yellow>"+row.chatWins()+"</yellow>")); })); return true; }
            default -> { sender.sendMessage(Text.mm("<red>Usage: /chatgames <start|stop|stats|top></red>")); return true; }
        }
    }

    public synchronized boolean startRandom(String requestedType) {
        if (active != null) return false;
        List<GamePrompt> pool = prompts;
        if (requestedType != null && !requestedType.isBlank()) { pool = prompts.stream().filter(p -> p.type().equalsIgnoreCase(requestedType)).toList(); if (pool.isEmpty()) return false; }
        GamePrompt rendered = pool.get(ThreadLocalRandom.current().nextInt(pool.size())).render();
        Set<String> acceptedAnswers = rendered.answers().stream().map(answer -> rendered.exact() ? normalizeExact(answer) : normalize(answer)).collect(java.util.stream.Collectors.toSet());
        active = new ActiveGame(acceptedAnswers, rendered.exact(), System.currentTimeMillis());
        Bukkit.broadcast(Text.mm("<aqua><bold>CHAT GAME — " + Text.escapeMini(rendered.type().toUpperCase(Locale.ROOT)) + "</bold></aqua>\n<white>" + Text.escapeMini(rendered.question()) + "</white>\n<gray>Type the answer in chat. First correct answer wins.</gray>"));
        int seconds = Math.max(10, plugin.getConfig().getInt("chat-games.answer-time-seconds",60));
        expiryTask = Bukkit.getScheduler().runTaskLater(plugin, () -> { ActiveGame old; synchronized (this) { old = active; active = null; expiryTask = null; } if (old != null) Bukkit.broadcast(Text.mm("<gray>The chat game expired with no winner.</gray>")); }, seconds * 20L);
        return true;
    }

    public synchronized void stopGame(String message) { if (active == null) return; active = null; if (expiryTask != null) { expiryTask.cancel(); expiryTask = null; } if (message != null) Bukkit.broadcast(Text.mm(message)); }
    private void reward(Player winner) { for (String command : plugin.getConfig().getStringList("chat-games.reward-commands")) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", winner.getName())); }
    private void showStats(CommandSender sender, UUID id, String name) { plugin.database().stats(id).thenAccept(s -> plugin.sync(() -> sender.sendMessage(Text.mm("<gold>"+Text.escapeMini(name)+"</gold><gray>'s chat-game wins:</gray> <yellow>"+s.chatWins()+"</yellow>")))); }
    private void resolve(String name, CommandSender sender, java.util.function.BiConsumer<UUID,String> consumer) { Player p=Bukkit.getPlayerExact(name); if(p!=null){consumer.accept(p.getUniqueId(),p.getName());return;} plugin.database().findPlayerByName(name).thenAccept(opt->plugin.sync(()->{if(opt.isEmpty())sender.sendMessage(Text.mm("<red>Unknown player.</red>"));else consumer.accept(opt.get().uuid(),opt.get().name());})); }
    private boolean deny(CommandSender sender) { sender.sendMessage(Text.mm("<red>No permission.</red>")); return true; }
    private String normalize(String text) { return normalizeExact(text).toLowerCase(Locale.ROOT); }
    private String normalizeExact(String text) { return text == null ? "" : text.trim().replaceAll("\\s+", " "); }

    private void seedPrompts() {
        prompts.add(new GamePrompt("reaction", "Type: diamond", Set.of("diamond"), false)); prompts.add(new GamePrompt("reaction", "Type: creeper", Set.of("creeper"), false));
        prompts.add(new GamePrompt("unscramble", "Unscramble: dnmoaid", Set.of("diamond"), false)); prompts.add(new GamePrompt("unscramble", "Unscramble: rpeceer", Set.of("creeper"), false));
        prompts.add(new GamePrompt("math", "Solve: 18 × 7", Set.of("126"), false)); prompts.add(new GamePrompt("math", "Solve: 144 ÷ 12 + 9", Set.of("21"), false));
        prompts.add(new GamePrompt("trivia", "Which dimension contains the Ender Dragon?", Set.of("the end","end"), false)); prompts.add(new GamePrompt("trivia", "What material is needed with diamonds to craft a netherite upgrade?", Set.of("netherite ingot","netherite"), false));
        prompts.add(new GamePrompt("quicktype", "Type exactly: The wandering trader disappeared mysteriously", Set.of("The wandering trader disappeared mysteriously"), true)); prompts.add(new GamePrompt("quicktype", "Type exactly: Skeletons are terrible archery teachers", Set.of("Skeletons are terrible archery teachers"), true));
        prompts.add(new GamePrompt("missingword", "Netherite gear is upgraded using a ______ Upgrade Smithing Template", Set.of("netherite"), false)); prompts.add(new GamePrompt("missingword", "A ______ eye is used to locate strongholds", Set.of("ender","eye of ender"), false));
    }
    private record ActiveGame(Set<String> answers, boolean exact, long startedAt) {}
    private record GamePrompt(String type, String question, Set<String> answers, boolean exact) { GamePrompt render() { return this; } }
}
