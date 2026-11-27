package de.danoeh.antennapod.ui.episodeslist;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import de.danoeh.antennapod.actionbutton.DownloadActionButton;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.net.admark.AdmarkIntegration;
import de.danoeh.antennapod.net.admark.AdmarkScanSummary;
import de.danoeh.antennapod.ui.common.Converter;
import de.danoeh.antennapod.ui.common.ThemeUtils;

public final class AdmarkEpisodeBadge {
    public interface StillBound {
        boolean test(long itemId);
    }

    private AdmarkEpisodeBadge() {
    }

    public static void bind(@Nullable TextView badge, @Nullable TextView detail,
                            @NonNull FeedItem item, @NonNull Activity activity,
                            @NonNull StillBound stillBound, @Nullable Runnable onApplied) {
        TextView anchor = detail == null ? badge : detail;
        if (anchor == null) {
            return;
        }
        Object previous = anchor.getTag(R.id.admarkBinding);
        if (previous instanceof Binding) {
            ((Binding) previous).stop();
        }
        Binding binding = new Binding(badge, detail, item, activity, stillBound, onApplied);
        anchor.setEnabled(true);
        anchor.setTag(R.id.admarkBinding, binding);
        anchor.addOnAttachStateChangeListener(binding);
        binding.show(AdmarkIntegration.getStoredScan(item));
        binding.run();
    }

    private static final class Binding implements Runnable, View.OnAttachStateChangeListener {
        private final TextView badge;
        private final TextView detail;
        private final TextView anchor;
        private final FeedItem item;
        private final Activity activity;
        private final StillBound stillBound;
        private final Runnable onApplied;
        private boolean stopped;

        Binding(TextView badge, TextView detail, FeedItem item, Activity activity,
                StillBound stillBound, Runnable onApplied) {
            this.badge = badge;
            this.detail = detail;
            this.anchor = detail == null ? badge : detail;
            this.item = item;
            this.activity = activity;
            this.stillBound = stillBound;
            this.onApplied = onApplied;
        }

        private boolean current() {
            return !stopped && !activity.isDestroyed() && stillBound.test(item.getId());
        }

        void show(AdmarkScanSummary summary) {
            if (!current()) {
                return;
            }
            apply(badge, detail, summary, activity);
            boolean canAnalyze = AdmarkIntegration.tracks(item)
                    && (summary == null || summary.getStatus() == AdmarkScanSummary.Status.FAILED);
            if (summary == null && canAnalyze) {
                anchor.setVisibility(View.VISIBLE);
                anchor.setText(R.string.admark_analyze_episode);
                anchor.setContentDescription(activity.getString(R.string.admark_analyze_episode));
                anchor.setBackground(null);
                anchor.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null);
                anchor.setTextColor(ThemeUtils.getColorFromAttr(activity, R.attr.colorPrimary));
            }
            boolean canDownload = summary != null && summary.getStatus() == AdmarkScanSummary.Status.READY
                    && !summary.isAudioReady() && AdmarkIntegration.tracks(item);
            anchor.setOnClickListener(canAnalyze ? v -> {
                anchor.setEnabled(false);
                anchor.setText(R.string.admark_submitting);
                AdmarkIntegration.analyzeEpisode(item, result -> activity.runOnUiThread(() -> {
                    if (!current()) {
                        return;
                    }
                    anchor.setEnabled(true);
                    show(result);
                    schedule();
                }));
            } : canDownload ? v -> downloadAudio() : null);
            anchor.setClickable(canAnalyze || canDownload);
            if (onApplied != null) {
                onApplied.run();
            }
        }

