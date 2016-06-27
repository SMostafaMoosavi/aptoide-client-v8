package cm.aptoide.pt.downloadmanager;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;

import com.liulishuo.filedownloader.BaseDownloadTask;
import com.liulishuo.filedownloader.FileDownloadLargeFileListener;
import com.liulishuo.filedownloader.FileDownloader;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import cm.aptoide.pt.database.Database;
import cm.aptoide.pt.database.realm.Download;
import cm.aptoide.pt.database.realm.FileToDownload;
import cm.aptoide.pt.logger.Logger;
import cm.aptoide.pt.preferences.Application;
import cm.aptoide.pt.utils.AptoideUtils;
import cm.aptoide.pt.utils.FileUtils;
import io.realm.Realm;
import lombok.Cleanup;
import lombok.Setter;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.observables.ConnectableObservable;

/**
 * Created by trinkes on 5/13/16.
 */
public class DownloadTask extends FileDownloadLargeFileListener {

	public static final int INTERVAL = 1000;    //interval between progress updates
	static public final int PROGRESS_MAX_VALUE = 100;
	public static final int APTOIDE_DOWNLOAD_TASK_TAG_KEY = 888;
	private static final String TAG = DownloadTask.class.getSimpleName();

	final Download download;
	private final long appId;
	/**
	 * this boolean is used to change between serial and parallel download (in this downloadTask) the default value is true
	 */
	@Setter
	boolean isSerial = true;
	NotificationCompat.Builder builder;
	Intent pauseDownloadsIntent;
	Intent openAppsManagerIntent;
	Intent resumeDownloadIntent;
	private ConnectableObservable<Integer> observable;
	private NotificationManager notificationManager;
	private Looper mLooper;

	public DownloadTask(Download download) {
		this.download = download;
		this.appId = download.getAppId();

		this.observable = Observable.interval(INTERVAL / 4, INTERVAL, TimeUnit.MILLISECONDS).map(aLong -> updateProgress()).filter(integer -> {
			if (integer <= PROGRESS_MAX_VALUE && download.getOverallDownloadStatus() == Download.PROGRESS) {
				if (integer == PROGRESS_MAX_VALUE && download.getOverallDownloadStatus() != Download.COMPLETED) {
					setDownloadStatus(Download.COMPLETED, download);
					removeNotification(download);
					AptoideDownloadManager.getInstance().startNextDownload();
				}
				return true;
			} else {
				return false;
			}
		}).publish();
		observable.connect();
	}

	@NonNull
	static String getFilePathFromFileType(FileToDownload fileToDownload) {
		String path;
		switch (fileToDownload.getFileType()) {
			case FileToDownload.APK:
				path = AptoideDownloadManager.APK_PATH;
				break;
			case FileToDownload.OBB:
				path = AptoideDownloadManager.OBB_PATH + fileToDownload.getPackageName();
				break;
			case FileToDownload.GENERIC:
			default:
				path = AptoideDownloadManager.GENERIC_PATH;
				break;
		}
		return path;
	}

	private void removeNotification(Download download) {
		notificationManager.cancel(download.getFilesToDownload().get(0).getDownloadId());
	}

	/**
	 * Update the overall download progress. It updates the value on database and in memory list
	 *
	 * @return new current progress
	 */
	@NonNull
	public Integer updateProgress() {
		if (download.getOverallProgress() >= PROGRESS_MAX_VALUE || download.getOverallDownloadStatus() != Download.PROGRESS) {
			return download.getOverallProgress();
		}

		int progress = 0;
		for (final FileToDownload fileToDownload : download.getFilesToDownload()) {
			progress += fileToDownload.getProgress();
		}
		download.setOverallProgress((int) Math.floor((float) progress / download.getFilesToDownload().size()));
		saveDownloadInDb(download);
		return download.getOverallProgress();
	}

	/**
	 * @throws IllegalArgumentException
	 */
	public void startDownload() throws IllegalArgumentException {
		if (download.getFilesToDownload() != null) {
			for (FileToDownload fileToDownload : download.getFilesToDownload()) {
				if (TextUtils.isEmpty(fileToDownload.getLink())) {
					throw new IllegalArgumentException("A link to download must be provided");
				}
				BaseDownloadTask baseDownloadTask = FileDownloader.getImpl().create(fileToDownload.getLink());
				baseDownloadTask.setTag(APTOIDE_DOWNLOAD_TASK_TAG_KEY, this);
				fileToDownload.setDownloadId(baseDownloadTask.setListener(this)
						.setCallbackProgressTimes(PROGRESS_MAX_VALUE)
						.setPath(AptoideDownloadManager.DOWNLOADS_STORAGE_PATH + fileToDownload.getFileName())
						.ready());
				fileToDownload.setAppId(appId);
			}

			if (isSerial) {
				// To form a queue with the same queueTarget and execute them linearly
				FileDownloader.getImpl().start(this, true);
			} else {
				// To form a queue with the same queueTarget and execute them in parallel
				FileDownloader.getImpl().start(this, false);
			}
		}
		buildNotification();
		saveDownloadInDb(download);
	}

