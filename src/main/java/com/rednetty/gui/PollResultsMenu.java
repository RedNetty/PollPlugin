package com.rednetty.gui;

import com.rednetty.menu.Menu;
import com.rednetty.menu.MenuItem;
import com.rednetty.poll.Poll;
import com.rednetty.poll.PollManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.stream.Collectors;

public class PollResultsMenu extends Menu {
    private final Poll poll;
    private final PollManager pollManager;

    public PollResultsMenu(Player player, Poll poll, PollManager pollManager) {
        super(player, ChatColor.GOLD + "Poll Results", 54);
        this.poll = poll;
        this.pollManager = pollManager;
        setupMenu();
    }

    private void setupMenu() {
        createBorder();

        // Poll information header
        setItem(4, new MenuItem(Material.PAPER, ChatColor.GOLD + "Poll Results")
                .addLoreLine(ChatColor.YELLOW + "Question: " + ChatColor.WHITE + poll.getQuestion())
                .addLoreLine(ChatColor.GRAY + "Created by: " + ChatColor.WHITE + poll.getCreatorName())
                .addLoreLine(ChatColor.GRAY + "Created: " + ChatColor.WHITE + poll.getFormattedCreationDate())
                .addLoreLine(ChatColor.GRAY + "Status: " + (poll.isActive() ?
                        ChatColor.GREEN + "ACTIVE - " + poll.getTimeRemaining() :
                        ChatColor.RED + "CLOSED"))
                .addLoreLine(ChatColor.GRAY + "Total votes: " + ChatColor.WHITE + poll.getTotalVotes()));

        // Display results
        displayResults();

        // Summary information
        displaySummary();

        // Navigation and action buttons
        setupActionButtons();
    }

    private void displayResults() {
        Map<String, Integer> results = poll.getResults();
        int[] resultSlots = {19, 20, 21, 22, 23, 24, 28, 29, 30, 31, 32, 33};

        // Sort results by vote count (descending)
        var sortedResults = results.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());