        void downloadAudio() {
            if (!item.getMedia().isDownloaded()) {
                new DownloadActionButton(item).onClick(activity);
                schedule();
                return;
            }
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.admark_replace_audio)
                    .setMessage(R.string.admark_replace_audio_message)
                    .setNegativeButton(R.string.cancel_label, null)
                    .setPositiveButton(R.string.download_label, (dialog, which) ->
                            Single.fromCallable(() -> {
                                DBWriter.deleteFeedMediaOfItem(activity.getApplicationContext(), item.getMedia()).get();
                                return DBReader.getFeedItem(item.getId());
                            }).subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(updated -> {
                                        if (!activity.isDestroyed()) {
                                            new DownloadActionButton(updated).onClick(activity);
                                        } else {
                                            DownloadServiceInterface.get().downloadNow(
                                                    activity.getApplicationContext(), updated, false);
                                        }
                                    }, error -> Toast.makeText(activity, R.string.admark_download_failed,
                                            Toast.LENGTH_LONG).show()))
                    .show();
        }

        void schedule() {
            anchor.removeCallbacks(this);
            if (current() && anchor.isAttachedToWindow() && AdmarkIntegration.shouldQueryScan(item)) {
                anchor.postDelayed(this, 15000);
            }
        }

        @Override
        public void run() {
            if (!current() || !AdmarkIntegration.shouldQueryScan(item)) {
                return;
            }
            AdmarkIntegration.refreshStoredScan(item, summary -> activity.runOnUiThread(() -> {
                show(summary);
                schedule();
            }));
        }

        void stop() {
            stopped = true;
            anchor.removeCallbacks(this);
            anchor.removeOnAttachStateChangeListener(this);
        }

        @Override
        public void onViewAttachedToWindow(View view) {
            run();
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            anchor.removeCallbacks(this);
        }
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
        TextView indicatorView = detail == null ? badge : detail;
        if (indicatorView != null) {
            float density = indicatorView.getResources().getDisplayMetrics().density;
            GradientDrawable light = new GradientDrawable();
            light.setShape(GradientDrawable.OVAL);
            light.setColor(color);
            light.setSize((int) (10 * density), (int) (10 * density));
            indicatorView.setCompoundDrawablesRelativeWithIntrinsicBounds(light, null, null, null);
            indicatorView.setCompoundDrawablePadding((int) (6 * density));
        }
        if (badge != null) {
            badge.setVisibility(View.VISIBLE);
            badge.setText(scan.getStatus() == AdmarkScanSummary.Status.READY && scan.isAudioReady()
                    ? context.getString(R.string.admark_ready) : detailText(context, scan));
            badge.setTextColor(ThemeUtils.getColorFromAttr(context, R.attr.colorOnSurface));
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
            detail.setTextColor(ThemeUtils.getColorFromAttr(context, R.attr.colorOnSurface));
            detail.setContentDescription(text);
        }
    }

    @NonNull
    private static String detailText(@NonNull Context context, @NonNull AdmarkScanSummary scan) {
        String badge = context.getString(R.string.admark_badge);
        if (scan.getStatus() == AdmarkScanSummary.Status.READY) {
            if (!scan.isAudioReady()) {
                return context.getString(R.string.admark_download_audio);
            }
            int count = scan.getSegmentCount();
            if (count == 0) {
                return context.getString(R.string.admark_no_ads);
            }
            String segments = context.getResources().getQuantityString(
                    R.plurals.admark_segments, count, count);
            String time = Converter.getDurationStringLong(
                    (int) Math.min(scan.getTotalAdMs(), Integer.MAX_VALUE));
            return context.getString(R.string.admark_detail_ready, badge, segments, time);
        }
        if (scan.getStatus() == AdmarkScanSummary.Status.PENDING) {
            int stageId;
            String stage = scan.getStage();
            if ("queued".equals(stage)) {
                stageId = R.string.admark_queued;
            } else if ("transcribing".equals(stage)) {
                stageId = R.string.admark_transcribing;
            } else if ("detecting_ads".equals(stage)) {
                stageId = R.string.admark_detecting;
            } else if ("saving".equals(stage)) {
                stageId = R.string.admark_saving;
            } else {
                stageId = R.string.admark_running;
            }
            String stageText = context.getString(stageId);
            return scan.getPercent() < 0 ? stageText
                    : context.getString(R.string.admark_stage_percent, stageText, scan.getPercent());
        }
        return context.getString(R.string.admark_detail_failed, badge);
    }
}
