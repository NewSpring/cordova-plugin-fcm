package com.gae.scaffolder.plugin;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.PluginResult;

import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.iid.FirebaseInstanceId;

import com.google.android.gms.appinvite.AppInvite;
import com.google.android.gms.appinvite.AppInviteInvitation;
import com.google.android.gms.appinvite.AppInviteInvitationResult;
import com.google.android.gms.appinvite.AppInviteReferral;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import java.util.Map;


import static android.app.Activity.RESULT_OK;
import static com.google.android.gms.appinvite.AppInviteInvitation.IntentBuilder.PlatformMode.PROJECT_PLATFORM_IOS;

public class FCMPlugin extends CordovaPlugin implements GoogleApiClient.OnConnectionFailedListener {
 
	private static final String TAG = "FCMPlugin";
	
	public static CordovaWebView gWebView;
	public static String notificationCallBack = "FCMPlugin.onNotificationReceived";
	public static String tokenRefreshCallBack = "FCMPlugin.onTokenRefreshReceived";
	public static Boolean notificationCallBackReady = false;
	public static Map<String, Object> lastPush = null;

	private static final int REQUEST_INVITE = 48;

	private GoogleApiClient _googleApiClient;
  private CallbackContext _sendInvitationCallbackContext;
  private CallbackContext _getInvitationCallbackContext;
	 
