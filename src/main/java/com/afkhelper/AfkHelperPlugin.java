
package com.afkhelper;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.AnimationID;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@Slf4j
@PluginDescriptor(
		name = "AFK Helper",
		description = "Customisable combat, health, AFK and skilling alerts"
)
public class AfkHelperPlugin extends Plugin
{
	@Inject
	private AfkHelperConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private Notifier notifier;

	private AfkHelperScannerPanel scannerPanel;
	private NavigationButton navigationButton;

	private boolean wasHealthLow;
	private boolean wasPrayerLow;
	private boolean logoutWarningSent;

	private long lastHealthAlert;
	private long lastPrayerAlert;

	private final Set<Integer> alertedMonsterIndices = new HashSet<>();
	private final Map<Integer, Long> lastMonsterAlerts = new HashMap<>();

	private final Map<Skill, Long> lastSkillingActivity = new HashMap<>();
	private final Set<Skill> skillingWarningsSent = new HashSet<>();

	@Override
	protected void startUp()
	{
		scannerPanel = new AfkHelperScannerPanel(
				config,
				configManager,
				client,
				clientThread
		);

		BufferedImage icon = createPanelIcon();

		navigationButton = NavigationButton.builder()
				.tooltip("AFK Helper")
				.icon(icon)
				.priority(10)
				.panel(scannerPanel)
				.build();

		clientToolbar.addNavigation(navigationButton);

		wasHealthLow = false;
		wasPrayerLow = false;
		logoutWarningSent = false;
		lastHealthAlert = 0;
		lastPrayerAlert = 0;

		alertedMonsterIndices.clear();
		lastMonsterAlerts.clear();
		lastSkillingActivity.clear();
		skillingWarningsSent.clear();

		log.info("AFK Helper started!");
	}

	@Override
	protected void shutDown()
	{
		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
			navigationButton = null;
		}

		scannerPanel = null;

		alertedMonsterIndices.clear();
		lastMonsterAlerts.clear();
		lastSkillingActivity.clear();
		skillingWarningsSent.clear();

		log.info("AFK Helper stopped!");
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (!config.enabled()
				|| !config.skillingMonitoring()
				|| !config.activityMonitoring()
				|| client.getGameState() != GameState.LOGGED_IN
				|| client.getLocalPlayer() == null
				|| event.getActor() != client.getLocalPlayer())
		{
			return;
		}

		Skill skill = getSkillingSkillForAnimation(
				client.getLocalPlayer().getAnimation()
		);

