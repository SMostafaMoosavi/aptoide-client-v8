package cm.aptoide.pt.home.apps;

import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import cm.aptoide.pt.R;
import cm.aptoide.pt.networking.image.ImageLoader;
import rx.subjects.PublishSubject;

/**
 * Created by filipegoncalves on 3/12/18.
 */

class ActiveAppDownloadViewHolder extends AppsViewHolder {

  private TextView appName;
  private ImageView appIcon;
  private ProgressBar progressBar;
  private TextView downloadState;
  private TextView downloadProgress;
  private ImageView pauseButton;
  private PublishSubject<App> pauseDownload;

  public ActiveAppDownloadViewHolder(View itemView, PublishSubject<App> pauseDownload) {
    super(itemView);

    appName = (TextView) itemView.findViewById(R.id.app_downloads_app_name);
    appIcon = (ImageView) itemView.findViewById(R.id.app_downloads_icon);
    progressBar = (ProgressBar) itemView.findViewById(R.id.app_downloads_progress_bar);
    downloadState = (TextView) itemView.findViewById(R.id.app_downloads_download_state);
    downloadProgress = (TextView) itemView.findViewById(R.id.app_download_progress_number);
    pauseButton = (ImageView) itemView.findViewById(R.id.app_download_pause_button);
    this.pauseDownload = pauseDownload;
  }

  @Override public void setApp(App app) {
    ImageLoader.with(itemView.getContext())
        .load(((DownloadApp) app).getIcon(), appIcon);
    appName.setText(((DownloadApp) app).getAppName());

    progressBar.setProgress(((DownloadApp) app).getProgress());
    downloadState.setText(R.string.apps_short_downloading);
    downloadProgress.setText(String.format("%d%%", ((DownloadApp) app).getProgress()));

    pauseButton.setOnClickListener(pause -> pauseDownload.onNext(app));
  }
}