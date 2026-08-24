package io.github.xtx.smptoolkit.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class Locations {
    private Locations() {}

    public static String encode(Location l) {
        if (l == null || l.getWorld() == null) return "";
        return l.getWorld().getName() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ() + ";" + l.getYaw() + ";" + l.getPitch();
    }

    public static Location decode(String value) {
        if (value == null || value.isBlank()) return null;
        String[] p = value.split(";", -1);
        if (p.length != 6) return null;
        World world = Bukkit.getWorld(p[0]);
        if (world == null) return null;
        try {
            return new Location(world, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]), Float.parseFloat(p[4]), Float.parseFloat(p[5]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
