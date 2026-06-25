package org.minimarex.utxo;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONObject;

/** Receive tab: shows the node's default address with a QR code. */
public class ReceiveView extends BaseView {

    private final TextView address;
    private final ImageView qr;

    public ReceiveView(MainActivity a) {
        super(a, R.layout.view_receive);
        address = find(R.id.rcvAddress);
        qr = find(R.id.rcvQr);

        android.widget.Button copy = find(R.id.rcvCopy);
        copy.setOnClickListener(v -> copyAddress());
        Button refreshBtn = find(R.id.rcvRefresh);
        refreshBtn.setOnClickListener(v -> fetchFresh());
        refreshBtn.setTextColor(Design.accent());
        root.setBackgroundColor(Design.bg());
        address.setBackgroundColor(Design.surface());
        address.setTextColor(Design.text());
        copy.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Design.accent()));
        copy.setTextColor(Design.onAccent());
        refresh();
    }

    @Override
    public void refresh() {
        String addr = act.defaultAddress();
        if (addr == null || addr.isEmpty()) {
            address.setText("Fetching address…");
            qr.setImageBitmap(null);
            return;
        }
        address.setText(addr);
        renderQr(addr);
    }

    /** getaddress is no longer fetched on every reload — fetch it lazily when the tab is opened. */
    @Override
    public void onShown() {
        String addr = act.defaultAddress();
        if (addr == null || addr.isEmpty()) fetchFresh();
    }

    private void fetchFresh() {
        act.node().cmd("getaddress", new NodeApi.Cb() {
            @Override public void onResult(JSONObject json) {
                JSONObject r = json.optJSONObject("response");
                String addr = r == null ? "" : r.optString("miniaddress", r.optString("address", ""));
                if (!addr.isEmpty()) {
                    act.setDefaultAddress(addr);   // so the display and the Copy button both work
                    address.setText(addr);
                    renderQr(addr);
                }
            }
            @Override public void onError(String message) {
                if (!NodeApi.ERR_NOT_ENABLED.equals(message)) {
                    Toast.makeText(act, message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void renderQr(final String text) {
        qr.setTag(text);
        new Thread(() -> {
            Bitmap bmp = null;
            try {
                int size = 480;
                BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size);
                bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
                for (int x = 0; x < size; x++) {
                    for (int y = 0; y < size; y++) {
                        bmp.setPixel(x, y, m.get(x, y) ? Color.BLACK : Color.WHITE);
                    }
                }
            } catch (Exception e) {
                bmp = null;
            }
            final Bitmap result = bmp;
            act.runOnUiThread(() -> {
                if (text.equals(qr.getTag())) qr.setImageBitmap(result);
            });
        }).start();
    }

    private void copyAddress() {
        String addr = act.defaultAddress();
        if (addr == null || addr.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Minima address", addr));
        Toast.makeText(act, "Address copied", Toast.LENGTH_SHORT).show();
    }
}
