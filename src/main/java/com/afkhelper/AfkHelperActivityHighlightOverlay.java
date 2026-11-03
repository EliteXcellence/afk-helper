package com.afkhelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.Locale;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Perspective;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

public class AfkHelperActivityHighlightOverlay extends Overlay
{
    private static final Color ELIGIBLE_COLOR =
            new Color(40, 220, 100, 45);
    private static final Color ELIGIBLE_OUTLINE =
            new Color(70, 255, 130, 150);

    private static final Color INELIGIBLE_COLOR =
            new Color(230, 65, 65, 35);
    private static final Color INELIGIBLE_OUTLINE =
            new Color(255, 90, 90, 145);

    private static final Color LABEL_BACKGROUND =
            new Color(20, 20, 20, 210);
    private static final Color ELIGIBLE_TEXT =
            new Color(120, 255, 160);
    private static final Color INELIGIBLE_TEXT =
            new Color(255, 130, 130);

    private final Client client;
    private final AfkHelperConfig config;

    // Only objects that could be a skilling activity are remembered. They
    // are added and removed from spawn/despawn events instead of scanning
    // every tile in the scene each frame. Identity-based, so an object that
    // covers several tiles (a tree) is only stored and drawn once. Events and
    // overlay rendering both run on the client thread.
    private final Set<TileObject> trackedObjects =
            Collections.newSetFromMap(new IdentityHashMap<>());

    // Don't bother drawing objects further away than this.
    private static final int MAX_DISTANCE_TILES = 25;

    public AfkHelperActivityHighlightOverlay(
            Client client,
            AfkHelperConfig config)
    {
        this.client = client;
        this.config = config;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(OverlayPriority.HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.enabled()
                || !config.eligibleActivityHighlightingEnabled()
                || !config.skillingMonitoring()
                || client.getGameState() != GameState.LOGGED_IN
                || client.getScene() == null)
        {
            return null;
        }

        Player localPlayer = client.getLocalPlayer();

        if (localPlayer == null || trackedObjects.isEmpty())
        {
            return null;
        }

        LocalPoint playerLocation = localPlayer.getLocalLocation();

        if (playerLocation == null)
        {
            return null;
        }

        for (TileObject object : trackedObjects)
        {
            renderObject(graphics, object, playerLocation);
        }

        return null;
    }

    /** Remember an object if it could be a skilling activity. */
    public void objectSpawned(TileObject object)
    {
        if (mightBeActivity(object))
        {
            trackedObjects.add(object);
        }
    }

    public void objectDespawned(TileObject object)
    {
        if (object != null)
        {
            trackedObjects.remove(object);
        }
    }

    public void clear()
    {
        trackedObjects.clear();
    }

    /**
     * One-off scan of the loaded scene, for when the plugin is switched on
     * while already logged in. After this, spawn events keep things current.
     * Must be called on the client thread.
     */
    public void scanScene()
    {
        trackedObjects.clear();

        if (client.getGameState() != GameState.LOGGED_IN
                || client.getScene() == null)
        {
            return;
        }

        Tile[][][] tiles = client.getScene().getTiles();

        if (tiles == null)
        {
            return;
        }

        for (Tile[][] plane : tiles)
        {
            if (plane == null)
            {
                continue;
            }

            for (Tile[] row : plane)
            {
                if (row == null)
                {
                    continue;
                }

                for (Tile tile : row)
                {
                    if (tile == null)
                    {
                        continue;
                    }

                    objectSpawned(tile.getWallObject());
                    objectSpawned(tile.getDecorativeObject());
                    objectSpawned(tile.getGroundObject());

                    GameObject[] gameObjects = tile.getGameObjects();

                    if (gameObjects != null)
                    {
                        for (GameObject gameObject : gameObjects)
                        {
                            objectSpawned(gameObject);
                        }
                    }
                }
            }
        }
    }

