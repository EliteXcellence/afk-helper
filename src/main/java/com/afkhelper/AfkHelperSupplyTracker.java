package com.afkhelper;

import java.util.HashMap;
import java.util.Map;

public class AfkHelperSupplyTracker
{
    private final Map<Integer, Integer> previousInventory = new HashMap<>();
    private final Map<Integer, Integer> suppliesConsumed = new HashMap<>();
    private final Map<Integer, Integer> itemsGained = new HashMap<>();

    private boolean initialised;

    public void updateInventory(int[] itemIds, int[] quantities)
    {
        if (itemIds == null || quantities == null
                || itemIds.length != quantities.length)
        {
            return;
        }

        Map<Integer, Integer> current = new HashMap<>();

        for (int i = 0; i < itemIds.length; i++)
        {
            if (itemIds[i] > 0 && quantities[i] > 0)
            {
                current.merge(itemIds[i], quantities[i], Integer::sum);
            }
        }

        if (!initialised)
        {
            previousInventory.putAll(current);
            initialised = true;
            return;
        }

        for (Map.Entry<Integer, Integer> entry : previousInventory.entrySet())
        {
            int id = entry.getKey();
            int difference = entry.getValue() - current.getOrDefault(id, 0);

            if (difference > 0)
            {
                suppliesConsumed.merge(id, difference, Integer::sum);
            }
        }

        for (Map.Entry<Integer, Integer> entry : current.entrySet())
        {
            int id = entry.getKey();
            int difference = entry.getValue() - previousInventory.getOrDefault(id, 0);

            if (difference > 0)
            {
                itemsGained.merge(id, difference, Integer::sum);
            }
        }

        previousInventory.clear();
        previousInventory.putAll(current);
    }

    public int getSuppliesConsumed(int itemId)
    {
        return suppliesConsumed.getOrDefault(itemId, 0);
    }

    public int getItemsGained(int itemId)
    {
        return itemsGained.getOrDefault(itemId, 0);
    }

    public int getTotalSuppliesConsumed()
    {
        return suppliesConsumed.values().stream()
                .mapToInt(Integer::intValue).sum();
    }

    public int getTotalItemsGained()
    {
        return itemsGained.values().stream()
                .mapToInt(Integer::intValue).sum();
    }

    public Map<Integer, Integer> getSuppliesConsumed()
    {
        return new HashMap<>(suppliesConsumed);
    }

    public Map<Integer, Integer> getItemsGained()
    {
        return new HashMap<>(itemsGained);
    }

    public String getSummary()
    {
        return "Consumed: " + getTotalSuppliesConsumed()
                + " | Gained: " + getTotalItemsGained();
    }


    /**
     * Forget the previous inventory snapshot but keep the running totals,
     * so stats carry over to the next session without counting the
     * inventory loaded at login as items gained or used.
     */
    public void rebaseline()
    {
        previousInventory.clear();
        initialised = false;
    }

    public void reset()
    {
        previousInventory.clear();
        suppliesConsumed.clear();
        itemsGained.clear();
        initialised = false;
    }
}