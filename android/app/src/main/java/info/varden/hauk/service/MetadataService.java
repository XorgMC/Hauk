package info.varden.hauk.service;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import info.varden.hauk.Constants;
import info.varden.hauk.http.MetadataUpdatePacket;
import info.varden.hauk.struct.Share;
import info.varden.hauk.utils.ReceiverDataRegistry;
import lombok.Getter;

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
                this.navMetaCount++;
                if(!mapsExtra.equals(this.navMeta)) {
                    this.navMeta = mapsExtra;
                    if(this.navMetaCount > 5) {
                        this.navMetaCount = 0;
                        String navMetaOut;
                        try {
                            navMetaOut = this.navMeta.split("ca.")[1].trim();
                        } catch (Exception e) {
                            navMetaOut = this.navMeta;
                        }
                        new MetadataUpdatePacket(this, this.share.getSession(), this.audioMetadata, null).send();
                    }
                }
            }
            /*StringJoiner sj = new StringJoiner("");
            for(String s : sbn.getNotification().extras.keySet()) {
                sj.add(s);
                sj.add("=");
                try {
                    sj.add(sbn.getNotification().extras.get(s).toString());
                } catch (Exception e) {
                    //e.printStackTrace();
                    sj.add("---");
                }
                sj.add(";;;");
            }
            Log.d("MetaSvc","Got NAV notification: " + sj.toString());*/
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

    public static class NavigationMetadata {
        @Getter private String destination;
        @Getter private String nextTurn;
        @Getter private String nextTurnDst;
        @Getter private String nextTurnIcon;
        @Getter private String arrival;

        public String getDestination() {
            return this.destination;
        }

        public String getNextTurn() {
            return this.nextTurn;
        }

        public String getNextTurnDst() {
            return this.nextTurnDst;
        }

        public String getNextTurnIcon() {
            return this.nextTurnIcon;
        }
        public String getArrival() {
            return this.arrival;
        }

        public NavigationMetadata() {
            this.destination = "";
            this.nextTurn = "";
            this.nextTurnDst = "";
            this.nextTurnIcon = "";
            this.arrival = "";
        }

        public boolean shallUpdate(StatusBarNotification sbn, Context context) {
            boolean changed = false;
            Bundle notificationExtras = sbn.getNotification().extras;
            if(notificationExtras.containsKey("android.title")) {
                String titleStr = notificationExtras.getString("android.title");
                if(titleStr.contains("–")) {
                    String[] title = titleStr.split("–", 2);
                    if(!title[0].equals(this.nextTurnDst) || !title[1].equals(this.nextTurn)) changed = true;
                    this.nextTurn = title[1];
                    this.nextTurnDst = title[0];
                } else {
                    if(!titleStr.equals(this.nextTurn) || !this.nextTurnDst.isEmpty()) changed = true;
                    this.nextTurn = titleStr; this.nextTurnDst = "";
                }
            }

            if(notificationExtras.containsKey("android.subText")) {
                String subStr = Objects.requireNonNull(notificationExtras.getString("android.subText"));
                String[] sub = subStr.split("·");
                String arriv;
                if(sub.length == 3) {
                    arriv = sub[2].split("ca.")[1].trim() + ", " + sub[1].trim();
                } else {
                    arriv = subStr;
                }
                if(arriv != this.arrival) changed = true;
                this.arrival = arriv;
            }

            if(notificationExtras.containsKey("android.text")) {
                String textStr = notificationExtras.getString("android.text");
                String[] text = textStr.split("–", 2);
                if(text[0].trim() != this.destination) changed = true;
                this.destination = text[0].trim();
            }

            if(notificationExtras.containsKey(Notification.EXTRA_LARGE_ICON)) {
                try {
                    PackageManager manager = context.getPackageManager();
                    Resources resources = manager.getResourcesForApplication(sbn.getPackageName());

                    Bitmap bitmap = BitmapFactory.decodeResource(resources, notificationExtras.getInt(Notification.EXTRA_LARGE_ICON));
                    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteStream);
                    byte[] byteArray = byteStream.toByteArray();
                    String baseString = Base64.encodeToString(byteArray,Base64.DEFAULT);
                    if(!baseString.equals(this.nextTurnIcon)) changed = true;
                    this.nextTurnIcon = baseString;

                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
            }
            return changed;
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
