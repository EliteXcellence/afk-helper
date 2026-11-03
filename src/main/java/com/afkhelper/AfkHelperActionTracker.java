package com.afkhelper;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Skill;

public class AfkHelperActionTracker
{
    private final Map<Skill, Integer> lastAnimations =
            new EnumMap<>(Skill.class);

    private final Map<Skill, Boolean> wasActive =
            new EnumMap<>(Skill.class);

    public boolean shouldRecordAction(Skill skill, int animationId)
    {
        if (skill == null || animationId < 0)
        {
            return false;
        }

        Integer previousAnimation = lastAnimations.get(skill);
        boolean previouslyActive = wasActive.getOrDefault(skill, false);

        boolean newAction =
                !previouslyActive
                        || previousAnimation == null
                        || previousAnimation != animationId;

        lastAnimations.put(skill, animationId);
        wasActive.put(skill, true);

        return newAction;
    }

    public void markIdle(Skill skill)
    {
        if (skill != null)
        {
            wasActive.put(skill, false);
        }
    }

    public void reset()
    {
        lastAnimations.clear();
        wasActive.clear();
    }
}