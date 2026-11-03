package com.afkhelper;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Actor;
import net.runelite.api.NPC;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AfkHelperCombatTracker
{
    private final AfkHelperSessionStats sessionStats;

    private final Map<Integer, Long> targetStartTimes = new HashMap<>();
    private final Map<Integer, Integer> targetDamage = new HashMap<>();
    private final Map<Integer, String> targetNames = new HashMap<>();

    @Inject
    public AfkHelperCombatTracker(AfkHelperSessionStats sessionStats)
    {
        this.sessionStats = sessionStats;
    }

    public void updateTarget(Actor actor)
    {
        if (!(actor instanceof NPC))
        {
            return;
        }

        NPC npc = (NPC) actor;
        int index = npc.getIndex();

        targetNames.put(index, npc.getName());

        targetStartTimes.putIfAbsent(index, System.currentTimeMillis());
    }

    public void recordDamage(Actor actor, int damage)
    {
        if (!(actor instanceof NPC) || damage <= 0)
        {
            return;
        }

        NPC npc = (NPC) actor;
        int index = npc.getIndex();

        updateTarget(npc);

        targetDamage.merge(index, damage, Integer::sum);
        sessionStats.recordDamage(npc.getName(), damage);
    }

    public void recordKill(Actor actor)
    {
        if (!(actor instanceof NPC))
        {
            return;
        }

        NPC npc = (NPC) actor;
        int index = npc.getIndex();

        Long startTime = targetStartTimes.remove(index);
        targetDamage.remove(index);
        targetNames.remove(index);

        if (startTime == null)
        {
            return;
        }

        long durationSeconds = Math.max(
                1,
                (System.currentTimeMillis() - startTime) / 1000
        );

        sessionStats.recordKillDuration(durationSeconds);
        sessionStats.recordMonsterKill(0);
    }

    public int getDamageForTarget(int npcIndex)
    {
        return targetDamage.getOrDefault(npcIndex, 0);
    }

    public String getTargetName(int npcIndex)
    {
        return targetNames.getOrDefault(npcIndex, "Unknown");
    }

    public int getTotalDamageForTarget(int npcIndex)
    {
        return getDamageForTarget(npcIndex);
    }

    public void reset()
    {
        targetStartTimes.clear();
        targetDamage.clear();
        targetNames.clear();
    }
}