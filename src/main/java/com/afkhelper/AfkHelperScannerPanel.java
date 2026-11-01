
package com.afkhelper;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;

public class AfkHelperScannerPanel extends PluginPanel
{
    private static final String GROUP = "afkhelper";

    private final AfkHelperConfig config;
    private final ConfigManager configManager;
    private final Client client;
    private final ClientThread clientThread;

    private final JPanel contentPanel = new JPanel();
    private final JPanel npcListPanel = new JPanel();

    private final JLabel scanStatus =
            new JLabel("Press Scan to find nearby NPCs.");

    private final List<JCheckBox> npcCheckboxes = new ArrayList<>();

    private JLabel savedLabel;

    public AfkHelperScannerPanel(
            AfkHelperConfig config,
            ConfigManager configManager,
            Client client,
            ClientThread clientThread)
    {
        this.config = config;
        this.configManager = configManager;
        this.client = client;
        this.clientThread = clientThread;

        setLayout(new BorderLayout());

        contentPanel.setLayout(
                new BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        );
        contentPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("AFK HELPER");
        title.setAlignmentX(LEFT_ALIGNMENT);

        contentPanel.add(title);
        contentPanel.add(Box.createVerticalStrut(12));

        addScannerSection();
        addCombatSection();
        addHealthSection();
        addAfkSection();
        addSkillingSection();
        addLevelUpSection();
        addAppearanceSection();
        addAudioSection();

        add(contentPanel, BorderLayout.NORTH);
    }

    private void addScannerSection()
    {
        JPanel details = createSection("Monster Scanner", true);

        details.add(new JLabel(
                "Scan nearby NPCs and select monsters to save."
        ));

        JButton scanButton = new JButton("Scan nearby NPCs");
        scanButton.setAlignmentX(LEFT_ALIGNMENT);
        scanButton.addActionListener(e -> scanNearbyNpcs());

        details.add(Box.createVerticalStrut(8));
        details.add(scanButton);
        details.add(Box.createVerticalStrut(6));

        addNumberSetting(
                details,
                "Monster scan radius (tiles)",
                config.monsterScanRadius(),
                5,
                30,
                "monsterScanRadius"
        );

        scanStatus.setAlignmentX(LEFT_ALIGNMENT);
        details.add(scanStatus);
        details.add(Box.createVerticalStrut(6));

        npcListPanel.setLayout(
                new BoxLayout(npcListPanel, BoxLayout.Y_AXIS)
        );
        npcListPanel.setAlignmentX(LEFT_ALIGNMENT);
        npcListPanel.setBorder(
                BorderFactory.createTitledBorder("Nearby NPCs")
        );

        details.add(npcListPanel);

        JButton saveButton = new JButton("Save selected monsters");
        saveButton.setAlignmentX(LEFT_ALIGNMENT);
        saveButton.addActionListener(e -> saveSelectedMonsters());

        details.add(Box.createVerticalStrut(8));
        details.add(saveButton);
        details.add(Box.createVerticalStrut(6));
        details.add(new JLabel("Saved monsters:"));

        savedLabel = new JLabel(getSavedMonsterText());
        savedLabel.setAlignmentX(LEFT_ALIGNMENT);
        details.add(savedLabel);
    }

    private String getSavedMonsterText()
    {
        String saved = config.savedMonsterNames();

        return saved == null || saved.trim().isEmpty()
                ? "None"
                : saved;
    }

