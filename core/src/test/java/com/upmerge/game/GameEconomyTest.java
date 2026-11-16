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

    @Test public void eachToolHasItsOwnPackPriceAndFailedPurchaseIsAtomic() {
        GameEconomy economy = new GameEconomy(54L);
        assertFalse(economy.buyPack(GameEconomy.Tool.BOMB));
        assertEquals(54L, economy.coins());
        assertTrue(economy.buyPack(GameEconomy.Tool.SPLIT));
        assertEquals(19L, economy.coins());
        assertFalse(economy.buyPack(GameEconomy.Tool.MOVE));
        economy.setCoins(55L);
        assertTrue(economy.buyPack(GameEconomy.Tool.BOMB));
        assertEquals(0L, economy.coins());
        assertFalse(economy.buyPack(GameEconomy.Tool.BOMB));
    }

    @Test public void rewardsSaturateInsteadOfOverflowing() {
        GameEconomy economy = new GameEconomy(Long.MAX_VALUE - 1L);
        assertEquals(1L, economy.rewardForMerge(30L));
        assertEquals(Long.MAX_VALUE, economy.coins());
        assertEquals(0L, economy.rewardForMerge(0L));
    }

    @Test public void directAchievementGrantsSaturateSafely() {
        GameEconomy economy = new GameEconomy(Long.MAX_VALUE - 2L);
        economy.grantCoins(10L);
        assertEquals(Long.MAX_VALUE, economy.coins());
        economy.grantCoins(-5L);
        assertEquals(Long.MAX_VALUE, economy.coins());
    }

    @Test public void negativeStoredBalanceIsClampedToZero() {
        GameEconomy economy = new GameEconomy(-100L);
        assertEquals(0L, economy.coins());
        economy.setCoins(-1L);
        assertEquals(0L, economy.coins());
    }
}
