/*
 * Copyright (c) 2016.
 * Modified by Marcelo Benites on 04/10/2016.
 */

package cm.aptoide.pt.install;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import cm.aptoide.pt.AptoideApplication;
import cm.aptoide.pt.BaseService;
import cm.aptoide.pt.DeepLinkIntentReceiver;
import cm.aptoide.pt.R;
import cm.aptoide.pt.database.realm.Download;
import cm.aptoide.pt.database.realm.Installed;
import cm.aptoide.pt.download.DownloadAnalytics;
import cm.aptoide.pt.downloadmanager.AptoideDownloadManager;
import cm.aptoide.pt.logger.Logger;
import cm.aptoide.pt.repository.RepositoryFactory;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Named;
import rx.Completable;
import rx.Observable;
import rx.subscriptions.CompositeSubscription;

public class InstallService extends BaseService {

  public static final String TAG = "InstallService";

  public static final String ACTION_OPEN_DOWNLOAD_MANAGER = "OPEN_DOWNLOAD_MANAGER";
  public static final String ACTION_OPEN_APP_VIEW = "OPEN_APP_VIEW";
  public static final String ACTION_STOP_INSTALL = "STOP_INSTALL";
  public static final String ACTION_STOP_ALL_INSTALLS = "STOP_ALL_INSTALLS";
  public static final String ACTION_START_INSTALL = "START_INSTALL";
  public static final String ACTION_INSTALL_FINISHED = "INSTALL_FINISHED";
  public static final String EXTRA_INSTALLATION_MD5 = "INSTALLATION_MD5";
  public static final String EXTRA_INSTALLER_TYPE = "INSTALLER_TYPE";
  public static final String EXTRA_FORCE_DEFAULT_INSTALL = "EXTRA_FORCE_DEFAULT_INSTALL";
  public static final int INSTALLER_TYPE_DEFAULT = 0;

  private static final int NOTIFICATION_ID = 8;

  @Inject AptoideDownloadManager downloadManager;
  @Inject @Named("default") Installer defaultInstaller;
  @Inject InstalledRepository installedRepository;
  @Inject DownloadAnalytics downloadAnalytics;
  private InstallManager installManager;
  private CompositeSubscription subscriptions;
  private Notification notification;
  private String marketName;