    private void scanNearbyNpcs()
    {
        scanStatus.setText("Scanning...");
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
                    scanStatus.setText("Please log in before scanning.");
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
                if (npc == null || npc.getName() == null
                        || npc.getName().trim().isEmpty())
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

                    if (previousDistance == null
                            || distance < previousDistance)
                    {
                        nearbyNpcs.put(name, distance);
                    }
                }
            }

            SwingUtilities.invokeLater(() ->
                    displayNearbyNpcs(nearbyNpcs)
            );
        });
    }

    private void displayNearbyNpcs(Map<String, Integer> nearbyNpcs)
    {
        npcListPanel.removeAll();
        npcCheckboxes.clear();

        if (nearbyNpcs.isEmpty())
        {
            npcListPanel.add(new JLabel("No NPCs found nearby."));
            scanStatus.setText("Scan complete: no NPCs found.");
        }
        else
        {
            Set<String> savedNames = getSavedMonsterSet();

            for (Map.Entry<String, Integer> entry : nearbyNpcs.entrySet())
            {
                String name = entry.getKey();
                int distance = entry.getValue();

                JCheckBox checkbox = new JCheckBox(
                        name + " (" + distance + " tiles)",
                        savedNames.contains(name)
                );

                checkbox.setAlignmentX(LEFT_ALIGNMENT);

                npcCheckboxes.add(checkbox);
                npcListPanel.add(checkbox);
            }

            scanStatus.setText(
                    "Scan complete: "
                            + nearbyNpcs.size()
                            + " different NPC names found."
            );
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
                String label = checkbox.getText();
                int distanceStart = label.lastIndexOf(" (");

                String name = distanceStart >= 0
                        ? label.substring(0, distanceStart)
                        : label;

                selectedNames.add(name);
            }
        }

        String savedNames = String.join(", ", selectedNames);

        configManager.setConfiguration(
                GROUP,
                "savedMonsterNames",
                savedNames
        );

        savedLabel.setText(
                selectedNames.isEmpty() ? "None" : savedNames
        );

        scanStatus.setText(
                selectedNames.size() + " monster names saved."
        );

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

        addThresholdSetting(
                details,
                "HP threshold type",
                config.healthThresholdMode(),
                "healthThresholdMode",
                "HP percentage threshold",
                config.playerHealthThreshold(),
                1, 99,
                "playerHealthThreshold",
                "Exact HP threshold",
                config.healthExactThreshold(),
                1, 200,
                "healthExactThreshold"
        );

        details.add(Box.createVerticalStrut(8));

        addBooleanSetting(details, "Enable Prayer warnings",
                config.prayerMonitoring(), "prayerMonitoring");

        addThresholdSetting(
                details,
                "Prayer threshold type",
                config.prayerThresholdMode(),
                "prayerThresholdMode",
                "Prayer percentage threshold",
                config.prayerThreshold(),
                1, 99,
                "prayerThreshold",
                "Exact Prayer threshold",
                config.prayerExactThreshold(),
                1, 200,
                "prayerExactThreshold"
        );

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
        JLabel modeText = new JLabel(modeLabel);
        modeText.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(modeText);

        JComboBox<AfkHelperConfig.ThresholdMode> modeSelector =
                new JComboBox<>(AfkHelperConfig.ThresholdMode.values());

        modeSelector.setSelectedItem(initialMode);
        modeSelector.setMaximumSize(new Dimension(180, 28));
        modeSelector.setAlignmentX(LEFT_ALIGNMENT);

        panel.add(modeSelector);
        panel.add(Box.createVerticalStrut(5));

        JLabel percentageText = new JLabel(percentageLabel);
        percentageText.setAlignmentX(LEFT_ALIGNMENT);

        JSpinner percentageSpinner = new JSpinner(
                new SpinnerNumberModel(
                        percentageValue,
                        percentageMinimum,
                        percentageMaximum,
                        1
                )
        );
        percentageSpinner.setMaximumSize(new Dimension(120, 28));
        percentageSpinner.setAlignmentX(LEFT_ALIGNMENT);

        JLabel exactText = new JLabel(exactLabel);
        exactText.setAlignmentX(LEFT_ALIGNMENT);

        JSpinner exactSpinner = new JSpinner(
                new SpinnerNumberModel(
                        exactValue,
                        exactMinimum,
                        exactMaximum,
                        1
                )
        );
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
                    GROUP,
                    modeKey,
                    modeSelector.getSelectedItem()
            );

            updateVisibility.run();
        });

        percentageSpinner.addChangeListener(e ->
                configManager.setConfiguration(
                        GROUP,
                        percentageKey,
                        percentageSpinner.getValue()
                )
        );

        exactSpinner.addChangeListener(e ->
                configManager.setConfiguration(
                        GROUP,
                        exactKey,
                        exactSpinner.getValue()
                )
        );

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

        JLabel soundLabel = new JLabel("Default alert sound:");
        soundLabel.setAlignmentX(LEFT_ALIGNMENT);
        details.add(soundLabel);

        JComboBox<AfkHelperConfig.AlertSound> soundSelector =
                new JComboBox<>(AfkHelperConfig.AlertSound.values());

        soundSelector.setSelectedItem(config.defaultSound());
        soundSelector.setMaximumSize(new Dimension(180, 28));
        soundSelector.setAlignmentX(LEFT_ALIGNMENT);

        soundSelector.addActionListener(e ->
                configManager.setConfiguration(
                        GROUP,
                        "defaultSound",
                        soundSelector.getSelectedItem()
                )
        );

        details.add(soundSelector);
        details.add(Box.createVerticalStrut(8));

        addNumberSetting(details, "Audio volume (%)",
                config.audioVolume(), 0, 100, "audioVolume");
    }

    private JPanel createSection(String title, boolean expanded)
    {
        JPanel section = new JPanel();
        section.setLayout(
                new BoxLayout(section, BoxLayout.Y_AXIS)
        );
        section.setAlignmentX(LEFT_ALIGNMENT);
        section.setBorder(BorderFactory.createEtchedBorder());

        JButton heading = new JButton(title);
        heading.setHorizontalAlignment(JButton.LEFT);
        heading.setAlignmentX(LEFT_ALIGNMENT);
        heading.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 32)
        );

        JPanel details = new JPanel();
        details.setLayout(
                new BoxLayout(details, BoxLayout.Y_AXIS)
        );
        details.setBorder(new EmptyBorder(6, 8, 8, 8));
        details.setAlignmentX(LEFT_ALIGNMENT);
        details.setVisible(expanded);

        heading.addActionListener(e ->
        {
            details.setVisible(!details.isVisible());
            revalidate();
            repaint();
        });

        section.add(heading);
        section.add(details);

        contentPanel.add(section);
        contentPanel.add(Box.createVerticalStrut(6));

        return details;
    }

    private void addBooleanSetting(
            JPanel panel,
            String label,
            boolean initialValue,
            String key)
    {
        JCheckBox checkbox = new JCheckBox(label, initialValue);
        checkbox.setAlignmentX(LEFT_ALIGNMENT);

        checkbox.addActionListener(e ->
                configManager.setConfiguration(
                        GROUP,
                        key,
                        checkbox.isSelected()
                )
        );

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
        JLabel settingLabel = new JLabel(label);
        settingLabel.setAlignmentX(LEFT_ALIGNMENT);

        JSpinner spinner = new JSpinner(
                new SpinnerNumberModel(
                        initialValue,
                        minimum,
                        maximum,
                        1
                )
        );

        spinner.setMaximumSize(new Dimension(120, 28));
        spinner.setAlignmentX(LEFT_ALIGNMENT);

        spinner.addChangeListener(e ->
                configManager.setConfiguration(
                        GROUP,
                        key,
                        spinner.getValue()
                )
        );

        panel.add(settingLabel);
        panel.add(spinner);
        panel.add(Box.createVerticalStrut(5));
    }
}