	public FCMPlugin() {}
	
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		gWebView = webView;
		Log.d(TAG, "==> FCMPlugin initialize");
		FirebaseMessaging.getInstance().subscribeToTopic("android");
		FirebaseMessaging.getInstance().subscribeToTopic("all");
	}
	
	@Override
	public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {

		Log.d(TAG,"==> FCMPlugin execute: "+ action);
		
		try{
			// READY //
			if (action.equals("ready")) {
				//
				callbackContext.success();
			}
			// GET TOKEN //
			else if (action.equals("getToken")) {
				cordova.getActivity().runOnUiThread(new Runnable() {
					public void run() {
						try{
							String token = FirebaseInstanceId.getInstance().getToken();
							callbackContext.success( FirebaseInstanceId.getInstance().getToken() );
							Log.d(TAG,"\tToken: "+ token);
						}catch(Exception e){
							Log.d(TAG,"\tError retrieving token");
						}
					}
				});
			}
			// NOTIFICATION CALLBACK REGISTER //
			else if (action.equals("registerNotification")) {
				notificationCallBackReady = true;
				cordova.getActivity().runOnUiThread(new Runnable() {
					public void run() {
						if(lastPush != null) FCMPlugin.sendPushPayload( lastPush );
						lastPush = null;
						callbackContext.success();
					}
				});
			}
			// UN/SUBSCRIBE TOPICS //
			else if (action.equals("subscribeToTopic")) {
				cordova.getThreadPool().execute(new Runnable() {
					public void run() {
						try{
							FirebaseMessaging.getInstance().subscribeToTopic( args.getString(0) );
							callbackContext.success();
						}catch(Exception e){
							callbackContext.error(e.getMessage());
						}
					}
				});
			}
			else if (action.equals("unsubscribeFromTopic")) {
				cordova.getThreadPool().execute(new Runnable() {
					public void run() {
						try{
							FirebaseMessaging.getInstance().unsubscribeFromTopic( args.getString(0) );
							callbackContext.success();
						}catch(Exception e){
							callbackContext.error(e.getMessage());
						}
					}
				});
			}
			else if (action.equals("onDynamicLink")){
				Log.d(TAG, "==> onDynamicLink");
				getInvitation(callbackContext);
			}
			else{
				callbackContext.error("Method not found");
				return false;
			}
		}catch(Exception e){
			Log.d(TAG, "ERROR: onPluginAction: " + e.getMessage());
			callbackContext.error(e.getMessage());
			return false;
		}
		
		//cordova.getThreadPool().execute(new Runnable() {
		//	public void run() {
		//	  //
		//	}
		//});
		
		//cordova.getActivity().runOnUiThread(new Runnable() {
        //    public void run() {
        //      //
        //    }
        //});
		return true;
	}
	
	public static void sendPushPayload(Map<String, Object> payload) {
		Log.d(TAG, "==> FCMPlugin sendPushPayload");
		Log.d(TAG, "\tnotificationCallBackReady: " + notificationCallBackReady);
		Log.d(TAG, "\tgWebView: " + gWebView);
	    try {
		    JSONObject jo = new JSONObject();
			for (String key : payload.keySet()) {
			    jo.put(key, payload.get(key));
				Log.d(TAG, "\tpayload: " + key + " => " + payload.get(key));
            }
			String callBack = "javascript:" + notificationCallBack + "(" + jo.toString() + ")";
			if(notificationCallBackReady && gWebView != null){
				Log.d(TAG, "\tSent PUSH to view: " + callBack);
				gWebView.sendJavascript(callBack);
			}else {
				Log.d(TAG, "\tView not ready. SAVED NOTIFICATION: " + callBack);
				lastPush = payload;
			}
		} catch (Exception e) {
			Log.d(TAG, "\tERROR sendPushToView. SAVED NOTIFICATION: " + e.getMessage());
			lastPush = payload;
		}
	}

	public static void sendTokenRefresh(String token) {
		Log.d(TAG, "==> FCMPlugin sendRefreshToken");
	  try {
			String callBack = "javascript:" + tokenRefreshCallBack + "('" + token + "')";
			gWebView.sendJavascript(callBack);
		} catch (Exception e) {
			Log.d(TAG, "\tERROR sendRefreshToken: " + e.getMessage());
		}
	}

	private void getInvitation(final CallbackContext callbackContext) {
      this._getInvitationCallbackContext = callbackContext;

      cordova.getThreadPool().execute(new Runnable() {
          @Override
          public void run() {
              if (respondWithReferral(cordova.getActivity().getIntent())) return;

              AppInvite.AppInviteApi.getInvitation(getGoogleApiClient(), cordova.getActivity(), false)
                  .setResultCallback(new ResultCallback<AppInviteInvitationResult>() {
                      @Override
                      public void onResult(@NonNull AppInviteInvitationResult result) {
                          if (result.getStatus().isSuccess()) {
                              Intent intent = result.getInvitationIntent();

                              respondWithDeepLink(intent, AppInviteReferral.getDeepLink(intent));
                          } else {
                              _getInvitationCallbackContext.error("Not launched by invitation");
                          }
                      }
              });
          }
      });
  }

	private boolean respondWithReferral(Intent intent) {
    if (AppInviteReferral.hasReferral(intent)) {
          respondWithDeepLink(intent, AppInviteReferral.getDeepLink(intent));

          return true;
      }

      String action = intent.getAction();
      String data = intent.getDataString();

      if (Intent.ACTION_VIEW.equals(action) && data != null) {
          respondWithDeepLink(intent, data);

          return true;
      }

      return false;
  }

	private void respondWithDeepLink(Intent intent, String deepLink) {
      if (_getInvitationCallbackContext == null) return;

      JSONObject response = new JSONObject();
      String invitationId = AppInviteReferral.getInvitationId(intent);

      try {
          if (invitationId != null && invitationId != "") {
              response.put("invitationId", invitationId);
          }

          response.put("deepLink", deepLink);

          PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, response);
          pluginResult.setKeepCallback(true);
          _getInvitationCallbackContext.sendPluginResult(pluginResult);
      } catch (JSONException e) {
          Log.e(TAG, "Fail to handle dynamic link data", e);
      }
  }

	private GoogleApiClient getGoogleApiClient() {
    if (this._googleApiClient == null) {
      this._googleApiClient = new GoogleApiClient.Builder(webView.getContext())
          .addOnConnectionFailedListener(this)
          .addApi(AppInvite.API)
          .build();
    }

    return this._googleApiClient;
  }

	@Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    super.onActivityResult(requestCode, resultCode, intent);

    if (_sendInvitationCallbackContext == null || requestCode != REQUEST_INVITE) {
        return;
    }

    if (resultCode == RESULT_OK) {
        final String[] ids = AppInviteInvitation.getInvitationIds(resultCode, intent);
        try {
            _sendInvitationCallbackContext.success(new JSONArray(Arrays.asList(ids)));
        } catch (Exception e) {
            _sendInvitationCallbackContext.error(e.getMessage());
        }
    } else {
        _sendInvitationCallbackContext.error("Resultcode: " + resultCode);
    }
  }

  @Override
  public void onConnectionFailed(@NonNull ConnectionResult result) {
    this._getInvitationCallbackContext.error(
        "Connection to Google API failed with errorcode: " + result.getErrorCode());
  }

	@Override
  public void onNewIntent(Intent intent) {
      super.onNewIntent(intent);

      respondWithReferral(intent);
  }
  
  @Override
	public void onDestroy() {
		gWebView = null;
		notificationCallBackReady = false;
	}
} 
