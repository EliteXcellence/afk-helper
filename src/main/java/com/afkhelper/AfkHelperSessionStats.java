
package com.afkhelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.runelite.api.Skill;

public class AfkHelperSessionStats
{
    private final Map<Skill, Integer> lastXp = new HashMap<>();
    private final Map<String, Integer> damageByMonster = new HashMap<>();

    private long sessionStartTime;
    private long currentKillStartTime;
    private long totalMonsterKillTimeSeconds;

    private int xpGained;
    private int actions;
    private int monstersKilled;
    private int combatXpGained;
    private int totalDamage;

    private final List<Long> completedKillTimes = new ArrayList<>();

    public void startSession(Map<Skill, Integer> currentXp, boolean reset)
    {
        if (reset)
        {
            reset();
        }

        lastXp.clear();

        if (currentXp != null)
        {
            lastXp.putAll(currentXp);
        }

        sessionStartTime = System.currentTimeMillis();
    }

    public void updateXp(Skill skill, int currentXp)
    {
        if (skill == null)
        {
            return;
        }

        Integer previousXp = lastXp.put(skill, currentXp);

        if (previousXp != null && currentXp > previousXp)
        {
            int gained = currentXp - previousXp;
            xpGained += gained;

            if (skill == Skill.ATTACK
                    || skill == Skill.STRENGTH
                    || skill == Skill.DEFENCE
                    || skill == Skill.HITPOINTS
                    || skill == Skill.RANGED
                    || skill == Skill.MAGIC
                    || skill == Skill.PRAYER)
            {
                combatXpGained += gained;
            }
        }
    }

    public void recordAction()
    {
        actions++;
    }

    public void recordMonsterKill(int xp)
    {
        monstersKilled++;
        combatXpGained += Math.max(0, xp);
    }

    public void startMonsterKill()
    {
        currentKillStartTime = System.currentTimeMillis();
    }

    public void recordDamage(String monsterName, int damage)
    {
        if (damage <= 0)
        {
            return;
        }

        totalDamage += damage;

        if (monsterName != null)
        {
            damageByMonster.merge(monsterName, damage, Integer::sum);
        }
    }

    public void recordKillDuration(long durationSeconds)
    {
        if (durationSeconds <= 0)
        {
            return;
        }

        completedKillTimes.add(durationSeconds);
        totalMonsterKillTimeSeconds += durationSeconds;
    }

    public int getXpGained()
    {
        return xpGained;
    }

    public long getXpPerHour()
    {
        long seconds = getSessionDurationSeconds();

        if (seconds <= 0)
        {
            return 0;
        }

        return xpGained * 3600L / seconds;
    }

    public int getActions()
    {
        return actions;
    }

    public int getMonstersKilled()
    {
        return monstersKilled;
    }

    public int getCombatXpGained()
    {
        return combatXpGained;
    }

    public int getTotalDamage()
    {
        return totalDamage;
    }

    public Map<String, Integer> getDamageByMonster()
    {
        return new HashMap<>(damageByMonster);
    }

    public long getSessionDurationSeconds()
    {
        if (sessionStartTime == 0)
        {
            return 0;
        }

        return Math.max(
                0,
                (System.currentTimeMillis() - sessionStartTime) / 1000
        );
    }

    public String getSessionDurationText()
    {
        return formatDuration(getSessionDurationSeconds());
    }

    public long getDamagePerHour()
    {
        long seconds = getSessionDurationSeconds();

        if (seconds <= 0)
        {
            return 0;
        }

        return totalDamage * 3600L / seconds;
    }

    public long getKillsPerHour()
    {
        long seconds = getSessionDurationSeconds();

        if (seconds <= 0)
        {
            return 0;
        }

        return monstersKilled * 3600L / seconds;
    }

    public long getAverageKillTimeSeconds()
    {
        if (completedKillTimes.isEmpty())
        {
            return 0;
        }

        return totalMonsterKillTimeSeconds / completedKillTimes.size();
    }

    public String getAverageKillTimeText()
    {
        if (completedKillTimes.isEmpty())
        {
            return "N/A";
        }

        return formatDuration(getAverageKillTimeSeconds());
    }

    public long getCurrentKillDurationSeconds()
    {
        if (currentKillStartTime == 0)
        {
            return 0;
        }

        return Math.max(
                0,
                (System.currentTimeMillis() - currentKillStartTime) / 1000
        );
    }

    public void reset()
    {
        lastXp.clear();
        damageByMonster.clear();
        completedKillTimes.clear();

        sessionStartTime = System.currentTimeMillis();
        currentKillStartTime = 0;
        totalMonsterKillTimeSeconds = 0;

        xpGained = 0;
        actions = 0;
        monstersKilled = 0;
        combatXpGained = 0;
        totalDamage = 0;
    }

    private String formatDuration(long totalSeconds)
    {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0)
        {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }

        return String.format("%02d:%02d", minutes, seconds);
    }
}
