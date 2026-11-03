package com.afkhelper;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.game.ItemManager;

@Singleton
public class AfkHelperProfitTracker
{
    private final ItemManager itemManager;

    private final Map<Integer, Integer> previousInventory = new HashMap<>();
    private final Map<Integer, Integer> itemsGained = new HashMap<>();
    private final Map<Integer, Integer> itemsConsumed = new HashMap<>();
    private final Map<Integer, Long> itemPrices = new HashMap<>();

    private long totalGainedValue;
    private long totalConsumedValue;
    private boolean initialised;

    @Inject
    public AfkHelperProfitTracker(ItemManager itemManager)
    {
        this.itemManager = itemManager;
    }

    public void updateInventory(int[] itemIds, int[] quantities)
    {
        Map<Integer, Integer> currentInventory = new HashMap<>();

        if (itemIds != null && quantities != null)
        {
            int length = Math.min(itemIds.length, quantities.length);

            for (int i = 0; i < length; i++)
            {
                if (itemIds[i] > 0 && quantities[i] > 0)
                {
                    currentInventory.merge(
                            itemIds[i],
                            quantities[i],
                            Integer::sum
                    );
                }
            }
        }

        if (!initialised)
        {
            previousInventory.putAll(currentInventory);
            initialised = true;
            return;
        }

        Map<Integer, Integer> allItemIds = new HashMap<>(previousInventory);

        for (Map.Entry<Integer, Integer> entry : currentInventory.entrySet())
        {
            allItemIds.putIfAbsent(entry.getKey(), 0);
        }

        for (Integer itemId : allItemIds.keySet())
        {
            int previousQuantity = previousInventory.getOrDefault(itemId, 0);
            int currentQuantity = currentInventory.getOrDefault(itemId, 0);
            int difference = currentQuantity - previousQuantity;

            if (difference > 0)
            {
                recordGain(itemId, difference);
            }
            else if (difference < 0)
            {
                recordConsumption(itemId, -difference);
            }
        }

        previousInventory.clear();
        previousInventory.putAll(currentInventory);
    }

    private long getPrice(int itemId)
    {
        return itemPrices.computeIfAbsent(
                itemId,
                id -> Math.max(0L, itemManager.getItemPrice(id))
        );
    }

    private void recordGain(int itemId, int quantity)
    {
        itemsGained.merge(itemId, quantity, Integer::sum);
        totalGainedValue += getPrice(itemId) * quantity;
    }

    private void recordConsumption(int itemId, int quantity)
    {
        itemsConsumed.merge(itemId, quantity, Integer::sum);
        totalConsumedValue += getPrice(itemId) * quantity;
    }

    public long getTotalGainedValue()
    {
        return totalGainedValue;
    }

    public long getTotalConsumedValue()
    {
        return totalConsumedValue;
    }

    public long getEstimatedProfit()
    {
        return totalGainedValue - totalConsumedValue;
    }

    public Map<Integer, Integer> getItemsGained()
    {
        return new HashMap<>(itemsGained);
    }

    public Map<Integer, Integer> getItemsConsumed()
    {
        return new HashMap<>(itemsConsumed);
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
        itemsGained.clear();
        itemsConsumed.clear();
        itemPrices.clear();

        totalGainedValue = 0;
        totalConsumedValue = 0;
        initialised = false;
    }
}