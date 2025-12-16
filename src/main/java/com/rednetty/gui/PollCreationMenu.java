package com.rednetty.gui;

import com.rednetty.menu.Menu;
import com.rednetty.menu.MenuItem;
import com.rednetty.poll.Poll;
import com.rednetty.poll.PollManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.conversations.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class PollCreationMenu extends Menu {
    private final Poll poll;
    private final PollManager pollManager;

    public PollCreationMenu(Player player, Poll poll, PollManager pollManager) {
        super(player, ChatColor.DARK_GREEN + "Creating Poll", 54);
        this.poll = poll;
        this.pollManager = pollManager;
        setupMenu();
    }

    private void setupMenu() {
        createBorder();

        // Poll info section
        setItem(10, new MenuItem(Material.PAPER, ChatColor.GOLD + "Poll Question")
                .addLoreLine(ChatColor.GRAY + "Question: " + ChatColor.WHITE + poll.getQuestion())
                .addLoreLine(ChatColor.GRAY + "Duration: " + ChatColor.WHITE + poll.getTimeRemaining())
                .addLoreLine(ChatColor.GRAY + "Creator: " + ChatColor.WHITE + player.getName()));

        // Add option button
        setItem(12, new MenuItem(Material.EMERALD, ChatColor.GREEN + "Add Option")
                .addLoreLine(ChatColor.GRAY + "Click to add a new answer option")
                .addLoreLine(ChatColor.GRAY + "Maximum 6 options allowed")
                .addLoreLine("")
                .addLoreLine(ChatColor.YELLOW + "Current options: " + ChatColor.WHITE + poll.getOptions().size() + "/6")
                .setClickHandler((p, slot) -> {
                    if (poll.getOptions().size() >= 6) {
                        p.sendMessage(ChatColor.RED + "Maximum of 6 options allowed!");
                        return;
                    }
                    promptForOption();
                }));

        // Quick option buttons
        setQuickOptionButtons();

        // Options display area (slots 19-24, 28-33, 37-42)
        updateOptionsDisplay();

        // Action buttons
        setItem(48, new MenuItem(Material.LIME_STAINED_GLASS_PANE, ChatColor.GREEN + "Create Poll")
                .addLoreLine(ChatColor.GRAY + "Finalize and create the poll")
                .addLoreLine("")
                .addLoreLine(ChatColor.YELLOW + "Requires at least 2 options")
                .setClickHandler((p, slot) -> createPoll()));

        setItem(49, new MenuItem(Material.YELLOW_STAINED_GLASS_PANE, ChatColor.YELLOW + "Preview")
                .addLoreLine(ChatColor.GRAY + "Preview how the poll will look")
                .setClickHandler((p, slot) -> previewPoll()));

        setItem(50, new MenuItem(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + "Cancel")
                .addLoreLine(ChatColor.GRAY + "Cancel poll creation")
                .setClickHandler((p, slot) -> {
                    p.sendMessage(ChatColor.YELLOW + "Poll creation cancelled.");
                    close();
                }));
    }

    private void setQuickOptionButtons() {
        // Yes/No preset
        setItem(14, new MenuItem(Material.BOOK, ChatColor.AQUA + "Yes/No Poll")
                .addLoreLine(ChatColor.GRAY + "Quick setup for Yes/No question")
                .setClickHandler((p, slot) -> {
                    poll.setOptions(new ArrayList<>());
                    poll.addOption("Yes");
                    poll.addOption("No");
                    updateOptionsDisplay();
                    updateAddOptionButton();
                    p.sendMessage(ChatColor.GREEN + "Added Yes/No options!");
                }));

        // Multiple choice preset
        setItem(15, new MenuItem(Material.WRITABLE_BOOK, ChatColor.AQUA + "Multiple Choice")
                .addLoreLine(ChatColor.GRAY + "Quick setup for A/B/C/D options")
                .setClickHandler((p, slot) -> {
                    poll.setOptions(new ArrayList<>());
                    poll.addOption("Option A");
                    poll.addOption("Option B");
                    poll.addOption("Option C");
                    poll.addOption("Option D");
                    updateOptionsDisplay();
                    updateAddOptionButton();
                    p.sendMessage(ChatColor.GREEN + "Added multiple choice options!");
                }));

        // Clear all options
        setItem(16, new MenuItem(Material.BARRIER, ChatColor.RED + "Clear All")
                .addLoreLine(ChatColor.GRAY + "Remove all options")
                .setClickHandler((p, slot) -> {
                    poll.setOptions(new ArrayList<>());
                    updateOptionsDisplay();
                    updateAddOptionButton();
                    p.sendMessage(ChatColor.YELLOW + "All options cleared!");
                }));
    }

    private void updateOptionsDisplay() {
        // Clear previous options
        int[] optionSlots = {19, 20, 21, 22, 23, 24, 28, 29, 30, 31, 32, 33, 37, 38, 39, 40, 41, 42};
        for (int slot : optionSlots) {
            removeItem(slot);
        }

        // Display current options
        List<String> currentOptions = poll.getOptions();
        for (int i = 0; i < currentOptions.size() && i < 6; i++) {
            int slot = optionSlots[i];
            String option = currentOptions.get(i);

            setItem(slot, new MenuItem(Material.PAPER, ChatColor.WHITE + option)
                    .addLoreLine(ChatColor.GRAY + "Option " + (i + 1))
                    .addLoreLine("")
                    .addLoreLine(ChatColor.RED + "Right-click to remove")
                    .setClickHandler((p, clickedSlot) -> {
                        List<String> updatedOptions = new ArrayList<>(poll.getOptions());
                        updatedOptions.remove(option);
                        poll.setOptions(updatedOptions);
                        updateOptionsDisplay();
                        updateAddOptionButton();
                        p.sendMessage(ChatColor.YELLOW + "Option removed: " + option);
                    }));
        }
    }

    private void updateAddOptionButton() {
        setItem(12, new MenuItem(Material.EMERALD, ChatColor.GREEN + "Add Option")
                .addLoreLine(ChatColor.GRAY + "Click to add a new answer option")
                .addLoreLine(ChatColor.GRAY + "Maximum 6 options allowed")
                .addLoreLine("")
                .addLoreLine(ChatColor.YELLOW + "Current options: " + ChatColor.WHITE + poll.getOptions().size() + "/6")
                .setClickHandler((p, slot) -> {
                    if (poll.getOptions().size() >= 6) {
                        p.sendMessage(ChatColor.RED + "Maximum of 6 options allowed!");
                        return;
                    }
                    promptForOption();
                }));
    }

    private void promptForOption() {
        close();

        ConversationFactory factory = new ConversationFactory(pollManager.getPollStorage().getPlugin())
                .withModality(true)
                .withTimeout(60)
                .withFirstPrompt(new OptionPrompt())
                .addConversationAbandonedListener(event -> {
                    if (!event.gracefulExit()) {
                        player.sendMessage(ChatColor.RED + "Option input cancelled.");
                    }
                    // Reopen menu after conversation
                    new PollCreationMenu(player, poll, pollManager).open();
                });

        factory.buildConversation(player).begin();
    }

    private void createPoll() {
        if (poll.getOptions().size() < 2) {
            player.sendMessage(ChatColor.RED + "You need at least 2 options to create a poll!");
            return;
        }

        if (pollManager.savePoll(poll)) {
            player.sendMessage(ChatColor.GREEN + "Poll created successfully!");
            player.sendMessage(ChatColor.GRAY + "Poll ID: " + ChatColor.WHITE + poll.getPollUUID().toString().substring(0, 8));
            player.sendMessage(ChatColor.GRAY + "Players can now vote using " + ChatColor.YELLOW + "/poll");
            close();
        } else {
            player.sendMessage(ChatColor.RED + "Failed to create poll. Please try again.");
        }
    }

    private void previewPoll() {
        if (poll.getOptions().isEmpty()) {
            player.sendMessage(ChatColor.RED + "Add some options first!");
            return;
        }

        PollVotingMenu preview = new PollVotingMenu(player, poll, pollManager, true);
        preview.open();
    }

    private class OptionPrompt extends StringPrompt {
        @Override
        public String getPromptText(ConversationContext context) {
            return ChatColor.YELLOW + "Enter the option text (or 'cancel' to abort):";
        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {
            if (input.equalsIgnoreCase("cancel")) {
                context.getForWhom().sendRawMessage(ChatColor.RED + "Option input cancelled.");
                return END_OF_CONVERSATION;
            }

            if (input.trim().length() < 1) {
                context.getForWhom().sendRawMessage(ChatColor.RED + "Option cannot be empty!");
                return this;
            }

            if (input.length() > 50) {
                context.getForWhom().sendRawMessage(ChatColor.RED + "Option must be 50 characters or less!");
                return this;
            }

            if (poll.getOptions().contains(input.trim())) {
                context.getForWhom().sendRawMessage(ChatColor.RED + "That option already exists!");
                return this;
            }

            poll.addOption(input.trim());
            context.getForWhom().sendRawMessage(ChatColor.GREEN + "Option added: " + input.trim());

            return END_OF_CONVERSATION;
        }
    }
}