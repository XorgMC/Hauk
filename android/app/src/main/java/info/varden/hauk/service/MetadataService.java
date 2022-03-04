package info.varden.hauk.service;

import android.content.ComponentName;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import info.varden.hauk.Constants;
import info.varden.hauk.http.MetadataUpdatePacket;
import info.varden.hauk.struct.Share;
import info.varden.hauk.utils.ReceiverDataRegistry;

public class MetadataService extends NotificationListenerService {
    /**
     * The share that is to be represented in the notification.
     */
    private Share share;

    /**
     * The audio metadata
     */
    private AudioMetadata audioMetadata;

    private int navMetaCount = 0;
    private String navMeta = "";

    /**
     * If service is enabled
     */
    private boolean enabled = false;

    public static final String ACTION_START = "START";
    public static final String ACTION_STOP = "STOP";

    public MetadataService() {
        this.audioMetadata = new AudioMetadata();
    }

    /*
        These are the package names of the apps. for which we want to
        listen the notifications
     */
    private static final class ApplicationPackageNames {
        public static final String MAPS_PACK_NAME = "com.google.android.apps.maps";
    }

    /*
        These are the return codes we use in the method which intercepts
        the notifications, to decide whether we should do something or not
     */
    public static final class InterceptedNotificationCode {
        public static final int MAPS_CODE = 1;
        public static final int OTHER_NOTIFICATIONS_CODE = 0; // We ignore all notification with code == 4
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent.getAction().equals(ACTION_START)) {
            this.share = (Share) ReceiverDataRegistry.retrieve(intent.getIntExtra(Constants.EXTRA_SHARE, -1));

            this.enabled = true;

            Log.d("MetaSvc", "MetaSvc started with session id " + this.share.getID() + "/" + this.share.getSession().getID());
        } else {
            this.share = null;
            this.enabled = false;
        }

        return super.onStartCommand(intent, flags, startId);
    }


    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn){
        if(!this.enabled) return;
        int notificationCode = matchNotificationCode(sbn);

        this.getCurrentlyPlaying();

        if(notificationCode == InterceptedNotificationCode.MAPS_CODE && sbn.getNotification().category.equals("navigation")){
            String mapsExtra = sbn.getNotification().extras.getString("android.subText");
            if(mapsExtra != null) {
                if(!mapsExtra.equals(this.navMeta)) {
                    mapsExtra = this.navMeta;
                    this.navMetaCount++;
                    if(this.navMetaCount > 10) {
                        this.navMetaCount = 0;
                        String navMetaOut;
                        try {
                            navMetaOut = this.navMeta.split("ca.")[1].trim();
                        } catch (Exception e) {
                            navMetaOut = this.navMeta;
                        }
                        new MetadataUpdatePacket(this, this.share.getSession(), this.audioMetadata, navMetaOut).send();
                    }
                }
            }
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn){
        if(!this.enabled) return;
        /*int notificationCode = matchNotificationCode(sbn);

        if(notificationCode != InterceptedNotificationCode.OTHER_NOTIFICATIONS_CODE) {

            StatusBarNotification[] activeNotifications = this.getActiveNotifications();

            if(activeNotifications != null && activeNotifications.length > 0) {
                for (int i = 0; i < activeNotifications.length; i++) {
                    if (notificationCode == matchNotificationCode(activeNotifications[i])) {
                        Log.d("MetaSvc","Got NAV notification." + activeNotifications[i].getNotification().tickerText);
                        break;
                    }
                }
            }
        }*/
    }

    private boolean isPlaying(MediaController ctrl) {
        if(ctrl == null) return false;
        if(ctrl.getPlaybackState() == null) return false;
        return ctrl.getPlaybackState().getState() == PlaybackState.STATE_PLAYING;
    }

    private String getCurrentlyPlaying() {
        MediaSessionManager msm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        List<MediaController> mcl = msm.getActiveSessions(new ComponentName(this, MetadataService.class));

        MediaController primaryCtrl = mcl.stream().filter(this::isPlaying).findFirst().orElse(null);
        if(primaryCtrl == null) return ""; if(primaryCtrl.getMetadata() == null) return "";

        if(this.audioMetadata.shallUpdate(primaryCtrl.getMetadata(), primaryCtrl.getPackageName())) {
            Log.d("MetaSvc", "Got new media! Track: " + audioMetadata.toString());

            try {
                new MetadataUpdatePacket(this, this.share.getSession(), this.audioMetadata).send();
            } catch(Exception e) {
                e.printStackTrace();
            }

        }

        return "";
    }

    private int matchNotificationCode(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();

        if(packageName.equals(ApplicationPackageNames.MAPS_PACK_NAME)){
            return(InterceptedNotificationCode.MAPS_CODE);
        }
        /*else if(packageName.equals(ApplicationPackageNames.INSTAGRAM_PACK_NAME)){
            return(InterceptedNotificationCode.INSTAGRAM_CODE);
        }
        else if(packageName.equals(ApplicationPackageNames.WHATSAPP_PACK_NAME)){
            return(InterceptedNotificationCode.WHATSAPP_CODE);
        }*/
        else{
            return(InterceptedNotificationCode.OTHER_NOTIFICATIONS_CODE);
        }
    }

    public static class AudioMetadata {
        private String title;
        private String artist;
        private String app;

        public boolean shallUpdate(MediaMetadata metadata, String appPkg) {
            String newTitle = metadata.containsKey(MediaMetadata.METADATA_KEY_TITLE) ? metadata.getString(MediaMetadata.METADATA_KEY_TITLE) : "unknown";
            String newArtist = metadata.containsKey(MediaMetadata.METADATA_KEY_ARTIST) ? metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) : "unknown";
            if(newTitle.equals(this.getTitle()) && newArtist.equals(this.getArtist()) && appPkg.equals(this.getApp())) return false;
            this.setTitle(newTitle); this.setArtist(newArtist); this.setApp(appPkg);
            return true;
        }

        public AudioMetadata() {
            this.title = ""; this.artist = ""; this.app = "";
        }

        public AudioMetadata(String title, String artist, String app) {
            this.title = title; this.artist = artist; this.app = app;
        }

        public String getTitle() { return this.title; }
        public void setTitle(String title) { this.title = title; }

        public String getArtist() { return this.artist; }
        public void setArtist(String artist) { this.artist = artist; }

        public String getApp() { return this.app; }
        public void setApp(String app) { this.app = app; }

        @Override
        public String toString() {
            return this.getTitle() + " - " + this.getArtist();
        }
    }
}
