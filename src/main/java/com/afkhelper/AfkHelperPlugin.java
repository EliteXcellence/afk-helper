package com.afkhelper;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
		name = "AFK Helper",
		description = "Customisable combat, health, AFK and skilling alerts"
)
public class AfkHelperPlugin extends Plugin
{
	private static final int[] RUNE_POUCH_RUNE_VARBITS = {
			Varbits.RUNE_POUCH_RUNE1,
			Varbits.RUNE_POUCH_RUNE2,
			Varbits.RUNE_POUCH_RUNE3,
			Varbits.RUNE_POUCH_RUNE4,
			Varbits.RUNE_POUCH_RUNE5,
			Varbits.RUNE_POUCH_RUNE6
	};

	private static final int[] RUNE_POUCH_AMOUNT_VARBITS = {
			Varbits.RUNE_POUCH_AMOUNT1,
			Varbits.RUNE_POUCH_AMOUNT2,
			Varbits.RUNE_POUCH_AMOUNT3,
			Varbits.RUNE_POUCH_AMOUNT4,
			Varbits.RUNE_POUCH_AMOUNT5,
			Varbits.RUNE_POUCH_AMOUNT6
	};

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

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private AfkHelperAudioPlayer audioPlayer;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private AfkHelperInventoryTracker inventoryTracker;

	private AfkHelperScannerPanel scannerPanel;
	private NavigationButton navigationButton;

	private AfkHelperSessionOverlay sessionOverlay;
	private AfkHelperFlashOverlay flashOverlay;
	private AfkHelperActivityHighlightOverlay activityHighlightOverlay;
	private AfkHelperAfkCountdown afkCountdown;

	// Idle time based on mouse clicks and key presses only (not movement).
	private final AfkHelperInputTracker inputTracker =
			new AfkHelperInputTracker();

	private final AfkHelperSessionStats sessionStats =
			new AfkHelperSessionStats();

	private final AfkHelperActionTracker actionTracker =
			new AfkHelperActionTracker();

	private final AfkHelperMonsterKillTracker monsterKillTracker =
			new AfkHelperMonsterKillTracker();

	private AfkHelperCombatTracker combatTracker;
	private AfkHelperDamageTracker damageTracker;

	private final Map<Skill, Integer> previousLevels =
			new EnumMap<>(Skill.class);

	private Skill currentSessionSkill;
	private GameState previousGameState = GameState.UNKNOWN;

	private boolean sessionStarted;
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
		combatTracker = new AfkHelperCombatTracker(sessionStats);
		damageTracker = new AfkHelperDamageTracker(combatTracker);

		scannerPanel = new AfkHelperScannerPanel(
				config,
				configManager,
				client,
				clientThread,
				this::resetSessionStatistics
		);

		navigationButton = NavigationButton.builder()
				.tooltip("AFK Helper")
				.icon(createPanelIcon())
				.priority(10)
				.panel(scannerPanel)
				.build();

		clientToolbar.addNavigation(navigationButton);

		sessionStarted = false;
		currentSessionSkill = null;
		previousGameState = client.getGameState();

		actionTracker.reset();
		monsterKillTracker.clear();
		resetOrRebaselineInventory(false);

		inputTracker.reset(Math.min(
				client.getMouseIdleTicks(),
				client.getKeyboardIdleTicks()));
		audioPlayer.start();
		mouseManager.registerMouseListener(inputTracker);
		keyManager.registerKeyListener(inputTracker);

		afkCountdown = new AfkHelperAfkCountdown(client, inputTracker);

		sessionOverlay = new AfkHelperSessionOverlay(
				client,
				sessionStats,
				inventoryTracker,
				config,
				afkCountdown::getIdleTimeText,
				this::getCurrentTargetName,
				inventoryTracker::getSupplySummary,
				() -> "Logout: " + afkCountdown.getLogoutCountdownText(
						client.getIdleTimeout(),
						config.logoutWarningSeconds()
				)
		);

		flashOverlay = new AfkHelperFlashOverlay(config);

		activityHighlightOverlay =
				new AfkHelperActivityHighlightOverlay(client, config);

		// Pick up objects already in the scene if switched on while
		// logged in. From here on, spawn/despawn events keep it current.
		clientThread.invokeLater((Runnable) () ->
		{
			if (activityHighlightOverlay != null)
			{
				activityHighlightOverlay.scanScene();
			}
		});

		if (previousGameState == GameState.LOGGED_IN)
		{
			startSession();
		}

