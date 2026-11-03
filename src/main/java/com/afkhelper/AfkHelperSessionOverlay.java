
package com.afkhelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.function.Supplier;
import javax.inject.Inject;

import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class AfkHelperSessionOverlay extends OverlayPanel
{
    private static final Color TITLE_COLOR = new Color(255, 190, 70);
    private static final Color SESSION_COLOR = new Color(100, 190, 255);
    private static final Color COMBAT_COLOR = new Color(255, 110, 100);
    private static final Color SUPPLY_COLOR = new Color(110, 220, 150);
    private static final Color VALUE_COLOR = new Color(245, 245, 245);

    private final AfkHelperSessionStats stats;
    private final AfkHelperInventoryTracker inventory;
    private final AfkHelperConfig config;

    private final Supplier<String> idleText;
    private final Supplier<String> combatText;
    private final Supplier<String> supplyText;
    private final Supplier<String> logoutText;

    @Inject
    public AfkHelperSessionOverlay(
            Client client,
            AfkHelperSessionStats stats,
            AfkHelperConfig config)
    {
        this(client, stats, null, config, null, null, null, null);
    }

    public AfkHelperSessionOverlay(
            Client client,
            AfkHelperSessionStats stats)
    {
        this(client, stats, null, null, null, null, null, null);
    }

    public AfkHelperSessionOverlay(
            Client client,
            AfkHelperSessionStats stats,
            AfkHelperInventoryTracker inventory)
    {
        this(client, stats, inventory, null, null, null, null, null);
    }

    public AfkHelperSessionOverlay(
            Client client,
            AfkHelperSessionStats stats,
            AfkHelperInventoryTracker inventory,
            Supplier<String> idleText,
            Supplier<String> combatText,
            Supplier<String> supplyText,
            Supplier<String> profitText)
    {
        this(
                client,
                stats,
                inventory,
                null,
                idleText,
                combatText,
                supplyText,
                profitText
        );
    }

    public AfkHelperSessionOverlay(
            Client client,
            AfkHelperSessionStats stats,
            AfkHelperInventoryTracker inventory,
            AfkHelperConfig config,
            Supplier<String> idleText,
            Supplier<String> combatText,
            Supplier<String> supplyText,
            Supplier<String> logoutText)
    {
        this.stats = stats;
        this.inventory = inventory;
        this.config = config;
        this.idleText = idleText;
        this.combatText = combatText;
        this.supplyText = supplyText;
        this.logoutText = logoutText;

        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        panelComponent.getChildren().clear();

        if (config != null && !config.sessionOverlayEnabled())
        {
            return null;
        }

        addTitle("AFK HELPER", TITLE_COLOR);

        addSection("SESSION", SESSION_COLOR);

        if (show("overlayShowDuration"))
        {
            addLine("Duration", stats.getSessionDurationText(), SESSION_COLOR);
        }

        if (show("overlayShowXpGained"))
        {
            addLine("XP gained", number(stats.getXpGained()), SESSION_COLOR);
        }

        if (show("overlayShowXpPerHour"))
        {
            addLine("XP / hour", number(stats.getXpPerHour()), SESSION_COLOR);
        }

        if (show("overlayShowActions"))
        {
            addLine("Actions", number(stats.getActions()), SESSION_COLOR);
        }

        if (show("overlayShowAfkStatus") && idleText != null)
        {
            addLine("AFK status", idleText.get(), SESSION_COLOR);
        }

        if (show("overlayShowLogoutCountdown") && logoutText != null)
        {
            addLine("Logout", logoutText.get(), SESSION_COLOR);
        }

        if (show("overlayShowCombatStatus") && combatText != null)
        {
            addLine("Combat status", combatText.get(), SESSION_COLOR);
        }

        addSection("COMBAT", COMBAT_COLOR);

        if (show("overlayShowKills"))
        {
            addLine("Kills", number(stats.getMonstersKilled()), COMBAT_COLOR);
        }

        if (show("overlayShowKillsPerHour"))
        {
            addLine("Kills / hour", number(stats.getKillsPerHour()), COMBAT_COLOR);
        }

        if (show("overlayShowDamage"))
        {
            addLine("Damage", number(stats.getTotalDamage()), COMBAT_COLOR);
        }

        if (show("overlayShowDamagePerHour"))
        {
            addLine("Damage / hour", number(stats.getDamagePerHour()), COMBAT_COLOR);
        }

        if (show("overlayShowAverageKillTime"))
        {
            addLine("Average kill", stats.getAverageKillTimeText(), COMBAT_COLOR);
        }

        if (inventory != null)
        {
            addSection("SUPPLIES & PROFIT", SUPPLY_COLOR);

            if (show("overlayShowSuppliesUsed"))
            {
                addLine("Supplies used", number(inventory.getSuppliesConsumed()), SUPPLY_COLOR);
            }

            if (show("overlayShowItemsGained"))
            {
                addLine("Items gained", number(inventory.getItemsGained()), SUPPLY_COLOR);
            }

            if (show("overlayShowEstimatedProfit"))
            {
                addLine("Estimated profit", gp(inventory.getEstimatedProfit()), SUPPLY_COLOR);
            }

            if (show("overlayShowSupplyCost"))
            {
                addLine("Supply cost", gp(inventory.getConsumableTotalCost()), SUPPLY_COLOR);
            }

            if (show("overlayShowSupplyCostPerHour"))
            {
                addLine("Supply cost / hour", gp(inventory.getConsumableCostPerHour()), SUPPLY_COLOR);
            }

            if (show("overlayShowCombatCost"))
            {
                addLine("Combat cost", gp(inventory.getCombatTotalCost()), SUPPLY_COLOR);
            }

            if (show("overlayShowCombatCostPerHour"))
            {
                addLine("Combat cost / hour", gp(inventory.getCombatCostPerHour()), SUPPLY_COLOR);
            }

            if (supplyText != null)
            {
                addLine("Supply summary", supplyText.get(), SUPPLY_COLOR);
            }
        }

        return super.render(graphics);
    }

    private boolean show(String key)
    {
        if (config == null)
        {
            return true;
        }

        switch (key)
        {
            case "overlayShowDuration":
                return config.overlayShowDuration();
            case "overlayShowXpGained":
                return config.overlayShowXpGained();
            case "overlayShowXpPerHour":
                return config.overlayShowXpPerHour();
            case "overlayShowActions":
                return config.overlayShowActions();
            case "overlayShowKills":
                return config.overlayShowKills();
            case "overlayShowKillsPerHour":
                return config.overlayShowKillsPerHour();
            case "overlayShowDamage":
                return config.overlayShowDamage();
            case "overlayShowDamagePerHour":
                return config.overlayShowDamagePerHour();
            case "overlayShowAverageKillTime":
                return config.overlayShowAverageKillTime();
            case "overlayShowAfkStatus":
                return config.overlayShowAfkStatus();
            case "overlayShowLogoutCountdown":
                return config.overlayShowLogoutCountdown();
            case "overlayShowCombatStatus":
                return config.overlayShowCombatStatus();
            case "overlayShowSuppliesUsed":
                return config.overlayShowSuppliesUsed();
            case "overlayShowItemsGained":
                return config.overlayShowItemsGained();
            case "overlayShowEstimatedProfit":
                return config.overlayShowEstimatedProfit();
            case "overlayShowSupplyCost":
                return config.overlayShowSupplyCost();
            case "overlayShowSupplyCostPerHour":
                return config.overlayShowSupplyCostPerHour();
            case "overlayShowCombatCost":
                return config.overlayShowCombatCost();
            case "overlayShowCombatCostPerHour":
                return config.overlayShowCombatCostPerHour();
            default:
                return true;
        }
    }

    private void addTitle(String text, Color color)
    {
        panelComponent.getChildren().add(
                TitleComponent.builder()
                        .text(text)
                        .color(color)
                        .build()
        );
    }

    private void addSection(String text, Color color)
    {
        panelComponent.getChildren().add(
                TitleComponent.builder()
                        .text(text)
                        .color(color)
                        .build()
        );
    }

    private void addLine(String label, String value, Color labelColor)
    {
        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left(label)
                        .leftColor(labelColor)
                        .right(value == null ? "-" : value)
                        .rightColor(VALUE_COLOR)
                        .build()
        );
    }

    private String number(long value)
    {
        return String.format("%,d", value);
    }

    private String gp(long value)
    {
        return String.format("%,d gp", value);
    }
}