	private void buildNotification() {
		mLooper = Looper.myLooper();
		if (mLooper == null) {
			Looper.prepare();
			Looper.loop();
		}

		Observable.fromCallable((Callable<Void>) () -> {
			pauseDownloadsIntent = new Intent(AptoideDownloadManager.getContext(), NotificationEventReceiver.class);
			pauseDownloadsIntent.setAction(NotificationEventReceiver.DOWNLOADMANAGER_ACTION_PAUSE);
			openAppsManagerIntent = new Intent(AptoideDownloadManager.getContext(), NotificationEventReceiver.class);
			openAppsManagerIntent.setAction(NotificationEventReceiver.DOWNLOADMANAGER_ACTION_OPEN);
			resumeDownloadIntent = new Intent(AptoideDownloadManager.getContext(), NotificationEventReceiver.class);
			resumeDownloadIntent.setAction(NotificationEventReceiver.DOWNLOADMANAGER_ACTION_RESUME);
			resumeDownloadIntent.putExtra(NotificationEventReceiver.APP_ID_EXTRA, download.getAppId());
			PendingIntent pPause = PendingIntent.getBroadcast(AptoideDownloadManager.getContext(), download.getFilesToDownload()
					.get(0)
					.getDownloadId(), pauseDownloadsIntent, 0);
			PendingIntent pOpenAppsManager = PendingIntent.getBroadcast(AptoideDownloadManager.getContext(), download.getFilesToDownload()
					.get(0)
					.getDownloadId(), openAppsManagerIntent, 0);
			PendingIntent pResume = PendingIntent.getBroadcast(AptoideDownloadManager.getContext(), download.getFilesToDownload()
					.get(0)
					.getDownloadId(), resumeDownloadIntent, 0);

			builder = new NotificationCompat.Builder(AptoideDownloadManager.getContext()).setSmallIcon(android.R.drawable.ic_menu_edit)
					.setAutoCancel(false)
					.setOngoing(true)
					.setContentTitle(String.format(AptoideDownloadManager.getContext()
							.getResources()
							.getString(R.string.aptoide_downloading), Application.getConfiguration().getMarketName()))
					.setContentText(new StringBuilder().append(download.getAppName())
							.append(AptoideDownloadManager.getContext().getResources().getString(R.string.status))
							.append(download.getOverallDownloadStatus()))
					.setContentIntent(pOpenAppsManager)
					.setProgress(PROGRESS_MAX_VALUE, 0, false)
					.addAction(android.R.drawable.ic_menu_edit, AptoideDownloadManager.getContext().getString(R.string.pause_download), pPause)
					.addAction(android.R.drawable.ic_menu_edit, AptoideDownloadManager.getContext().getString(R.string.open_apps_manager), pOpenAppsManager);
			Notification notification = builder.build();

			notificationManager = (NotificationManager) AptoideDownloadManager.getContext().getSystemService(Context.NOTIFICATION_SERVICE);

			notificationManager.notify(download.getFilesToDownload().get(0).getDownloadId(), notification);

			final boolean[] isOngoing = {true};
			@Cleanup
			Realm realm = Database.get();
			realm.where(Download.class).equalTo("appId", appId).findAll().asObservable().subscribe(res -> {
				if ((res.size() > 0) && notificationManager != null) {
					Download download1 = res.get(0);
					boolean isToggled = false;
					if (download1.getOverallDownloadStatus() == Download.PROGRESS && !isOngoing[0]) {
						isOngoing[0] = !isOngoing[0];
						isToggled = true;
					} else if (download1.getOverallDownloadStatus() != Download.PROGRESS && isOngoing[0]) {
						isOngoing[0] = !isOngoing[0];
						isToggled = true;
					}
					if (isOngoing[0]) {
						builder.mActions.get(0).title = AptoideDownloadManager.getContext().getString(R.string.pause_download);
						builder.mActions.get(0).actionIntent = pPause;
					} else {
						builder.mActions.get(0).title = AptoideDownloadManager.getContext().getString(R.string.resume_download);
						builder.mActions.get(0).actionIntent = pResume;
					}

					if (isToggled) {
						builder.setProgress(PROGRESS_MAX_VALUE, download1.getOverallProgress(), false)
								.setContentText(new StringBuilder().append(download1.getAppName())
										.append(AptoideDownloadManager.getContext().getString(R.string.status))
										.append(Download.getStatusName(download1.getOverallDownloadStatus(), AptoideDownloadManager.getContext())))
								.setOngoing(isOngoing[0]);
						notificationManager.notify(download1.getFilesToDownload().get(0).getDownloadId(), builder.build());
					}
				}
			});
			if (!AptoideUtils.ThreadU.isUiThread()) {
				mLooper.quit();
			}
			return null;
		}).subscribeOn(AndroidSchedulers.from(mLooper)).subscribe();
	}

