package io.github.xtx.smptoolkit.modules.utilities;

import io.github.xtx.smptoolkit.SMPToolkitPlugin;
import org.bukkit.command.CommandSender;
import java.util.Locale;

class UtilitiesCommandsBase extends UtilitiesSocialCommands {
    UtilitiesCommandsBase(SMPToolkitPlugin plugin) { super(plugin); }
    public boolean handle(CommandSender sender, String label, String[] args) {
        String l = label.toLowerCase(Locale.ROOT);
        return switch (l) {
            case "tpa" -> tpa(sender,args);
            case "tpaccept" -> tpaccept(sender);
            case "tpdeny" -> tpdeny(sender);
            case "tptoggle" -> tptoggle(sender);
            case "sethome" -> sethome(sender,args);
            case "home" -> home(sender,args);
            case "delhome" -> delhome(sender,args);
            case "homes" -> homes(sender);
            case "setspawn" -> setspawn(sender);
            case "spawn" -> spawn(sender);
            case "back" -> back(sender);
            case "msg", "tell", "whisper", "w" -> msg(sender,args);
            case "reply", "r" -> reply(sender,args);
            case "ignore" -> ignore(sender,args);
            case "seen" -> seen(sender,args);
            case "realname" -> realname(sender,args);
            case "afk" -> afk(sender);
            case "rtp" -> rtp(sender);
            default -> false;
        };
    }
}
