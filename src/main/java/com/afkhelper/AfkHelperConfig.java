package com.afkhelper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("afkhelper")
public interface AfkHelperConfig extends Config
{
	enum AlertSound
	{
		BEEP,
		DOUBLE_BEEP,
		BELL,
		CHIME,
		SIREN
	}

	enum ThresholdMode
	{
		PERCENTAGE,
		EXACT_VALUE
	}

	@ConfigSection(name = "General", description = "General AFK Helper settings", position = 0)
	String generalSection = "general";

	@ConfigSection(name = "Combat", description = "Monster and combat monitoring", position = 1)
	String combatSection = "combat";

	@ConfigSection(name = "Player Health", description = "Player hitpoints and prayer warnings", position = 2)
	String healthSection = "health";

	@ConfigSection(name = "AFK Monitoring", description = "Inactivity and logout warnings", position = 3)
	String afkSection = "afk";

	@ConfigSection(name = "Skilling", description = "Skilling inactivity monitoring", position = 4)
	String skillingSection = "skilling";

	@ConfigSection(name = "Level-ups", description = "Skill level-up notifications", position = 5)
	String levelSection = "levels";

	@ConfigSection(name = "Appearance", description = "Visual alert settings", position = 6)
	String appearanceSection = "appearance";

	@ConfigSection(name = "Audio", description = "Sound and notification settings", position = 7)
	String audioSection = "audio";

	@ConfigSection(name = "Session Tracker", description = "Session statistics overlay settings", position = 8)
	String sessionSection = "session";

	@ConfigSection(name = "Activity Highlighting", description = "Highlight nearby eligible skilling activities", position = 9)
	String highlightingSection = "highlighting";

	@ConfigSection(name = "Supplies and Profit", description = "Track consumables, costs and estimated profit", position = 10)
	String suppliesSection = "supplies";

	// GENERAL

	@ConfigItem(keyName = "enabled", name = "Enable AFK Helper", description = "Master switch", position = 0, section = generalSection)
	default boolean enabled() { return true; }

	@ConfigItem(keyName = "notificationsEnabled", name = "Enable notifications", description = "Allow notifications", position = 1, section = generalSection)
	default boolean notificationsEnabled() { return true; }

	@ConfigItem(keyName = "windowsNotifications", name = "Windows notifications", description = "Use RuneLite desktop notifications", position = 2, section = generalSection)
	default boolean windowsNotifications() { return true; }

	@ConfigItem(keyName = "notificationCooldown", name = "Notification cooldown", description = "Seconds between repeated notifications", position = 3, section = generalSection)
	@Range(min = 0, max = 300)
	default int notificationCooldown() { return 5; }

	// COMBAT

	@ConfigItem(keyName = "monsterMonitoring", name = "Enable monster monitoring", description = "Monitor monsters you fight", position = 0, section = combatSection)
	default boolean monsterMonitoring() { return true; }

	@ConfigItem(keyName = "automaticMonsterTracking", name = "Automatic monster tracking", description = "Automatically track monsters you attack", position = 1, section = combatSection)
	default boolean automaticMonsterTracking() { return true; }

	@ConfigItem(keyName = "monsterScanRadius", name = "Monster scan radius", description = "Distance in tiles to scan for nearby monsters", position = 2, section = combatSection)
	@Range(min = 5, max = 30)
	default int monsterScanRadius() { return 10; }

	@ConfigItem(keyName = "savedMonsterNames", name = "Saved monsters", description = "Saved monster names selected for monitoring", position = 3, section = combatSection)
	default String savedMonsterNames() { return ""; }

	@ConfigItem(keyName = "monsterHealthThreshold", name = "Monster health threshold", description = "Alert at or below this health percentage", position = 4, section = combatSection)
	@Range(min = 1, max = 100)
	default int monsterHealthThreshold() { return 20; }

	@ConfigItem(keyName = "monsterAlertOnce", name = "Alert once per target", description = "Avoid repeating alerts for the same target", position = 5, section = combatSection)
	default boolean monsterAlertOnce() { return true; }

	@ConfigItem(keyName = "combatPerformanceTracking", name = "Combat performance tracking", description = "Track damage, kill rates and kill times", position = 6, section = combatSection)
	default boolean combatPerformanceTracking() { return true; }

	@ConfigItem(keyName = "monsterHealthDisplay", name = "Show target health", description = "Display current target health where available", position = 7, section = combatSection)
	default boolean monsterHealthDisplay() { return true; }

	// PLAYER HEALTH

	@ConfigItem(keyName = "playerHealthMonitoring", name = "Enable HP warnings", description = "Warn when hitpoints are low", position = 0, section = healthSection)
	default boolean playerHealthMonitoring() { return true; }

