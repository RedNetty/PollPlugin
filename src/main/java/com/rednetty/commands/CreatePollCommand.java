package com.rednetty.commands;

import com.rednetty.gui.PollCreationMenu;
import com.rednetty.poll.Poll;
import com.rednetty.poll.PollManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CreatePollCommand implements CommandExecutor, TabCompleter {
    private final PollManager pollManager;

    public CreatePollCommand(PollManager pollManager) {
        this.pollManager = pollManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        // Check permission
        if (!pollManager.canCreatePoll(player)) {
            player.sendMessage(ChatColor.RED + "You don't have permission to create polls!");
            return true;
        }

        // Check arguments
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /createpoll <duration> <question>");
            player.sendMessage(ChatColor.YELLOW + "Duration examples: 1d (1 day), 5h (5 hours), 30m (30 minutes)");
            return true;
        }

        String duration = args[0];
        StringBuilder questionBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            questionBuilder.append(args[i]);
            if (i < args.length - 1) {
                questionBuilder.append(" ");
            }
        }
        String question = questionBuilder.toString();

        // Validate duration
        if (!pollManager.isValidDuration(duration)) {
            player.sendMessage(ChatColor.RED + "Invalid duration format!");
            player.sendMessage(ChatColor.YELLOW + "Use: 1d (days), 5h (hours), or 30m (minutes)");
            return true;
        }

        // Validate question length
        if (question.length() < 5) {
            player.sendMessage(ChatColor.RED + "Question must be at least 5 characters long!");
            return true;
        }

        if (question.length() > 200) {
            player.sendMessage(ChatColor.RED + "Question must be less than 200 characters!");
            return true;
        }

        // Create poll object
        Poll poll = pollManager.createPoll(player.getUniqueId(), question, duration);

        // Open poll creation GUI
        PollCreationMenu menu = new PollCreationMenu(player, poll, pollManager);
        menu.open();

        player.sendMessage(ChatColor.GREEN + "Poll creation started!");
        player.sendMessage(ChatColor.YELLOW + "Duration: " + ChatColor.WHITE + pollManager.formatDuration(duration));
        player.sendMessage(ChatColor.YELLOW + "Question: " + ChatColor.WHITE + question);
        player.sendMessage(ChatColor.GRAY + "Use the GUI to add answer options (max 6).");

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Duration suggestions
            List<String> durations = Arrays.asList("1d", "3d", "7d", "1h", "3h", "6h", "12h", "30m", "60m");
            String input = args[0].toLowerCase();
            for (String duration : durations) {
                if (duration.startsWith(input)) {
                    completions.add(duration);
                }
            }
        } else if (args.length == 2) {
            // Question starters
            completions.addAll(Arrays.asList(
                    "Should", "Do", "What", "Which", "Would", "Is", "Are", "How"
            ));
        }

        return completions;
    }
}