package org.tvheadend.tvhclient;

import android.app.Activity;
import android.os.AsyncTask;
import android.view.View;

import org.tvheadend.tvhclient.model.Connection;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WakeOnLanTask extends AsyncTask<String, Void, Integer> {

    private final static String TAG = WakeOnLanTask.class.getSimpleName();

    private final static int WOL_SEND = 0;
    private final static int WOL_SEND_BROADCAST = 1;
    private final static int WOL_INVALID_MAC = 2;
    private final static int WOL_ERROR = 3;

    private Connection conn;
    private Activity activity;
    private Exception exception;
    private View view;
    private TVHClientApplication app;

    public WakeOnLanTask(Activity context, Connection conn, View view) {
        this.activity = context;
        this.conn = conn;
        this.view = view;
        this.app = (TVHClientApplication) context.getApplicationContext();
    }

    @Override
    protected Integer doInBackground(String... params) {
        // Exit if the MAC address is not ok, this should never happen because
        // it is already validated in the settings
        if (!validateMacAddress(conn.wol_mac_address)) {
            return WOL_INVALID_MAC;
        }
        // Get the MAC address parts from the string
        byte[] macBytes = getMacBytes(conn.wol_mac_address);

        // Assemble the byte array that the WOL consists of
        byte[] bytes = new byte[6 + 16 * macBytes.length];
        for (int i = 0; i < 6; i++) {
            bytes[i] = (byte) 0xff;
        }
        // Copy the elements from macBytes to i
        for (int i = 6; i < bytes.length; i += macBytes.length) {
            System.arraycopy(macBytes, 0, bytes, i, macBytes.length);
        }

        try {
            InetAddress address;
            if (!conn.wol_broadcast) {
                address = InetAddress.getByName(conn.address);
                app.log(TAG, "Sending WOL packet to " + address);
            } else {
                // Replace the last number by 255 to send the packet as a broadcast
                byte[] ipAddress = InetAddress.getByName(conn.address).getAddress();
                ipAddress[3] = (byte) 255;
                address = InetAddress.getByAddress(ipAddress);
                app.log(TAG, "Sending WOL packet as broadcast to " + address.toString());
            }
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, conn.wol_port);
            DatagramSocket socket = new DatagramSocket();
            socket.send(packet);
            socket.close();
            app.log(TAG, "Datagram packet send");
            if (!conn.wol_broadcast) {
                app.log(TAG, "Send WOL");
                return WOL_SEND;
            } else {
                app.log(TAG, "Send WOL broadcast");
                return WOL_SEND_BROADCAST;
            }
        } catch (Exception e) {
            this.exception = e;
            app.log(TAG, "Exception for address " + conn.address + ", Exception " + e.getLocalizedMessage());
            return WOL_ERROR;
        }
    }

    /**
     * Checks if the given MAC address is correct.
     * 
     * @param macAddress The MAC address that shall be validated
     * @return True if the MAC address is correct, false otherwise
     */
    private boolean validateMacAddress(final String macAddress) {
        if (macAddress == null) {
            return false;
        }
        // Check if the MAC address is valid
        Pattern pattern = Pattern.compile("([0-9a-fA-F]{2}(?::|-|$)){6}");
        Matcher matcher = pattern.matcher(macAddress);
        return matcher.matches();
    }

    /**
     * Splits the given MAC address into it's parts and saves it in the bytes
     * array
     * 
     * @param macAddress The MAC address that shall be split
     * @return The byte array that holds the MAC address parts
     */
    private byte[] getMacBytes(final String macAddress) {
        byte[] macBytes = new byte[6];

        // Parse the MAC address elements into the array.
        String[] hex = macAddress.split("(:|\\-)");
        for (int i = 0; i < 6; i++) {
            macBytes[i] = (byte) Integer.parseInt(hex[i], 16);
        }
        return macBytes;
    }

    /**
     * Depending on the wake on LAN status the toast with the success or error
     * message is shown to the user
     */
    @Override
    protected void onPostExecute(Integer result) {
        if (result == WOL_SEND) {
            app.showMessage(activity.getString(R.string.wol_send, conn.address));
        } else if (result == WOL_SEND_BROADCAST) {
            app.showMessage(activity.getString(R.string.wol_send_broadcast, conn.address));
        } else if (result == WOL_INVALID_MAC) {
            app.showMessage(activity.getString(R.string.wol_address_invalid));
        } else {
            final String msg = exception.getLocalizedMessage();
            app.showMessage(activity.getString(R.string.wol_error, conn.address, msg));
        }
    }
}