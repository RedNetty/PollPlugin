package com.rednetty.gui;

import com.rednetty.menu.Menu;
import com.rednetty.menu.MenuItem;
import com.rednetty.poll.Poll;
import com.rednetty.poll.PollManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;

public class PollVotingMenu extends Menu {
    private final Poll poll;
    private final PollManager pollManager;
    private final boolean previewMode;

    public PollVotingMenu(Player player, Poll poll, PollManager pollManager, boolean previewMode) {
        super(player, ChatColor.DARK_PURPLE + (previewMode ? "Poll Preview" : "Vote on Poll"), 54);
        this.poll = poll;
        this.pollManager = pollManager;
        this.previewMode = previewMode;
        setupMenu();
    }

    private void setupMenu() {
        createBorder();

        // Poll information header
        setItem(4, new MenuItem(Material.PAPER, ChatColor.GOLD + "Poll Information")
                .addLoreLine(ChatColor.YELLOW + "Question: " + ChatColor.WHITE + poll.getQuestion())
                .addLoreLine(ChatColor.GRAY + "Created by: " + ChatColor.WHITE + poll.getCreatorName())
                .addLoreLine(ChatColor.GRAY + "Created: " + ChatColor.WHITE + poll.getFormattedCreationDate())
                .addLoreLine(ChatColor.GRAY + "Expires: " + ChatColor.WHITE + poll.getTimeRemaining())
                .addLoreLine(ChatColor.GRAY + "Total votes: " + ChatColor.WHITE + poll.getTotalVotes()));

        boolean hasVoted = poll.hasVoted(player.getUniqueId());
        String playerVote = hasVoted ? poll.getVotes().get(player.getUniqueId()) : null;

        // Display voting options or results
        displayOptions(hasVoted, playerVote);

        // Navigation and action buttons
        setupActionButtons(hasVoted);
    }

    private void displayOptions(boolean hasVoted, String playerVote) {
        int[] optionSlots = {19, 20, 21, 22, 23, 24, 28, 29, 30, 31, 32, 33};
        Map<String, Integer> results = poll.getResults();

        for (int i = 0; i < poll.getOptions().size() && i < optionSlots.length; i++) {
            String option = poll.getOptions().get(i);
            int slot = optionSlots[i];
            int votes = results.getOrDefault(option, 0);

            Material material;
            String displayName;
            MenuItem menuItem;

            if (hasVoted || previewMode) {
                // Show results mode
                material = getResultMaterial(option, playerVote, votes);
                displayName = ChatColor.AQUA + option;

                menuItem = new MenuItem(material, displayName)
                        .addLoreLine(ChatColor.GRAY + "Votes: " + ChatColor.WHITE + votes);

                if (poll.getTotalVotes() > 0) {
                    double percentage = (double) votes / poll.getTotalVotes() * 100;
                    menuItem.addLoreLine(ChatColor.GRAY + "Percentage: " + ChatColor.WHITE + String.format("%.1f%%", percentage));

                    // Visual bar representation
                    String bar = createProgressBar(percentage);
                    menuItem.addLoreLine(ChatColor.GRAY + "Progress: " + bar);
                }

                if (option.equals(playerVote) && !previewMode) {
                    menuItem.addLoreLine("")
                            .addLoreLine(ChatColor.GREEN + "✓ Your choice");
                }

            } else {
                // Voting mode
                material = Material.PAPER;
                displayName = ChatColor.WHITE + option;

                menuItem = new MenuItem(material, displayName)
                        .addLoreLine(ChatColor.YELLOW + "Click to vote for this option")
                        .addLoreLine("")
                        .addLoreLine(ChatColor.GRAY + "Current votes: " + ChatColor.WHITE + votes);

                final String finalOption = option;
                menuItem.setClickHandler((p, clickedSlot) -> {
                    if (pollManager.vote(poll.getPollUUID(), p.getUniqueId(), finalOption)) {
                        p.sendMessage(ChatColor.GREEN + "Vote recorded!");
                        p.sendMessage(ChatColor.GRAY + "You voted for: " + ChatColor.WHITE + finalOption);

                        // Refresh the menu to show results
                        PollVotingMenu resultsMenu = new PollVotingMenu(p, poll, pollManager, false);
                        resultsMenu.open();
                    } else {
                        p.sendMessage(ChatColor.RED + "Failed to record your vote. You may have already voted.");
                    }
                });
            }

            setItem(slot, menuItem);
        }
    }

