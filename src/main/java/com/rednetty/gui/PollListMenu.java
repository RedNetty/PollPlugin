package com.rednetty.gui;

import com.rednetty.menu.Menu;
import com.rednetty.menu.MenuItem;
import com.rednetty.poll.Poll;
import com.rednetty.poll.PollManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public class PollListMenu extends Menu {
    private final PollManager pollManager;
    private int currentPage = 0;
    private final int pollsPerPage = 28; // 7x4 grid for polls

    public PollListMenu(Player player, PollManager pollManager) {
        super(player, ChatColor.DARK_BLUE + "Active Polls", 54);
        this.pollManager = pollManager;
        setupMenu();
    }

    public PollListMenu(Player player, PollManager pollManager, int page) {
        super(player, ChatColor.DARK_BLUE + "Active Polls", 54);
        this.pollManager = pollManager;
        this.currentPage = page;
        setupMenu();
    }

    private void setupMenu() {
        createBorder();

        List<Poll> activePolls = pollManager.getActivePolls();

        // Calculate pagination
        int totalPages = (int) Math.ceil((double) activePolls.size() / pollsPerPage);
        int startIndex = currentPage * pollsPerPage;
        int endIndex = Math.min(startIndex + pollsPerPage, activePolls.size());

        // Header info
        setItem(4, new MenuItem(Material.PAPER, ChatColor.GOLD + "Poll Information")
                .addLoreLine(ChatColor.GRAY + "Total Active Polls: " + ChatColor.WHITE + activePolls.size())
                .addLoreLine(ChatColor.GRAY + "Page: " + ChatColor.WHITE + (currentPage + 1) + "/" + Math.max(1, totalPages))
                .addLoreLine("")
                .addLoreLine(ChatColor.YELLOW + "Click on a poll to vote!"));

        // Display polls in a 7x4 grid (slots 10-16, 19-25, 28-34, 37-43)
        int[] pollSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        for (int i = startIndex; i < endIndex; i++) {
            Poll poll = activePolls.get(i);
            int slotIndex = i - startIndex;

            if (slotIndex < pollSlots.length) {
                int slot = pollSlots[slotIndex];
                Material material = getPollMaterial(poll);

                MenuItem pollItem = new MenuItem(material, ChatColor.AQUA + poll.getQuestion())
                        .addLoreLine(ChatColor.GRAY + "Created by: " + ChatColor.WHITE + poll.getCreatorName())
                        .addLoreLine(ChatColor.GRAY + "Created: " + ChatColor.WHITE + poll.getFormattedCreationDate())
                        .addLoreLine(ChatColor.GRAY + "Expires: " + ChatColor.WHITE + poll.getTimeRemaining())
                        .addLoreLine(ChatColor.GRAY + "Total votes: " + ChatColor.WHITE + poll.getTotalVotes())
                        .addLoreLine("");

                if (poll.hasVoted(player.getUniqueId())) {
                    String votedOption = poll.getVotes().get(player.getUniqueId());
                    pollItem.addLoreLine(ChatColor.GREEN + "✓ You voted: " + ChatColor.WHITE + votedOption);
                    pollItem.addLoreLine(ChatColor.YELLOW + "Click to view results");
                } else {
                    pollItem.addLoreLine(ChatColor.YELLOW + "Click to vote!");
                }

                pollItem.addLoreLine(ChatColor.GRAY + "ID: " + poll.getPollUUID().toString().substring(0, 8));

                pollItem.setClickHandler((p, clickedSlot) -> {
                    PollVotingMenu votingMenu = new PollVotingMenu(p, poll, pollManager, false);
                    votingMenu.open();
                });

                setItem(slot, pollItem);
            }
        }

        // Navigation buttons
        if (currentPage > 0) {
            setItem(45, new MenuItem(Material.ARROW, ChatColor.GREEN + "Previous Page")
                    .addLoreLine(ChatColor.GRAY + "Go to page " + currentPage)
                    .setClickHandler((p, slot) -> {
                        PollListMenu prevPage = new PollListMenu(p, pollManager, currentPage - 1);
                        prevPage.open();
                    }));
        }

        if (currentPage < totalPages - 1) {
            setItem(53, new MenuItem(Material.ARROW, ChatColor.GREEN + "Next Page")
                    .addLoreLine(ChatColor.GRAY + "Go to page " + (currentPage + 2))
                    .setClickHandler((p, slot) -> {
                        PollListMenu nextPage = new PollListMenu(p, pollManager, currentPage + 1);
                        nextPage.open();
                    }));
        }

        // Refresh button
        setItem(49, new MenuItem(Material.CLOCK, ChatColor.YELLOW + "Refresh")
                .addLoreLine(ChatColor.GRAY + "Update poll list")
                .setClickHandler((p, slot) -> {
                    PollListMenu refreshed = new PollListMenu(p, pollManager, currentPage);
                    refreshed.open();
                    p.sendMessage(ChatColor.GREEN + "Poll list refreshed!");
                }));

        // My polls button (if player has permission to create polls)
        if (pollManager.canCreatePoll(player)) {
            setItem(47, new MenuItem(Material.PLAYER_HEAD, ChatColor.LIGHT_PURPLE + "My Polls")
                    .addLoreLine(ChatColor.GRAY + "View polls you created")
                    .setClickHandler((p, slot) -> {
                        MyPollsMenu myPolls = new MyPollsMenu(p, pollManager);
                        myPolls.open();
                    }));
        }

        // Closed polls button
        setItem(51, new MenuItem(Material.GRAY_STAINED_GLASS, ChatColor.RED + "Closed Polls")
                .addLoreLine(ChatColor.GRAY + "View polls that have ended")
                .addLoreLine(ChatColor.GRAY + "See final results and statistics")
                .setClickHandler((p, slot) -> {
                    ClosedPollsMenu closedPolls = new ClosedPollsMenu(p, pollManager);
                    closedPolls.open();
                }));

        // Info and help
        if (activePolls.isEmpty()) {
            setItem(22, new MenuItem(Material.BARRIER, ChatColor.RED + "No Active Polls")
                    .addLoreLine(ChatColor.GRAY + "There are no active polls at the moment.")
                    .addLoreLine("")
                    .addLoreLine(ChatColor.YELLOW + "Staff can create polls using:")
                    .addLoreLine(ChatColor.WHITE + "/createpoll <duration> <question>"));
        }
    }

    private Material getPollMaterial(Poll poll) {
        // Choose material based on poll characteristics
        if (poll.hasVoted(player.getUniqueId())) {
            return Material.LIME_STAINED_GLASS; // Already voted
        }

        int optionCount = poll.getOptions().size();
        switch (optionCount) {
            case 2:
                return Material.PAPER; // Simple yes/no or binary choice
            case 3:
                return Material.BOOK;
            case 4:
                return Material.WRITABLE_BOOK;
            default:
                return Material.ENCHANTED_BOOK; // Complex poll with many options
        }
    }
}