    /**
     * Cheap, config-independent check done once when an object appears.
     * Objects that morph (impostors) can't be judged until they are
     * resolved, so they are kept and checked properly when drawn.
     */
    private boolean mightBeActivity(TileObject object)
    {
        if (object == null || object.getId() <= 0)
        {
            return false;
        }

        if (object instanceof GameObject
                && ((GameObject) object).getRenderable() instanceof Actor)
        {
            return false;
        }

        ObjectComposition composition =
                client.getObjectDefinition(object.getId());

        if (composition == null)
        {
            return false;
        }

        if (composition.getImpostorIds() != null)
        {
            return true;
        }

        String[] actions = composition.getActions();

        if (actions == null)
        {
            return false;
        }

        for (String action : actions)
        {
            if (action == null)
            {
                continue;
            }

            String normalised = action.toLowerCase(Locale.ROOT).trim();

            if (normalised.startsWith("chop")
                    || normalised.equals("mine")
                    || isFishingAction(normalised)
                    || normalised.equals("catch")
                    || normalised.equals("check"))
            {
                return true;
            }
        }

        return false;
    }

    private void renderObject(
            Graphics2D graphics,
            TileObject object,
            LocalPoint playerLocation)
    {
        if (object == null || object.getId() <= 0)
        {
            return;
        }

        // Players and NPCs can appear in a tile's game object list. Their
        // "id" is not an object id, so looking it up can match an unrelated
        // object (for example a tree) and put a highlight on a moving actor.
        if (object instanceof GameObject
                && ((GameObject) object).getRenderable() instanceof Actor)
        {
            return;
        }

        ObjectComposition composition =
                client.getObjectDefinition(object.getId());

        if (composition == null)
        {
            return;
        }

        // Only resolve an impostor when the object actually has impostors.
        // Calling getImpostor() without this check can throw a
        // NullPointerException in RuneLite.
        int[] impostorIds = composition.getImpostorIds();

        if (impostorIds != null)
        {
            ObjectComposition impostor = composition.getImpostor();

            if (impostor == null)
            {
                return;
            }

            composition = impostor;
        }

        ActivityRequirement requirement =
                getActivityRequirement(composition);

        if (requirement == null)
        {
            return;
        }

        if (!isSkillEnabled(requirement.skill))
        {
            return;
        }

        int playerLevel = client.getRealSkillLevel(requirement.skill);
        boolean eligible = playerLevel >= requirement.level;

        if (object.getPlane() != client.getPlane())
        {
            return;
        }

        LocalPoint localPoint = object.getLocalLocation();

        if (localPoint == null
                || localPoint.distanceTo(playerLocation)
                > MAX_DISTANCE_TILES * Perspective.LOCAL_TILE_SIZE)
        {
            return;
        }

        Polygon polygon =
                Perspective.getCanvasTilePoly(client, localPoint);

        if (polygon == null)
        {
            return;
        }

        if (eligible)
        {
            graphics.setColor(ELIGIBLE_COLOR);
            graphics.fillPolygon(polygon);
            graphics.setColor(ELIGIBLE_OUTLINE);
        }
        else
        {
            graphics.setColor(INELIGIBLE_COLOR);
            graphics.fillPolygon(polygon);
            graphics.setColor(INELIGIBLE_OUTLINE);
        }

        graphics.drawPolygon(polygon);

        String label = requirement.level + " " + requirement.skillName;

        drawRequirementLabel(
                graphics,
                polygon,
                label,
                eligible);
    }

    private ActivityRequirement getActivityRequirement(
            ObjectComposition composition)
    {
        String name = composition.getName();

        if (name == null)
        {
            return null;
        }

        String normalisedName = name.toLowerCase(Locale.ROOT);
        String[] actions = composition.getActions();

        if (actions == null)
        {
            return null;
        }

        for (String action : actions)
        {
            if (action == null)
            {
                continue;
            }

            String normalisedAction =
                    action.toLowerCase(Locale.ROOT).trim();

            if (config.woodcuttingMonitoring()
                    && normalisedAction.startsWith("chop"))
            {
                return getWoodcuttingRequirement(normalisedName);
            }

            if (config.miningMonitoring()
                    && normalisedAction.equals("mine"))
            {
                return getMiningRequirement(normalisedName);
            }

            if (config.fishingMonitoring()
                    && isFishingAction(normalisedAction))
            {
                return getFishingRequirement(normalisedName);
            }

            if (config.hunterMonitoring()
                    && (normalisedAction.equals("catch")
                    || normalisedAction.equals("check")))
            {
                return getHunterRequirement(normalisedName);
            }
        }

        return null;
    }

