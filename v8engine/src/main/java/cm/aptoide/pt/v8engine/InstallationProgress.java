package cm.aptoide.pt.v8engine;

import android.support.annotation.IntRange;
import android.support.annotation.Nullable;

/**
 * Created by trinkes on 10/04/2017.
 */

public class InstallationProgress {
  private final int progress;
  private final InstallationStatus state;
  private final boolean isIndeterminate;
  private final int speed;
  private final String md5;
  private final String packageName;
  private final int versionCode;
  private String appName;
  private String icon;

  public InstallationProgress(int progress, InstallationStatus state, boolean isIndeterminate,
      int speed, String md5, String packageName, int versionCode, String appName, String icon) {
    this.progress = progress;
    this.state = state;
    this.isIndeterminate = isIndeterminate;
    this.speed = speed;
    this.md5 = md5;
    this.packageName = packageName;
    this.versionCode = versionCode;
    this.appName = appName;
    this.icon = icon;
  }

  public InstallationProgress(int progress, InstallationStatus state, boolean isIndeterminate,
      int speed, String md5, String packageName, int versionCode) {
    this.progress = progress;
    this.state = state;
    this.isIndeterminate = isIndeterminate;
    this.speed = speed;
    this.md5 = md5;
    this.packageName = packageName;
    this.versionCode = versionCode;
    this.appName = null;
    this.icon = null;
  }

  public @IntRange(from = 0, to = 100) int getProgress() {
    return progress;
  }

  public InstallationStatus getState() {
    return state;
  }

  public boolean isIndeterminate() {
    return isIndeterminate;
  }

  public int getSpeed() {
    return speed;
  }

  public String getMd5() {
    return md5;
  }

  public String getPackageName() {
    return packageName;
  }

  public int getVersionCode() {
    return versionCode;
  }

  /**
   * @return null if the app is uninstalled and there is no installation in progress
   */
  public @Nullable String getAppName() {
    return appName;
  }

  /**
   * @return null if the app is uninstalled and there is no installation in progress
   */
  public @Nullable String getIcon() {
    return icon;
  }

  public enum InstallationStatus {
    INSTALLING, PAUSED, INSTALLED, UNINSTALLED, FAILED
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final InstallationProgress that = (InstallationProgress) o;

    if (versionCode != that.versionCode) return false;
    if (state != that.state) return false;
    if (!md5.equals(that.md5)) return false;
    return packageName.equals(that.packageName);
  }

  @Override public int hashCode() {
    int result = state.hashCode();
    result = 31 * result + md5.hashCode();
    result = 31 * result + packageName.hashCode();
    result = 31 * result + versionCode;
    return result;
  }
}
