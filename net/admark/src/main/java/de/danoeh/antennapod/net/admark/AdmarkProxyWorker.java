package de.danoeh.antennapod.net.admark;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

public class AdmarkProxyWorker extends Worker {
    private static final String TAG = "AdmarkProxyWorker";
    public static final String WORK_NAME_RETRY = "AdmarkProxyRetry";
    public static final String WORK_NAME_MIGRATE = "AdmarkProxyMigrate";
    public static final String KEY_MODE = "mode";
    public static final String MODE_RETRY = "retry";
    public static final String MODE_MIGRATE = "migrate";

    public AdmarkProxyWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    public static void enqueueRetry(@NonNull Context context) {
        enqueue(context, WORK_NAME_RETRY, MODE_RETRY, ExistingWorkPolicy.KEEP);
    }

    public static void enqueueMigrate(@NonNull Context context) {
        enqueue(context, WORK_NAME_MIGRATE, MODE_MIGRATE, ExistingWorkPolicy.REPLACE);
    }

    private static void enqueue(@NonNull Context context, @NonNull String workName,
                                @NonNull String mode, @NonNull ExistingWorkPolicy policy) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(AdmarkProxyWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .setInputData(new androidx.work.Data.Builder().putString(KEY_MODE, mode).build())
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(workName, policy, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        String mode = getInputData().getString(KEY_MODE);
        try {
            if (MODE_MIGRATE.equals(mode)) {
                boolean ok = AdmarkService.getInstance().migrateAllSubscriptions(getApplicationContext());
                return ok ? Result.success() : Result.retry();
            }
            boolean ok = AdmarkService.getInstance().retryPendingProxies(getApplicationContext());
            return ok ? Result.success() : Result.retry();
        } catch (Exception e) {
            Log.w(TAG, "proxy work failed: " + e.getMessage());
            return Result.retry();
        }
    }
}
