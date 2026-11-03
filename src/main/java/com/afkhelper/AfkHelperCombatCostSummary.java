package com.afkhelper;

import java.util.Map;

public class AfkHelperCombatCostSummary
{
    private final AfkHelperCombatCostTracker tracker;

    public AfkHelperCombatCostSummary(AfkHelperCombatCostTracker tracker)
    {
        this.tracker = tracker;
    }

    public long getTotalCost()
    {
        return tracker == null ? 0L : tracker.getTotalConsumedValue();
    }

    public double getCostPerHour()
    {
        return tracker == null ? 0.0 : tracker.getCostPerHour();
    }

    public String getTotalCostText()
    {
        return formatGp(getTotalCost());
    }

    public String getHourlyCostText()
    {
        return formatGp(Math.round(getCostPerHour())) + "/hr";
    }

    public String getSummary()
    {
        if (tracker == null)
        {
            return "Combat supplies: unavailable";
        }

        return "Combat supplies: " + getTotalCostText()
                + " (" + getHourlyCostText() + ")";
    }

    private String formatGp(long value)
    {
        if (value >= 1_000_000)
        {
            return String.format("%.2fm", value / 1_000_000.0);
        }

        if (value >= 1_000)
        {
            return String.format("%.1fk", value / 1_000.0);
        }

        return value + " gp";
    }
}