    private ActivityRequirement getWoodcuttingRequirement(String name)
    {
        if (name.contains("redwood"))
        {
            return requirement(Skill.WOODCUTTING, 90);
        }

        if (name.contains("magic"))
        {
            return requirement(Skill.WOODCUTTING, 75);
        }

        if (name.contains("yew"))
        {
            return requirement(Skill.WOODCUTTING, 60);
        }

        if (name.contains("mahogany"))
        {
            return requirement(Skill.WOODCUTTING, 50);
        }

        if (name.contains("maple"))
        {
            return requirement(Skill.WOODCUTTING, 45);
        }

        if (name.contains("teak"))
        {
            return requirement(Skill.WOODCUTTING, 35);
        }

        if (name.contains("willow"))
        {
            return requirement(Skill.WOODCUTTING, 30);
        }

        if (name.contains("oak"))
        {
            return requirement(Skill.WOODCUTTING, 15);
        }

        if (name.contains("tree")
                || name.contains("dead tree")
                || name.contains("achey"))
        {
            return requirement(Skill.WOODCUTTING, 1);
        }

        return null;
    }

    private ActivityRequirement getMiningRequirement(String name)
    {
        if (name.contains("runite"))
        {
            return requirement(Skill.MINING, 85);
        }

        if (name.contains("adamant"))
        {
            return requirement(Skill.MINING, 70);
        }

        if (name.contains("mithril"))
        {
            return requirement(Skill.MINING, 55);
        }

        if (name.contains("amethyst"))
        {
            return requirement(Skill.MINING, 92);
        }

        if (name.contains("gold"))
        {
            return requirement(Skill.MINING, 40);
        }

        if (name.contains("coal"))
        {
            return requirement(Skill.MINING, 30);
        }

        if (name.contains("silver"))
        {
            return requirement(Skill.MINING, 20);
        }

        if (name.contains("iron"))
        {
            return requirement(Skill.MINING, 15);
        }

        if (name.contains("copper")
                || name.contains("tin")
                || name.contains("clay")
                || name.contains("gem"))
        {
            return requirement(Skill.MINING, 1);
        }

        return null;
    }

    private boolean isFishingAction(String action)
    {
        return action.equals("fish")
                || action.equals("net")
                || action.equals("bait")
                || action.equals("lure")
                || action.equals("harpoon")
                || action.equals("cage");
    }

    private ActivityRequirement getFishingRequirement(String name)
    {
        if (name.contains("anglerfish"))
        {
            return requirement(Skill.FISHING, 82);
        }

        if (name.contains("dark crab"))
        {
            return requirement(Skill.FISHING, 85);
        }

        if (name.contains("shark"))
        {
            return requirement(Skill.FISHING, 76);
        }

        if (name.contains("monkfish"))
        {
            return requirement(Skill.FISHING, 62);
        }

        if (name.contains("karambwan"))
        {
            return requirement(Skill.FISHING, 65);
        }

        if (name.contains("swordfish"))
        {
            return requirement(Skill.FISHING, 50);
        }

        if (name.contains("lobster"))
        {
            return requirement(Skill.FISHING, 40);
        }

        if (name.contains("tuna"))
        {
            return requirement(Skill.FISHING, 35);
        }

        if (name.contains("salmon"))
        {
            return requirement(Skill.FISHING, 30);
        }

        if (name.contains("trout"))
        {
            return requirement(Skill.FISHING, 20);
        }

        if (name.contains("herring"))
        {
            return requirement(Skill.FISHING, 10);
        }

        if (name.contains("sardine"))
        {
            return requirement(Skill.FISHING, 5);
        }

        if (name.contains("shrimp")
                || name.contains("anchov")
                || name.contains("fishing spot"))
        {
            return requirement(Skill.FISHING, 1);
        }

        return null;
    }