		updateOverlays();

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
		audioPlayer.stop();
		mouseManager.unregisterMouseListener(inputTracker);
		keyManager.unregisterKeyListener(inputTracker);

		if (sessionOverlay != null)
		{
			overlayManager.remove(sessionOverlay);
			sessionOverlay = null;
		}

		if (flashOverlay != null)
		{
			overlayManager.remove(flashOverlay);
			flashOverlay = null;
		}

		if (activityHighlightOverlay != null)
		{
			overlayManager.remove(activityHighlightOverlay);
			activityHighlightOverlay = null;
		}

		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
			navigationButton = null;
		}

		if (damageTracker != null)
		{
			damageTracker.reset();
			damageTracker = null;
		}

		combatTracker = null;
		scannerPanel = null;
		afkCountdown = null;

		resetOrRebaselineInventory(false);

		if (!config.saveSessionStatistics())
		{
			sessionStats.reset();
		}

		sessionStarted = false;
		currentSessionSkill = null;
		previousLevels.clear();

		actionTracker.reset();
		monsterKillTracker.clear();

		alertedMonsterIndices.clear();
		lastMonsterAlerts.clear();
		lastSkillingActivity.clear();
		skillingWarningsSent.clear();

		log.info("AFK Helper stopped!");
	}

	private String getCurrentTargetName()
	{
		if (client.getLocalPlayer() == null)
		{
			return "Not attacking";
		}

		Actor interacting = client.getLocalPlayer().getInteracting();

		if (!(interacting instanceof NPC))
		{
			return "Not attacking";
		}

		String name = ((NPC) interacting).getName();

		return name == null || name.trim().isEmpty()
				? "Unknown NPC"
				: name;
	}

	private void updateOverlays()
	{
		if (sessionOverlay != null)
		{
			if (config.enabled() && config.sessionOverlayEnabled())
			{
				overlayManager.add(sessionOverlay);
			}
			else
			{
				overlayManager.remove(sessionOverlay);
			}
		}

		if (flashOverlay != null)
		{
			overlayManager.add(flashOverlay);
		}

		if (activityHighlightOverlay != null)
		{
			if (config.enabled()
					&& config.eligibleActivityHighlightingEnabled())
			{
				overlayManager.add(activityHighlightOverlay);
			}
			else
			{
				overlayManager.remove(activityHighlightOverlay);
			}
		}
	}

	private void triggerFlash()
	{
		if (flashOverlay != null
				&& config.enabled()
				&& config.screenFlash())
		{
			flashOverlay.triggerFlash();
		}
	}

	/**
	 * Called from the panel's reset button. Clears every session statistic,
	 * including supplies and profit, and starts counting again from now.
	 */
	private void resetSessionStatistics()
	{
		clientThread.invokeLater((Runnable) () ->
		{
			if (client.getGameState() == GameState.LOGGED_IN
					&& sessionStarted)
			{
				startSession(true);
			}
			else
			{
				sessionStats.reset();
				inventoryTracker.reset();
			}

			updateOverlays();
		});
	}

	private void resetOrRebaselineInventory(boolean forceReset)
	{
		if (forceReset || !config.saveSessionStatistics())
		{
			inventoryTracker.reset();
		}
		else
		{
			inventoryTracker.rebaseline();
		}
	}

	private void startSession()
	{
		startSession(false);
	}

	private void startSession(boolean forceReset)
	{
		boolean keepStats =
				config.saveSessionStatistics() && !forceReset;

		Map<Skill, Integer> startingXp = new EnumMap<>(Skill.class);
		previousLevels.clear();

		for (Skill skill : Skill.values())
		{
			startingXp.put(skill, client.getSkillExperience(skill));
			previousLevels.put(skill, client.getRealSkillLevel(skill));
		}

		sessionStats.startSession(startingXp, !keepStats);

		actionTracker.reset();
		monsterKillTracker.clear();
		resetOrRebaselineInventory(forceReset);

		if (combatTracker != null)
		{
			combatTracker.reset();
		}

		sessionStarted = true;
		currentSessionSkill = null;

		updateInventoryTracking();
		updateEquipmentTracking();
		updateRunePouchTracking();

		log.info("AFK Helper session started.");
	}

	private boolean isCombatSkill(Skill skill)
	{
		return skill == Skill.ATTACK
				|| skill == Skill.STRENGTH
				|| skill == Skill.DEFENCE
				|| skill == Skill.RANGED
				|| skill == Skill.MAGIC
				|| skill == Skill.HITPOINTS;
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (!config.enabled()
				|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		Skill skill = event.getSkill();

		if (skill == null)
		{
			return;
		}

		int currentLevel = client.getRealSkillLevel(skill);
		int previousLevel = previousLevels.getOrDefault(skill, currentLevel);

		if (currentLevel > previousLevel)
		{
			triggerFlash();

			if (config.levelUpNotifications())
			{
				sendAlert("AFK Helper: " + skill.getName()
								+ " level increased to " + currentLevel + "!",
						config.levelUpSound());
			}
		}

		previousLevels.put(skill, currentLevel);

		if (sessionStarted)
		{
			sessionStats.updateXp(
					skill,
					client.getSkillExperience(skill)
			);

			if (isCombatSkill(skill))
			{
				currentSessionSkill = skill;
			}
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!config.enabled()
				|| !sessionStarted
				|| client.getGameState() != GameState.LOGGED_IN
				|| damageTracker == null)
		{
			return;
		}

		if (config.combatPerformanceTracking())
		{
			damageTracker.handle(event);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (!config.enabled()
				|| !sessionStarted
				|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (event.getContainerId() == InventoryID.INVENTORY.getId())
		{
			updateInventoryTracking();
		}
		else if (event.getContainerId() == InventoryID.EQUIPMENT.getId())
		{
			updateEquipmentTracking();
		}
	}

	private boolean shouldTrackCombatSupplies()
	{
		return config.combatPerformanceTracking()
				|| config.supplyTrackingEnabled()
				|| config.profitTrackingEnabled();
	}

	private void updateInventoryTracking()
	{
		if (!shouldTrackCombatSupplies())
		{
			return;
		}

		ItemContainer container =
				client.getItemContainer(InventoryID.INVENTORY);

		if (container != null)
		{
			updateTrackerFromContainer(container, 0);
		}
	}

	private void updateEquipmentTracking()
	{
		if (!shouldTrackCombatSupplies())
		{
			return;
		}

		ItemContainer container =
				client.getItemContainer(InventoryID.EQUIPMENT);

		if (container != null)
		{
			updateTrackerFromContainer(container, 1);
		}
	}

	private void updateTrackerFromContainer(
			ItemContainer container,
			int containerType)
	{
		Item[] items = container.getItems();
		int[] itemIds = new int[items.length];
		int[] quantities = new int[items.length];

		for (int i = 0; i < items.length; i++)
		{
			Item item = items[i];

			if (item != null)
			{
				itemIds[i] = item.getId();
				quantities[i] = item.getQuantity();
			}
		}

		if (containerType == 0)
		{
			inventoryTracker.updateInventory(itemIds, quantities);
		}
		else
		{
			inventoryTracker.updateEquipment(itemIds, quantities);
		}
	}

	private void updateRunePouchTracking()
	{
		if (!shouldTrackCombatSupplies())
		{
			return;
		}

		int[] runeIds = new int[RUNE_POUCH_RUNE_VARBITS.length];
		int[] runeAmounts = new int[RUNE_POUCH_AMOUNT_VARBITS.length];

		for (int i = 0; i < RUNE_POUCH_RUNE_VARBITS.length; i++)
		{
			runeIds[i] = client.getVarbitValue(
					RUNE_POUCH_RUNE_VARBITS[i]
			);

			runeAmounts[i] = client.getVarbitValue(
					RUNE_POUCH_AMOUNT_VARBITS[i]
			);
		}

		inventoryTracker.updateRunePouch(runeIds, runeAmounts);
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (!config.enabled()
				|| client.getGameState() != GameState.LOGGED_IN
				|| client.getLocalPlayer() == null
				|| event.getActor() != client.getLocalPlayer())
		{
			return;
		}

		int animation = client.getLocalPlayer().getAnimation();
		Skill skill = getSkillingSkillForAnimation(animation);

		if (skill == null)
		{
			for (Skill trackedSkill : Skill.values())
			{
				actionTracker.markIdle(trackedSkill);
			}

			return;
		}

		if (sessionStarted)
		{
			currentSessionSkill = skill;

			if (actionTracker.shouldRecordAction(skill, animation))
			{
				sessionStats.recordAction();
			}
		}

		if (config.skillingMonitoring()
				&& config.activityMonitoring()
				&& isSkillEnabled(skill))
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

		if (contains(animation, 5208, 5215, 7259, 5207, 5212))
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
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();

		// Objects are re-announced through spawn events once the next
		// scene has loaded, so anything remembered now is stale.
		if (activityHighlightOverlay != null
				&& (state == GameState.LOADING
				|| state == GameState.LOGIN_SCREEN
				|| state == GameState.HOPPING))
		{
			activityHighlightOverlay.clear();
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		if (activityHighlightOverlay != null)
		{
			activityHighlightOverlay.objectSpawned(event.getGameObject());
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		if (activityHighlightOverlay != null)
		{
			activityHighlightOverlay.objectDespawned(event.getGameObject());
		}
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		if (activityHighlightOverlay != null)
		{
			activityHighlightOverlay.objectSpawned(event.getWallObject());
		}
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		if (activityHighlightOverlay != null)
		{
			activityHighlightOverlay.objectDespawned(event.getWallObject());
		}
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
	{
		if (activityHighlightOverlay != null)
		{
			activityHighlightOverlay.objectSpawned(
					event.getDecorativeObject());
		}
	}

	@Subscribe
	public void onDecorativeObjectDespawned(
			DecorativeObjectDespawned event)
	{
		if (activityHighlightOverlay != null)
		{
			activityHighlightOverlay.objectDespawned(
					event.getDecorativeObject());
		}
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		if (activityHighlightOverlay != null)
		{
			activityHighlightOverlay.objectSpawned(event.getGroundObject());
		}
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		if (activityHighlightOverlay != null)
		{
			activityHighlightOverlay.objectDespawned(event.getGroundObject());
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		GameState gameState = client.getGameState();

		if (gameState != previousGameState)
		{
			if (gameState == GameState.LOGGED_IN)
			{
				inputTracker.markInput();
				startSession();
			}
			else if (previousGameState == GameState.LOGGED_IN)
			{
				sessionStarted = false;
				currentSessionSkill = null;

				actionTracker.reset();
				monsterKillTracker.clear();

				if (combatTracker != null)
				{
					combatTracker.reset();
				}

				if (!config.saveSessionStatistics())
				{
					sessionStats.reset();
				}
			}

			previousGameState = gameState;
		}

		updateOverlays();

		if (!config.enabled() || gameState != GameState.LOGGED_IN)
		{
			wasHealthLow = false;
			wasPrayerLow = false;
			logoutWarningSent = false;
			return;
		}

		if (sessionStarted)
		{
			updateRunePouchTracking();
		}

		if (client.getLocalPlayer() != null)
		{
			monsterKillTracker.updateTarget(client.getLocalPlayer());
		}

		if (!config.notificationsEnabled())
		{
			return;
		}

		checkHealth();
		checkPrayer();
		checkMonsterHealth();
		checkLogoutWarning();
		checkSkillingInactivity();
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (!config.enabled()
				|| !sessionStarted
				|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		Actor actor = event.getActor();

		if (monsterKillTracker.isTrackedDeath(actor))
		{
			if (combatTracker != null)
			{
				combatTracker.recordKill(actor);
			}
			else
			{
				sessionStats.recordMonsterKill(0);
			}
		}
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
				triggerFlash();

				sendAlert("AFK Helper: No " + skill.getName()
						+ " activity detected for "
						+ config.activityStopDelay() + " seconds.");

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
			sendAlert("AFK Helper: Hitpoints are low! "
					+ current + "/" + maximum
					+ " (" + percentage + "%)");

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
			sendAlert("AFK Helper: Prayer is low! "
					+ current + "/" + maximum
					+ " (" + percentage + "%)");

			lastPrayerAlert = System.currentTimeMillis();
		}

		wasPrayerLow = low;
	}

	private void checkMonsterHealth()
	{
		if (!config.monsterMonitoring()
				|| client.getLocalPlayer() == null)
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

		triggerFlash();

		sendAlert("AFK Helper: " + npcName
				+ " is at " + percentage + "% health!");
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

		int idleTicks = inputTracker.getIdleTicks();

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

			sendAlert("AFK Helper: Logout in approximately "
					+ remainingSeconds
					+ " seconds due to inactivity!");

			logoutWarningSent = true;
		}

		if (remainingTicks > warningTicks)
		{
			logoutWarningSent = false;
		}
	}

	private void sendAlert(String message)
	{
		sendAlert(message, true);
	}

	private void sendAlert(String message, boolean playSound)
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

		if (playSound && config.audioEnabled())
		{
			audioPlayer.play(config.defaultSound(), config.audioVolume());
		}

		log.debug(message);
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

		long cooldownMillis = config.notificationCooldown() * 1000L;

		return System.currentTimeMillis() - lastAlert >= cooldownMillis;
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