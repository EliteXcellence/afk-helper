package com.afkhelper;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.events.HitsplatApplied;

@Singleton
public class AfkHelperDamageTracker
{
    private final AfkHelperCombatTracker combatTracker;

    @Inject
    public AfkHelperDamageTracker(AfkHelperCombatTracker combatTracker)
    {
        this.combatTracker = combatTracker;
    }

    public void handle(HitsplatApplied event)
    {
        if (event == null || event.getHitsplat() == null)
        {
            return;
        }

        Actor actor = event.getActor();

        if (!(actor instanceof NPC))
        {
            return;
        }

        int damage = event.getHitsplat().getAmount();

        if (damage > 0)
        {
            combatTracker.recordDamage(actor, damage);
        }
    }

    public void reset()
    {
        // Combat tracker lifecycle is managed by the plugin.
    }
}