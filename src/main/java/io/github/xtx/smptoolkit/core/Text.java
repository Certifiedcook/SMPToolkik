package io.github.xtx.smptoolkit.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Text {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Pattern DURATION = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);

    private Text() {}

    public static Component mm(String input) {
        return MM.deserialize(input == null ? "" : input);
    }

    public static String plain(Component component) {
        return PLAIN.serialize(component);
    }

    public static String escapeMini(String input) {
        if (input == null) return "";
        return input.replace("<", "\\<");
    }

    public static long parseDurationMillis(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        Matcher matcher = DURATION.matcher(raw.toLowerCase(Locale.ROOT));
        long total = 0;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) return -1;
            long n = Long.parseLong(matcher.group(1));
            total += switch (matcher.group(2)) {
                case "s" -> Duration.ofSeconds(n).toMillis();
                case "m" -> Duration.ofMinutes(n).toMillis();
                case "h" -> Duration.ofHours(n).toMillis();
                case "d" -> Duration.ofDays(n).toMillis();
                case "w" -> Duration.ofDays(n * 7).toMillis();
                default -> 0;
            };
            end = matcher.end();
        }
        return end == raw.length() && total > 0 ? total : -1;
    }

    public static String formatDuration(long millis) {
        if (millis < 0) return "permanent";
        long seconds = Math.max(0, millis / 1000);
        long days = seconds / 86400; seconds %= 86400;
        long hours = seconds / 3600; seconds %= 3600;
        long minutes = seconds / 60; seconds %= 60;
        StringBuilder out = new StringBuilder();
        if (days > 0) out.append(days).append("d ");
        if (hours > 0) out.append(hours).append("h ");
        if (minutes > 0) out.append(minutes).append("m ");
        if (seconds > 0 || out.isEmpty()) out.append(seconds).append("s");
        return out.toString().trim();
    }

    public static String join(String[] args, int start) {
        if (args == null || start >= args.length) return "";
        StringBuilder b = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) b.append(' ');
            b.append(args[i]);
        }
        return b.toString();
    }
}
