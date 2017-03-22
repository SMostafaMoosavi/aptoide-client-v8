/*
 * Copyright (c) 2017.
 * Modified by Marcelo Benites on 09/02/2017.
 */

package cm.aptoide.pt.v8engine.activity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.widget.Button;
import cm.aptoide.accountmanager.Account;
import cm.aptoide.accountmanager.AptoideAccountManager;
import cm.aptoide.pt.dataprovider.DataProvider;
import cm.aptoide.pt.dataprovider.repository.IdsRepositoryImpl;
import cm.aptoide.pt.interfaces.AptoideClientUUID;
import cm.aptoide.pt.preferences.secure.SecurePreferencesImplementation;
import cm.aptoide.pt.utils.GenericDialogs;
import cm.aptoide.pt.utils.design.ShowMessage;
import cm.aptoide.pt.v8engine.R;
import cm.aptoide.pt.v8engine.V8Engine;
import cm.aptoide.pt.v8engine.analytics.Analytics;
import com.jakewharton.rxbinding.view.RxView;
import rx.subscriptions.CompositeSubscription;

/**
 * Created by pedroribeiro on 15/12/16.
 */

public class ProfileStepOneActivity extends AccountBaseActivity {

  private static final String TAG = ProfileStepOneActivity.class.getSimpleName();

  private AptoideClientUUID aptoideClientUUID;
  private AptoideAccountManager accountManager;

  private Toolbar mToolbar;
  private Button mContinueButton;
  private Button mMoreInfoButton;
  private CompositeSubscription mSubscriptions;
  private ProgressDialog pleaseWaitDialog;
  private boolean externalLogin;

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(getLayoutId());
    aptoideClientUUID = new IdsRepositoryImpl(SecurePreferencesImplementation.getInstance(),
        DataProvider.getContext());
    accountManager = ((V8Engine) getApplicationContext()).getAccountManager();
    mSubscriptions = new CompositeSubscription();
    pleaseWaitDialog = GenericDialogs.createGenericPleaseWaitDialog(this,
        getApplicationContext().getString(cm.aptoide.accountmanager.R.string.please_wait));
    externalLogin = getIntent().getBooleanExtra(AptoideAccountManager.IS_FACEBOOK_OR_GOOGLE, false);
    bindViews();
    setupToolbar();
    setupListeners();
  }

  @Override public String getActivityTitle() {
    return getString(cm.aptoide.accountmanager.R.string.create_profile_logged_in_activity_title);
  }

  @Override public int getLayoutId() {
    return R.layout.logged_in_first_screen;
  }

  private void bindViews() {
    mContinueButton = (Button) findViewById(R.id.logged_in_continue);
    mMoreInfoButton = (Button) findViewById(R.id.logged_in_more_info_button);
    mToolbar = (Toolbar) findViewById(R.id.toolbar);
  }

  private void setupToolbar() {
    setSupportActionBar(mToolbar);
    getSupportActionBar().setTitle(getActivityTitle());
  }

  private void setupListeners() {
    mSubscriptions.add(RxView.clicks(mContinueButton)
        .doOnNext(click -> pleaseWaitDialog.show())
        .flatMap(click -> accountManager.updateAccount(Account.Access.PUBLIC)
            .doOnCompleted(() -> showContinueSuccessMessage())
            .doOnError(throwable -> showErrorMessage())
            .onErrorComplete()
            .doOnTerminate(() -> navigateToCreateStoreViewOrDismiss())
            .toObservable())
        .retry()
        .subscribe());
    mSubscriptions.add(RxView.clicks(mMoreInfoButton).subscribe(clicks -> {
      Analytics.Account.accountProfileAction(1, Analytics.Account.ProfileAction.MORE_INFO);
      navigateToProfileStepTwoView();
    }));
  }

  private void navigateToProfileStepTwoView() {
    startActivity(new Intent(this, ProfileStepTwoActivity.class));
    finish();
  }

  private void navigateToCreateStoreViewOrDismiss() {
    if (externalLogin) {
      dismiss();
    } else {
      startActivity(new Intent(this, CreateStoreActivity.class));
      dismiss();
    }
  }

  private void dismiss() {
    pleaseWaitDialog.dismiss();
    finish();
  }

  private void showErrorMessage() {
    ShowMessage.asSnack(this, cm.aptoide.accountmanager.R.string.unknown_error);
  }

  private void showContinueSuccessMessage() {
    ShowMessage.asSnack(this, cm.aptoide.accountmanager.R.string.successful);
    Analytics.Account.accountProfileAction(1, Analytics.Account.ProfileAction.CONTINUE);
  }
}


