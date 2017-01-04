/*
 * Copyright (c) 2016.
 * Modified by Marcelo Benites on 29/08/2016.
 */

package cm.aptoide.pt.v8engine.presenter;

import android.os.Bundle;
import cm.aptoide.accountmanager.AptoideAccountManager;
import cm.aptoide.pt.v8engine.payment.Payment;
import cm.aptoide.pt.v8engine.payment.AptoidePay;
import cm.aptoide.pt.v8engine.payment.Purchase;
import cm.aptoide.pt.v8engine.payment.exception.PaymentFailureException;
import cm.aptoide.pt.v8engine.payment.products.AptoideProduct;
import cm.aptoide.pt.v8engine.view.PaymentView;
import cm.aptoide.pt.v8engine.view.View;
import java.util.ArrayList;
import java.util.List;
import javax.security.auth.login.LoginException;
import rx.Completable;
import rx.Observable;
import rx.Single;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Created by marcelobenites on 8/19/16.
 */
public class PaymentPresenter implements Presenter {

  private static final String EXTRA_IS_PROCESSING_LOGIN =
      "cm.aptoide.pt.v8engine.payment.extra.IS_PROCESSING_LOGIN";

  private final PaymentView view;
  private final AptoidePay aptoidePay;
  private final AptoideProduct product;
  private final List<Payment> otherPayments;

  private boolean processingLogin;
  private Payment selectedPayment;
  private boolean otherPaymentsVisible;

  public PaymentPresenter(PaymentView view, AptoidePay aptoidePay, AptoideProduct product) {
    this.view = view;
    this.aptoidePay = aptoidePay;
    this.product = product;
    this.otherPayments = new ArrayList<>();
  }

  @Override public void present() {

    view.getLifecycle()
        .filter(event -> View.LifecycleEvent.RESUME.equals(event))
        .flatMap(resumed -> cancellationSelection())
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe();

    view.getLifecycle()
        .filter(event -> View.LifecycleEvent.CREATE.equals(event))
        .flatMap(created -> login())
        .observeOn(AndroidSchedulers.mainThread())
        .doOnNext(loggedIn -> showProductAndShowLoading(product))
        .flatMap(loggedIn -> treatOngoingPurchase().switchIfEmpty(loadPayments().andThen(
            Observable.merge(buySelection(), paymentSelection(), otherPaymentsSelection()))))
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe();
  }

  @Override public void saveState(Bundle state) {
    state.putBoolean(EXTRA_IS_PROCESSING_LOGIN, processingLogin);
  }

  @Override public void restoreState(Bundle state) {
    this.processingLogin = state.getBoolean(EXTRA_IS_PROCESSING_LOGIN);
  }

  private Observable<Void> login() {
    return Observable.defer(() -> {
      if (processingLogin) {
        return Observable.just(AptoideAccountManager.isLoggedIn())
            .flatMap(loggedIn -> (loggedIn) ? Observable.just(null) : Observable.error(
                new LoginException("Not logged In. Payment can not be processed!")));
      }
      return AptoideAccountManager.login(view.getContext()).doOnSubscribe(() -> saveLoginState());
    })
        .doOnNext(loggedIn -> clearLoginState())
        .doOnError(throwable -> clearLoginState())
        .subscribeOn(Schedulers.computation());
  }

  private Completable loadPayments() {
    return aptoidePay.availablePayments(view.getContext(), product)
        .observeOn(AndroidSchedulers.mainThread())
        .flatMapCompletable(payments -> {
          if (payments.isEmpty()) {
            view.showPaymentsNotFoundMessage();
            return Completable.complete();
          } else {
            return getDefaultPayment(payments).flatMapCompletable(
                defaultPayment -> showPayments(payments, defaultPayment));
          }
        })
        .doOnCompleted(() -> view.hideLoading())
        .doOnError(error -> hideLoadingAndDismiss(error))
        .onErrorComplete();
  }

  private Completable showPayments(List<Payment> allPayments, Payment selectedPayment) {
    return Completable.fromAction(() -> showSelectedPayment(selectedPayment))
        .andThen(Observable.from(allPayments)
            .filter(payment -> payment.getId() != selectedPayment.getId())
            .toList()
            .toSingle()
            .flatMapCompletable(otherPayments -> showOtherPayments(otherPayments)));
  }

