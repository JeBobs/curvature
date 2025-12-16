package cc.interstellarmc.curvature;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class CurvatureCommand implements CommandExecutor, TabCompleter {
    private final CurvaturePlugin plugin;

    public CurvatureCommand(CurvaturePlugin plugin) {
        this.plugin = plugin;
        plugin.getCommand("curvature").setExecutor(this);
        plugin.getCommand("curvature").setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /curvature reload | info <player> [curve]");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("curvature.reload")) {
                sender.sendMessage(ChatColor.RED + "You lack permission.");
                return true;
            }
            plugin.reloadPluginConfig();
            sender.sendMessage(ChatColor.GREEN + "Curvature config reloaded.");
            return true;
        }
        if (args[0].equalsIgnoreCase("info")) {
            if (!sender.hasPermission("curvature.info")) {
                sender.sendMessage(ChatColor.RED + "You lack permission.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /curvature info <player> [curve]");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not online.");
                return true;
            }
            try {
                PlayerState state = plugin.getStateManager().getWithDecay(target.getUniqueId());
                if (args.length == 2) {
                    sender.sendMessage(ChatColor.YELLOW + "-- Curvature Info for " + target.getName() + " --");
                    for (String name : plugin.getCurves().keySet()) {
                        ICurve c = plugin.getCurves().get(name);
                        double x = state.getX(name);
                        double y = c.apply(x);
                        sender.sendMessage(ChatColor.AQUA + name + ": " + ChatColor.WHITE + String.format("X=%.2f, Y=%.2f", x, y));
                    }
                } else {
                    String curve = args[2];
                    ICurve c = plugin.getCurves().get(curve);
                    if (c == null) {
                        sender.sendMessage(ChatColor.RED + "Unknown curve: " + curve);
                        return true;
                    }
                    double x = state.getX(curve);
                    double y = c.apply(x);
                    sender.sendMessage(ChatColor.YELLOW + curve + " for " + target.getName() + ": " +
                            ChatColor.WHITE + String.format("X=%.2f, Y=%.2f", x, y));
                }
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Error fetching data.");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("testinc")) {
            Player target = args.length > 1 ? Bukkit.getPlayer(args[1]) : (sender instanceof Player ? (Player)sender : null);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Target player required.");
                return true;
            }
            try {
                PlayerState state = plugin.getStateManager().getWithDecay(target.getUniqueId());
                state.setX("movement", state.getX("movement") + 10);
                sender.sendMessage(ChatColor.GREEN + "Manually added +10 to movement X.");
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Error.");
                e.printStackTrace();
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("setx")) {
            if (!sender.hasPermission("curvature.setx")) {
                sender.sendMessage(ChatColor.RED + "You lack permission.");
                return true;
            }
            if (args.length < 4) {
                sender.sendMessage(ChatColor.RED + "Usage: /curvature setx <player> <curve> <value>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not online.");
                return true;
            }
            String curve = args[2];
            ICurve c = plugin.getCurves().get(curve);
            if (c == null) {
                sender.sendMessage(ChatColor.RED + "Unknown curve: " + curve);
                return true;
            }
            double value;
            try {
                value = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Value must be a number.");
                return true;
            }
            try {
                PlayerState state = plugin.getStateManager().getWithDecay(target.getUniqueId());
                state.setX(curve, value);
                double y = c.apply(value);
                sender.sendMessage(ChatColor.GREEN + "Set " + curve + " X for " + target.getName() +
                        " to " + String.format("%.2f (Y=%.2f)", value, y));
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Error setting value.");
                e.printStackTrace();
            }
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Unknown subcommand.");
        return true;

    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("curvature")) return Collections.emptyList();
        if (args.length == 1) {
            List<String> base = new ArrayList<>();
            if (sender.hasPermission("curvature.reload")) base.add("reload");
            if (sender.hasPermission("curvature.info")) base.add("info");
            if (sender.hasPermission("curvature.setx")) base.add("setx");
            if (sender.hasPermission("curvature.use")) base.add("testinc");
            return filter(base, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("info") || sub.equals("testinc") || sub.equals("setx")) {
                return filter(
                        Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()),
                        args[1]);
            }
        }
        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("info") || sub.equals("setx")) {
                return filter(new ArrayList<>(plugin.getCurves().keySet()), args[2]);
            }
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(opt -> opt.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toList());
    }
}