	@ConfigItem(keyName = "healthThresholdMode", name = "HP threshold type", description = "Choose percentage or exact HP value", position = 1, section = healthSection)
	default ThresholdMode healthThresholdMode() { return ThresholdMode.PERCENTAGE; }

	@ConfigItem(keyName = "playerHealthThreshold", name = "HP warning threshold (%)", description = "Hitpoint percentage warning level", position = 2, section = healthSection)
	@Range(min = 1, max = 99)
	default int playerHealthThreshold() { return 20; }

	@ConfigItem(keyName = "healthExactThreshold", name = "Exact HP threshold", description = "Alert at or below this Hitpoints value", position = 3, section = healthSection)
	@Range(min = 1, max = 200)
	default int healthExactThreshold() { return 20; }

	@ConfigItem(keyName = "prayerMonitoring", name = "Enable Prayer warnings", description = "Warn when Prayer is low", position = 4, section = healthSection)
	default boolean prayerMonitoring() { return false; }

	@ConfigItem(keyName = "prayerThresholdMode", name = "Prayer threshold type", description = "Choose percentage or exact Prayer value", position = 5, section = healthSection)
	default ThresholdMode prayerThresholdMode() { return ThresholdMode.PERCENTAGE; }

	@ConfigItem(keyName = "prayerThreshold", name = "Prayer warning threshold (%)", description = "Prayer percentage warning level", position = 6, section = healthSection)
	@Range(min = 1, max = 99)
	default int prayerThreshold() { return 10; }

	@ConfigItem(keyName = "prayerExactThreshold", name = "Exact Prayer threshold", description = "Alert at or below this Prayer value", position = 7, section = healthSection)
	@Range(min = 1, max = 200)
	default int prayerExactThreshold() { return 10; }

	@ConfigItem(keyName = "repeatHealthWarnings", name = "Repeat health warnings", description = "Repeat warnings while health remains low", position = 8, section = healthSection)
	default boolean repeatHealthWarnings() { return false; }

	// AFK MONITORING

	@ConfigItem(keyName = "logoutWarning", name = "Enable logout warning", description = "Warn before expected idle logout", position = 0, section = afkSection)
	default boolean logoutWarning() { return true; }

	@ConfigItem(keyName = "logoutWarningSeconds", name = "Logout warning time", description = "Seconds before expected logout", position = 1, section = afkSection)
	@Range(min = 5, max = 120)
	default int logoutWarningSeconds() { return 30; }

	@ConfigItem(keyName = "activityMonitoring", name = "Enable activity monitoring", description = "Monitor supported activities", position = 2, section = afkSection)
	default boolean activityMonitoring() { return true; }

	@ConfigItem(keyName = "activityStopDelay", name = "Activity stop delay", description = "Seconds without detected activity", position = 3, section = afkSection)
	@Range(min = 5, max = 600)
	default int activityStopDelay() { return 30; }

	@ConfigItem(keyName = "afkCountdownEnabled", name = "Enable AFK countdown", description = "Display time since activity and expected logout countdown", position = 4, section = afkSection)
	default boolean afkCountdownEnabled() { return true; }

	@ConfigItem(keyName = "afkCountdownWarningSeconds", name = "Countdown warning time", description = "Warn this many seconds before expected logout", position = 5, section = afkSection)
	@Range(min = 5, max = 120)
	default int afkCountdownWarningSeconds() { return 60; }

	@ConfigItem(keyName = "idleWarningEnabled", name = "Warn when activity stops", description = "Notify when a monitored activity stops", position = 6, section = afkSection)
	default boolean idleWarningEnabled() { return true; }

	// SKILLING

	@ConfigItem(keyName = "skillingMonitoring", name = "Enable skilling monitoring", description = "Monitor supported skilling activities", position = 0, section = skillingSection)
	default boolean skillingMonitoring() { return true; }

	@ConfigItem(keyName = "woodcuttingMonitoring", name = "Woodcutting", description = "Monitor woodcutting", position = 1, section = skillingSection)
	default boolean woodcuttingMonitoring() { return true; }

	@ConfigItem(keyName = "miningMonitoring", name = "Mining", description = "Monitor mining", position = 2, section = skillingSection)
	default boolean miningMonitoring() { return true; }

	@ConfigItem(keyName = "fishingMonitoring", name = "Fishing", description = "Monitor fishing", position = 3, section = skillingSection)
	default boolean fishingMonitoring() { return true; }

	@ConfigItem(keyName = "cookingMonitoring", name = "Cooking", description = "Monitor cooking", position = 4, section = skillingSection)
	default boolean cookingMonitoring() { return true; }

	@ConfigItem(keyName = "firemakingMonitoring", name = "Firemaking", description = "Monitor firemaking", position = 5, section = skillingSection)
	default boolean firemakingMonitoring() { return true; }

	@ConfigItem(keyName = "agilityMonitoring", name = "Agility", description = "Monitor agility", position = 6, section = skillingSection)
	default boolean agilityMonitoring() { return true; }

