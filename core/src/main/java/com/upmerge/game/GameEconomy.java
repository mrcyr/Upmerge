package com.upmerge.game;

/** Persistent soft-currency rules shared by gameplay rewards and the native shop. */
public final class GameEconomy {
    public static final int SPLIT_CHARGE_PACK_SIZE = 3;
    public static final long SPLIT_CHARGE_PACK_PRICE = 35L;

    private long coins;

    public GameEconomy(long initialCoins) {
        coins = Math.max(0L, initialCoins);
    }

    public long coins() { return coins; }

    /** Mirrors the 4.11 reward rule: at least one coin, then one per 30 points. */
    public long rewardForMerge(long points) {
        if (points <= 0L) return 0L;
        long reward = Math.max(1L, points / 30L);
        coins = coins > Long.MAX_VALUE - reward ? Long.MAX_VALUE : coins + reward;
        return reward;
    }

    /** Buys one three-charge emergency-split pack. A failed purchase is atomic. */
    public boolean buySplitChargePack() {
        if (coins < SPLIT_CHARGE_PACK_PRICE) return false;
        coins -= SPLIT_CHARGE_PACK_PRICE;
        return true;
    }

    public void setCoins(long value) {
        coins = Math.max(0L, value);
    }
}
