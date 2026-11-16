package com.upmerge.game;

import org.junit.Test;
import static org.junit.Assert.*;

public class GameEconomyTest {
    @Test public void mergeRewardsFollowOriginalMinimumAndThirtyPointRule() {
        GameEconomy economy = new GameEconomy(0L);
        assertEquals(1L, economy.rewardForMerge(1L));
        assertEquals(1L, economy.rewardForMerge(29L));
        assertEquals(1L, economy.rewardForMerge(30L));
        assertEquals(3L, economy.rewardForMerge(99L));
        assertEquals(6L, economy.coins());
    }

    @Test public void splitPackPurchaseDeductsCoinsOnlyWhenAffordable() {
        GameEconomy economy = new GameEconomy(34L);
        assertFalse(economy.buySplitChargePack());
        assertEquals(34L, economy.coins());
        economy.setCoins(35L);
        assertTrue(economy.buySplitChargePack());
        assertEquals(0L, economy.coins());
        assertFalse(economy.buySplitChargePack());
    }

    @Test public void rewardsSaturateInsteadOfOverflowing() {
        GameEconomy economy = new GameEconomy(Long.MAX_VALUE - 1L);
        assertEquals(1L, economy.rewardForMerge(30L));
        assertEquals(Long.MAX_VALUE, economy.coins());
        assertEquals(0L, economy.rewardForMerge(0L));
    }

    @Test public void negativeStoredBalanceIsClampedToZero() {
        GameEconomy economy = new GameEconomy(-100L);
        assertEquals(0L, economy.coins());
        economy.setCoins(-1L);
        assertEquals(0L, economy.coins());
    }
}
