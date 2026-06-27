package org.minimarex.utxo;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * History tab — sent + received, sourced from the node, built node-friendly (lessons from myHistory).
 *
 * One bounded `history relevant:true max:50` call is made ONLY when this tab is visible (on show, and
 * at most once per new block while shown) — never on every reload, never while on another tab. Each
 * relevant transaction is classified client-side (in / out / self) from its input/output addresses vs
 * the wallet's own. The wallet's own in-flight sends (from the local store) are merged on top until
 * they appear on-chain. Rows are a light collapsed summary; the full breakdown is built lazily on tap.
 */
public class HistoryView extends BaseView {

    private static final String EXPLORER = "https://explorer.minima.global/transactions/";
    // Bounded page size: ~14 KB per txpow, so 25 ≈ ~350 KB/call — safely under the ~1 MB Binder limit.
    // Older history is paged in via `offset`, never by growing a single call.
    private static final int FETCH_PAGE = 25;
    private static final int FETCH_CAP = 300;     // max accumulated entries kept in memory
    private static final long STALE_MS = 15 * 60 * 1000L;   // posted-but-unmatched ⇒ assume confirmed

    private final SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ENGLISH);
    private final LinearLayout container;
    private final Set<String> expanded = new HashSet<>();

    private final List<Entry> nodeEntries = new ArrayList<>();   // accumulated on-chain history (deduped)
    private final Set<String> nodeIds = new HashSet<>();         // txpowids already in nodeEntries
    private int lastFetchBlock = -1;
    private boolean fetching = false;
    private boolean moreAvailable = false;       // last page was full ⇒ older txns exist
    private int pageMax = 8;                      // adaptive page size — shrinks if a page exceeds the 256 KB cap

    public HistoryView(MainActivity a) {
        super(a, R.layout.view_history);
        container = find(R.id.historyContainer);
        root.setBackgroundColor(Design.bg());
        loadPersisted();   // show accumulated on-chain history instantly, from the local DB
        render();
    }

    // ----- visibility-driven fetching -----

    // Skip the cheap re-render while a fetch is in flight — it will render on completion.
    @Override public void refresh() { if (visible() && !fetching) render(); }

    @Override public void onShown() { render(); fetch(true); }

    @Override public void onNewBlock() { if (visible()) fetch(false); }

    /** True only when the History tab is the one on screen — gates all node fetching. */
    private boolean visible() { return act.currentTab() == MainActivity.TAB_HISTORY; }

    /** Refresh the head of history (page 0) — picks up new txns and confirms in-flight sends.
     *  force = fetch even if the block hasn't advanced (user opened the tab). */
    private void fetch(boolean force) {
        if (fetching) return;
        if (!force && act.chainBlock() <= lastFetchBlock) return;
        fetchPage(0);
    }

    /** Page in older history via offset — never enlarges any single response. */
    private void loadOlder() {
        if (fetching) return;
        fetchPage(nodeEntries.size());
    }

    /** Issue one bounded `history` call at the given offset and merge/render the result. */
    private void fetchPage(final int offset) {
        fetching = true;
        act.node().cmd("history relevant:true max:" + pageMax + " offset:" + offset, new NodeApi.Cb() {
            @Override public void onResult(JSONObject json) {
                JSONObject resp = json.optJSONObject("response");
                JSONArray txpows = resp == null ? null : resp.optJSONArray("txpows");
                // The node caps any reply at 256 KB; an oversized page comes back dropped/empty. Shrink the
                // page and retry the SAME offset (8→4→2→1) so a contract-heavy node still loads.
                if (resp == null || txpows == null || !json.optBoolean("status", true)) {
                    if (pageMax > 1) { pageMax = Math.max(1, pageMax / 2); fetchPage(offset); return; }
                    fetching = false; return;
                }
                fetching = false;
                lastFetchBlock = act.chainBlock();
                mergePage(json, offset == 0);
                render();
            }
            @Override public void onError(String message) {
                if (pageMax > 1) { pageMax = Math.max(1, pageMax / 2); fetchPage(offset); return; }
                fetching = false;
            }
        });
    }

    /** Merge a page of on-chain history (dedupe by txpowid); on the head page also confirm local sends. */
    private void mergePage(JSONObject json, boolean isHead) {
        JSONObject resp = json.optJSONObject("response");
        JSONArray txpows = resp == null ? null : resp.optJSONArray("txpows");
        if (txpows == null) return;
        JSONArray details = resp.optJSONArray("details");   // per-txpow net effect → reliable in/out/self

        Set<String> mine = myAddressSet();
        Map<String, String> inputCoinToTxpow = new LinkedHashMap<>();   // head page, for confirming sends
        int pageTxns = 0;
        for (int i = 0; i < txpows.length(); i++) {
            JSONObject t = txpows.optJSONObject(i);
            if (t == null || !t.optBoolean("istransaction", false)) continue;
            pageTxns++;
            String txpowid = t.optString("txpowid", "");
            JSONObject body = t.optJSONObject("body");
            JSONObject txn = body == null ? null : body.optJSONObject("txn");
            if (txn == null) continue;
            JSONArray ins = txn.optJSONArray("inputs");
            JSONArray outs = txn.optJSONArray("outputs");
            JSONObject h = t.optJSONObject("header");
            int block = h == null ? 0 : (int) h.optLong("block", 0);
            long time = h == null ? 0 : h.optLong("timemilli", 0);

            if (isHead && ins != null) for (int j = 0; j < ins.length(); j++) {
                JSONObject in = ins.optJSONObject(j);
                String cid = in == null ? "" : in.optString("coinid", "");
                if (!cid.isEmpty()) inputCoinToTxpow.put(cid, txpowid);
            }

            if (txpowid.isEmpty() || nodeIds.contains(txpowid) || nodeEntries.size() >= FETCH_CAP) continue;
            Entry e = nodeEntryFromDetails(ins, outs, details != null && i < details.length() ? details.optJSONObject(i) : null);
            e.txpowid = txpowid;
            e.block = block;
            e.time = time;
            e.status = HistoryDb.STATUS_CONFIRMED;
            nodeEntries.add(e);
            nodeIds.add(txpowid);
            act.history().upsertNodeTx(nodeTxOf(e));   // persist — accumulates + survives restart
        }

        Collections.sort(nodeEntries, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) {
                if (a.block != b.block) return b.block - a.block;
                return Long.compare(b.time, a.time);
            }
        });
        moreAvailable = txpows.length() >= pageMax && nodeEntries.size() < FETCH_CAP;

        // Head page only: confirm any in-flight send whose spent input now appears on-chain; if a send
        // was broadcast long ago but isn't in the window, assume it confirmed (no explorer id) so it
        // stops showing as "pending" forever.
        if (isHead) {
            long now = System.currentTimeMillis();
            for (HistoryRow r : act.history().list(200)) {
                if (!HistoryDb.STATUS_POSTING.equals(r.status) && !HistoryDb.STATUS_POSTED.equals(r.status)) continue;
                String real = matchLocal(r, inputCoinToTxpow);
                if (real != null) {
                    act.history().update(r.internalid, HistoryDb.STATUS_CONFIRMED, real, null);
                } else if (now - r.ts > STALE_MS) {
                    act.history().update(r.internalid, HistoryDb.STATUS_CONFIRMED, null, null);
                }
            }
        }
    }

    /** Find the on-chain txpowid that spent one of this local row's input coins, or null. */
    private String matchLocal(HistoryRow r, Map<String, String> inputCoinToTxpow) {
        JSONArray ins = parseArr(r.inputs);
        if (ins == null) return null;
        for (int i = 0; i < ins.length(); i++) {
            JSONObject o = ins.optJSONObject(i);
            String cid = o == null ? "" : o.optString("coinid", "");
            if (!cid.isEmpty() && inputCoinToTxpow.containsKey(cid)) return inputCoinToTxpow.get(cid);
        }
        return null;
    }

    // ----- render -----

    /** Rebuild the card list: merge local in-flight/failed/old-confirmed rows with on-chain entries,
     *  dedupe a confirmed local send against its node entry, sort, and lay out (plus "Load older"). */
    private void render() {
        container.removeAllViews();

        Set<String> nodeTxids = new HashSet<>();
        for (Entry ne : nodeEntries) if (ne.txpowid != null) nodeTxids.add(ne.txpowid);

        List<Entry> display = new ArrayList<>();
        Map<String, String> txpowToInternal = new HashMap<>();   // real txpowid -> local internalid
        for (HistoryRow r : act.history().list(200)) {
            String realTx = (r.txnid != null && r.txnid.matches("^0x[0-9A-Fa-f]{64}$")) ? r.txnid : null;
            if (HistoryDb.STATUS_POSTING.equals(r.status) || HistoryDb.STATUS_POSTED.equals(r.status)) {
                display.add(localEntry(r, "pending"));
            } else if (HistoryDb.STATUS_ERROR.equals(r.status)) {
                display.add(localEntry(r, "failed"));
            } else if (HistoryDb.STATUS_CONFIRMED.equals(r.status)) {
                if (realTx != null) txpowToInternal.put(realTx, r.internalid);
                // A confirmed local send shows from the local record only when it's older than the
                // fetched on-chain window (otherwise the node entry below represents it).
                if (realTx == null || !nodeTxids.contains(realTx)) display.add(localEntry(r, "confirmed"));
            }
        }
        // Carry the local internalid onto its matching node entry so expand state survives confirmation.
        for (Entry ne : nodeEntries) {
            if (ne.txpowid != null && txpowToInternal.containsKey(ne.txpowid)) ne.internalid = txpowToInternal.get(ne.txpowid);
            display.add(ne);
        }

        // pending/failed first (newest first), then on-chain newest-first.
        Collections.sort(display, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) {
                int ra = rank(a), rb = rank(b);
                if (ra != rb) return ra - rb;
                if (a.block != b.block) return b.block - a.block;
                return Long.compare(b.time, a.time);
            }
        });

        if (display.isEmpty()) {
            TextView tv = new TextView(act);
            tv.setText("No transactions yet.");
            tv.setTextColor(Design.dim());
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, dp(40), 0, 0);
            container.addView(tv);
            return;
        }

        for (Entry e : display) container.addView(buildCard(e));

        if (moreAvailable) {
            TextView more = new TextView(act);
            more.setText("Load older ↓");
            more.setTextColor(Design.accent());
            more.setTextSize(12f);
            more.setGravity(Gravity.CENTER);
            more.setPadding(0, dp(14), 0, dp(14));
            more.setOnClickListener(v -> loadOlder());
            container.addView(more);
        }
    }

    /** Sort key: in-flight (pending/failed) rows rank above on-chain ones. */
    private int rank(Entry e) { return "pending".equals(e.direction) || "failed".equals(e.direction) ? 0 : 1; }

    /** Build one history card: header (badge·date·status), net-amount summary, lazy Details toggle. */
    private View buildCard(final Entry e) {
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Design.surface());
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(8);
        card.setLayoutParams(clp);

        // Header — direction badge · date · status pill
        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(pill(dirBadge(e.direction), dirColor(e.direction)));
        TextView time = new TextView(act);
        time.setText("  " + (e.time > 0 ? fmt.format(new Date(e.time)) : "—"));
        time.setTextColor(Design.dim());
        time.setTextSize(10f);
        time.setTypeface(Typeface.MONOSPACE);
        time.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(time);
        head.addView(pill(statusLabel(e), statusColor(e)));
        card.addView(head);

        // Summary — net amount → counterparty
        LinearLayout summary = new LinearLayout(act);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        summary.setPadding(0, dp(7), 0, 0);
        TextView amt = new TextView(act);
        amt.setText(e.netDisplay + " " + e.tokenLabel + (e.multiMore > 0 ? "  +" + e.multiMore + " tok" : ""));
        amt.setTextColor(dirColor(e.direction));
        amt.setTextSize(14f);
        amt.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        summary.addView(amt);
        TextView arrow = new TextView(act);
        arrow.setText("in".equals(e.direction) ? "   from " : "   to ");
        arrow.setTextColor(Design.dim());
        arrow.setTextSize(11f);
        summary.addView(arrow);
        TextView cp = new TextView(act);
        cp.setText(truncMid(e.counterparty, 10, 8));
        cp.setTextColor(Design.text());
        cp.setTextSize(12f);
        cp.setTypeface(Typeface.MONOSPACE);
        cp.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        summary.addView(cp);
        card.addView(summary);

        // Details toggle (lazy)
        final TextView toggle = new TextView(act);
        boolean open = expanded.contains(e.key());
        toggle.setText(open ? "Details ▴" : "Details ▾");
        toggle.setTextColor(Design.accent());
        toggle.setTextSize(11f);
        toggle.setPadding(0, dp(9), 0, dp(2));
        card.addView(toggle);

        final LinearLayout details = new LinearLayout(act);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setVisibility(open ? View.VISIBLE : View.GONE);
        if (open) buildDetails(details, e);
        card.addView(details);

        toggle.setOnClickListener(v -> {
            boolean nowOpen = details.getVisibility() != View.VISIBLE;
            // Build the breakdown only on first expand (cheap collapsed rows by default).
            if (nowOpen && details.getChildCount() == 0) buildDetails(details, e);
            details.setVisibility(nowOpen ? View.VISIBLE : View.GONE);
            toggle.setText(nowOpen ? "Details ▴" : "Details ▾");
            if (nowOpen) expanded.add(e.key()); else expanded.remove(e.key());
        });
        return card;
    }

    /** Populate the expanded breakdown: FROM/TO addresses, change/burn, block, tx hash + explorer link,
     *  and (for failed local rows) Re-post / Dismiss actions. */
    private void buildDetails(LinearLayout d, final Entry e) {
        // FROM
        if (e.inputs != null && e.inputs.length() > 0) {
            d.addView(kvLabel("FROM", e.inputs.length() + (e.inputs.length() == 1 ? " input" : " inputs"), null));
            for (int i = 0; i < e.inputs.length(); i++) {
                JSONObject o = e.inputs.optJSONObject(i);
                if (o == null) continue;
                d.addView(addressLine(addrOf(o), o.optString("amount", "")));
            }
        }
        // TO
        if (e.outputs != null && e.outputs.length() > 0) {
            d.addView(kvLabel("TO", e.outputs.length() + (e.outputs.length() == 1 ? " output" : " outputs"), null));
            for (int i = 0; i < e.outputs.length(); i++) {
                JSONObject o = e.outputs.optJSONObject(i);
                if (o == null) continue;
                String a = addrOf(o);
                d.addView(addressLine(a + (isMine(a) ? "  (yours)" : ""), o.optString("amount", "")));
            }
        }
        // CHANGE / BURN / NOTE / INTERNAL ID — local in-flight sends only
        if (e.changeaddr != null && !e.changeaddr.isEmpty()) {
            d.addView(kvLabel("CHANGE", isMine(e.changeaddr) ? "yours" : "external", null));
            d.addView(addressLine(e.changeaddr, ""));
        }
        if (e.burn != null && !e.burn.isEmpty() && isPositive(e.burn)) {
            d.addView(kvLabel("BURN", null, null));
            d.addView(plainRow(compactAmount(e.burn) + " MINIMA"));
        }
        // BLOCK / confirmations
        if (e.block > 0) {
            d.addView(kvLabel("BLOCK", null, null));
            int confs = Math.max(0, act.chainBlock() - e.block);
            d.addView(plainRow("#" + e.block + "   (" + confs + " confirmation" + (confs == 1 ? "" : "s") + ")"));
        }
        // TX HASH
        d.addView(kvLabel("TX HASH", null, null));
        final String id = e.txpowid == null ? "" : e.txpowid;
        if (id.matches("^0x[0-9A-Fa-f]{64}$")) {
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(3), 0, dp(3));
            TextView h = new TextView(act);
            h.setText(truncMid(id, 10, 8));
            h.setTextColor(Design.text());
            h.setTextSize(12f);
            h.setTypeface(Typeface.MONOSPACE);
            h.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(h);
            row.addView(copyChip(id));
            TextView ex = new TextView(act);
            ex.setText("  Explorer ↗");
            ex.setTextColor(Design.accent());
            ex.setTextSize(11f);
            ex.setPadding(dp(6), dp(2), dp(2), dp(2));
            ex.setOnClickListener(v -> {
                try { act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(EXPLORER + id))); }
                catch (Exception ignored) {}
            });
            row.addView(ex);
            d.addView(row);
        } else {
            d.addView(italic("(appears once the transaction confirms on-chain)"));
        }
        // INTERNAL ID + NOTE (local)
        if (e.internalid != null) {
            d.addView(kvLabel("INTERNAL ID", null, null));
            d.addView(addressLine(e.internalid, ""));
        }
        if (e.note != null && !e.note.isEmpty()) {
            d.addView(kvLabel("NOTE", null, null));
            d.addView(plainRow(e.note));
        }
        // Failed-row actions
        if ("failed".equals(e.direction) && e.localRow != null) {
            LinearLayout actions = new LinearLayout(act);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0, dp(12), 0, 0);
            if (canRepost(e.localRow)) actions.addView(actionChip("Re-post", Design.accent(), v -> repost(e.localRow)));
            actions.addView(actionChip("Dismiss", Design.red(), v -> {
                act.history().delete(e.localRow.internalid);
                render();
            }));
            d.addView(actions);
        }
    }

    // ----- classification -----

    // Direction/net are derived from whether output addresses are the wallet's own. This relies on
    // act.myAddresses() being current (populated from `scripts`); a change address not yet surfaced
    // there would be counted as external. In practice change uses default addresses that `scripts`
    // returns, so the gap is rare and self-corrects on the next reload.
    private Entry classify(JSONArray inputs, JSONArray outputs, Set<String> mine) {
        Entry e = new Entry();
        e.inputs = inputs;
        e.outputs = outputs;
        boolean mineIn = anyMine(inputs, mine);

        Map<String, BigDecimal> toOthers = new LinkedHashMap<>();
        Map<String, BigDecimal> toMine = new LinkedHashMap<>();
        String firstOther = null;
        if (outputs != null) for (int i = 0; i < outputs.length(); i++) {
            JSONObject o = outputs.optJSONObject(i);
            if (o == null) continue;
            String a = addrOf(o);
            String tok = o.optString("tokenid", "0x00");
            BigDecimal amt = dec(o.optString("amount", "0"));
            if (isMine(a)) add(toMine, tok, amt);
            else { add(toOthers, tok, amt); if (firstOther == null) firstOther = a; }
        }

        if (!mineIn) {
            e.direction = "in";
            String tok = pickToken(toMine);
            e.netDisplay = "+" + compactAmount(bd(toMine, tok).toPlainString());
            e.tokenLabel = tokenLabel(tok, inputs, outputs);
            e.counterparty = firstAddr(inputs);
            e.multiMore = Math.max(0, toMine.size() - 1);
        } else if (anyPositive(toOthers)) {
            e.direction = "out";
            String tok = pickToken(toOthers);
            e.netDisplay = "−" + compactAmount(bd(toOthers, tok).toPlainString());
            e.tokenLabel = tokenLabel(tok, inputs, outputs);
            e.counterparty = firstOther != null ? firstOther : "—";
            e.multiMore = Math.max(0, toOthers.size() - 1);
        } else {
            e.direction = "self";
            String tok = pickToken(toMine);
            e.netDisplay = compactAmount(bd(toMine, tok).toPlainString());
            e.tokenLabel = tokenLabel(tok, inputs, outputs);
            e.counterparty = "your own addresses";
            e.multiMore = Math.max(0, toMine.size() - 1);
        }
        return e;
    }

    // ----- node-history entries (from details.difference) + persistence -----

    /** Direction + net amount from the node's details.difference (net per-token effect on the wallet) —
     *  reliable, unlike diffing outputs against our address set, so "in / out / self" is always correct. */
    private Entry nodeEntryFromDetails(JSONArray ins, JSONArray outs, JSONObject difference) {
        Entry e = new Entry();
        e.inputs = ins; e.outputs = outs;
        String pTid = "0x00"; BigDecimal pAmt = BigDecimal.ZERO; int toks = 0;
        if (difference != null) for (java.util.Iterator<String> it = difference.keys(); it.hasNext(); ) {
            String tid = it.next(); toks++;
            BigDecimal a = dec(difference.optString(tid, "0"));
            if (a.abs().compareTo(pAmt.abs()) > 0) { pAmt = a; pTid = tid; }
        }
        int sign = pAmt.signum();
        e.direction = sign > 0 ? "in" : sign < 0 ? "out" : "self";
        e.netDisplay = (sign > 0 ? "+" : sign < 0 ? "−" : "") + compactAmount(pAmt.abs().toPlainString());
        e.tokenLabel = tokenLabel(pTid, ins, outs);
        e.multiMore = Math.max(0, toks - 1);
        e.counterparty = sign > 0 ? firstAddr(ins) : firstNonMineOut(outs);
        return e;
    }

    /** First output address that isn't ours (the recipient), else the first output address. */
    private String firstNonMineOut(JSONArray outs) {
        if (outs != null) for (int i = 0; i < outs.length(); i++) {
            JSONObject o = outs.optJSONObject(i);
            if (o != null && !isMine(addrOf(o))) return addrOf(o);
        }
        return firstAddr(outs);
    }

    /** Snapshot an Entry into a persistable NodeTx row. */
    private NodeTx nodeTxOf(Entry e) {
        NodeTx n = new NodeTx();
        n.txpowid = e.txpowid; n.block = e.block; n.time = e.time;
        n.direction = e.direction; n.net = e.netDisplay; n.token = e.tokenLabel;
        n.counterparty = e.counterparty; n.multiMore = e.multiMore;
        n.inputs = e.inputs != null ? e.inputs.toString() : "[]";
        n.outputs = e.outputs != null ? e.outputs.toString() : "[]";
        return n;
    }

    /** Load the persisted on-chain history into memory at startup — instant + offline. */
    private void loadPersisted() {
        for (NodeTx n : act.history().loadNodeTx(FETCH_CAP)) {
            if (n.txpowid == null || nodeIds.contains(n.txpowid)) continue;
            Entry e = new Entry();
            e.txpowid = n.txpowid; e.block = n.block; e.time = n.time;
            e.direction = n.direction; e.netDisplay = n.net; e.tokenLabel = n.token;
            e.counterparty = n.counterparty; e.multiMore = n.multiMore;
            e.inputs = parseArr(n.inputs); e.outputs = parseArr(n.outputs);
            e.status = HistoryDb.STATUS_CONFIRMED;
            nodeEntries.add(e); nodeIds.add(n.txpowid);
        }
    }

    /** Build a display Entry from a local store row (kind = pending / failed / confirmed). */
    private Entry localEntry(HistoryRow r, String kind) {
        Entry e = new Entry();
        e.localRow = r;
        e.internalid = r.internalid;
        e.txpowid = (r.txnid != null && r.txnid.matches("^0x[0-9A-Fa-f]{64}$")) ? r.txnid : null;
        e.time = r.ts;
        e.block = 0;
        e.changeaddr = r.changeaddr;
        e.burn = r.burn;
        e.note = r.note;
        e.inputs = parseArr(r.inputs);
        e.outputs = parseArr(r.outputs);
        e.tokenLabel = Util.isMinima(r.tokenid) ? "MINIMA"
                : (r.tokenName != null && !r.tokenName.isEmpty() ? r.tokenName : truncMid(r.tokenid, 6, 4));
        e.netDisplay = "−" + compactAmount(r.amount);
        e.counterparty = r.recipient == null ? "—" : r.recipient;
        if ("confirmed".equals(kind)) {
            e.direction = isMine(r.recipient) ? "self" : "out";
            e.status = HistoryDb.STATUS_CONFIRMED;
        } else {
            e.direction = kind;   // "pending" or "failed"
            e.status = "failed".equals(kind) ? HistoryDb.STATUS_ERROR : HistoryDb.STATUS_POSTED;
        }
        return e;
    }

    // ----- Re-post (failed local rows) -----

    /** Re-post is only possible if we stored the input coins to rebuild the transaction from. */
    private boolean canRepost(HistoryRow r) {
        JSONArray ins = parseArr(r.inputs);
        return ins != null && ins.length() > 0;
    }

    /** Rebuild a failed send from its stored inputs/outputs and re-broadcast it via TxnBuilder. */
    private void repost(final HistoryRow r) {
        JSONArray insArr = parseArr(r.inputs);
        if (insArr == null || insArr.length() == 0) { toast("Can't re-post — inputs not stored."); return; }
        List<Coin> ins = new ArrayList<>();
        for (int i = 0; i < insArr.length(); i++) {
            JSONObject o = insArr.optJSONObject(i);
            if (o == null) continue;
            Coin c = new Coin();
            c.coinid = o.optString("coinid", "");
            c.amount = o.optString("amount", "0");
            c.tokenid = r.tokenid;
            if (c.coinid.isEmpty()) { toast("Can't re-post — a coinid is missing."); return; }
            ins.add(c);
        }
        List<TxnBuilder.Out> outs = new ArrayList<>();
        JSONArray outsArr = parseArr(r.outputs);
        if (outsArr != null && outsArr.length() > 0) {
            for (int i = 0; i < outsArr.length(); i++) {
                JSONObject o = outsArr.optJSONObject(i);
                if (o == null) continue;
                outs.add(new TxnBuilder.Out(o.optString("address", ""), o.optString("amount", "")));
            }
        } else {
            outs.add(new TxnBuilder.Out(r.recipient, r.amount));
        }
        act.history().update(r.internalid, HistoryDb.STATUS_POSTING, null, "Re-posting…");
        render();
        new TxnBuilder(act, ins, outs, r.tokenid, r.burn, new TxnBuilder.Done() {
            @Override public void onPosted(String txpowid, List<TxnBuilder.OutCoin> outputs) {
                act.history().update(r.internalid, HistoryDb.STATUS_POSTED, null, "");
                toast("Re-posted — confirms shortly.");
                render();
            }
            @Override public void onFailed(String message) {
                if (NodeApi.ERR_NOT_ENABLED.equals(message)) message = "Enable this wallet in Minima Core → Apps first.";
                act.history().update(r.internalid, HistoryDb.STATUS_ERROR, null, message);
                render();
            }
        }).run();
    }

    // ----- entry model -----

    private static class Entry {
        String direction;     // in / out / self / pending / failed
        String status;
        String netDisplay;    // e.g. "+1.5" / "−0.0004"
        String tokenLabel;
        String counterparty;
        long time;
        int block;
        String txpowid;       // real on-chain id (null until confirmed)
        int multiMore;        // extra tokens moved beyond the primary (summary hint)
        JSONArray inputs, outputs;
        // local in-flight fields:
        HistoryRow localRow;
        String internalid, changeaddr, burn, note;

        // Prefer the stable internalid so expand state survives a pending→confirmed transition.
        String key() { return internalid != null ? internalid : (txpowid != null ? txpowid : (block + ":" + time)); }
    }

    // ----- small builders (shared look) -----

    /** A row showing a truncated address, optional tappable-to-copy amount, and a COPY chip. */
    private View addressLine(String addr, String amountRaw) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(3), 0, dp(3));
        TextView a = new TextView(act);
        a.setText(truncMid(addr, 12, 10));
        a.setTextColor(Design.text());
        a.setTextSize(12f);
        a.setTypeface(Typeface.MONOSPACE);
        a.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(a);
        if (amountRaw != null && !amountRaw.isEmpty()) {
            final String exact = amountRaw;
            TextView am = new TextView(act);
            am.setText(compactAmount(amountRaw));
            am.setTextColor(Design.text());
            am.setTextSize(12f);
            am.setTypeface(Typeface.MONOSPACE);
            am.setPadding(dp(8), 0, dp(8), 0);
            am.setOnClickListener(v -> copy(exact, "Amount"));
            row.addView(am);
        }
        row.addView(copyChip(addr));
        return row;
    }

    /** A single monospace text row. */
    private View plainRow(String text) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextColor(Design.text());
        t.setTextSize(12f);
        t.setTypeface(Typeface.MONOSPACE);
        t.setPadding(0, dp(3), 0, dp(3));
        return t;
    }

    /** A dim italic note row (e.g. "appears once confirmed"). */
    private View italic(String text) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextColor(Design.dim());
        t.setTextSize(11f);
        t.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC));
        t.setPadding(0, dp(3), 0, dp(3));
        return t;
    }

    /** A section label (e.g. FROM / TO / BLOCK) with optional dim/accent trailing tags. */
    private LinearLayout kvLabel(String label, String dimTag, String accentTag) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(11), 0, dp(2));
        TextView l = new TextView(act);
        l.setText(label);
        l.setTextColor(Design.dim());
        l.setTextSize(10f);
        l.setLetterSpacing(0.12f);
        l.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        r.addView(l);
        if (dimTag != null) r.addView(tag(dimTag, Design.dim()));
        if (accentTag != null) r.addView(tag(accentTag, Design.accent()));
        return r;
    }

    /** A small "· text" inline tag. */
    private TextView tag(String text, int color) {
        TextView v = new TextView(act);
        v.setText("  · " + text);
        v.setTextColor(color);
        v.setTextSize(9f);
        v.setTypeface(Typeface.MONOSPACE);
        return v;
    }

    /** A colored uppercase status/direction pill. */
    private TextView pill(String text, int color) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(9f);
        t.setLetterSpacing(0.08f);
        t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        return t;
    }

    /** An outlined COPY chip that copies the given text and flashes "COPIED". */
    private TextView copyChip(final String text) {
        final TextView c = new TextView(act);
        c.setText("COPY");
        c.setTextSize(9f);
        c.setTypeface(Typeface.MONOSPACE);
        c.setTextColor(Design.dim());
        c.setPadding(dp(6), dp(2), dp(6), dp(2));
        GradientDrawable g = new GradientDrawable();
        g.setStroke(dp(1), Design.border2());
        g.setColor(0x00000000);
        c.setBackground(g);
        c.setOnClickListener(v -> {
            copy(text, "Copied");
            c.setText("COPIED");
            c.postDelayed(() -> c.setText("COPY"), 1000);
        });
        return c;
    }

    /** An outlined tappable action button (Re-post / Dismiss). */
    private TextView actionChip(String text, int color, View.OnClickListener onClick) {
        TextView b = new TextView(act);
        b.setText(text);
        b.setTextColor(color);
        b.setTextSize(11f);
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable g = new GradientDrawable();
        g.setStroke(dp(1), color);
        g.setColor(0x00000000);
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        b.setLayoutParams(lp);
        b.setOnClickListener(onClick);
        return b;
    }

    // ----- helpers -----

    /** Direction → header badge text. */
    private String dirBadge(String dir) {
        switch (dir) {
            case "in":      return "↓ In";
            case "out":     return "↑ Out";
            case "self":    return "↻ Self";
            case "pending": return "⧗ Pending";
            case "failed":  return "✕ Failed";
            default:        return dir;
        }
    }

    /** Direction → accent color for the net amount. */
    private int dirColor(String dir) {
        switch (dir) {
            case "in":      return Design.success();
            case "out":     return Design.text();
            case "self":    return Design.dim();
            case "pending": return Design.amber();
            case "failed":  return Design.red();
            default:        return Design.text();
        }
    }

    /** Status pill text for an entry. */
    private String statusLabel(Entry e) {
        if ("pending".equals(e.direction)) return "PENDING";
        if ("failed".equals(e.direction)) return "FAILED";
        return "CONFIRMED";
    }

    /** Status pill color for an entry. */
    private int statusColor(Entry e) {
        if ("pending".equals(e.direction)) return Design.amber();
        if ("failed".equals(e.direction)) return Design.red();
        return Design.success();
    }

    /** Flatten the wallet's own addresses (both address forms) into a lookup set. */
    private Set<String> myAddressSet() {
        Set<String> s = new HashSet<>();
        for (String[] a : act.myAddresses()) { if (a[0] != null) s.add(a[0]); if (a[1] != null) s.add(a[1]); }
        return s;
    }

    /** Is this address one of the wallet's own (either address form)? */
    private boolean isMine(String addr) {
        if (addr == null) return false;
        for (String[] a : act.myAddresses()) if (addr.equals(a[0]) || addr.equals(a[1])) return true;
        return false;
    }

    /** Does any coin in the array belong to the wallet? (used to detect "out" vs "in"). */
    private boolean anyMine(JSONArray coins, Set<String> mine) {
        if (coins == null) return false;
        for (int i = 0; i < coins.length(); i++) {
            JSONObject o = coins.optJSONObject(i);
            if (o == null) continue;
            if (mine.contains(o.optString("address", "")) || mine.contains(o.optString("miniaddress", ""))) return true;
        }
        return false;
    }

    /** Prefer the readable miniaddress, fall back to the raw address. */
    private String addrOf(JSONObject coin) {
        String mini = coin.optString("miniaddress", "");
        return !mini.isEmpty() ? mini : coin.optString("address", "");
    }

    /** Address of the first coin (the sender, for "in" rows), or "—". */
    private String firstAddr(JSONArray coins) {
        if (coins != null && coins.length() > 0) { JSONObject o = coins.optJSONObject(0); if (o != null) return addrOf(o); }
        return "—";
    }

    /** Display label for a token: "MINIMA", else its name from a matching coin, else a truncated id. */
    private String tokenLabel(String tokenid, JSONArray inputs, JSONArray outputs) {
        if (Util.isMinima(tokenid)) return "MINIMA";
        // Pull the token's name from a matching coin if present.
        for (JSONArray arr : new JSONArray[]{outputs, inputs}) {
            if (arr == null) continue;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && tokenid.equals(o.optString("tokenid", ""))) {
                    String n = Util.tokenName(o.opt("token"), tokenid);
                    if (n != null && !n.isEmpty()) return n;
                }
            }
        }
        return truncMid(tokenid, 6, 4);
    }

    /** Accumulate amount per tokenid into the map. */
    private void add(Map<String, BigDecimal> m, String tok, BigDecimal amt) {
        m.put(tok, m.containsKey(tok) ? m.get(tok).add(amt) : amt);
    }

    /** Map lookup defaulting to zero. */
    private BigDecimal bd(Map<String, BigDecimal> m, String tok) {
        return m.containsKey(tok) ? m.get(tok) : BigDecimal.ZERO;
    }

    /** Choose the token to headline a multi-token row: Minima if present, else the largest amount. */
    private String pickToken(Map<String, BigDecimal> m) {
        if (m.containsKey("0x00")) return "0x00";
        String best = "0x00"; BigDecimal max = BigDecimal.valueOf(-1);
        for (Map.Entry<String, BigDecimal> en : m.entrySet()) if (en.getValue().compareTo(max) > 0) { max = en.getValue(); best = en.getKey(); }
        return best;
    }

    /** True if any token amount is > 0 (distinguishes an "out" from a "self" move). */
    private boolean anyPositive(Map<String, BigDecimal> m) {
        for (BigDecimal v : m.values()) if (v.signum() > 0) return true;
        return false;
    }

    /** Parse to BigDecimal, zero on failure. */
    private BigDecimal dec(String s) { try { return new BigDecimal(s); } catch (Exception e) { return BigDecimal.ZERO; } }

    /** Middle-ellipsize a long string, keeping h head + t tail chars. */
    private String truncMid(String s, int h, int t) {
        s = s == null ? "" : s;
        if (s.length() <= h + t + 1) return s;
        return s.substring(0, h) + "…" + s.substring(s.length() - t);
    }

    /** Format an amount for display: cap at 8 dp (append "…" if truncated) and group thousands. */
    private String compactAmount(String raw) {
        try {
            BigDecimal b = new BigDecimal(raw);
            boolean trunc = Util.decimalPlaces(b) > 8;
            BigDecimal t = trunc ? b.setScale(8, RoundingMode.DOWN) : b;
            String s = t.stripTrailingZeros().toPlainString();
            String[] parts = s.split("\\.");
            String w = parts[0].replaceAll("\\B(?=(\\d{3})+(?!\\d))", ",");
            String out = parts.length > 1 ? w + "." + parts[1] : w;
            return trunc ? out + "…" : out;
        } catch (Exception e) {
            return raw == null ? "" : raw;
        }
    }

    /** Parse a stored JSON array string, null on empty/invalid. */
    private JSONArray parseArr(String json) {
        if (json == null || json.isEmpty()) return null;
        try { return new JSONArray(json); } catch (Exception e) { return null; }
    }

    /** True if the amount string parses to a positive number. */
    private boolean isPositive(String amt) {
        try { return new BigDecimal(amt).signum() > 0; } catch (Exception e) { return false; }
    }

    /** Copy text to the clipboard and toast the label. */
    private void copy(String text, String label) {
        if (text == null || text.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(act, label, Toast.LENGTH_SHORT).show();
    }

    /** Short toast helper. */
    private void toast(String msg) { Toast.makeText(act, msg, Toast.LENGTH_SHORT).show(); }

    /** Convert dp to device pixels. */
    private int dp(int v) { return (int) (v * act.getResources().getDisplayMetrics().density); }
}
