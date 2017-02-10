/*
 * Copyright (c) 2016.
 * Modified by Neurophobic Animal on 31/05/2016.
 */

package cm.aptoide.pt.v8engine.view.recycler.widget;

import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import cm.aptoide.pt.logger.Logger;
import cm.aptoide.pt.navigation.NavigationManagerV4;
import cm.aptoide.pt.v8engine.view.recycler.displayable.Displayable;
import rx.subscriptions.CompositeSubscription;

/**
 * Class that represents a generic Widget. All widgets should extend this class.
 */
public abstract class Widget<T extends Displayable> extends RecyclerView.ViewHolder {

  private static final String TAG = Widget.class.getName();

  private final NavigationManagerV4 appNav;
  protected CompositeSubscription compositeSubscription;

  public Widget(@NonNull View itemView) {
    super(itemView);
    appNav = NavigationManagerV4.Builder.buildWith(getContext());

    try {
      assignViews(itemView);
    } catch (Exception e) {
      Logger.e(TAG, "assignViews(View)", e);
    }
  }

  protected NavigationManagerV4 getNavigationManager() {
    return appNav;
  }

  protected abstract void assignViews(View itemView);

  public abstract void bindView(T displayable);

  public void unbindView() {
    if (compositeSubscription != null && !compositeSubscription.isUnsubscribed()) {
      compositeSubscription.clear();
    }
  }

  public FragmentActivity getContext() {
    return (FragmentActivity) itemView.getContext();
  }

  public void internalBindView(T displayable) {
    if (compositeSubscription == null) {
      compositeSubscription = new CompositeSubscription();
    }
    displayable.setVisible(true);
    bindView(displayable);
  }
}
