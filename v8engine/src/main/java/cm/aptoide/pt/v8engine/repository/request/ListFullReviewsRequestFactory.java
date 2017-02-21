package cm.aptoide.pt.v8engine.repository.request;

import cm.aptoide.accountmanager.AptoideAccountManager;
import cm.aptoide.pt.dataprovider.DataProvider;
import cm.aptoide.pt.dataprovider.repository.IdsRepositoryImpl;
import cm.aptoide.pt.dataprovider.ws.v7.BaseRequestWithStore;
import cm.aptoide.pt.dataprovider.ws.v7.ListFullReviewsRequest;
import cm.aptoide.pt.interfaces.AccessToken;
import cm.aptoide.pt.interfaces.AptoideClientUUID;
import cm.aptoide.pt.preferences.secure.SecurePreferencesImplementation;

/**
 * Created by neuro on 03-01-2017.
 */
class ListFullReviewsRequestFactory {

  private final AptoideClientUUID aptoideClientUUID;
  private final AccessToken accessToken;

  public ListFullReviewsRequestFactory() {
    aptoideClientUUID = () -> new IdsRepositoryImpl(SecurePreferencesImplementation.getInstance(),
        DataProvider.getContext()).getUniqueIdentifier();

    accessToken = AptoideAccountManager::getAccessToken;
  }

  public ListFullReviewsRequest newListFullReviews(String url, boolean refresh,
      BaseRequestWithStore.StoreCredentials storeCredentials) {
    return ListFullReviewsRequest.ofAction(url, refresh, accessToken.get(),
        aptoideClientUUID.getUniqueIdentifier(), storeCredentials);
  }
}
