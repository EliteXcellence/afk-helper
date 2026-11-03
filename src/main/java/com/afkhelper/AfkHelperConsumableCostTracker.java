package com.afkhelper;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.game.ItemManager;

@Singleton
public class AfkHelperConsumableCostTracker
{
    private final ItemManager itemManager;

    private final Map<Integer, Integer> previousInventory = new HashMap<>();
    private final Map<Integer, Integer> consumedItems = new HashMap<>();
    private final Map<Integer, Long> itemPrices = new HashMap<>();

    private long totalConsumedValue;
    private long sessionStartTime;
    private boolean initialised;

    @Inject
    public AfkHelperConsumableCostTracker(ItemManager itemManager)
    {
        this.itemManager = itemManager;
        reset();
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

        for (Map.Entry<Integer, Integer> entry : previousInventory.entrySet())
        {
            int itemId = entry.getKey();
            int previousQuantity = entry.getValue();
            int currentQuantity = currentInventory.getOrDefault(itemId, 0);
            int difference = previousQuantity - currentQuantity;

            if (difference > 0)
            {
                recordConsumption(itemId, difference);
            }
        }

        previousInventory.clear();
        previousInventory.putAll(currentInventory);
    }

    private void recordConsumption(int itemId, int quantity)
    {
        if (itemId <= 0 || quantity <= 0)
        {
            return;
        }

        long price = itemPrices.computeIfAbsent(
                itemId,
                id -> itemManager.getItemPrice(id)
        );

        consumedItems.merge(itemId, quantity, Integer::sum);
        totalConsumedValue += price * quantity;
    }

    public long getTotalConsumedValue()
    {
        return totalConsumedValue;
    }

    public long getConsumedValuePerHour()
    {
        long elapsedSeconds = Math.max(
                1,
                (System.currentTimeMillis() - sessionStartTime) / 1000
        );

        return totalConsumedValue * 3600L / elapsedSeconds;
    }

    public int getConsumedQuantity(int itemId)
    {
        return consumedItems.getOrDefault(itemId, 0);
    }

    public Map<Integer, Integer> getConsumedItems()
    {
        return new HashMap<>(consumedItems);
    }

    public String getTotalConsumedValueText()
    {
        return formatValue(totalConsumedValue);
    }

    public String getConsumedValuePerHourText()
    {
        return formatValue(getConsumedValuePerHour()) + "/hr";
    }

    private String formatValue(long value)
    {
        return String.format("%,d gp", value);
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
        consumedItems.clear();
        itemPrices.clear();

        totalConsumedValue = 0;
        sessionStartTime = System.currentTimeMillis();
        initialised = false;
    }
}