package cm.aptoide.pt.v8engine.presenter;

import android.os.Bundle;
import cm.aptoide.accountmanager.AptoideAccountManager;
import cm.aptoide.pt.preferences.managed.ManagerPreferences;
import cm.aptoide.pt.v8engine.crashreports.CrashReport;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;

public class MyAccountPresenter implements Presenter {

  private final MyAccountView view;
  private final AptoideAccountManager accountManager;
  private final CrashReport crashReport;
  private final MyAccountNavigator navigator;

  public MyAccountPresenter(MyAccountView view, AptoideAccountManager accountManager,
      CrashReport crashReport, MyAccountNavigator navigator) {
    this.view = view;
    this.accountManager = accountManager;
    this.crashReport = crashReport;
    this.navigator = navigator;
  }

  @Override public void present() {
    view.getLifecycle()
        .filter(event -> event.equals(View.LifecycleEvent.CREATE))
        .flatMap(resumed -> signOutClick())
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe();
    view.getLifecycle()
        .filter(event -> event.equals(View.LifecycleEvent.CREATE))
        .flatMap(resumed -> view.moreNotificationsClick()
            .doOnNext(__ -> navigator.navigateToInboxView()))
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe();
  }

  @Override public void saveState(Bundle state) {
    // does nothing
  }

  @Override public void restoreState(Bundle state) {
    // does nothing
  }

  private Observable<Void> signOutClick() {
    return view.signOutClick()
        .flatMap(click -> accountManager.logout()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnCompleted(() -> {
              ManagerPreferences.setAddressBookSyncValues(false);
              view.navigateToHome();
            })
            .doOnError(throwable -> crashReport.log(throwable)).<Void>toObservable())
        .retry();
  }
}