        for (int i = 0; i < sortedResults.size() && i < resultSlots.length; i++) {
            var entry = sortedResults.get(i);
            String option = entry.getKey();
            int votes = entry.getValue();
            int slot = resultSlots[i];

            Material material = getResultMaterial(votes, i == 0); // First place gets special material
            String displayName = ChatColor.AQUA + option;

            MenuItem resultItem = new MenuItem(material, displayName)
                    .addLoreLine(ChatColor.GRAY + "Votes: " + ChatColor.WHITE + votes);

            if (poll.getTotalVotes() > 0) {
                double percentage = (double) votes / poll.getTotalVotes() * 100;
                resultItem.addLoreLine(ChatColor.GRAY + "Percentage: " + ChatColor.WHITE + String.format("%.1f%%", percentage));

                // Visual bar representation
                String bar = createProgressBar(percentage);
                resultItem.addLoreLine(ChatColor.GRAY + "Progress: " + bar);

                // Ranking
                if (i == 0 && votes > 0) {
                    resultItem.addLoreLine("")
                            .addLoreLine(ChatColor.GOLD + "🏆 WINNER! 🏆");
                } else if (i == 1 && votes > 0) {
                    resultItem.addLoreLine("")
                            .addLoreLine(ChatColor.GRAY + "🥈 Second Place");
                } else if (i == 2 && votes > 0) {
                    resultItem.addLoreLine("")
                            .addLoreLine(ChatColor.DARK_GRAY + "🥉 Third Place");
                }
            }

            // Show if the viewing player voted for this option
            if (poll.hasVoted(player.getUniqueId())) {
                String playerVote = poll.getVotes().get(player.getUniqueId());
                if (option.equals(playerVote)) {
                    resultItem.addLoreLine("")
                            .addLoreLine(ChatColor.GREEN + "✓ Your choice");
                }
            }

            setItem(slot, resultItem);
        }
    }

    private void displaySummary() {
        // Winner information
        if (poll.getTotalVotes() > 0) {
            Map<String, Integer> results = poll.getResults();
            var winner = results.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);

            if (winner != null) {
                double winnerPercentage = (double) winner.getValue() / poll.getTotalVotes() * 100;

                setItem(40, new MenuItem(Material.GOLD_INGOT, ChatColor.GOLD + "Poll Winner")
                        .addLoreLine(ChatColor.WHITE + winner.getKey())
                        .addLoreLine(ChatColor.GRAY + "Votes: " + ChatColor.WHITE + winner.getValue())
                        .addLoreLine(ChatColor.GRAY + "Percentage: " + ChatColor.WHITE + String.format("%.1f%%", winnerPercentage))
                        .addLoreLine("")
                        .addLoreLine(ChatColor.YELLOW + "🎉 Congratulations! 🎉"));
            }
        } else {
            setItem(40, new MenuItem(Material.BARRIER, ChatColor.RED + "No Votes Cast")
                    .addLoreLine(ChatColor.GRAY + "This poll received no votes.")
                    .addLoreLine("")
                    .addLoreLine(ChatColor.YELLOW + "Better luck next time!"));
        }
    }

    private void setupActionButtons() {
        // Back button
        if (poll.isActive()) {
            setItem(45, new MenuItem(Material.ARROW, ChatColor.YELLOW + "Back to Active Polls")
                    .addLoreLine(ChatColor.GRAY + "Return to the active poll list")
                    .setClickHandler((p, slot) -> {
                        PollListMenu pollList = new PollListMenu(p, pollManager);
                        pollList.open();
                    }));
        } else {
            setItem(45, new MenuItem(Material.ARROW, ChatColor.YELLOW + "Back to Closed Polls")
                    .addLoreLine(ChatColor.GRAY + "Return to the closed poll list")
                    .setClickHandler((p, slot) -> {
                        ClosedPollsMenu closedPolls = new ClosedPollsMenu(p, pollManager);
                        closedPolls.open();
                    }));
        }

        // Refresh button
        setItem(49, new MenuItem(Material.CLOCK, ChatColor.YELLOW + "Refresh Results")
                .addLoreLine(ChatColor.GRAY + "Update poll results")
                .setClickHandler((p, slot) -> {
                    // Reload poll data from storage
                    Poll refreshedPoll = pollManager.getPoll(poll.getPollUUID());
                    if (refreshedPoll != null) {
                        PollResultsMenu refreshed = new PollResultsMenu(p, refreshedPoll, pollManager);
                        refreshed.open();
                    } else {
                        p.sendMessage(ChatColor.RED + "This poll no longer exists.");
                        if (poll.isActive()) {
                            PollListMenu pollList = new PollListMenu(p, pollManager);
                            pollList.open();
                        } else {
                            ClosedPollsMenu closedPolls = new ClosedPollsMenu(p, pollManager);
                            closedPolls.open();
                        }
                    }
                }));

        // Vote button (if poll is active and player hasn't voted)
        if (poll.isActive() && !poll.hasVoted(player.getUniqueId())) {
            setItem(53, new MenuItem(Material.EMERALD, ChatColor.GREEN + "Cast Your Vote")
                    .addLoreLine(ChatColor.GRAY + "Click to vote on this poll")
                    .setClickHandler((p, slot) -> {
                        PollVotingMenu votingMenu = new PollVotingMenu(p, poll, pollManager, false);
                        votingMenu.open();
                    }));
        }

        // Management buttons (if player has permission)
        if (poll.isActive() && pollManager.canClosePoll(player, poll)) {
            setItem(47, new MenuItem(Material.BARRIER, ChatColor.RED + "Close Poll")
                    .addLoreLine(ChatColor.GRAY + "Close this poll permanently")
                    .addLoreLine(ChatColor.GRAY + "This action cannot be undone")
                    .setClickHandler((p, slot) -> {
                        if (pollManager.closePoll(poll.getPollUUID())) {
                            p.sendMessage(ChatColor.GREEN + "Poll closed successfully!");
                            // Refresh to show closed poll
                            Poll closedPoll = pollManager.getPoll(poll.getPollUUID());
                            PollResultsMenu results = new PollResultsMenu(p, closedPoll, pollManager);
                            results.open();
                        } else {
                            p.sendMessage(ChatColor.RED + "Failed to close the poll.");
                        }
                    }));
        }

        if (pollManager.canRemovePoll(player, poll)) {
            setItem(51, new MenuItem(Material.TNT, ChatColor.DARK_RED + "Delete Poll")
                    .addLoreLine(ChatColor.GRAY + "Permanently delete this poll")
                    .addLoreLine(ChatColor.RED + "This action cannot be undone!")
                    .setClickHandler((p, slot) -> {
                        if (pollManager.removePoll(poll.getPollUUID())) {
                            p.sendMessage(ChatColor.GREEN + "Poll deleted successfully!");
                            if (poll.isActive()) {
                                PollListMenu pollList = new PollListMenu(p, pollManager);
                                pollList.open();
                            } else {
                                ClosedPollsMenu closedPolls = new ClosedPollsMenu(p, pollManager);
                                closedPolls.open();
                            }
                        } else {
                            p.sendMessage(ChatColor.RED + "Failed to delete the poll.");
                        }
                    }));
        }

        // Poll info
        setItem(46, new MenuItem(Material.BOOK, ChatColor.AQUA + "Poll Details")
                .addLoreLine(ChatColor.GRAY + "ID: " + ChatColor.WHITE + poll.getPollUUID().toString().substring(0, 8))
                .addLoreLine(ChatColor.GRAY + "Full ID: " + ChatColor.WHITE + poll.getPollUUID().toString())
                .addLoreLine("")
                .addLoreLine(ChatColor.YELLOW + "Use /poll results " + poll.getPollUUID().toString().substring(0, 8))
                .addLoreLine(ChatColor.YELLOW + "to share these results with others!"));
    }

    private Material getResultMaterial(int votes, boolean isWinner) {
        if (isWinner && votes > 0) {
            return Material.GOLD_BLOCK; // Winner
        } else if (votes == 0) {
            return Material.GRAY_STAINED_GLASS; // No votes
        } else if (votes <= 2) {
            return Material.RED_STAINED_GLASS; // Few votes
        } else if (votes <= 5) {
            return Material.YELLOW_STAINED_GLASS; // Some votes
        } else if (votes <= 10) {
            return Material.BLUE_STAINED_GLASS; // Good votes
        } else {
            return Material.PURPLE_STAINED_GLASS; // Many votes
        }
    }

    private String createProgressBar(double percentage) {
        int bars = (int) Math.round(percentage / 10.0); // 10 bars total
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < 10; i++) {
            if (i < bars) {
                sb.append(ChatColor.GREEN + "█");
            } else {
                sb.append(ChatColor.GRAY + "░");
            }
        }

        return sb.toString();
    }
}