    private ActivityRequirement getHunterRequirement(String name)
    {
        if (name.contains("black chinchompa"))
        {
            return requirement(Skill.HUNTER, 73);
        }

        if (name.contains("red chinchompa"))
        {
            return requirement(Skill.HUNTER, 63);
        }

        if (name.contains("chinchompa"))
        {
            return requirement(Skill.HUNTER, 53);
        }

        if (name.contains("black salamander"))
        {
            return requirement(Skill.HUNTER, 67);
        }

        if (name.contains("red salamander"))
        {
            return requirement(Skill.HUNTER,  salamanderLevel(name));
        }

        if (name.contains("orange salamander"))
        {
            return requirement(Skill.HUNTER, 47);
        }

        if (name.contains("salamander"))
        {
            return requirement(Skill.HUNTER, 29);
        }

        if (name.contains("bird snare"))
        {
            return requirement(Skill.HUNTER, 5);
        }

        if (name.contains("butterfly"))
        {
            return requirement(Skill.HUNTER, 15);
        }

        if (name.contains("rabbit snare"))
        {
            return requirement(Skill.HUNTER, 27);
        }

        if (name.contains("box trap"))
        {
            return requirement(Skill.HUNTER, 27);
        }

        return null;
    }

    private int salamanderLevel(String name)
    {
        if (name.contains("red salamander"))
        {
            return  salamanderLevelForRed();
        }

        return 1;
    }

    private int salamanderLevelForRed()
    {
        return  redSalamanderRequiredLevel();
    }

    private int redSalamanderRequiredLevel()
    {
        return  redSalamanderLevel();
    }

    private int redSalamanderLevel()
    {
        return 47;
    }

    private ActivityRequirement requirement(Skill skill, int level)
    {
        return new ActivityRequirement(skill, level);
    }

    private boolean isSkillEnabled(Skill skill)
    {
        if (!config.skillingMonitoring())
        {
            return false;
        }

        switch (skill)
        {
            case WOODCUTTING:
                return config.woodcuttingMonitoring();

            case MINING:
                return config.miningMonitoring();

            case FISHING:
                return config.fishingMonitoring();

            case HUNTER:
                return config.hunterMonitoring();

            default:
                return false;
        }
    }

    private void drawRequirementLabel(
            Graphics2D graphics,
            Polygon polygon,
            String text,
            boolean eligible)
    {
        FontMetrics metrics = graphics.getFontMetrics();

        int textWidth = metrics.stringWidth(text);
        int textHeight = metrics.getHeight();

        int padding = 4;
        int labelWidth = textWidth + padding * 2;
        int labelHeight = textHeight + padding;

        int x = polygon.getBounds().x
                + (polygon.getBounds().width - labelWidth) / 2;

        int y = polygon.getBounds().y - labelHeight;

        if (y < 0)
        {
            y = polygon.getBounds().y + 2;
        }

        graphics.setColor(LABEL_BACKGROUND);
        graphics.fillRoundRect(
                x, y, labelWidth, labelHeight, 5, 5);

        graphics.setColor(
                eligible ? ELIGIBLE_TEXT : INELIGIBLE_TEXT);

        graphics.drawString(
                text,
                x + padding,
                y + metrics.getAscent());
    }

    private static class ActivityRequirement
    {
        private final Skill skill;
        private final int level;
        private final String skillName;

        private ActivityRequirement(Skill skill, int level)
        {
            this.skill = skill;
            this.level = level;

            switch (skill)
            {
                case WOODCUTTING:
                    skillName = "Woodcutting";
                    break;

                case MINING:
                    skillName = "Mining";
                    break;

                case FISHING:
                    skillName = "Fishing";
                    break;

                case HUNTER:
                    skillName = "Hunter";
                    break;

                default:
                    skillName = skill.getName();
                    break;
            }
        }
    }
}