	private void saveDownloadInDb(Download download) {
		@Cleanup
		Realm realm = Database.get();
		Database.save(download, realm);
	}

	public Observable<Integer> getObservable() {
		return observable;
	}

	@Override
	protected void pending(BaseDownloadTask task, int soFarBytes, int totalBytes) {
		Logger.d(TAG, "pending() called with: " + "task = [" + task + "], soFarBytes = [" +
				soFarBytes + "], totalBytes = [" + totalBytes + "]");
		setDownloadStatus(Download.PENDING, download, task);
	}

	@Override
	protected void progress(BaseDownloadTask task, int soFarBytes, int totalBytes) {
		progress(task, (long) soFarBytes, (long) totalBytes);
	}

	@Override
	protected void paused(BaseDownloadTask task, int soFarBytes, int totalBytes) {
		Logger.d(TAG, "paused() called with: " + "task = [" + task + "], soFarBytes = [" +
				soFarBytes + "], totalBytes = [" + totalBytes + "]");
		download.setOverallDownloadStatus(Download.PAUSED);
		saveDownloadInDb(download);
	}

	private void setDownloadStatus(@Download.DownloadState int status, Download download) {
		setDownloadStatus(status, download, null);
	}

	private void setDownloadStatus(@Download.DownloadState int status, Download download, @Nullable BaseDownloadTask task) {
		if (task != null) {
			for (final FileToDownload fileToDownload : download.getFilesToDownload()) {
				if (fileToDownload.getDownloadId() == task.getId()) {
					fileToDownload.setStatus(status);
				}
			}
		}

		this.download.setOverallDownloadStatus(status);
		saveDownloadInDb(download);
	}

	@Override
	protected void pending(BaseDownloadTask task, long soFarBytes, long totalBytes) {
		Logger.d(TAG, "pending() called with: " + "task = [" + task + "], soFarBytes = [" + soFarBytes + "], " +
				"totalBytes = [" + totalBytes + "]");
		setDownloadStatus(Download.PENDING, download, task);
	}

	@Override
	protected void progress(BaseDownloadTask task, long soFarBytes, long totalBytes) {
		Logger.d(TAG, "progress() called with: " + "task = [" + task + "], soFarBytes = [" + soFarBytes + "], " +
				"totalBytes = [" + totalBytes + "]");
		for (FileToDownload fileToDownload : download.getFilesToDownload()) {
			if (fileToDownload.getDownloadId() == task.getId()) {
				fileToDownload.setProgress((int) Math.floor((float) soFarBytes / totalBytes * DownloadTask.PROGRESS_MAX_VALUE));
			}
		}
		setDownloadStatus(Download.PROGRESS, download, task);
	}

	@Override
	protected void blockComplete(BaseDownloadTask task) {
		Logger.d(TAG, "blockComplete() called with: " + "task = [" + task + "]");
	}

	@Override
	protected void completed(BaseDownloadTask task) {
		Logger.d(TAG, "completed() called with: " + "task = [" + task + "]");
		for (FileToDownload fileToDownload : download.getFilesToDownload()) {
			if (fileToDownload.getDownloadId() == task.getId()) {
				fileToDownload.setPath(getFilePathFromFileType(fileToDownload));
				fileToDownload.setStatus(Download.COMPLETED);
				moveFileToRightPlace(download);
				fileToDownload.setProgress(DownloadTask.PROGRESS_MAX_VALUE);
			}
		}
		saveDownloadInDb(download);
	}

	@Override
	protected void paused(BaseDownloadTask task, long soFarBytes, long totalBytes) {
		Logger.d(TAG, "paused() called with: " + "task = [" + task + "], soFarBytes = [" + soFarBytes + "], " +
				"totalBytes" +
				" = [" + totalBytes + "]");
		setDownloadStatus(Download.PAUSED, download, task);
	}

	@Override
	protected void error(BaseDownloadTask task, Throwable e) {
		Logger.d(TAG, "error() called with: " + "task = [" + task + "], e = [" + e + "]");
		Logger.printException(e);
		AptoideDownloadManager.getInstance().pauseDownload(download.getAppId());
		setDownloadStatus(Download.ERROR, download, task);
	}

	@Override
	protected void warn(BaseDownloadTask task) {
		Logger.d(TAG, "warn() called with: " + "task = [" + task + "]");
		setDownloadStatus(Download.WARN, download, task);
	}

	private void moveFileToRightPlace(Download download) {
		for (final FileToDownload fileToDownload : download.getFilesToDownload()) {
			if (fileToDownload.getStatus() != Download.COMPLETED) {
				return;
			}
		}

		for (final FileToDownload fileToDownload : download.getFilesToDownload()) {
			if (!FileUtils.copyFile(AptoideDownloadManager.DOWNLOADS_STORAGE_PATH, fileToDownload.getPath(), fileToDownload.getFileName())) {
				setDownloadStatus(Download.ERROR, download);
				AptoideDownloadManager.getInstance().pauseDownload(download.getAppId());
			}
		}
	}
}