    private Material getResultMaterial(String option, String playerVote, int votes) {
        if (option.equals(playerVote) && !previewMode) {
            return Material.LIME_STAINED_GLASS; // Player's choice
        }

        // Color based on vote count (more votes = better material)
        if (votes == 0) {
            return Material.GRAY_STAINED_GLASS;
        } else if (votes <= 2) {
            return Material.RED_STAINED_GLASS;
        } else if (votes <= 5) {
            return Material.YELLOW_STAINED_GLASS;
        } else if (votes <= 10) {
            return Material.BLUE_STAINED_GLASS;
        } else {
            return Material.PURPLE_STAINED_GLASS;
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

    private void setupActionButtons(boolean hasVoted) {
        // Back to poll list
        setItem(45, new MenuItem(Material.ARROW, ChatColor.YELLOW + "Back to Poll List")
                .addLoreLine(ChatColor.GRAY + "Return to the main poll list")
                .setClickHandler((p, slot) -> {
                    PollListMenu pollList = new PollListMenu(p, pollManager);
                    pollList.open();
                }));

        // Refresh button
        setItem(49, new MenuItem(Material.CLOCK, ChatColor.YELLOW + "Refresh")
                .addLoreLine(ChatColor.GRAY + "Update poll results")
                .setClickHandler((p, slot) -> {
                    // Reload poll data from storage
                    Poll refreshedPoll = pollManager.getPoll(poll.getPollUUID());
                    if (refreshedPoll != null) {
                        PollVotingMenu refreshed = new PollVotingMenu(p, refreshedPoll, pollManager, previewMode);
                        refreshed.open();
                    } else {
                        p.sendMessage(ChatColor.RED + "This poll is no longer active.");
                        PollListMenu pollList = new PollListMenu(p, pollManager);
                        pollList.open();
                    }
                }));

        // Admin controls (if player has permission)
        if (pollManager.canClosePoll(player, poll)) {
            setItem(47, new MenuItem(Material.BARRIER, ChatColor.RED + "Close Poll")
                    .addLoreLine(ChatColor.GRAY + "Close this poll permanently")
                    .addLoreLine(ChatColor.GRAY + "This action cannot be undone")
                    .setClickHandler((p, slot) -> {
                        if (pollManager.closePoll(poll.getPollUUID())) {
                            p.sendMessage(ChatColor.GREEN + "Poll closed successfully!");
                            PollListMenu pollList = new PollListMenu(p, pollManager);
                            pollList.open();
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
                            PollListMenu pollList = new PollListMenu(p, pollManager);
                            pollList.open();
                        } else {
                            p.sendMessage(ChatColor.RED + "Failed to delete the poll.");
                        }
                    }));
        }

        // Preview mode specific buttons
        if (previewMode) {
            setItem(53, new MenuItem(Material.EMERALD, ChatColor.GREEN + "Finish & Create")
                    .addLoreLine(ChatColor.GRAY + "Save this poll and make it active")
                    .setClickHandler((p, slot) -> {
                        if (pollManager.savePoll(poll)) {
                            p.sendMessage(ChatColor.GREEN + "Poll created successfully!");
                            p.sendMessage(ChatColor.GRAY + "Poll ID: " + ChatColor.WHITE + poll.getPollUUID().toString().substring(0, 8));
                            close();
                        } else {
                            p.sendMessage(ChatColor.RED + "Failed to create poll.");
                        }
                    }));
        }

        // Vote status indicator
        if (!previewMode) {
            Material statusMaterial = hasVoted ? Material.LIME_DYE : Material.GRAY_DYE;
            String statusText = hasVoted ? ChatColor.GREEN + "You have voted" : ChatColor.YELLOW + "You haven't voted yet";

            setItem(40, new MenuItem(statusMaterial, statusText)
                    .addLoreLine(hasVoted ?
                            ChatColor.GRAY + "Your vote: " + ChatColor.WHITE + poll.getVotes().get(player.getUniqueId()) :
                            ChatColor.GRAY + "Click an option above to vote"));
        }
    }
}