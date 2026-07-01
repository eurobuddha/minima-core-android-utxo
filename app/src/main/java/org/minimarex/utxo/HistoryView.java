package org.minimarex.utxo;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * History tab — a faithful mirror of the standalone Minima History app: the SAME rows (↓received /
 * ↑sent / ⟲self glyph + signed amount + token, counterparty · time, #block) and the SAME tap-detail
 * (per-token effect + full inputs/outputs breakdown). Direction + amount come from the node's
 * details.difference (net per-token effect on the wallet). On-chain history is persisted (nodetx),
 * so it accumulates and shows instantly + offline; pages are adaptive to stay under the node's 256 KB cap.
 */
public class HistoryView extends BaseView {

    private static final int CAP = 1000;       // rows kept + shown

    private final LinearLayout container;
    private int pageMax = 8;                    // adaptive page size — shrinks under the 256 KB cap
    private boolean fetching = false;
    private int lastFetchBlock = -1;
    private boolean moreAvailable = false;

    public HistoryView(MainActivity a) {
        super(a, R.layout.view_history);
        container = find(R.id.historyContainer);
        render();   // show whatever is already persisted, instantly + offline
    }

    @Override public void refresh() { if (visible() && !fetching) render(); }
    @Override public void onShown() { render(); fetch(true); }
    @Override public void onNewBlock() { if (visible()) fetch(false); }
    private boolean visible() { return act.currentTab() == MainActivity.TAB_HISTORY; }

    private void fetch(boolean force) {
        if (fetching) return;
        if (!force && act.chainBlock() <= lastFetchBlock) return;
        fetchPage(0);
    }

    private void loadOlder() { if (!fetching) fetchPage(act.history().nodeTxCount()); }

    /** One adaptive `history` page: shrink (8→4→2→1) + retry under the 256 KB cap; persist new rows; re-render. */
    private void fetchPage(final int offset) {
        fetching = true;
        act.node().cmd("history relevant:true max:" + pageMax + " offset:" + offset, new NodeApi.Cb() {
            @Override public void onResult(JSONObject json) {
                JSONObject resp = json.optJSONObject("response");
                JSONArray txpows = resp == null ? null : resp.optJSONArray("txpows");
                if (resp == null || txpows == null || !json.optBoolean("status", true)) {
                    if (pageMax > 1) { pageMax = Math.max(1, pageMax / 2); fetchPage(offset); return; }
                    fetching = false; render(); return;
                }
                fetching = false;
                lastFetchBlock = act.chainBlock();
                JSONArray details = resp.optJSONArray("details");
                int got = txpows.length();
                for (int i = 0; i < got; i++) {
                    JSONObject t = txpows.optJSONObject(i);
                    if (t == null || !t.optBoolean("istransaction", false)) continue;
                    NodeTx n = NodeTx.from(t, details != null && i < details.length() ? details.optJSONObject(i) : null);
                    if (!n.txpowid.isEmpty()) act.history().upsertNodeTx(n);
                }
                moreAvailable = got >= pageMax && act.history().nodeTxCount() < CAP;
                render();
            }
            @Override public void onError(String message) {
                if (pageMax > 1) { pageMax = Math.max(1, pageMax / 2); fetchPage(offset); return; }
                fetching = false; render();
            }
        });
    }

    // ----- render (identical to the History app's list) -----

    private void render() {
        root.setBackgroundColor(Design.bg());          // was showing dark in light mode
        container.setBackgroundColor(Design.bg());
        container.removeAllViews();
        List<NodeTx> rows = act.history().loadNodeTx(CAP);
        if (rows.isEmpty()) {
            TextView empty = new TextView(act);
            empty.setText(fetching ? "Loading history…" : "No transactions yet.");
            empty.setTextColor(Design.dim());
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(40), 0, 0);
            container.addView(empty);
            return;
        }
        for (NodeTx n : rows) container.addView(row(n));
        if (moreAvailable) {
            TextView more = new TextView(act);
            more.setText("Load older ▾");
            more.setTextColor(Design.accent());
            more.setGravity(Gravity.CENTER);
            more.setPadding(0, dp(12), 0, dp(8));
            more.setOnClickListener(v -> loadOlder());
            container.addView(more);
        }
    }

    private View row(final NodeTx n) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(11), dp(8), dp(11));

        int color = "received".equals(n.direction) ? 0xFF1EA85A          // green in / red out (both themes)
                : "sent".equals(n.direction) ? Design.red() : Design.dim();
        String g = "received".equals(n.direction) ? "↓" : "sent".equals(n.direction) ? "↑" : "⟲";
        String sign = n.incoming ? "+" : "sent".equals(n.direction) ? "−" : "";

        TextView glyph = new TextView(act);
        glyph.setText(g); glyph.setTextColor(color); glyph.setTextSize(18f); glyph.setWidth(dp(28));
        row.addView(glyph);

        LinearLayout mid = new LinearLayout(act);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(dp(6), 0, dp(6), 0);
        mid.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView line1 = new TextView(act);
        line1.setText(sign + Util.tidyAmount(n.amount) + "  " + n.tokenName);
        line1.setTextColor(color); line1.setTextSize(15f); line1.setTypeface(Typeface.DEFAULT_BOLD);
        TextView line2 = new TextView(act);
        String cp = (n.counterparty == null || n.counterparty.isEmpty()) ? "" : Util.shorten(n.counterparty) + "  ·  ";
        line2.setText(cp + relative(n.timemilli));
        line2.setTextColor(Design.dim()); line2.setTextSize(12f);
        mid.addView(line1); mid.addView(line2);
        row.addView(mid);

        TextView right = new TextView(act);
        right.setText("#" + n.block); right.setTextColor(Design.dim()); right.setTextSize(11f); right.setGravity(Gravity.END);
        row.addView(right);

        row.setOnClickListener(v -> showDetail(n));
        return row;
    }

    // ----- detail dialog (identical to the History app) -----

    private void showDetail(NodeTx n) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(12));
        kv(box, "Direction", n.direction);
        kv(box, "Amount", (n.incoming ? "+" : "sent".equals(n.direction) ? "−" : "") + Util.tidyAmount(n.amount) + " " + n.tokenName);
        kv(box, "Block", String.valueOf(n.block));
        kv(box, "Time", new SimpleDateFormat("dd MMM yyyy  HH:mm:ss", Locale.ENGLISH).format(new Date(n.timemilli)));
        copyRow(box, "Txpow id", n.txpowid);
        if (n.counterparty != null && !n.counterparty.isEmpty()) copyRow(box, n.incoming ? "From" : "To", n.counterparty);
        kv(box, "Per-token effect", prettyDeltas(n.deltas));
        addBreakdown(box, "Inputs", n.inputs);
        addBreakdown(box, "Outputs", n.outputs);
        ScrollView sv = new ScrollView(act);
        sv.addView(box);
        new AlertDialog.Builder(act).setTitle("Transaction").setView(sv).setPositiveButton("Close", null).show();
    }

    private void kv(LinearLayout p, String k, String v) {
        TextView t = new TextView(act);
        t.setText(k + ":  " + v);
        t.setTextColor(Design.text()); t.setTextSize(13f); t.setPadding(0, dp(4), 0, dp(4));
        p.addView(t);
    }

    private void copyRow(LinearLayout p, String k, final String v) {
        TextView t = new TextView(act);
        t.setText(k + ":  " + v + "   (tap to copy)");
        t.setTextColor(Design.dim()); t.setTextSize(12f); t.setTypeface(Typeface.MONOSPACE); t.setPadding(0, dp(4), 0, dp(4));
        t.setOnClickListener(view -> {
            ((ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(k, v));
            Toast.makeText(act, "Copied", Toast.LENGTH_SHORT).show();
        });
        p.addView(t);
    }

    private void addBreakdown(LinearLayout p, String title, String json) {
        try {
            JSONArray a = new JSONArray(json);
            if (a.length() == 0) return;
            TextView h = new TextView(act);
            h.setText(title);
            h.setTextColor(Design.accent()); h.setTextSize(12f); h.setTypeface(Typeface.DEFAULT_BOLD); h.setPadding(0, dp(8), 0, dp(2));
            p.addView(h);
            for (int i = 0; i < a.length(); i++) {
                JSONObject c = a.optJSONObject(i);
                if (c == null) continue;
                String tid = c.optString("tokenid", "0x00");
                String tok = Util.isMinima(tid) ? "Minima" : Util.shorten(tid);
                TextView t = new TextView(act);
                t.setText("• " + Util.tidyAmount(c.optString("amount", "")) + " " + tok + "  →  " + Util.shorten(c.optString("addr", "")));
                t.setTextColor(Design.dim()); t.setTextSize(12f); t.setPadding(dp(6), dp(1), 0, dp(1));
                p.addView(t);
            }
        } catch (Exception ignored) {}
    }

    private String prettyDeltas(String json) {
        try {
            JSONObject o = new JSONObject(json);
            StringBuilder sb = new StringBuilder();
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String tid = it.next();
                if (sb.length() > 0) sb.append(", ");
                sb.append(Util.tidyAmount(o.optString(tid, ""))).append(" ").append(Util.isMinima(tid) ? "Minima" : Util.shorten(tid));
            }
            return sb.length() > 0 ? sb.toString() : "—";
        } catch (Exception e) { return "—"; }
    }

    private static String relative(long ms) {
        if (ms <= 0) return "";
        long d = System.currentTimeMillis() - ms;
        if (d < 60000) return "just now";
        if (d < 3600000) return (d / 60000) + "m ago";
        if (d < 86400000) return (d / 3600000) + "h ago";
        if (d < 7 * 86400000L) return (d / 86400000) + "d ago";
        return new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(new Date(ms));
    }

    private int dp(int v) { return (int) (v * act.getResources().getDisplayMetrics().density); }
}
