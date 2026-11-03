package com.afkhelper;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AfkHelperInventoryTracker
{
    private final AfkHelperSupplyTracker supplyTracker;
    private final AfkHelperProfitTracker profitTracker;
    private final AfkHelperConsumableCostTracker consumableCostTracker;
    private final AfkHelperCombatCostTracker combatCostTracker;

    private int[] inventoryItemIds = new int[0];
    private int[] inventoryQuantities = new int[0];
    private int[] equipmentItemIds = new int[0];
    private int[] equipmentQuantities = new int[0];
    private int[] runePouchItemIds = new int[0];
    private int[] runePouchQuantities = new int[0];

    @Inject
    public AfkHelperInventoryTracker(
            AfkHelperSupplyTracker supplyTracker,
            AfkHelperProfitTracker profitTracker,
            AfkHelperConsumableCostTracker consumableCostTracker,
            AfkHelperCombatCostTracker combatCostTracker)
    {
        this.supplyTracker = supplyTracker;
        this.profitTracker = profitTracker;
        this.consumableCostTracker = consumableCostTracker;
        this.combatCostTracker = combatCostTracker;
    }

    public void updateInventory(int[] itemIds, int[] quantities)
    {
        inventoryItemIds = copy(itemIds);
        inventoryQuantities = copy(quantities);

        supplyTracker.updateInventory(inventoryItemIds, inventoryQuantities);
        profitTracker.updateInventory(inventoryItemIds, inventoryQuantities);
        consumableCostTracker.updateInventory(inventoryItemIds, inventoryQuantities);

        updateCombinedCombatTracking();
    }

    public void updateEquipment(int[] itemIds, int[] quantities)
    {
        equipmentItemIds = copy(itemIds);
        equipmentQuantities = copy(quantities);
        updateCombinedCombatTracking();
    }

    public void updateRunePouch(int[] itemIds, int[] quantities)
    {
        runePouchItemIds = copy(itemIds);
        runePouchQuantities = copy(quantities);
        updateCombinedCombatTracking();
    }

    private void updateCombinedCombatTracking()
    {
        combatCostTracker.updateCombined(
                inventoryItemIds,
                inventoryQuantities,
                equipmentItemIds,
                equipmentQuantities,
                runePouchItemIds,
                runePouchQuantities
        );
    }

    private int[] copy(int[] values)
    {
        return values == null ? new int[0] : values.clone();
    }

    private int sum(Map<Integer, Integer> values)
    {
        return values.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    public void recordCombatItemUsage(int itemId, int quantity)
    {
        combatCostTracker.recordUsage(itemId, quantity);
    }

    public int getSuppliesConsumed()
    {
        return sum(supplyTracker.getSuppliesConsumed());
    }

    public int getItemsGained()
    {
        return sum(supplyTracker.getItemsGained());
    }

    public long getEstimatedProfit()
    {
        return profitTracker.getEstimatedProfit();
    }

    public long getConsumableTotalCost()
    {
        return consumableCostTracker.getTotalConsumedValue();
    }

    public long getConsumableCostPerHour()
    {
        return consumableCostTracker.getConsumedValuePerHour();
    }

    public long getCombatCostPerHour()
    {
        return combatCostTracker.getCostPerHour();
    }

    public long getCombatTotalCost()
    {
        return combatCostTracker.getTotalConsumedValue();
    }

    public Map<Integer, Integer> getSuppliesConsumedByItem()
    {
        return supplyTracker.getSuppliesConsumed();
    }

    public Map<Integer, Integer> getItemsGainedByItem()
    {
        return supplyTracker.getItemsGained();
    }

    public Map<Integer, Integer> getCombatItemsConsumed()
    {
        return combatCostTracker.getItemsConsumed();
    }

    public String getSupplySummary()
    {
        return supplyTracker.getSummary();
    }

    /** Keep totals, but start comparing against a fresh inventory. */
    public void rebaseline()
    {
        supplyTracker.rebaseline();
        profitTracker.rebaseline();
        consumableCostTracker.rebaseline();
        combatCostTracker.rebaseline();

        inventoryItemIds = new int[0];
        inventoryQuantities = new int[0];
        equipmentItemIds = new int[0];
        equipmentQuantities = new int[0];
        runePouchItemIds = new int[0];
        runePouchQuantities = new int[0];
    }

    public void reset()
    {
        supplyTracker.reset();
        profitTracker.reset();
        consumableCostTracker.reset();
        combatCostTracker.reset();

        inventoryItemIds = new int[0];
        inventoryQuantities = new int[0];
        equipmentItemIds = new int[0];
        equipmentQuantities = new int[0];
        runePouchItemIds = new int[0];
        runePouchQuantities = new int[0];
    }
}