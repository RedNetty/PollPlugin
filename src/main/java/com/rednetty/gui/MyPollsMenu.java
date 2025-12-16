package com.rednetty.gui;

import com.rednetty.menu.Menu;
import com.rednetty.menu.MenuItem;
import com.rednetty.poll.Poll;
import com.rednetty.poll.PollManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public class MyPollsMenu extends Menu {
    private final PollManager pollManager;
    private int currentPage = 0;
    private final int pollsPerPage = 21; // 7x3 grid for polls

    public MyPollsMenu(Player player, PollManager pollManager) {
        super(player, ChatColor.LIGHT_PURPLE + "My Polls", 54);
        this.pollManager = pollManager;
        setupMenu();
    }

    public MyPollsMenu(Player player, PollManager pollManager, int page) {
        super(player, ChatColor.LIGHT_PURPLE + "My Polls", 54);
        this.pollManager = pollManager;
        this.currentPage = page;
        setupMenu();
    }

    private void setupMenu() {
        createBorder();

        List<Poll> myPolls = pollManager.getPollsByCreator(player.getUniqueId());

        // Calculate pagination
        int totalPages = (int) Math.ceil((double) myPolls.size() / pollsPerPage);
        int startIndex = currentPage * pollsPerPage;
        int endIndex = Math.min(startIndex + pollsPerPage, myPolls.size());

        // Header info
        setItem(4, new MenuItem(Material.PLAYER_HEAD, ChatColor.GOLD + "Your Poll Statistics")
                .addLoreLine(ChatColor.GRAY + "Total Polls Created: " + ChatColor.WHITE + myPolls.size())
                .addLoreLine(ChatColor.GRAY + "Page: " + ChatColor.WHITE + (currentPage + 1) + "/" + Math.max(1, totalPages))
                .addLoreLine("")
                .addLoreLine(ChatColor.YELLOW + "Click on a poll to manage it"));

        // Display polls in a 7x3 grid (slots 19-25, 28-34, 37-43)
        int[] pollSlots = {
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        for (int i = startIndex; i < endIndex; i++) {
            Poll poll = myPolls.get(i);
            int slotIndex = i - startIndex;

            if (slotIndex < pollSlots.length) {
                int slot = pollSlots[slotIndex];
                Material material = getPollStatusMaterial(poll);

                MenuItem pollItem = new MenuItem(material, ChatColor.AQUA + poll.getQuestion())
                        .addLoreLine(ChatColor.GRAY + "Created: " + ChatColor.WHITE + poll.getFormattedCreationDate())
                        .addLoreLine(ChatColor.GRAY + "Status: " + getStatusText(poll))
                        .addLoreLine(ChatColor.GRAY + "Total votes: " + ChatColor.WHITE + poll.getTotalVotes())
                        .addLoreLine("");

                if (poll.isActive()) {
                    pollItem.addLoreLine(ChatColor.GREEN + "✓ Active - Players can vote")
                            .addLoreLine(ChatColor.GRAY + "Expires: " + ChatColor.WHITE + poll.getTimeRemaining());
                } else {
                    pollItem.addLoreLine(ChatColor.RED + "✗ Closed - No longer accepting votes");
                }

                pollItem.addLoreLine("")
                        .addLoreLine(ChatColor.YELLOW + "Left-click to view details")
                        .addLoreLine(ChatColor.YELLOW + "Right-click for quick actions")
                        .addLoreLine(ChatColor.GRAY + "ID: " + poll.getPollUUID().toString().substring(0, 8));

                pollItem.setClickHandler((p, clickedSlot) -> openPollManagement(poll));

                setItem(slot, pollItem);
            }
        }

        // Navigation buttons
        if (currentPage > 0) {
            setItem(45, new MenuItem(Material.ARROW, ChatColor.GREEN + "Previous Page")
                    .addLoreLine(ChatColor.GRAY + "Go to page " + currentPage)
                    .setClickHandler((p, slot) -> {
                        MyPollsMenu prevPage = new MyPollsMenu(p, pollManager, currentPage - 1);
                        prevPage.open();
                    }));
        }

        if (currentPage < totalPages - 1) {
            setItem(53, new MenuItem(Material.ARROW, ChatColor.GREEN + "Next Page")
                    .addLoreLine(ChatColor.GRAY + "Go to page " + (currentPage + 2))
                    .setClickHandler((p, slot) -> {
                        MyPollsMenu nextPage = new MyPollsMenu(p, pollManager, currentPage + 1);
                        nextPage.open();
                    }));
        }

        // Action buttons
        setItem(46, new MenuItem(Material.BOOK, ChatColor.YELLOW + "Back to All Polls")
                .addLoreLine(ChatColor.GRAY + "Return to the main poll list")
                .setClickHandler((p, slot) -> {
                    PollListMenu pollList = new PollListMenu(p, pollManager);
                    pollList.open();
                }));

        setItem(49, new MenuItem(Material.CLOCK, ChatColor.YELLOW + "Refresh")
                .addLoreLine(ChatColor.GRAY + "Update poll list")
                .setClickHandler((p, slot) -> {
                    MyPollsMenu refreshed = new MyPollsMenu(p, pollManager, currentPage);
                    refreshed.open();
                    p.sendMessage(ChatColor.GREEN + "Poll list refreshed!");
                }));

        setItem(52, new MenuItem(Material.EMERALD, ChatColor.GREEN + "Create New Poll")
                .addLoreLine(ChatColor.GRAY + "Start creating a new poll")
                .addLoreLine("")
                .addLoreLine(ChatColor.YELLOW + "Use: /createpoll <duration> <question>")
                .setClickHandler((p, slot) -> {
                    close();
                    p.sendMessage(ChatColor.YELLOW + "Use the command: " + ChatColor.WHITE + "/createpoll <duration> <question>");
                    p.sendMessage(ChatColor.GRAY + "Example: " + ChatColor.WHITE + "/createpoll 1d Should we have a server event?");
                }));

        // Statistics and summary
        if (!myPolls.isEmpty()) {
            int activeCount = (int) myPolls.stream().filter(Poll::isActive).count();
            int totalVotes = myPolls.stream().mapToInt(Poll::getTotalVotes).sum();

            setItem(47, new MenuItem(Material.WRITABLE_BOOK, ChatColor.AQUA + "Statistics")
                    .addLoreLine(ChatColor.GRAY + "Active polls: " + ChatColor.WHITE + activeCount)
                    .addLoreLine(ChatColor.GRAY + "Closed polls: " + ChatColor.WHITE + (myPolls.size() - activeCount))
                    .addLoreLine(ChatColor.GRAY + "Total votes received: " + ChatColor.WHITE + totalVotes)
                    .addLoreLine("")
                    .addLoreLine(ChatColor.YELLOW + "Average votes per poll: " + ChatColor.WHITE +
                            String.format("%.1f", totalVotes / (double) myPolls.size())));
        }

        // Empty state
        if (myPolls.isEmpty()) {
            setItem(22, new MenuItem(Material.BARRIER, ChatColor.RED + "No Polls Created")
                    .addLoreLine(ChatColor.GRAY + "You haven't created any polls yet.")
                    .addLoreLine("")
                    .addLoreLine(ChatColor.YELLOW + "Create your first poll using:")
                    .addLoreLine(ChatColor.WHITE + "/createpoll <duration> <question>")
                    .addLoreLine("")
                    .addLoreLine(ChatColor.GRAY + "Example:")
                    .addLoreLine(ChatColor.WHITE + "/createpoll 3d What should we build next?"));
        }
    }

    private void openPollManagement(Poll poll) {
        // Open the poll voting menu where they can also manage it
        PollVotingMenu managementMenu = new PollVotingMenu(player, poll, pollManager, false);
        managementMenu.open();
    }

    private Material getPollStatusMaterial(Poll poll) {
        if (poll.isActive()) {
            int votes = poll.getTotalVotes();
            if (votes == 0) {
                return Material.YELLOW_STAINED_GLASS; // No votes yet
            } else if (votes < 5) {
                return Material.ORANGE_STAINED_GLASS; // Few votes
            } else {
                return Material.LIME_STAINED_GLASS; // Popular poll
            }
        } else {
            return Material.GRAY_STAINED_GLASS; // Closed poll
        }
    }

    private String getStatusText(Poll poll) {
        if (poll.isActive()) {
            return ChatColor.GREEN + "Active";
        } else {
            return ChatColor.RED + "Closed";
        }
    }
}