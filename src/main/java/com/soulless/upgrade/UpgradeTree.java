package com.soulless.upgrade;

import java.util.List;

/**
 * How one artifact grows at the Soul Anvil: tiers bought in order, each for a price in souls. A tier offers one to
 * three options; with more than one the player picks a single path (a play style) and the others stay closed.
 * Option ids are what the item's code asks for ({@link Upgrades#has}); their names and descriptions come from the
 * lang file ("upgrade.soulless.<id>" and "upgrade.soulless.<id>.desc").
 */
public record UpgradeTree(List<Tier> tiers) {
    public record Tier(int souls, List<String> options) {
        public static Tier of(int souls, String... options) {
            return new Tier(souls, List.of(options));
        }

        public boolean choice() {
            return options.size() > 1;
        }
    }

    public int size() {
        return tiers.size();
    }
}