		if (skill != null && isSkillEnabled(skill))
		{
			lastSkillingActivity.put(skill, System.currentTimeMillis());
			skillingWarningsSent.remove(skill);
		}
	}

	private Skill getSkillingSkillForAnimation(int animation)
	{
		if (contains(animation,
				879, 877, 875, 873, 871, 869, 867,
				8303, 2846, 24, 2117, 7264, 8324,
				8778, 10064, 10065, 10066, 10067,
				10068, 10069, 10070, 10071, 10072,
				10073, 10074, 3291, 3290, 3289, 3288,
				3287, 3286, 3285, 8305, 3292, 23,
				2116, 8777, 7266, 8323, 8327, 8780,
				10517, 10518, 10519, 10520, 10521,
				10522, 10523, 10524, 10525, 10526, 10527))
		{
			return Skill.WOODCUTTING;
		}

		if (contains(animation,
				625, 626, 627, 3873, 629, 628, 624,
				8313, 7139, 642, 8346, 8887, 4482,
				7283, 8347, 8787, 8788, 8789,
				6753, 6754, 6755, 3866, 6757, 6756,
				6752, 8312, 6758, 335, 8344, 8886,
				4481, 7282, 8345, 8786, 6747, 6748,
				6749, 6108, 6751, 6750, 6746, 8314,
				7140, 643, 8349, 8888, 4483, 7284, 8350))
		{
			return Skill.MINING;
		}

		if (contains(animation,
				620, 621, 623, 619, 618, 5108, 7401,
				88, 7402, 8336, 8784, 622, 1193, 7553,
				8969, 7151, 6709, 6703, 6704, 6705,
				6706, 6707, 6708, 6710, 6711, 8188,
				8189, 8190, 8191, 8192, 8193, 6932, 9350))
		{
			return Skill.FISHING;
		}

		if (contains(animation, 897, 896, 7529))
		{
			return Skill.COOKING;
		}

		if (contains(animation,
				733, 10563, 10564, 10565, 10566,
				10567, 10568, 10569, 10570, 10571,
				10572, 10573))
		{
			return Skill.FIREMAKING;
		}

		if (contains(animation,
				5208, 5215, 7259, 5207, 5212))
		{
			return Skill.HUNTER;
		}

		return null;
	}

	private boolean contains(int value, int... values)
	{
		for (int candidate : values)
		{
			if (candidate == value)
			{
				return true;
			}
		}

		return false;
	}

	private boolean isSkillEnabled(Skill skill)
	{
		switch (skill)
		{
			case WOODCUTTING:
				return config.woodcuttingMonitoring();
			case MINING:
				return config.miningMonitoring();
			case FISHING:
				return config.fishingMonitoring();
			case COOKING:
				return config.cookingMonitoring();
			case FIREMAKING:
				return config.firemakingMonitoring();
			case HUNTER:
				return config.hunterMonitoring();
			case AGILITY:
				return config.agilityMonitoring();
			default:
				return false;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!config.enabled()
				|| !config.notificationsEnabled()
				|| client.getGameState() != GameState.LOGGED_IN)
		{
			wasHealthLow = false;
			wasPrayerLow = false;
			logoutWarningSent = false;
			return;
		}

		checkHealth();
		checkPrayer();
		checkMonsterHealth();
		checkLogoutWarning();
		checkSkillingInactivity();
	}

	private void checkSkillingInactivity()
	{
		if (!config.skillingMonitoring() || !config.activityMonitoring())
		{
			return;
		}

		long now = System.currentTimeMillis();
		long delay = config.activityStopDelay() * 1000L;

		Skill[] skills = {
				Skill.WOODCUTTING,
				Skill.MINING,
				Skill.FISHING,
				Skill.COOKING,
				Skill.FIREMAKING,
				Skill.HUNTER
		};

		for (Skill skill : skills)
		{
			if (!isSkillEnabled(skill))
			{
				skillingWarningsSent.remove(skill);
				continue;
			}

			Long lastActivity = lastSkillingActivity.get(skill);

			if (lastActivity == null)
			{
				continue;
			}

			if (now - lastActivity >= delay
					&& !skillingWarningsSent.contains(skill))
			{
				sendAlert(
						"AFK Helper: No " + skill.getName()
								+ " activity detected for "
								+ config.activityStopDelay() + " seconds."
				);

				skillingWarningsSent.add(skill);
			}
		}
	}

	private void checkHealth()
	{
		if (!config.playerHealthMonitoring())
		{
			wasHealthLow = false;
			return;
		}

		int current = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int maximum = client.getRealSkillLevel(Skill.HITPOINTS);

		if (maximum <= 0)
		{
			return;
		}

		int percentage = current * 100 / maximum;
		boolean low;

		if (config.healthThresholdMode()
				== AfkHelperConfig.ThresholdMode.EXACT_VALUE)
		{
			low = current <= config.healthExactThreshold();
		}
		else
		{
			low = percentage <= config.playerHealthThreshold();
		}

		if (low && shouldAlert(wasHealthLow, lastHealthAlert))
		{
			String message =
					"AFK Helper: Hitpoints are low! "
							+ current + "/" + maximum
							+ " (" + percentage + "%)";

			sendAlert(message);
			lastHealthAlert = System.currentTimeMillis();
		}

		wasHealthLow = low;
	}

	private void checkPrayer()
	{
		if (!config.prayerMonitoring())
		{
			wasPrayerLow = false;
			return;
		}

		int current = client.getBoostedSkillLevel(Skill.PRAYER);
		int maximum = client.getRealSkillLevel(Skill.PRAYER);

		if (maximum <= 0)
		{
			return;
		}

		int percentage = current * 100 / maximum;
		boolean low;

		if (config.prayerThresholdMode()
				== AfkHelperConfig.ThresholdMode.EXACT_VALUE)
		{
			low = current <= config.prayerExactThreshold();
		}
		else
		{
			low = percentage <= config.prayerThreshold();
		}

		if (low && shouldAlert(wasPrayerLow, lastPrayerAlert))
		{
			String message =
					"AFK Helper: Prayer is low! "
							+ current + "/" + maximum
							+ " (" + percentage + "%)";

			sendAlert(message);
			lastPrayerAlert = System.currentTimeMillis();
		}

		wasPrayerLow = low;
	}

	private void checkMonsterHealth()
	{
		if (!config.monsterMonitoring())
		{
			return;
		}

		if (client.getLocalPlayer() == null)
		{
			return;
		}

		Actor interacting = client.getLocalPlayer().getInteracting();

		if (!(interacting instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) interacting;
		String npcName = npc.getName();

		if (npcName == null || !isSavedMonster(npcName))
		{
			return;
		}

		int healthRatio = npc.getHealthRatio();
		int healthScale = npc.getHealthScale();

		if (healthRatio < 0 || healthScale <= 0)
		{
			return;
		}

		int percentage = healthRatio * 100 / healthScale;

		if (percentage > config.monsterHealthThreshold())
		{
			return;
		}

		int npcIndex = npc.getIndex();
		long currentTime = System.currentTimeMillis();

		if (config.monsterAlertOnce())
		{
			if (!alertedMonsterIndices.add(npcIndex))
			{
				return;
			}
		}
		else
		{
			long previousAlert = lastMonsterAlerts.getOrDefault(npcIndex, 0L);
			long cooldown = config.notificationCooldown() * 1000L;

			if (currentTime - previousAlert < cooldown)
			{
				return;
			}

			lastMonsterAlerts.put(npcIndex, currentTime);
		}

		sendAlert(
				"AFK Helper: " + npcName
						+ " is at " + percentage
						+ "% health!"
		);
	}

	private boolean isSavedMonster(String npcName)
	{
		String savedNames = config.savedMonsterNames();

		if (savedNames == null || savedNames.trim().isEmpty())
		{
			return false;
		}

		for (String savedName : savedNames.split(","))
		{
			if (npcName.equalsIgnoreCase(savedName.trim()))
			{
				return true;
			}
		}

		return false;
	}

	private void checkLogoutWarning()
	{
		if (!config.logoutWarning())
		{
			logoutWarningSent = false;
			return;
		}

		int idleTimeoutTicks = client.getIdleTimeout();

		if (idleTimeoutTicks <= 0)
		{
			return;
		}

		int mouseIdleTicks = client.getMouseIdleTicks();
		int keyboardIdleTicks = client.getKeyboardIdleTicks();
		int idleTicks = Math.min(mouseIdleTicks, keyboardIdleTicks);

		int warningTicks = Math.max(
				0,
				config.logoutWarningSeconds() * 50
		);

		int remainingTicks = Math.max(
				0,
				idleTimeoutTicks - idleTicks
		);

		if (remainingTicks <= warningTicks && !logoutWarningSent)
		{
			int remainingSeconds = (remainingTicks * 20 + 999) / 1000;

			sendAlert(
					"AFK Helper: Logout in approximately "
							+ remainingSeconds + " seconds due to inactivity!"
			);

			logoutWarningSent = true;
		}

		if (remainingTicks > warningTicks)
		{
			logoutWarningSent = false;
		}
	}

	private void sendAlert(String message)
	{
		client.addChatMessage(
				ChatMessageType.GAMEMESSAGE,
				"",
				message,
				null
		);

		if (config.windowsNotifications())
		{
			notifier.notify(message);
		}

		log.info(message);
	}

	private boolean shouldAlert(boolean wasLow, long lastAlert)
	{
		if (!wasLow)
		{
			return true;
		}

		if (!config.repeatHealthWarnings())
		{
			return false;
		}

		long cooldownMillis =
				config.notificationCooldown() * 1000L;

		return System.currentTimeMillis() - lastAlert
				>= cooldownMillis;
	}

	private BufferedImage createPanelIcon()
	{
		BufferedImage image = new BufferedImage(
				16,
				16,
				BufferedImage.TYPE_INT_ARGB
		);

		Graphics2D graphics = image.createGraphics();

		graphics.setColor(new Color(70, 170, 100));
		graphics.fillOval(1, 1, 14, 14);

		graphics.setColor(Color.WHITE);
		graphics.drawString("A", 4, 12);

		graphics.dispose();

		return image;
	}

	@Provides
	AfkHelperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AfkHelperConfig.class);
	}
}
