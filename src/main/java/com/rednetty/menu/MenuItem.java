package com.rednetty.menu;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an item in a menu with an optional click handler
 */
public class MenuItem {

    private ItemStack itemStack;
    private String displayName;
    private List<String> lore = new ArrayList<>();
    private boolean glowing = false;
    private MenuClickHandler clickHandler;

    public MenuItem(Material material) {
        this(new ItemStack(material));
    }

    public MenuItem(Material material, String displayName) {
        this(new ItemStack(material));
        this.displayName = displayName;
    }

    public MenuItem(ItemStack itemStack) {
        this.itemStack = itemStack.clone();

        if (itemStack.hasItemMeta()) {
            ItemMeta meta = itemStack.getItemMeta();

            if (meta.hasDisplayName()) {
                this.displayName = meta.getDisplayName();
            }

            if (meta.hasLore()) {
                this.lore = new ArrayList<>(meta.getLore());
            }
        }
    }

    public MenuItem setDisplayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    public MenuItem setLore(List<String> lore) {
        this.lore = new ArrayList<>(lore);
        return this;
    }

    public MenuItem addLoreLine(String line) {
        this.lore.add(line);
        return this;
    }

    public MenuItem setGlowing(boolean glowing) {
        this.glowing = glowing;
        return this;
    }

    public MenuItem setClickHandler(MenuClickHandler clickHandler) {
        this.clickHandler = clickHandler;
        return this;
    }

    public MenuClickHandler getClickHandler() {
        return clickHandler;
    }

    public ItemStack toItemStack() {
        ItemStack result = itemStack.clone();
        ItemMeta meta = result.getItemMeta();

        if (meta != null) {
            if (displayName != null) {
                meta.setDisplayName(displayName);
            }

            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }

            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            result.setItemMeta(meta);
        }

        return result;
    }
}