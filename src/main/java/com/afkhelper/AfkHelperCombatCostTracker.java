package com.afkhelper;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.game.ItemManager;

@Singleton
public class AfkHelperCombatCostTracker
{
    private final ItemManager itemManager;

    private final Map<Integer, Integer> previousItems = new HashMap<>();
    private final Map<Integer, Integer> consumedItems = new HashMap<>();
    private final Map<Integer, Long> itemPrices = new HashMap<>();

    private long totalConsumedValue;
    private long sessionStartTime;
    private boolean initialised;

    @Inject
    public AfkHelperCombatCostTracker(ItemManager itemManager)
    {
        this.itemManager = itemManager;
        reset();
    }

    public void updateInventory(int[] itemIds, int[] quantities)
    {
        updateCombined(itemIds, quantities, null, null, null, null);
    }

    public void updateEquipment(int[] itemIds, int[] quantities)
    {
        updateCombined(null, null, itemIds, quantities, null, null);
    }

    public void updateRunePouch(int[] runeIds, int[] runeAmounts)
    {
        updateCombined(null, null, null, null, runeIds, runeAmounts);
    }

    public void updateCombined(
            int[] inventoryIds,
            int[] inventoryQuantities,
            int[] equipmentIds,
            int[] equipmentQuantities,
            int[] runeIds,
            int[] runeAmounts)
    {
        Map<Integer, Integer> current = new HashMap<>();

        addItems(current, inventoryIds, inventoryQuantities);
        addItems(current, equipmentIds, equipmentQuantities);
        addItems(current, runeIds, runeAmounts);

        if (!initialised)
        {
            previousItems.putAll(current);
            initialised = true;
            return;
        }

        for (Map.Entry<Integer, Integer> entry : previousItems.entrySet())
        {
            int itemId = entry.getKey();
            int previousQuantity = entry.getValue();
            int currentQuantity = current.getOrDefault(itemId, 0);
            int difference = previousQuantity - currentQuantity;

            if (difference > 0 && isCombatSupply(itemId))
            {
                recordUsage(itemId, difference);
            }
        }

        previousItems.clear();
        previousItems.putAll(current);
    }

    private void addItems(
            Map<Integer, Integer> destination,
            int[] itemIds,
            int[] quantities)
    {
        if (itemIds == null || quantities == null)
        {
            return;
        }

        int length = Math.min(itemIds.length, quantities.length);

        for (int i = 0; i < length; i++)
        {
            int itemId = itemIds[i];
            int quantity = quantities[i];

            if (itemId > 0 && quantity > 0)
            {
                destination.merge(itemId, quantity, Integer::sum);
            }
        }
    }

    public void recordUsage(int itemId, int quantity)
    {
        if (itemId <= 0 || quantity <= 0 || !isCombatSupply(itemId))
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

    private boolean isCombatSupply(int itemId)
    {
        String name = itemManager.getItemComposition(itemId).getName();

        if (name == null)
        {
            return false;
        }

        String lower = name.toLowerCase();

        return lower.endsWith("arrow")
                || lower.endsWith("bolt")
                || lower.endsWith("dart")
                || lower.endsWith("javelin")
                || lower.endsWith("knife")
                || lower.endsWith("thrownaxe")
                || lower.endsWith("rune");
    }

    public long getTotalConsumedValue()
    {
        return totalConsumedValue;
    }

    public long getCostPerHour()
    {
        long elapsedSeconds = Math.max(
                1,
                (System.currentTimeMillis() - sessionStartTime) / 1000
        );

        return totalConsumedValue * 3600L / elapsedSeconds;
    }

    public Map<Integer, Integer> getItemsConsumed()
    {
        return new HashMap<>(consumedItems);
    }

    public int getConsumedQuantity(int itemId)
    {
        return consumedItems.getOrDefault(itemId, 0);
    }


    /**
     * Forget the previous inventory snapshot but keep the running totals,
     * so stats carry over to the next session without counting the
     * inventory loaded at login as items gained or used.
     */
    public void rebaseline()
    {
        previousItems.clear();
        initialised = false;
    }

    public void reset()
    {
        previousItems.clear();
        consumedItems.clear();
        itemPrices.clear();

        totalConsumedValue = 0;
        sessionStartTime = System.currentTimeMillis();
        initialised = false;
    }
}