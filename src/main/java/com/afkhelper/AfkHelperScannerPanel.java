package com.afkhelper;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.Scrollable;
import javax.swing.UIManager;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;

public class AfkHelperScannerPanel extends PluginPanel
{
    private static final String GROUP = "afkhelper";
    private static final int MIN_WRAP_WIDTH = 80;
    // Borders/padding around the text: content (10+10), section etched
    // border (2+2), details panel (8+8).
    private static final int FIXED_PADDING = 40;
    // Breathing room so text never touches the right-hand edge.
    private static final int RIGHT_MARGIN = 24;

    private final AfkHelperConfig config;
    private final ConfigManager configManager;
    private final Client client;
    private final ClientThread clientThread;
    private final Runnable resetSession;

    private final JPanel contentPanel = new JPanel();
    private final JPanel npcListPanel = new JPanel();

    // These lists must be initialised before scanStatus calls createWrappedLabel().
    private final List<JCheckBox> npcCheckboxes = new ArrayList<>();
    private final List<JLabel> wrappedLabels = new ArrayList<>();
    private final List<JCheckBox> wrappedCheckboxes = new ArrayList<>();

    private final JLabel scanStatus =
            createWrappedLabel("Press Scan to find nearby monsters.");

    private JScrollPane scrollPane;
    private JLabel savedLabel;
    private int wrapWidth = 190;

    public AfkHelperScannerPanel(
            AfkHelperConfig config,
            ConfigManager configManager,
            Client client,
            ClientThread clientThread)
    {
        this(config, configManager, client, clientThread, () -> {});
    }