  private Observable<Void> paymentSelection() {
    return view.paymentSelection()
        .flatMap(paymentViewModel -> getSelectedPayment(otherPayments, paymentViewModel))
        .<Void>flatMap(selectedPayment -> showPayments(getAllPayments(), selectedPayment)
            .doOnCompleted(() -> hideOtherPayments()).toObservable())
        .doOnError(throwable -> hideLoadingAndDismiss(throwable))
        .retry();
  }

  private List<Payment> getAllPayments() {
    final List<Payment> allPayments = new ArrayList<>(otherPayments.size());
    allPayments.addAll(otherPayments);
    if (selectedPayment != null) {
      allPayments.add(selectedPayment);
    }
    return allPayments;
  }

  private Observable<Void> treatOngoingPurchase() {
    return aptoidePay.getPurchase(product)
        .observeOn(AndroidSchedulers.mainThread())
        .doOnNext(purchase -> hideLoadingAndDismiss(purchase))
        .doOnError(error -> hideLoadingAndDismiss(error))
        .onErrorResumeNext(throwable -> Observable.empty())
        .map(success -> null);
  }

  private Observable<Void> buySelection() {
    return view.buySelection()
        .doOnNext(payment -> view.showLoading())
        .flatMap(payment -> aptoidePay.process(selectedPayment).toObservable())
        .observeOn(AndroidSchedulers.mainThread())
        .doOnNext(purchase -> hideLoadingAndDismiss(purchase))
        .doOnError(throwable -> hideLoadingAndDismiss(throwable)).<Void>map(
            success -> null).retry();
  }

  private Observable<Void> otherPaymentsSelection() {
    return view.otherPaymentsSelection().flatMap(event -> {
      if (!otherPaymentsVisible) {
        return showPayments(getAllPayments(), selectedPayment).toObservable();
      }
      hideOtherPayments();
      return Observable.empty();
    });
  }

  private void hideOtherPayments() {
    view.hideOtherPayments();
    otherPaymentsVisible = false;
  }

  private Completable showOtherPayments(List<Payment> otherPayments) {
    return Observable.from(otherPayments)
        .map(payment -> convertToPaymentViewModel(payment))
        .toList()
        .doOnNext(paymentViewModels -> view.showOtherPayments(paymentViewModels))
        .doOnNext(paymentViewModels -> otherPaymentsVisible = true)
        .doOnCompleted(() -> {
          this.otherPayments.clear();
          this.otherPayments.addAll(otherPayments);
        })
        .toCompletable();
  }

  private Observable<Void> cancellationSelection() {
    return view.cancellationSelection()
        .doOnNext(cancellation -> view.dismiss())
        .compose(view.bindUntilEvent(View.LifecycleEvent.PAUSE));
  }

  private void showProductAndShowLoading(AptoideProduct product) {
    view.showLoading();
    view.showProduct(product);
  }

  private PaymentView.PaymentViewModel convertToPaymentViewModel(Payment payment) {
    return new PaymentView.PaymentViewModel(payment.getId(), payment.getName(),
        payment.getDescription(), payment.getPrice().getAmount(), payment.getPrice().getCurrency());
  }

  private void showSelectedPayment(Payment selectedPayment) {
    this.selectedPayment = selectedPayment;
    view.showSelectedPayment(convertToPaymentViewModel(selectedPayment));
  }

  private Observable<Payment> getSelectedPayment(List<Payment> payments,
      PaymentView.PaymentViewModel selectedPaymentViewModel) {
    return Observable.from(payments)
        .first(payment -> payment.getId() == selectedPaymentViewModel.getId());
  }

  private Single<Payment> getDefaultPayment(List<Payment> payments) {
    return Observable.from(payments)
        .first(payment -> isDefaultPayment(payment))
        .toSingle();
  }

  private boolean isDefaultPayment(Payment payment) {
    if (selectedPayment != null && selectedPayment.getId() == payment.getId()) {
      return true;
    }
    if (selectedPayment == null && payment.getId() == 1) { // PayPal
      return true;
    }
    return false;
  }

  private void hideLoadingAndDismiss(Throwable throwable) {
    throwable.printStackTrace();
    view.hideLoading();
    view.dismiss(throwable);
  }

  private void hideLoadingAndDismiss(Purchase purchase) {
    view.hideLoading();
    view.dismiss(purchase);
  }

  private boolean clearLoginState() {
    return processingLogin = false;
  }

  private boolean saveLoginState() {
    return processingLogin = true;
  }
}
