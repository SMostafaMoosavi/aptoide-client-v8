/*
 * Copyright (c) 2016.
 * Modified by SithEngineer on 20/07/2016.
 */

package cm.aptoide.pt.dataprovider.ws.v7;

import cm.aptoide.accountmanager.AptoideAccountManager;
import cm.aptoide.pt.dataprovider.DataProvider;
import cm.aptoide.pt.dataprovider.IdsRepository;
import cm.aptoide.pt.dataprovider.ws.Api;
import cm.aptoide.pt.networkclient.WebService;
import cm.aptoide.pt.networkclient.okhttp.OkHttpClientFactory;
import cm.aptoide.pt.preferences.secure.SecurePreferencesImplementation;
import cm.aptoide.pt.utils.AptoideUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import rx.Observable;

/**
 * Created by sithengineer on 20/07/16.
 */
public class PostCommentRequest extends V7<Void,PostCommentRequest.Body> {
	
	protected PostCommentRequest(Body body, String baseHost) {
		super(body, OkHttpClientFactory.getSingletonClient(), WebService.getDefaultConverter(), baseHost);
	}

	public static PostCommentRequest of(long reviewId, String text) {
		//
		//  http://ws75-primary.aptoide.com/api/7/setComment/review_id/1/body/amazing%20review/access_token/ca01ee1e05ab4d82d99ef143e2816e667333c6ef
		//
		IdsRepository idsRepository = new IdsRepository(SecurePreferencesImplementation.getInstance(), DataProvider.getContext());
		Body body = new Body(idsRepository.getAptoideClientUUID(), AptoideAccountManager.getAccessToken(), AptoideUtils.Core.getVerCode(), "pool", Api.LANG,
				Api
				.isMature(), Api.Q, reviewId, text);
		return new PostCommentRequest(body, BASE_HOST);
	}

	@Override
	protected Observable<Void> loadDataFromNetwork(Interfaces interfaces, boolean bypassCache) {
		return interfaces.postComment(body, true);
	}

	@Data
	@Accessors(chain = false)
	@EqualsAndHashCode(callSuper = true)
	public static class Body extends BaseBody {

		private long reviewId;
		private String body;

		public Body(String aptoideId, String accessToken, int aptoideVersionCode, String cdn, String lang, boolean mature, String q, long reviewId, String
				text) {
			super(aptoideId, accessToken, aptoideVersionCode, cdn, lang, mature, q);
			this.reviewId = reviewId;
			this.body = text;
		}
	}
}
