package com.rednetty.commands;

import com.rednetty.gui.ClosedPollsMenu;
import com.rednetty.gui.PollListMenu;
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
import java.util.UUID;

public class PollCommand implements CommandExecutor, TabCompleter {
    private final PollManager pollManager;

    public PollCommand(PollManager pollManager) {
        this.pollManager = pollManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        // Handle subcommands
        if (args.length == 0) {
            // Open poll list GUI
            openPollListGUI(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "close":
                return handleClosePoll(player, args);
            case "remove":
            case "delete":
                return handleRemovePoll(player, args);
            case "list":
                openPollListGUI(player);
                return true;
            case "closed":
            case "history":
                openClosedPollsGUI(player);
                return true;
            case "results":
                return handleShowResults(player, args);
            case "help":
                sendHelpMessage(player);
                return true;
            default:
                player.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCommand);
                sendHelpMessage(player);
                return true;
        }
    }

    private void openPollListGUI(Player player) {
        List<Poll> activePolls = pollManager.getActivePolls();

        if (activePolls.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "There are no active polls at the moment.");
            return;
        }

        PollListMenu menu = new PollListMenu(player, pollManager);
        menu.open();
    }

    private boolean handleClosePoll(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /poll close <pollId>");
            return true;
        }

        String pollId = args[1];
        Poll poll = pollManager.getPoll(pollId);

        if (poll == null) {
            player.sendMessage(ChatColor.RED + "Poll not found or already closed!");
            return true;
        }

        if (!pollManager.canClosePoll(player, poll)) {
            player.sendMessage(ChatColor.RED + "You don't have permission to close this poll!");
            player.sendMessage(ChatColor.GRAY + "You can only close polls you created or have poll.close permission.");
            return true;
        }

        if (pollManager.closePoll(poll.getPollUUID())) {
            player.sendMessage(ChatColor.GREEN + "Poll closed successfully!");
            player.sendMessage(ChatColor.GRAY + "Question: " + poll.getQuestion());

            // Show final results
            showPollResults(player, poll);
        } else {
            player.sendMessage(ChatColor.RED + "Failed to close the poll. Please try again.");
        }

        return true;
    }

    private boolean handleRemovePoll(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /poll remove <pollId>");
            return true;
        }

        String pollId = args[1];
        Poll poll = pollManager.getPoll(pollId);

        if (poll == null) {
            player.sendMessage(ChatColor.RED + "Poll not found!");
            return true;
        }

        if (!pollManager.canRemovePoll(player, poll)) {
            player.sendMessage(ChatColor.RED + "You don't have permission to remove this poll!");
            player.sendMessage(ChatColor.GRAY + "You can only remove polls you created or have poll.remove permission.");
            return true;
        }

        String question = poll.getQuestion();
        if (pollManager.removePoll(poll.getPollUUID())) {
            player.sendMessage(ChatColor.GREEN + "Poll removed successfully!");
            player.sendMessage(ChatColor.GRAY + "Question: " + question);
        } else {
            player.sendMessage(ChatColor.RED + "Failed to remove the poll. Please try again.");
        }

        return true;
    }

    private void showPollResults(Player player, Poll poll) {
        player.sendMessage(ChatColor.GOLD + "=== Poll Results ===");
        player.sendMessage(ChatColor.YELLOW + "Total votes: " + ChatColor.WHITE + poll.getTotalVotes());

        if (poll.getTotalVotes() > 0) {
            poll.getResults().forEach((option, votes) -> {
                double percentage = (double) votes / poll.getTotalVotes() * 100;
                player.sendMessage(ChatColor.AQUA + option + ChatColor.GRAY + ": " +
                        ChatColor.WHITE + votes + ChatColor.GRAY + " (" +
                        String.format("%.1f", percentage) + "%)");
            });
        } else {
            player.sendMessage(ChatColor.GRAY + "No votes were cast.");
        }
    }
    private void openClosedPollsGUI(Player player) {
        List<Poll> closedPolls = pollManager.getClosedPolls();

        if (closedPolls.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "There are no closed polls to view.");
            return;
        }

        ClosedPollsMenu menu = new ClosedPollsMenu(player, pollManager);
        menu.open();
    }

    private boolean handleShowResults(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /poll results <pollId>");
            return true;
        }

        String pollId = args[1];
        Poll poll = pollManager.getPoll(pollId);

        if (poll == null) {
            player.sendMessage(ChatColor.RED + "Poll not found!");
            return true;
        }

        showPollResults(player, poll);
        return true;
    }
    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Poll Commands ===");
        player.sendMessage(ChatColor.YELLOW + "/poll" + ChatColor.GRAY + " - Open the poll list GUI");
        player.sendMessage(ChatColor.YELLOW + "/poll list" + ChatColor.GRAY + " - Open the poll list GUI");
        player.sendMessage(ChatColor.YELLOW + "/poll closed" + ChatColor.GRAY + " - View closed polls");
        player.sendMessage(ChatColor.YELLOW + "/poll results <pollId>" + ChatColor.GRAY + " - Show poll results");
        player.sendMessage(ChatColor.YELLOW + "/poll close <pollId>" + ChatColor.GRAY + " - Close a poll");
        player.sendMessage(ChatColor.YELLOW + "/poll remove <pollId>" + ChatColor.GRAY + " - Remove a poll");
        player.sendMessage(ChatColor.YELLOW + "/createpoll <duration> <question>" + ChatColor.GRAY + " - Create a new poll");
        player.sendMessage(ChatColor.GRAY + "Duration examples: 1d, 5h, 30m");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("close", "remove", "list", "closed", "results", "help");
            String input = args[0].toLowerCase();
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(input)) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("close") || subCommand.equals("remove") ||
                    subCommand.equals("delete") || subCommand.equals("results")) {
                // Suggest poll IDs (first 8 characters of UUID for readability)
                List<Poll> allPolls = pollManager.getAllPolls();
                for (Poll poll : allPolls) {
                    String shortId = poll.getPollUUID().toString().substring(0, 8);
                    completions.add(shortId);
                }
            }
        }

        return completions;
    }
}