	@ConfigItem(keyName = "hunterMonitoring", name = "Hunter", description = "Monitor hunter", position = 7, section = skillingSection)
	default boolean hunterMonitoring() { return true; }

	// LEVEL-UPS

	@ConfigItem(keyName = "levelUpNotifications", name = "Enable level-up notifications", description = "Notify when a skill levels up", position = 0, section = levelSection)
	default boolean levelUpNotifications() { return true; }

	@ConfigItem(keyName = "levelUpSound", name = "Level-up sound", description = "Play a sound for level-ups", position = 1, section = levelSection)
	default boolean levelUpSound() { return true; }

	// APPEARANCE

	@ConfigItem(keyName = "screenFlash", name = "Enable screen flash", description = "Display a visual alert over the RuneLite game area", position = 0, section = appearanceSection)
	default boolean screenFlash() { return true; }

	@ConfigItem(keyName = "flashOpacity", name = "Flash opacity", description = "Flash opacity percentage", position = 1, section = appearanceSection)
	@Range(min = 1, max = 100)
	default int flashOpacity() { return 50; }

	@ConfigItem(keyName = "flashDuration", name = "Flash duration", description = "Flash duration in milliseconds", position = 2, section = appearanceSection)
	@Range(min = 100, max = 5000)
	default int flashDuration() { return 1000; }

	// AUDIO

	@ConfigItem(keyName = "audioEnabled", name = "Enable audio alerts", description = "Play alert sounds", position = 0, section = audioSection)
	default boolean audioEnabled() { return true; }

	@ConfigItem(keyName = "defaultSound", name = "Default sound", description = "Choose the default alert sound", position = 1, section = audioSection)
	default AlertSound defaultSound() { return AlertSound.BEEP; }

	@ConfigItem(keyName = "audioVolume", name = "Audio volume", description = "Default alert volume percentage", position = 2, section = audioSection)
	@Range(min = 0, max = 100)
	default int audioVolume() { return 70; }

	@ConfigItem(keyName = "separateAlertSounds", name = "Separate alert sounds", description = "Use different sounds for different alert types", position = 3, section = audioSection)
	default boolean separateAlertSounds() { return false; }

	@ConfigItem(keyName = "idleAlertSound", name = "Idle alert sound", description = "Sound used for inactivity warnings", position = 4, section = audioSection)
	default AlertSound idleAlertSound() { return AlertSound.BEEP; }

	@ConfigItem(keyName = "combatAlertSound", name = "Combat alert sound", description = "Sound used for combat warnings", position = 5, section = audioSection)
	default AlertSound combatAlertSound() { return AlertSound.DOUBLE_BEEP; }

	@ConfigItem(keyName = "healthAlertSound", name = "Health alert sound", description = "Sound used for HP and Prayer warnings", position = 6, section = audioSection)
	default AlertSound healthAlertSound() { return AlertSound.SIREN; }

	// SESSION TRACKER

	@ConfigItem(keyName = "sessionOverlayEnabled", name = "Show Session Tracker", description = "Master switch for the session statistics overlay", position = 0, section = sessionSection)
	default boolean sessionOverlayEnabled() { return true; }

	@ConfigItem(keyName = "sessionOverlayOpacity", name = "Overlay opacity", description = "Transparency level of the session overlay", position = 1, section = sessionSection)
	@Range(min = 10, max = 100)
	default int sessionOverlayOpacity() { return 80; }

	@ConfigItem(keyName = "saveSessionStatistics", name = "Save session statistics", description = "Keep session statistics when logging out and back in", position = 2, section = sessionSection)
	default boolean saveSessionStatistics() { return false; }

	@ConfigItem(keyName = "overlayShowDuration", name = "Show session duration", description = "Display elapsed session time", position = 3, section = sessionSection)
	default boolean overlayShowDuration() { return true; }

	@ConfigItem(keyName = "overlayShowXpGained", name = "Show XP gained", description = "Display experience gained this session", position = 4, section = sessionSection)
	default boolean overlayShowXpGained() { return true; }

	@ConfigItem(keyName = "overlayShowXpPerHour", name = "Show XP per hour", description = "Display experience gained per hour", position = 5, section = sessionSection)
	default boolean overlayShowXpPerHour() { return true; }

	@ConfigItem(keyName = "overlayShowActions", name = "Show actions", description = "Display tracked actions", position = 6, section = sessionSection)
	default boolean overlayShowActions() { return true; }

	@ConfigItem(keyName = "overlayShowKills", name = "Show kills", description = "Display monsters killed", position = 7, section = sessionSection)
	default boolean overlayShowKills() { return true; }

	@ConfigItem(keyName = "overlayShowKillsPerHour", name = "Show kills per hour", description = "Display kill rate", position = 8, section = sessionSection)
	default boolean overlayShowKillsPerHour() { return true; }

