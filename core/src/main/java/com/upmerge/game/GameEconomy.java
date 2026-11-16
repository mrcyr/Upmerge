package com.upmerge.game;

/** Persistent soft-currency rules shared by gameplay rewards and the native shop. */
public final class GameEconomy {
    public static final int CHARGE_PACK_SIZE = 3;
    public enum Tool {
        SPLIT(35L), MOVE(25L), SWAP(30L), BOMB(55L), UNDO(40L);
        public final long packPrice;
        Tool(long packPrice) { this.packPrice = packPrice; }
    }

    private long coins;

    public GameEconomy(long initialCoins) { coins = Math.max(0L, initialCoins); }
    public long coins() { return coins; }

    /** Mirrors the 4.11 reward rule: at least one coin, then one per 30 points. */
    public long rewardForMerge(long points) {
        if (points <= 0L) return 0L;
        long reward = Math.max(1L, points / 30L);
        coins = coins > Long.MAX_VALUE - reward ? Long.MAX_VALUE : coins + reward;
        return reward;
    }

    /** Purchases a three-charge pack; a failed purchase never changes the balance. */
    public boolean buyPack(Tool tool) {
        if (tool == null || coins < tool.packPrice) return false;
        coins -= tool.packPrice;
        return true;
    }

    public void setCoins(long value) { coins = Math.max(0L, value); }
}