    public AfkHelperScannerPanel(
            AfkHelperConfig config,
            ConfigManager configManager,
            Client client,
            ClientThread clientThread,
            Runnable resetSession)
    {
        // false = don't let PluginPanel wrap us in its own scroll pane, which
        // sized our content to its preferred height and left the bottom half
        // of the sidebar empty. We provide our own scroll pane below.
        super(false);

        this.config = config;
        this.configManager = configManager;
        this.client = client;
        this.clientThread = clientThread;
        this.resetSession = resetSession;

        setLayout(new BorderLayout());

        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("AFK HELPER");
        title.setAlignmentX(LEFT_ALIGNMENT);
        contentPanel.add(title);
        contentPanel.add(Box.createVerticalStrut(12));

        addSessionSection();
        addOverlaySection();
        addScannerSection();
        addCombatSection();
        addHealthSection();
        addAfkSection();
        addSkillingSection();
        addLevelUpSection();
        addAppearanceSection();
        addAudioSection();
        addActivityHighlightingSection();

        // Pin the content to the top at its natural height. Without this the
        // scroll viewport stretches the BoxLayout to fill the panel, which
        // spreads the extra space between sections and moves the headers
        // when one is expanded or collapsed.
        JPanel contentWrapper = new ScrollableWrapper();
        contentWrapper.add(contentPanel, BorderLayout.NORTH);

        scrollPane = new JScrollPane(contentWrapper);
        scrollPane.setHorizontalScrollBarPolicy(
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);

        scrollPane.getViewport().addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentResized(ComponentEvent event)
            {
                updateWrappingWidth();
            }
        });

        addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentResized(ComponentEvent event)
            {
                updateWrappingWidth();
            }
        });

        SwingUtilities.invokeLater(this::updateWrappingWidth);
    }

    /**
     * Content that always matches the viewport width (never wider, so
     * nothing is clipped on the right) while scrolling vertically.
     */
    private static class ScrollableWrapper extends JPanel implements Scrollable
    {
        ScrollableWrapper()
        {
            super(new BorderLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(
                Rectangle visibleRect, int orientation, int direction)
        {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(
                Rectangle visibleRect, int orientation, int direction)
        {
            return Math.max(16, visibleRect.height - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }
    }

    private int getTextOverhead()
    {
        JCheckBox probe = new JCheckBox();
        Insets insets = probe.getInsets();
        Icon icon = UIManager.getIcon("CheckBox.icon");
        int iconWidth = icon != null ? icon.getIconWidth() : 20;

        int checkboxExtra = insets.left + insets.right
                + iconWidth + probe.getIconTextGap();

        return FIXED_PADDING + checkboxExtra + RIGHT_MARGIN;
    }

    private void updateWrappingWidth()
    {
        int viewportWidth = scrollPane != null
                ? scrollPane.getViewport().getWidth()
                : getWidth();
        int availableWidth = viewportWidth - getTextOverhead();

        if (availableWidth <= 0)
        {
            return;
        }

        int newWidth = Math.max(MIN_WRAP_WIDTH, availableWidth);

        if (newWidth == wrapWidth)
        {
            return;
        }

        wrapWidth = newWidth;

        for (JLabel label : wrappedLabels)
        {
            updateLabelHtml(label);
        }

        for (JCheckBox checkbox : wrappedCheckboxes)
        {
            updateCheckboxHtml(checkbox);
        }

        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private JLabel createWrappedLabel(String text)
    {
        JLabel label = new JLabel();
        label.putClientProperty("wrappedText", text);
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        wrappedLabels.add(label);
        updateLabelHtml(label);

        return label;
    }

    private void updateLabelHtml(JLabel label)
    {
        Object value = label.getClientProperty("wrappedText");

        if (value == null)
        {
            return;
        }

        label.setText(
                "<html><body style='width:" + wrapWidth + "px'>"
                        + escapeHtml(value.toString())
                        + "</body></html>");
    }

    private String escapeHtml(String text)
    {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private void addSessionSection()
    {
        JPanel details = createSection("Session Tracker", true);

        details.add(createWrappedLabel(
                "Reset all statistics collected during this session."));

        JButton resetButton = new JButton("Reset session statistics");
        resetButton.setAlignmentX(LEFT_ALIGNMENT);
        resetButton.addActionListener(e -> resetSession.run());

        details.add(Box.createVerticalStrut(8));
        details.add(resetButton);
        details.add(Box.createVerticalStrut(6));
    }

    private void addOverlaySection()
    {
        JPanel details = createSection("Session Overlay", false);

        details.add(createWrappedLabel(
                "Choose which session statistics appear in the on-screen overlay."));

        addBooleanSetting(details, "Enable session overlay",
                config.sessionOverlayEnabled(), "sessionOverlayEnabled");

        details.add(Box.createVerticalStrut(8));
        details.add(createWrappedLabel("SESSION STATISTICS"));

        addBooleanSetting(details, "Session duration",
                config.overlayShowDuration(), "overlayShowDuration");
        addBooleanSetting(details, "XP gained",
                config.overlayShowXpGained(), "overlayShowXpGained");
        addBooleanSetting(details, "XP per hour",
                config.overlayShowXpPerHour(), "overlayShowXpPerHour");
        addBooleanSetting(details, "Actions performed",
                config.overlayShowActions(), "overlayShowActions");
        addBooleanSetting(details, "AFK status",
                config.overlayShowAfkStatus(), "overlayShowAfkStatus");
        addBooleanSetting(details, "Logout countdown",
                config.overlayShowLogoutCountdown(), "overlayShowLogoutCountdown");

        details.add(Box.createVerticalStrut(8));
        details.add(createWrappedLabel("COMBAT STATISTICS"));

        addBooleanSetting(details, "Combat status",
                config.overlayShowCombatStatus(), "overlayShowCombatStatus");
        addBooleanSetting(details, "Monster kills",
                config.overlayShowKills(), "overlayShowKills");
        addBooleanSetting(details, "Kills per hour",
                config.overlayShowKillsPerHour(), "overlayShowKillsPerHour");
        addBooleanSetting(details, "Damage dealt",
                config.overlayShowDamage(), "overlayShowDamage");
        addBooleanSetting(details, "Damage per hour",
                config.overlayShowDamagePerHour(), "overlayShowDamagePerHour");
        addBooleanSetting(details, "Average kill time",
                config.overlayShowAverageKillTime(), "overlayShowAverageKillTime");

        details.add(Box.createVerticalStrut(8));
        details.add(createWrappedLabel("SUPPLIES AND PROFIT"));

        addBooleanSetting(details, "Supplies used",
                config.overlayShowSuppliesUsed(), "overlayShowSuppliesUsed");
        addBooleanSetting(details, "Items gained",
                config.overlayShowItemsGained(), "overlayShowItemsGained");
        addBooleanSetting(details, "Estimated profit",
                config.overlayShowEstimatedProfit(), "overlayShowEstimatedProfit");
        addBooleanSetting(details, "Supply cost",
                config.overlayShowSupplyCost(), "overlayShowSupplyCost");
        addBooleanSetting(details, "Supply cost per hour",
                config.overlayShowSupplyCostPerHour(), "overlayShowSupplyCostPerHour");
        addBooleanSetting(details, "Combat cost",
                config.overlayShowCombatCost(), "overlayShowCombatCost");
        addBooleanSetting(details, "Combat cost per hour",
                config.overlayShowCombatCostPerHour(), "overlayShowCombatCostPerHour");
    }

    private void addScannerSection()
    {
        JPanel details = createSection("Monster Scanner", true);

        details.add(createWrappedLabel(
                "Scan for nearby attackable combat NPCs."));

        JButton scanButton = new JButton("Scan nearby monsters");
        scanButton.setAlignmentX(LEFT_ALIGNMENT);
        scanButton.addActionListener(e -> scanNearbyNpcs());

        details.add(Box.createVerticalStrut(8));
        details.add(scanButton);
        details.add(Box.createVerticalStrut(6));

        addNumberSetting(details, "Monster scan radius (tiles)",
                config.monsterScanRadius(), 5, 30, "monsterScanRadius");

        details.add(scanStatus);
        details.add(Box.createVerticalStrut(6));

        npcListPanel.setLayout(new BoxLayout(npcListPanel, BoxLayout.Y_AXIS));
        npcListPanel.setAlignmentX(LEFT_ALIGNMENT);
        npcListPanel.setBorder(
                BorderFactory.createTitledBorder("Nearby Monsters"));
        npcListPanel.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        details.add(npcListPanel);

        JButton saveButton = new JButton("Save selected monsters");
        saveButton.setAlignmentX(LEFT_ALIGNMENT);
        saveButton.addActionListener(e -> saveSelectedMonsters());

        details.add(Box.createVerticalStrut(8));
        details.add(saveButton);
        details.add(Box.createVerticalStrut(6));
        details.add(createWrappedLabel("Saved monsters:"));

        savedLabel = createWrappedLabel(getSavedMonsterText());
        details.add(savedLabel);
    }

    private String getSavedMonsterText()
    {
        String saved = config.savedMonsterNames();
        return saved == null || saved.trim().isEmpty() ? "None" : saved;
    }

    private void scanNearbyNpcs()
    {
        scanStatus.setText(htmlText("Scanning..."));
        npcListPanel.removeAll();
        npcCheckboxes.clear();
        revalidate();
        repaint();

        clientThread.invokeLater((Runnable) () ->
        {
            if (client.getGameState() != GameState.LOGGED_IN
                    || client.getLocalPlayer() == null)
            {
                SwingUtilities.invokeLater(() ->
                {
                    scanStatus.setText(htmlText(
                            "Please log in before scanning."));
                    npcListPanel.revalidate();
                    npcListPanel.repaint();
                });
                return;
            }

            WorldPoint playerLocation =
                    client.getLocalPlayer().getWorldLocation();
            int radius = config.monsterScanRadius();

            Map<String, Integer> nearbyNpcs = new LinkedHashMap<>();

            for (NPC npc : client.getNpcs())
            {
                if (!isAttackableMonster(npc))
                {
                    continue;
                }

                WorldPoint npcLocation = npc.getWorldLocation();

                if (npcLocation == null)
                {
                    continue;
                }

                int distance = playerLocation.distanceTo(npcLocation);

                if (distance <= radius)
                {
                    String name = npc.getName();
                    Integer previousDistance = nearbyNpcs.get(name);

                    if (previousDistance == null || distance < previousDistance)
                    {
                        nearbyNpcs.put(name, distance);
                    }
                }
            }

            SwingUtilities.invokeLater(() -> displayNearbyNpcs(nearbyNpcs));
        });
    }

    private boolean isAttackableMonster(NPC npc)
    {
        if (npc == null || npc.getName() == null
                || npc.getName().trim().isEmpty())
        {
            return false;
        }

        NPCComposition composition = npc.getComposition();

        if (composition == null)
        {
            return false;
        }

        String[] actions = composition.getActions();

        if (actions == null)
        {
            return false;
        }

        for (String action : actions)
        {
            if (action != null && action.equalsIgnoreCase("Attack"))
            {
                return true;
            }
        }

        return false;
    }

    private void displayNearbyNpcs(Map<String, Integer> nearbyNpcs)
    {
        npcListPanel.removeAll();
        npcCheckboxes.clear();

        if (nearbyNpcs.isEmpty())
        {
            npcListPanel.add(createWrappedLabel(
                    "No attackable monsters found nearby."));
            scanStatus.setText(htmlText(
                    "Scan complete: no monsters found."));
        }
        else
        {
            Set<String> savedNames = getSavedMonsterSet();

            for (Map.Entry<String, Integer> entry : nearbyNpcs.entrySet())
            {
                String name = entry.getKey();
                int distance = entry.getValue();

                JCheckBox checkbox = createWrappedCheckbox(
                        name + " (" + distance + " tiles)",
                        savedNames.contains(name));

                npcCheckboxes.add(checkbox);
                npcListPanel.add(checkbox);
            }

            scanStatus.setText(htmlText(
                    "Scan complete: " + nearbyNpcs.size()
                            + " attackable monsters found."));
        }

        npcListPanel.revalidate();
        npcListPanel.repaint();
        revalidate();
        repaint();
    }

    private Set<String> getSavedMonsterSet()
    {
        Set<String> names = new LinkedHashSet<>();
        String saved = config.savedMonsterNames();

        if (saved == null || saved.trim().isEmpty())
        {
            return names;
        }

        for (String name : saved.split(","))
        {
            if (!name.trim().isEmpty())
            {
                names.add(name.trim());
            }
        }

        return names;
    }

    private void saveSelectedMonsters()
    {
        Set<String> selectedNames = new LinkedHashSet<>();

        for (JCheckBox checkbox : npcCheckboxes)
        {
            if (checkbox.isSelected())
            {
                selectedNames.add(
                        checkbox.getClientProperty("npcName").toString());
            }
        }

        String savedNames = String.join(", ", selectedNames);

        configManager.setConfiguration(
                GROUP, "savedMonsterNames", savedNames);

        savedLabel.setText(htmlText(
                selectedNames.isEmpty() ? "None" : savedNames));

        scanStatus.setText(htmlText(
                selectedNames.size() + " monster names saved."));

        savedLabel.revalidate();
        savedLabel.repaint();
    }

    private void addCombatSection()
    {
        JPanel details = createSection("Combat", false);

        addBooleanSetting(details, "Enable monster monitoring",
                config.monsterMonitoring(), "monsterMonitoring");
        addBooleanSetting(details, "Automatic monster tracking",
                config.automaticMonsterTracking(), "automaticMonsterTracking");
        addNumberSetting(details, "Monster health threshold (%)",
                config.monsterHealthThreshold(), 1, 100,
                "monsterHealthThreshold");
        addBooleanSetting(details, "Alert once per target",
                config.monsterAlertOnce(), "monsterAlertOnce");
    }

    private void addHealthSection()
    {
        JPanel details = createSection("Player Health", false);

        addBooleanSetting(details, "Enable HP warnings",
                config.playerHealthMonitoring(), "playerHealthMonitoring");

        addThresholdSetting(details, "HP threshold type",
                config.healthThresholdMode(), "healthThresholdMode",
                "HP percentage threshold", config.playerHealthThreshold(),
                1, 99, "playerHealthThreshold",
                "Exact HP threshold", config.healthExactThreshold(),
                1, 200, "healthExactThreshold");

        details.add(Box.createVerticalStrut(8));

        addBooleanSetting(details, "Enable Prayer warnings",
                config.prayerMonitoring(), "prayerMonitoring");

        addThresholdSetting(details, "Prayer threshold type",
                config.prayerThresholdMode(), "prayerThresholdMode",
                "Prayer percentage threshold", config.prayerThreshold(),
                1, 99, "prayerThreshold",
                "Exact Prayer threshold", config.prayerExactThreshold(),
                1, 200, "prayerExactThreshold");

        details.add(Box.createVerticalStrut(8));

        addBooleanSetting(details, "Repeat health warnings",
                config.repeatHealthWarnings(), "repeatHealthWarnings");
    }

    private void addThresholdSetting(
            JPanel panel,
            String modeLabel,
            AfkHelperConfig.ThresholdMode initialMode,
            String modeKey,
            String percentageLabel,
            int percentageValue,
            int percentageMinimum,
            int percentageMaximum,
            String percentageKey,
            String exactLabel,
            int exactValue,
            int exactMinimum,
            int exactMaximum,
            String exactKey)
    {
        panel.add(createWrappedLabel(modeLabel));

        JComboBox<AfkHelperConfig.ThresholdMode> modeSelector =
                new JComboBox<>(AfkHelperConfig.ThresholdMode.values());
        modeSelector.setSelectedItem(initialMode);
        modeSelector.setMaximumSize(new Dimension(180, 28));
        modeSelector.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(modeSelector);
        panel.add(Box.createVerticalStrut(5));

        JLabel percentageText = createWrappedLabel(percentageLabel);

        JSpinner percentageSpinner = new JSpinner(
                new SpinnerNumberModel(percentageValue,
                        percentageMinimum, percentageMaximum, 1));
        percentageSpinner.setMaximumSize(new Dimension(120, 28));
        percentageSpinner.setAlignmentX(LEFT_ALIGNMENT);

        JLabel exactText = createWrappedLabel(exactLabel);

        JSpinner exactSpinner = new JSpinner(
                new SpinnerNumberModel(exactValue,
                        exactMinimum, exactMaximum, 1));
        exactSpinner.setMaximumSize(new Dimension(120, 28));
        exactSpinner.setAlignmentX(LEFT_ALIGNMENT);

        Runnable updateVisibility = () ->
        {
            boolean percentageSelected =
                    modeSelector.getSelectedItem()
                            == AfkHelperConfig.ThresholdMode.PERCENTAGE;

            percentageText.setVisible(percentageSelected);
            percentageSpinner.setVisible(percentageSelected);
            exactText.setVisible(!percentageSelected);
            exactSpinner.setVisible(!percentageSelected);

            panel.revalidate();
            panel.repaint();
        };

        modeSelector.addActionListener(e ->
        {
            configManager.setConfiguration(
                    GROUP, modeKey, modeSelector.getSelectedItem());
            updateVisibility.run();
        });

        percentageSpinner.addChangeListener(e ->
                configManager.setConfiguration(
                        GROUP, percentageKey, percentageSpinner.getValue()));

        exactSpinner.addChangeListener(e ->
                configManager.setConfiguration(
                        GROUP, exactKey, exactSpinner.getValue()));

        panel.add(percentageText);
        panel.add(percentageSpinner);
        panel.add(exactText);
        panel.add(exactSpinner);
        panel.add(Box.createVerticalStrut(5));

        updateVisibility.run();
    }

    private void addAfkSection()
    {
        JPanel details = createSection("AFK Monitoring", false);

        addBooleanSetting(details, "Enable logout warning",
                config.logoutWarning(), "logoutWarning");
        addNumberSetting(details, "Logout warning time (seconds)",
                config.logoutWarningSeconds(), 5, 120,
                "logoutWarningSeconds");
        addBooleanSetting(details, "Enable activity monitoring",
                config.activityMonitoring(), "activityMonitoring");
        addNumberSetting(details, "Activity stop delay (seconds)",
                config.activityStopDelay(), 5, 600,
                "activityStopDelay");
    }

    private void addSkillingSection()
    {
        JPanel details = createSection("Skilling", false);

        addBooleanSetting(details, "Enable skilling monitoring",
                config.skillingMonitoring(), "skillingMonitoring");
        addBooleanSetting(details, "Woodcutting",
                config.woodcuttingMonitoring(), "woodcuttingMonitoring");
        addBooleanSetting(details, "Mining",
                config.miningMonitoring(), "miningMonitoring");
        addBooleanSetting(details, "Fishing",
                config.fishingMonitoring(), "fishingMonitoring");
        addBooleanSetting(details, "Cooking",
                config.cookingMonitoring(), "cookingMonitoring");
        addBooleanSetting(details, "Firemaking",
                config.firemakingMonitoring(), "firemakingMonitoring");
        addBooleanSetting(details, "Agility",
                config.agilityMonitoring(), "agilityMonitoring");
        addBooleanSetting(details, "Hunter",
                config.hunterMonitoring(), "hunterMonitoring");
    }

    private void addLevelUpSection()
    {
        JPanel details = createSection("Level-ups", false);

        addBooleanSetting(details, "Enable level-up notifications",
                config.levelUpNotifications(), "levelUpNotifications");
        addBooleanSetting(details, "Play level-up sound",
                config.levelUpSound(), "levelUpSound");
    }

    private void addAppearanceSection()
    {
        JPanel details = createSection("Appearance", false);

        addBooleanSetting(details, "Enable screen flash",
                config.screenFlash(), "screenFlash");
        addNumberSetting(details, "Flash opacity (%)",
                config.flashOpacity(), 1, 100, "flashOpacity");
        addNumberSetting(details, "Flash duration (milliseconds)",
                config.flashDuration(), 100, 5000, "flashDuration");
    }

    private void addAudioSection()
    {
        JPanel details = createSection("Audio", false);

        addBooleanSetting(details, "Enable audio alerts",
                config.audioEnabled(), "audioEnabled");

        details.add(createWrappedLabel("Default alert sound:"));

        JComboBox<AfkHelperConfig.AlertSound> soundSelector =
                new JComboBox<>(AfkHelperConfig.AlertSound.values());
        soundSelector.setSelectedItem(config.defaultSound());
        soundSelector.setMaximumSize(new Dimension(180, 28));
        soundSelector.setAlignmentX(LEFT_ALIGNMENT);

        soundSelector.addActionListener(e ->
                configManager.setConfiguration(
                        GROUP, "defaultSound", soundSelector.getSelectedItem()));

        details.add(soundSelector);
        details.add(Box.createVerticalStrut(8));

        addNumberSetting(details, "Audio volume (%)",
                config.audioVolume(), 0, 100, "audioVolume");
    }

    private void addActivityHighlightingSection()
    {
        JPanel details = createSection("Activity Highlighting", false);

        details.add(createWrappedLabel(
                "Highlight nearby activities you have the level to perform."));

        addBooleanSetting(details, "Enable eligible activity highlighting",
                config.eligibleActivityHighlightingEnabled(),
                "eligibleActivityHighlightingEnabled");
    }

    private JPanel createSection(String title, boolean expanded)
    {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setAlignmentX(LEFT_ALIGNMENT);
        section.setBorder(BorderFactory.createEtchedBorder());
        section.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JButton heading = new JButton(title);
        heading.setHorizontalAlignment(JButton.LEFT);
        heading.setAlignmentX(LEFT_ALIGNMENT);
        heading.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setBorder(new EmptyBorder(6, 8, 8, 8));
        details.setAlignmentX(LEFT_ALIGNMENT);
        details.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        details.setVisible(expanded);

        heading.addActionListener(e ->
        {
            details.setVisible(!details.isVisible());
            section.revalidate();
            contentPanel.revalidate();
            contentPanel.repaint();
        });

        section.add(heading);
        section.add(details);

        contentPanel.add(section);
        contentPanel.add(Box.createVerticalStrut(6));

        return details;
    }

    private JCheckBox createWrappedCheckbox(
            String label,
            boolean selected)
    {
        JCheckBox checkbox = new JCheckBox();
        checkbox.putClientProperty("wrappedText", label);
        checkbox.setSelected(selected);
        checkbox.setAlignmentX(LEFT_ALIGNMENT);
        checkbox.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        checkbox.putClientProperty(
                "npcName",
                label.replaceFirst(" \\(\\d+ tiles\\)$", ""));

        wrappedCheckboxes.add(checkbox);
        updateCheckboxHtml(checkbox);

        return checkbox;
    }

    private void updateCheckboxHtml(JCheckBox checkbox)
    {
        Object value = checkbox.getClientProperty("wrappedText");

        if (value == null)
        {
            return;
        }

        checkbox.setText(
                "<html><body style='width:" + wrapWidth + "px'>"
                        + escapeHtml(value.toString())
                        + "</body></html>");
    }

    private void addBooleanSetting(
            JPanel panel,
            String label,
            boolean initialValue,
            String key)
    {
        JCheckBox checkbox = createWrappedCheckbox(label, initialValue);

        checkbox.addActionListener(e ->
                configManager.setConfiguration(
                        GROUP, key, checkbox.isSelected()));

        panel.add(checkbox);
    }

    private void addNumberSetting(
            JPanel panel,
            String label,
            int initialValue,
            int minimum,
            int maximum,
            String key)
    {
        panel.add(createWrappedLabel(label));

        JSpinner spinner = new JSpinner(
                new SpinnerNumberModel(initialValue, minimum, maximum, 1));
        spinner.setMaximumSize(new Dimension(120, 28));
        spinner.setAlignmentX(LEFT_ALIGNMENT);

        spinner.addChangeListener(e ->
                configManager.setConfiguration(
                        GROUP, key, spinner.getValue()));

        panel.add(spinner);
        panel.add(Box.createVerticalStrut(5));
    }

    private String htmlText(String text)
    {
        return "<html><body style='width:" + wrapWidth + "px'>"
                + escapeHtml(text)
                + "</body></html>";
    }
}