package info.varden.hauk.http;

import android.content.Context;

import info.varden.hauk.Constants;
import info.varden.hauk.R;
import info.varden.hauk.service.MetadataService;
import info.varden.hauk.struct.Session;
import info.varden.hauk.struct.Version;
import info.varden.hauk.utils.Log;

/**
 * Packet that is sent to update the client's metadata.
 *
 * @author Fabian Schillig
 */
public class MetadataUpdatePacket extends Packet {
    /**
     * Creates the packet.
     *
     * @param ctx      Android application context.
     * @param session  The session for which location is being updated.
     * @param audioMeta The updated location data obtained from GNSS/network sensors.
     */
    public MetadataUpdatePacket(Context ctx, Session session, MetadataService.AudioMetadata audioMeta) {
        super(ctx, session.getServerURL(), session.getConnectionParameters(), Constants.URL_PATH_POST_EXTRA);
        setParameter(Constants.PACKET_PARAM_SESSION_ID, session.getID());

        setParameter(Constants.PACKET_PARAM_AUDIOMETA, audioMeta.getTitle() + " - " + audioMeta.getArtist());
    }

    @SuppressWarnings("DesignForExtension")
    @Override
    protected void onSuccess(String[] data, Version backendVersion) throws ServerException {
        // Somehow the data array can be empty? Check for this.
        if (data.length < 1) {
            throw new ServerException(getContext(), R.string.err_empty);
        }

        if (!data[0].equals(Constants.PACKET_RESPONSE_OK)) {
            // If the first line of the response is not "OK", an error of some sort has occurred and
            // should be displayed to the user.
            StringBuilder err = new StringBuilder();
            for (String line : data) {
                err.append(line);
                err.append(System.lineSeparator());
            }
            throw new ServerException(err.toString());
        }
    }

    @Override
    protected void onFailure(Exception ex) {
        Log.w("MetadataUpdatePacket", "Sending MetadataUpdatePacket failed: " + ex.getMessage());
    }
}
