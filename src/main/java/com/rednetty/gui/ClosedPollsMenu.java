package com.rednetty.gui;

import com.rednetty.menu.Menu;
import com.rednetty.menu.MenuItem;
import com.rednetty.poll.Poll;
import com.rednetty.poll.PollManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class ClosedPollsMenu extends Menu {
    private final PollManager pollManager;
    private int currentPage = 0;
    private final int pollsPerPage = 28; // 7x4 grid for polls

    public ClosedPollsMenu(Player player, PollManager pollManager) {
        super(player, ChatColor.DARK_RED + "Closed Polls", 54);
        this.pollManager = pollManager;
        setupMenu();
    }

    public ClosedPollsMenu(Player player, PollManager pollManager, int page) {
        super(player, ChatColor.DARK_RED + "Closed Polls", 54);
        this.pollManager = pollManager;
        this.currentPage = page;
        setupMenu();
    }

    private void setupMenu() {
        createBorder();

        List<Poll> closedPolls = pollManager.getClosedPolls();

        // Calculate pagination
        int totalPages = (int) Math.ceil((double) closedPolls.size() / pollsPerPage);
        int startIndex = currentPage * pollsPerPage;
        int endIndex = Math.min(startIndex + pollsPerPage, closedPolls.size());

        // Header info
        setItem(4, new MenuItem(Material.PAPER, ChatColor.GOLD + "Closed Poll History")
                .addLoreLine(ChatColor.GRAY + "Total Closed Polls: " + ChatColor.WHITE + closedPolls.size())
                .addLoreLine(ChatColor.GRAY + "Page: " + ChatColor.WHITE + (currentPage + 1) + "/" + Math.max(1, totalPages))
                .addLoreLine("")
                .addLoreLine(ChatColor.YELLOW + "Click on a poll to view results!"));

        // Display polls in a 7x4 grid (slots 10-16, 19-25, 28-34, 37-43)
        int[] pollSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        for (int i = startIndex; i < endIndex; i++) {
            Poll poll = closedPolls.get(i);
            int slotIndex = i - startIndex;

            if (slotIndex < pollSlots.length) {
                int slot = pollSlots[slotIndex];
                Material material = getClosedPollMaterial(poll);

                MenuItem pollItem = new MenuItem(material, ChatColor.RED + poll.getQuestion())
                        .addLoreLine(ChatColor.GRAY + "Created by: " + ChatColor.WHITE + poll.getCreatorName())
                        .addLoreLine(ChatColor.GRAY + "Created: " + ChatColor.WHITE + poll.getFormattedCreationDate())
                        .addLoreLine(ChatColor.GRAY + "Status: " + ChatColor.RED + "CLOSED")
                        .addLoreLine(ChatColor.GRAY + "Total votes: " + ChatColor.WHITE + poll.getTotalVotes())
                        .addLoreLine("");

                // Show results summary
                if (poll.getTotalVotes() > 0) {
                    Map<String, Integer> results = poll.getResults();
                    String topOption = results.entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .orElse("None");
                    int topVotes = results.getOrDefault(topOption, 0);
                    double topPercentage = (double) topVotes / poll.getTotalVotes() * 100;

                    pollItem.addLoreLine(ChatColor.GREEN + "Winner: " + ChatColor.WHITE + topOption)
                            .addLoreLine(ChatColor.GRAY + "Votes: " + ChatColor.WHITE + topVotes +
                                    ChatColor.GRAY + " (" + String.format("%.1f", topPercentage) + "%)");
                } else {
                    pollItem.addLoreLine(ChatColor.GRAY + "No votes were cast");
                }

                if (poll.hasVoted(player.getUniqueId())) {
                    String votedOption = poll.getVotes().get(player.getUniqueId());
                    pollItem.addLoreLine(ChatColor.AQUA + "You voted: " + ChatColor.WHITE + votedOption);
                }

                pollItem.addLoreLine("")
                        .addLoreLine(ChatColor.YELLOW + "Click to view detailed results")
                        .addLoreLine(ChatColor.GRAY + "ID: " + poll.getPollUUID().toString().substring(0, 8));

                pollItem.setClickHandler((p, clickedSlot) -> {
                    PollResultsMenu resultsMenu = new PollResultsMenu(p, poll, pollManager);
                    resultsMenu.open();
                });

                setItem(slot, pollItem);
            }
        }

        // Navigation buttons
        if (currentPage > 0) {
            setItem(45, new MenuItem(Material.ARROW, ChatColor.GREEN + "Previous Page")
                    .addLoreLine(ChatColor.GRAY + "Go to page " + currentPage)
                    .setClickHandler((p, slot) -> {
                        ClosedPollsMenu prevPage = new ClosedPollsMenu(p, pollManager, currentPage - 1);
                        prevPage.open();
                    }));
        }

        if (currentPage < totalPages - 1) {
            setItem(53, new MenuItem(Material.ARROW, ChatColor.GREEN + "Next Page")
                    .addLoreLine(ChatColor.GRAY + "Go to page " + (currentPage + 2))
                    .setClickHandler((p, slot) -> {
                        ClosedPollsMenu nextPage = new ClosedPollsMenu(p, pollManager, currentPage + 1);
                        nextPage.open();
                    }));
        }

        // Back to active polls
        setItem(46, new MenuItem(Material.BOOK, ChatColor.YELLOW + "Back to Active Polls")
                .addLoreLine(ChatColor.GRAY + "Return to the active poll list")
                .setClickHandler((p, slot) -> {
                    PollListMenu pollList = new PollListMenu(p, pollManager);
                    pollList.open();
                }));

        // Refresh button
        setItem(49, new MenuItem(Material.CLOCK, ChatColor.YELLOW + "Refresh")
                .addLoreLine(ChatColor.GRAY + "Update poll list")
                .setClickHandler((p, slot) -> {
                    ClosedPollsMenu refreshed = new ClosedPollsMenu(p, pollManager, currentPage);
                    refreshed.open();
                    p.sendMessage(ChatColor.GREEN + "Poll list refreshed!");
                }));

        // Statistics
        if (!closedPolls.isEmpty()) {
            int totalVotes = closedPolls.stream().mapToInt(Poll::getTotalVotes).sum();

            setItem(52, new MenuItem(Material.WRITABLE_BOOK, ChatColor.AQUA + "Statistics")
                    .addLoreLine(ChatColor.GRAY + "Closed polls: " + ChatColor.WHITE + closedPolls.size())
                    .addLoreLine(ChatColor.GRAY + "Total votes cast: " + ChatColor.WHITE + totalVotes)
                    .addLoreLine(ChatColor.GRAY + "Average votes per poll: " + ChatColor.WHITE +
                            String.format("%.1f", totalVotes / (double) closedPolls.size())));
        }

        // Empty state
        if (closedPolls.isEmpty()) {
            setItem(22, new MenuItem(Material.BARRIER, ChatColor.RED + "No Closed Polls")
                    .addLoreLine(ChatColor.GRAY + "There are no closed polls to view.")
                    .addLoreLine("")
                    .addLoreLine(ChatColor.YELLOW + "Polls will appear here after they")
                    .addLoreLine(ChatColor.YELLOW + "expire or are manually closed."));
        }
    }

    private Material getClosedPollMaterial(Poll poll) {
        int votes = poll.getTotalVotes();
        if (votes == 0) {
            return Material.GRAY_STAINED_GLASS; // No votes
        } else if (votes < 5) {
            return Material.RED_STAINED_GLASS; // Few votes
        } else if (votes < 15) {
            return Material.ORANGE_STAINED_GLASS; // Moderate votes
        } else {
            return Material.PURPLE_STAINED_GLASS; // Popular poll
        }
    }
}