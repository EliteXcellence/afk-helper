package com.afkhelper;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.events.ActorDeath;

@Singleton
public class AfkHelperMonsterDeathHandler
{
    private final AfkHelperMonsterKillTracker monsterKillTracker;
    private final AfkHelperCombatTracker combatTracker;

    @Inject
    public AfkHelperMonsterDeathHandler(
            AfkHelperMonsterKillTracker monsterKillTracker,
            AfkHelperCombatTracker combatTracker)
    {
        this.monsterKillTracker = monsterKillTracker;
        this.combatTracker = combatTracker;
    }

    public void handle(ActorDeath event)
    {
        if (event == null)
        {
            return;
        }

        Actor actor = event.getActor();

        if (!(actor instanceof NPC))
        {
            return;
        }

        if (monsterKillTracker.isTrackedDeath(actor))
        {
            combatTracker.recordKill(actor);
        }
    }

    public void reset()
    {
        monsterKillTracker.clear();
    }
}