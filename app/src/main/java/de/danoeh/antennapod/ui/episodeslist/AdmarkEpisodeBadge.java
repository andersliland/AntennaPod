package de.danoeh.antennapod.ui.episodeslist;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.net.admark.AdmarkIntegration;
import de.danoeh.antennapod.net.admark.AdmarkScanSummary;
import de.danoeh.antennapod.ui.common.Converter;

public final class AdmarkEpisodeBadge {
    public interface StillBound {
        boolean test(long itemId);
    }

    private AdmarkEpisodeBadge() {
    }

    public static void bind(@Nullable TextView badge, @Nullable TextView detail,
                            @NonNull FeedItem item, @NonNull Activity activity,
                            @NonNull StillBound stillBound, @Nullable Runnable onApplied) {
        apply(badge, detail, AdmarkIntegration.getStoredScan(item), activity);
        if (onApplied != null) {
            onApplied.run();
        }
        if (!AdmarkIntegration.shouldQueryScan(item)) {
            return;
        }
        final long itemId = item.getId();
        AdmarkIntegration.refreshStoredScan(item, summary -> activity.runOnUiThread(() -> {
            if (activity.isDestroyed() || !stillBound.test(itemId)) {
                return;
            }
            apply(badge, detail, summary, activity);
            if (onApplied != null) {
                onApplied.run();
            }
        }));
    }

    private static void apply(@Nullable TextView badge, @Nullable TextView detail,
                              @Nullable AdmarkScanSummary scan, @NonNull Context context) {
        if (scan == null) {
            if (badge != null) {
                badge.setVisibility(View.GONE);
            }
            if (detail != null) {
                detail.setVisibility(View.GONE);
            }
            return;
        }
        String text = detailText(context, scan);
        int color = scan.getTrafficLightColor();
        if (badge != null) {
            badge.setVisibility(View.VISIBLE);
            badge.setText(R.string.admark_badge);
            badge.setTextColor(color);
            badge.setContentDescription(text);
            float density = badge.getResources().getDisplayMetrics().density;
            GradientDrawable drawable = new GradientDrawable();
            drawable.setCornerRadius(8f * density);
            drawable.setStroke(Math.max(1, (int) density), color);
            badge.setBackground(drawable);
        }
        if (detail != null) {
            detail.setVisibility(View.VISIBLE);
            detail.setText(text);
            detail.setTextColor(color);
            detail.setContentDescription(text);
        }
    }

    @NonNull
    private static String detailText(@NonNull Context context, @NonNull AdmarkScanSummary scan) {
        String badge = context.getString(R.string.admark_badge);
        if (scan.getStatus() == AdmarkScanSummary.Status.READY) {
            int count = scan.getSegmentCount();
            String segments = context.getResources().getQuantityString(
                    R.plurals.admark_segments, count, count);
            String time = Converter.getDurationStringLong(
                    (int) Math.min(scan.getTotalAdMs(), Integer.MAX_VALUE));
            return context.getString(R.string.admark_detail_ready, badge, segments, time);
        }
        if (scan.getStatus() == AdmarkScanSummary.Status.PENDING) {
            return context.getString(R.string.admark_detail_pending, badge);
        }
        return context.getString(R.string.admark_detail_failed, badge);
    }
}
