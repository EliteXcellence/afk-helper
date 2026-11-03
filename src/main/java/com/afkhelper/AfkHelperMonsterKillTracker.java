package com.afkhelper;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Player;

@Singleton
public class AfkHelperMonsterKillTracker
{
    private int trackedNpcIndex = -1;
    private String trackedNpcName = "No target";

    @Inject
    public AfkHelperMonsterKillTracker()
    {
    }

    public void updateTarget(Player player)
    {
        if (player == null)
        {
            clear();
            return;
        }

        Actor interacting = player.getInteracting();

        if (!(interacting instanceof NPC))
        {
            return;
        }

        NPC npc = (NPC) interacting;

        trackedNpcIndex = npc.getIndex();
        trackedNpcName = npc.getName() == null
                ? "Unknown"
                : npc.getName();
    }

    public boolean isTrackedDeath(Actor actor)
    {
        if (!(actor instanceof NPC))
        {
            return false;
        }

        NPC npc = (NPC) actor;

        if (npc.getIndex() != trackedNpcIndex)
        {
            return false;
        }

        clear();
        return true;
    }

    public int getTrackedNpcIndex()
    {
        return trackedNpcIndex;
    }

    public String getTrackedNpcName()
    {
        return trackedNpcName;
    }

    public boolean hasTrackedTarget()
    {
        return trackedNpcIndex >= 0;
    }

    public void clear()
    {
        trackedNpcIndex = -1;
        trackedNpcName = "No target";
    }
}