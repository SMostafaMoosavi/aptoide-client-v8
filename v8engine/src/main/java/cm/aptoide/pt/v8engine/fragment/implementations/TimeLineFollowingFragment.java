package cm.aptoide.pt.v8engine.fragment.implementations;

import android.os.Bundle;
import android.support.annotation.NonNull;
import cm.aptoide.pt.dataprovider.ws.v7.GetFollowingRequest;
import cm.aptoide.pt.dataprovider.ws.v7.V7;
import cm.aptoide.pt.model.v7.GetFollowers;
import cm.aptoide.pt.utils.AptoideUtils;
import cm.aptoide.pt.v8engine.R;
import cm.aptoide.pt.v8engine.view.recycler.displayable.Displayable;
import cm.aptoide.pt.v8engine.view.recycler.displayable.implementations.grid.FollowUserDisplayable;
import cm.aptoide.pt.v8engine.view.recycler.displayable.implementations.grid.MessageWhiteBgDisplayable;
import cm.aptoide.pt.v8engine.view.recycler.listeners.EndlessRecyclerOnScrollListener;
import java.util.List;

/**
 * Created by trinkes on 10/03/2017.
 */

public class TimeLineFollowingFragment extends TimeLineFollowFragment {

  private Long userId;
  private Long storeId;

  public static TimeLineFollowFragment newInstanceUsingUserId(Long id, long followNumber,
      String storeTheme) {
    Bundle args = buildBundle(followNumber, storeTheme);
    if (id != null) {
      args.putLong(BundleKeys.USER_ID, id);
    }
    TimeLineFollowingFragment fragment = new TimeLineFollowingFragment();
    fragment.setArguments(args);
    return fragment;
  }

  @NonNull private static Bundle buildBundle(long followNumber, String storeTheme) {
    Bundle args = new Bundle();
    args.putString(TITLE_KEY,
        AptoideUtils.StringU.getFormattedString(R.string.social_timeline_following_fragment_title,
            followNumber));
    args.putString(BundleCons.STORE_THEME, storeTheme);
    return args;
  }

  public static TimeLineFollowFragment newInstanceUsingStoreId(Long id, long followNumber,
      String storeTheme) {
    Bundle args = buildBundle(followNumber, storeTheme);
    if (id != null) {
      args.putLong(BundleKeys.STORE_ID, id);
    }
    TimeLineFollowingFragment fragment = new TimeLineFollowingFragment();
    fragment.setArguments(args);
    return fragment;
  }

  @Override protected V7 buildRequest() {
    return GetFollowingRequest.of(getBodyDecorator(), userId, storeId);
  }

  @Override protected Displayable createUserDisplayable(GetFollowers.TimelineUser user) {
    return new FollowUserDisplayable(user, false);
  }

  @Override EndlessRecyclerOnScrollListener.BooleanAction<GetFollowers> getFirstResponseAction(
      List<Displayable> dispList) {
    return response -> {
      dispList.add(0, new MessageWhiteBgDisplayable(getHeaderMessage()));
      return false;
    };
  }

  public String getFooterMessage(int hidden) {
    return getString(R.string.private_following_message, hidden);
  }

  public String getHeaderMessage() {
    return getString(R.string.social_timeline_share_bar_following);
  }

  @Override public void loadExtras(Bundle args) {
    super.loadExtras(args);
    if (args.containsKey(BundleKeys.USER_ID)) {
      userId = args.getLong(BundleKeys.USER_ID);
    }
    if (args.containsKey(BundleKeys.STORE_ID)) {
      storeId = args.getLong(BundleKeys.STORE_ID);
    }
  }
}