package de.danoeh.antennapod.net.admark;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure helpers around the comma-separated most-recently-used key lists used by
 * {@code AdmarkPreferences} to cap stored entries. Kept free of Android
 * framework classes so they can be unit tested on the JVM.
 */
public final class AdmarkKeyLru {
    private AdmarkKeyLru() {
    }

    public static final class Result {
        public final String order;
        public final List<String> evicted;

        Result(String order, List<String> evicted) {
            this.order = order;
            this.evicted = evicted;
        }
    }

    /**
     * Moves {@code key} to the front of the CSV order and trims the list to
     * {@code maxEntries}, returning the new order plus the keys that fell off.
     */
    @NonNull
    public static Result push(@NonNull String currentOrder, @NonNull String key, int maxEntries) {
        List<String> keys = new ArrayList<>();
        keys.add(key);
        for (String previous : currentOrder.split(",", -1)) {
            if (previous != null && !previous.isEmpty() && !previous.equals(key)) {
                keys.add(previous);
            }
        }
        List<String> evicted = new ArrayList<>();
        StringBuilder kept = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i < maxEntries) {
                if (kept.length() > 0) {
                    kept.append(',');
                }
                kept.append(keys.get(i));
            } else if (!keys.get(i).isEmpty()) {
                evicted.add(keys.get(i));
            }
        }
        return new Result(kept.toString(), evicted);
    }

    @NonNull
    public static List<String> split(@NonNull String order) {
        List<String> keys = new ArrayList<>();
        for (String key : order.split(",", -1)) {
            if (!key.isEmpty()) {
                keys.add(key);
            }
        }
        return keys;
    }
}
