package cm.aptoide.pt.v8engine.notification.view;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import cm.aptoide.pt.v8engine.R;
import cm.aptoide.pt.v8engine.notification.AptoideNotification;
import java.util.List;

/**
 * Created by pedroribeiro on 16/05/17.
 */

public class InboxAdapter extends RecyclerView.Adapter<InboxViewHolder> {

  private List<AptoideNotification> notifications;

  public InboxAdapter(List<AptoideNotification> notifications) {
    this.notifications = notifications;
  }

  @Override public InboxViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    return new InboxViewHolder(LayoutInflater.from(parent.getContext())
        .inflate(R.layout.fragment_inbox_list_item, parent, false));
  }

  @Override public void onBindViewHolder(InboxViewHolder holder, int position) {
    holder.setNotification(notifications.get(position));
  }

  @Override public int getItemCount() {
    return notifications.size();
  }

  public void updateNotifications(List<AptoideNotification> notifications) {
    this.notifications = notifications;
    notifyDataSetChanged();
  }
}