  @Override public void onCreate() {
    super.onCreate();
    getApplicationComponent().inject(this);
    Logger.d(TAG, "Install service is starting");
    final AptoideApplication application = (AptoideApplication) getApplicationContext();
    installManager = application.getInstallManager();
    marketName = application.getMarketName();
    subscriptions = new CompositeSubscription();
    setupNotification();
    installedRepository = RepositoryFactory.getInstalledRepository(getApplicationContext());
  }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null) {
      String md5 = intent.getStringExtra(EXTRA_INSTALLATION_MD5);
      if (ACTION_START_INSTALL.equals(intent.getAction())) {
        subscriptions.add(downloadAndInstall(this, md5, intent.getExtras()
            .getBoolean(EXTRA_FORCE_DEFAULT_INSTALL, false)).subscribe(
            hasNext -> treatNext(hasNext), throwable -> removeNotificationAndStop()));
      } else if (ACTION_STOP_INSTALL.equals(intent.getAction())) {
        subscriptions.add(stopDownload(md5).subscribe(hasNext -> treatNext(hasNext),
            throwable -> removeNotificationAndStop()));
      } else if (ACTION_OPEN_APP_VIEW.equals(intent.getAction())) {
        openAppView(md5);
      } else if (ACTION_OPEN_DOWNLOAD_MANAGER.equals(intent.getAction())) {
        openDownloadManager();
      } else if (ACTION_STOP_ALL_INSTALLS.equals(intent.getAction())) {
        stopAllDownloads();
      }
    } else {
      subscriptions.add(
          downloadAndInstallCurrentDownload(this, false).subscribe(hasNext -> treatNext(hasNext),
              throwable -> removeNotificationAndStop()));
    }
    return START_STICKY;
  }

  @Override public void onDestroy() {
    subscriptions.unsubscribe();
    super.onDestroy();
  }

  @Nullable @Override public IBinder onBind(Intent intent) {
    return null;
  }

  private Observable<Boolean> stopDownload(String md5) {
    return downloadManager.pauseDownloadSync(md5)
        .andThen(hasNextDownload());
  }

  private void stopAllDownloads() {
    downloadManager.pauseAllDownloads();
    removeNotificationAndStop();
  }

  private void treatNext(boolean hasNext) {
    if (!hasNext) {
      removeNotificationAndStop();
    }
  }

  private Observable<Boolean> downloadAndInstallCurrentDownload(Context context,
      boolean forceDefaultInstall) {
    return downloadManager.getCurrentDownload()
        .first()
        .flatMap(currentDownload -> downloadAndInstall(context, currentDownload.getMd5(),
            forceDefaultInstall));
  }

  private Observable<Boolean> downloadAndInstall(Context context, String md5,
      boolean forceDefaultInstall) {
    return downloadManager.getDownload(md5)
        .first()
        .doOnNext(download -> initInstallationProgress(download))
        .flatMap(download -> downloadManager.startDownload(download)
            .first())
        .flatMap(download -> downloadManager.getDownload(download.getMd5()))
        .doOnNext(download -> {
          stopOnDownloadError(download.getOverallDownloadStatus());
          if (download.getOverallDownloadStatus() == Download.PROGRESS) {
            downloadAnalytics.startProgress(download);
          }
        })
        .first(download -> download.getOverallDownloadStatus() == Download.COMPLETED)
        .flatMap(download -> stopForegroundAndInstall(context, download, true,
            forceDefaultInstall).andThen(sendBackgroundInstallFinishedBroadcast(download))
            .andThen(hasNextDownload()));
  }

  private void initInstallationProgress(Download download) {
    Installed installed = convertDownloadToInstalled(download);
    installedRepository.save(installed);
  }

  private void stopOnDownloadError(int downloadStatus) {
    if (downloadStatus == Download.ERROR) {
      removeNotificationAndStop();
    }
  }

  private Observable<Boolean> hasNextDownload() {
    return downloadManager.getCurrentDownloads()
        .first()
        .map(downloads -> downloads != null && !downloads.isEmpty());
  }

  private void removeNotificationAndStop() {
    stopForeground(true);
    stopSelf();
  }

  private Completable sendBackgroundInstallFinishedBroadcast(Download download) {
    return Completable.fromAction(() -> {
      sendBroadcast(
          new Intent(ACTION_INSTALL_FINISHED).putExtra(EXTRA_INSTALLATION_MD5, download.getMd5()));
    });
  }

  private Completable stopForegroundAndInstall(Context context, Download download,
      boolean removeNotification, boolean forceDefaultInstall) {
    Installer installer = getInstaller(download.getMd5());
    stopForeground(removeNotification);
    switch (download.getAction()) {
      case Download.ACTION_INSTALL:
        return installer.install(context, download.getMd5(), forceDefaultInstall);
      case Download.ACTION_UPDATE:
        return installer.update(context, download.getMd5(), forceDefaultInstall);
      case Download.ACTION_DOWNGRADE:
        return installer.downgrade(context, download.getMd5(), forceDefaultInstall);
      default:
        return Completable.error(
            new IllegalArgumentException("Invalid download action " + download.getAction()));
    }
  }

  private Installer getInstaller(String md5) {
    return defaultInstaller;
  }

  private void setupNotification() {
    subscriptions.add(installManager.getCurrentInstallation()
        .subscribe(installation -> {
          if (!installation.isIndeterminate()) {

            String md5 = installation.getMd5();
            int requestCode = md5.hashCode();

            NotificationCompat.Action downloadManagerAction =
                getDownloadManagerAction(requestCode, md5);
            PendingIntent appViewPendingIntent =
                getPendingIntent(requestCode, ACTION_OPEN_APP_VIEW, md5);
            NotificationCompat.Action pauseAction = getPauseAction(requestCode, md5);

            if (notification == null) {
              notification = buildNotification(installation, pauseAction, downloadManagerAction,
                  appViewPendingIntent);
            } else {
              long oldWhen = notification.when;
              notification = buildNotification(installation, pauseAction, downloadManagerAction,
                  appViewPendingIntent);
              notification.when = oldWhen;
            }

            startForeground(NOTIFICATION_ID, notification);
          }
        }, throwable -> removeNotificationAndStop()));
  }

  @NonNull private NotificationCompat.Action getPauseAction(int requestCode, String md5) {
    Bundle appIdExtras = new Bundle();
    appIdExtras.putString(AptoideDownloadManager.FILE_MD5_EXTRA, md5);
    return getAction(cm.aptoide.pt.downloadmanager.R.drawable.media_pause,
        getString(cm.aptoide.pt.downloadmanager.R.string.pause_download), requestCode,
        ACTION_STOP_INSTALL, md5);
  }

  @NonNull private NotificationCompat.Action getDownloadManagerAction(int requestCode, String md5) {
    Bundle appIdExtras = new Bundle();
    appIdExtras.putString(AptoideDownloadManager.FILE_MD5_EXTRA, md5);
    return getAction(R.drawable.ic_manager, getString(R.string.open_apps_manager), requestCode,
        ACTION_OPEN_DOWNLOAD_MANAGER, md5);
  }

  private Notification buildNotification(Install installation,
      NotificationCompat.Action pauseAction, NotificationCompat.Action openDownloadManager,
      PendingIntent contentIntent) {
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
    builder.setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(String.format(Locale.ENGLISH,
            getResources().getString(cm.aptoide.pt.downloadmanager.R.string.aptoide_downloading),
            marketName))
        .setContentText(new StringBuilder().append(installation.getAppName())
            .append(" - ")
            .append(getString(cm.aptoide.pt.database.R.string.download_progress)))
        .setContentIntent(contentIntent)
        .setProgress(AptoideDownloadManager.PROGRESS_MAX_VALUE, installation.getProgress(),
            installation.isIndeterminate())
        .addAction(pauseAction)
        .addAction(openDownloadManager);
    return builder.build();
  }

  private NotificationCompat.Action getAction(int icon, String title, int requestCode,
      String action, String md5) {
    return new NotificationCompat.Action(icon, title, getPendingIntent(requestCode, action, md5));
  }

  private PendingIntent getPendingIntent(int requestCode, String action, String md5) {
    Intent intent = new Intent(this, InstallService.class);
    if (!TextUtils.isEmpty(md5)) {
      final Bundle bundle = new Bundle();
      bundle.putString(EXTRA_INSTALLATION_MD5, md5);
      intent.putExtras(bundle);
    }
    return PendingIntent.getService(this, requestCode, intent.setAction(action),
        PendingIntent.FLAG_ONE_SHOT);
  }

  private void openDownloadManager() {
    Intent intent = createDeeplinkingIntent();
    intent.putExtra(DeepLinkIntentReceiver.DeepLinksTargets.FROM_DOWNLOAD_NOTIFICATION, true);
    startActivity(intent);
  }

  private void openAppView(String md5) {
    Intent intent = createDeeplinkingIntent();
    intent.putExtra(DeepLinkIntentReceiver.DeepLinksTargets.APP_VIEW_FRAGMENT, true);
    intent.putExtra(DeepLinkIntentReceiver.DeepLinksKeys.APP_MD5_KEY, md5);
    startActivity(intent);
  }

  @NonNull private Intent createDeeplinkingIntent() {
    Intent intent = new Intent();
    intent.setClass(getApplicationContext(), AptoideApplication.getActivityProvider()
        .getMainActivityFragmentClass());
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    return intent;
  }

  @NonNull private Installed convertDownloadToInstalled(Download download) {
    Installed installed = new Installed();
    installed.setPackageAndVersionCode(download.getPackageName() + download.getVersionCode());
    installed.setVersionCode(download.getVersionCode());
    installed.setVersionName(download.getVersionName());
    installed.setStatus(Installed.STATUS_WAITING);
    installed.setType(Installed.TYPE_UNKNOWN);
    installed.setPackageName(download.getPackageName());
    return installed;
  }
}
