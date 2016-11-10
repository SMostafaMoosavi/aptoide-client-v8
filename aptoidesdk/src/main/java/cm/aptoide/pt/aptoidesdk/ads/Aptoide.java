package cm.aptoide.pt.aptoidesdk.ads;

import android.content.Context;
import android.support.annotation.NonNull;
import cm.aptoide.pt.aptoidesdk.BuildConfig;
import cm.aptoide.pt.aptoidesdk.entities.App;
import cm.aptoide.pt.aptoidesdk.entities.SearchResult;
import cm.aptoide.pt.aptoidesdk.parser.Parsers;
import cm.aptoide.pt.aptoidesdk.proxys.GetAdsProxy;
import cm.aptoide.pt.aptoidesdk.proxys.GetAppProxy;
import cm.aptoide.pt.aptoidesdk.proxys.ListSearchAppsProxy;
import cm.aptoide.pt.dataprovider.repository.IdsRepositoryImpl;
import cm.aptoide.pt.logger.Logger;
import cm.aptoide.pt.preferences.secure.SecurePreferences;
import cm.aptoide.pt.preferences.secure.SecurePreferencesImplementation;
import cm.aptoide.pt.utils.AptoideUtils;
import java.util.LinkedList;
import java.util.List;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import static android.content.ContentValues.TAG;

/**
 * Created by neuro on 21-10-2016.
 */

public class Aptoide {

  private static final GetAdsProxy getAdsProxy = new GetAdsProxy();
  private static final ListSearchAppsProxy listSearchAppsProxy = new ListSearchAppsProxy();
  private static final GetAppProxy getAppProxy = new GetAppProxy();
  private static String aptoideClientUUID;
  private static String oemid;
  private static String MISSED_INTEGRATION_MESSAGE =
      "Aptoide not integrated, did you forget to call Aptoide.integrate()?";

  private Aptoide() {
  }

  public static App getApp(Ad ad) {
    return getAppObservable(ad).toBlocking().first();
  }

  public static App getApp(SearchResult searchResult) {
    return getAppObservable(searchResult.getId()).toBlocking().first();
  }

  private static Observable<App> getAppObservable(Ad ad) {
    return getAppObservable(ad.data.appId).map(app -> {
      handleAds(ad).subscribe(t -> {
      }, throwable -> Logger.w(TAG, "Error extracting referrer.", throwable));
      return app;
    });
  }

  @NonNull private static Observable<Object> handleAds(Ad ad) {
    return Observable.fromCallable(() -> {
      ReferrerUtils.knockCpc(ad);
      return new Object();
    })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .doOnNext(o -> ReferrerUtils.extractReferrer(ad, ReferrerUtils.RETRIES, false));
  }

  public static App getApp(String packageName, String storeName) {
    return getAppObservable(packageName, storeName).toBlocking().first();
  }

  private static Observable<App> getAppObservable(String packageName, String storeName) {
    return getAppProxy.getApp(packageName, storeName, aptoideClientUUID)
        .map(App::fromGetApp)
        .onErrorReturn(throwable -> null);
  }

  public static App getApp(long appId) {
    return getAppObservable(appId).toBlocking().first();
  }

  private static Observable<App> getAppObservable(long appId) {
    return getAppProxy.getApp(appId, aptoideClientUUID)
        .map(App::fromGetApp)
        .onErrorReturn(throwable -> null);
  }

  public static Context getContext() {
    if (AptoideUtils.getContext() == null) {
      throw new RuntimeException(MISSED_INTEGRATION_MESSAGE);
    }

    return AptoideUtils.getContext();
  }

  public static String getOemid() {
    if (AptoideUtils.getContext() == null) {
      throw new RuntimeException(MISSED_INTEGRATION_MESSAGE);
    }

    return oemid;
  }

  public static void integrate(Context context, String oemid) {
    AptoideUtils.setContext(context);
    setUserAgent();

    Aptoide.oemid = oemid;
    Aptoide.aptoideClientUUID =
        new IdsRepositoryImpl(SecurePreferencesImplementation.getInstance(context),
            context).getAptoideClientUUID();

    Logger.setDBG(BuildConfig.DEBUG);
  }

  private static void setUserAgent() {
    SecurePreferences.setUserAgent(AptoideUtils.NetworkUtils.getDefaultUserAgent(
        new IdsRepositoryImpl(SecurePreferencesImplementation.getInstance(), getContext()),
        () -> null));
  }

  public static List<Ad> getAds(int limit) {
    return getAdsProxy.getAds(limit, aptoideClientUUID)
        .map(ReferrerUtils::parse)
        .onErrorReturn(throwable -> new LinkedList<>())
        .toBlocking()
        .first();
  }

  public static List<Ad> getAds(int limit, boolean mature) {
    return getAdsProxy.getAds(limit, mature, aptoideClientUUID)
        .map(ReferrerUtils::parse)
        .onErrorReturn(throwable -> new LinkedList<>())
        .toBlocking()
        .first();
  }

  public static List<Ad> getAds(int limit, List<String> keyword) {
    return getAdsProxy.getAds(limit, aptoideClientUUID, keyword)
        .map(ReferrerUtils::parse)
        .onErrorReturn(throwable -> new LinkedList<>())
        .toBlocking()
        .first();
  }

  public static List<Ad> getAds(int limit, List<String> keyword, boolean mature) {
    return getAdsProxy.getAds(limit, mature, aptoideClientUUID, keyword)
        .map(ReferrerUtils::parse)
        .onErrorReturn(throwable -> new LinkedList<>())
        .toBlocking()
        .first();
  }

  public static List<SearchResult> searchApps(String query) {
    return listSearchAppsProxy.search(query, aptoideClientUUID)
        .map(Parsers::parse)
        .onErrorReturn(throwable -> new LinkedList<>())
        .toBlocking()
        .first();
  }

  public static List<SearchResult> searchApps(String query, String storeName) {
    return listSearchAppsProxy.search(query, storeName, aptoideClientUUID)
        .map(Parsers::parse)
        .onErrorReturn(throwable -> new LinkedList<>())
        .toBlocking()
        .first();
  }
}