	@ConfigItem(keyName = "overlayShowDamage", name = "Show damage", description = "Display total damage dealt", position = 9, section = sessionSection)
	default boolean overlayShowDamage() { return true; }

	@ConfigItem(keyName = "overlayShowDamagePerHour", name = "Show damage per hour", description = "Display hourly damage rate", position = 10, section = sessionSection)
	default boolean overlayShowDamagePerHour() { return true; }

	@ConfigItem(keyName = "overlayShowAverageKillTime", name = "Show average kill time", description = "Display average monster kill duration", position = 11, section = sessionSection)
	default boolean overlayShowAverageKillTime() { return true; }

	@ConfigItem(keyName = "overlayShowAfkStatus", name = "Show AFK status", description = "Display current inactivity status", position = 12, section = sessionSection)
	default boolean overlayShowAfkStatus() { return true; }

	@ConfigItem(keyName = "overlayShowLogoutCountdown", name = "Show logout countdown", description = "Display estimated time until idle logout", position = 13, section = sessionSection)
	default boolean overlayShowLogoutCountdown() { return true; }

	@ConfigItem(keyName = "overlayShowCombatStatus", name = "Show combat status", description = "Display current combat target or status", position = 14, section = sessionSection)
	default boolean overlayShowCombatStatus() { return true; }

	@ConfigItem(keyName = "overlayShowSuppliesUsed", name = "Show supplies used", description = "Display tracked supplies consumed", position = 15, section = sessionSection)
	default boolean overlayShowSuppliesUsed() { return true; }

	@ConfigItem(keyName = "overlayShowItemsGained", name = "Show items gained", description = "Display tracked inventory gains", position = 16, section = sessionSection)
	default boolean overlayShowItemsGained() { return true; }

	@ConfigItem(keyName = "overlayShowEstimatedProfit", name = "Show estimated profit", description = "Display estimated session profit or loss", position = 17, section = sessionSection)
	default boolean overlayShowEstimatedProfit() { return true; }

	@ConfigItem(keyName = "overlayShowSupplyCost", name = "Show supply cost", description = "Display total consumable cost", position = 18, section = sessionSection)
	default boolean overlayShowSupplyCost() { return true; }

	@ConfigItem(keyName = "overlayShowSupplyCostPerHour", name = "Show supply cost per hour", description = "Display hourly consumable cost", position = 19, section = sessionSection)
	default boolean overlayShowSupplyCostPerHour() { return true; }

	@ConfigItem(keyName = "overlayShowCombatCost", name = "Show combat cost", description = "Display total ammunition and rune cost", position = 20, section = sessionSection)
	default boolean overlayShowCombatCost() { return true; }

	@ConfigItem(keyName = "overlayShowCombatCostPerHour", name = "Show combat cost per hour", description = "Display hourly ammunition and rune cost", position = 21, section = sessionSection)
	default boolean overlayShowCombatCostPerHour() { return true; }

	// ACTIVITY HIGHLIGHTING

	@ConfigItem(keyName = "eligibleActivityHighlightingEnabled", name = "Enable eligible activity highlighting", description = "Highlight recognised skilling activities when your level meets their requirement", position = 0, section = highlightingSection)
	default boolean eligibleActivityHighlightingEnabled() { return true; }

	// SUPPLIES AND PROFIT

	@ConfigItem(keyName = "supplyTrackingEnabled", name = "Enable supply tracking", description = "Track consumable items used during the session", position = 0, section = suppliesSection)
	default boolean supplyTrackingEnabled() { return true; }

	@ConfigItem(keyName = "profitTrackingEnabled", name = "Enable profit tracking", description = "Calculate estimated item costs and output value using Grand Exchange prices", position = 1, section = suppliesSection)
	default boolean profitTrackingEnabled() { return true; }

	@ConfigItem(keyName = "showSupplyOverlay", name = "Show supply and profit statistics", description = "Display consumable costs and estimated profit or loss in the session overlay", position = 2, section = suppliesSection)
	default boolean showSupplyOverlay() { return true; }

	@ConfigItem(keyName = "showCostPerHour", name = "Show cost or profit per hour", description = "Display estimated hourly consumable costs or skilling profit", position = 3, section = suppliesSection)
	default boolean showCostPerHour() { return true; }

	@ConfigItem(keyName = "trackCombatAmmoAndRunes", name = "Track ammunition and runes", description = "Estimate ammunition and rune costs for the current combat style", position = 4, section = suppliesSection)
	default boolean trackCombatAmmoAndRunes() { return true; }

	@ConfigItem(keyName = "trackSkillingMaterials", name = "Track skilling materials", description = "Estimate consumed materials and output value for supported skills", position = 5, section = suppliesSection)
	default boolean trackSkillingMaterials() { return true; }
}