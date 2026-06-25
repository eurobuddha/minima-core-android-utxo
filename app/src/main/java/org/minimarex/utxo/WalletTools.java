package org.minimarex.utxo;

import android.app.AlertDialog;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The UTXO operations behind the Wallet tab's "Tools" dropdown: Split, Consolidate, Distribute,
 * Untrack. Split/merge build a custom transaction via {@link TxnBuilder}; Distribute hands off to
 * {@link DistributeManager}; the auto-consolidate uses the node's native "consolidate" command.
 */
public class WalletTools {

    public interface Status {
        void show(String message, boolean ok);
    }

    private static final int SPLIT_SCALE = 11;

    private final MainActivity act;
    private final Status status;

    public WalletTools(MainActivity act, Status status) {
        this.act = act;
        this.status = status;
    }

    // ---- shared helpers ----

    private EditText input(String hint, String preset, boolean decimal) {
        EditText e = new EditText(act);
        e.setHint(hint);
        e.setText(preset);
        e.setInputType(decimal
                ? (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL)
                : InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    private LinearLayout box(View... children) {
        LinearLayout l = new LinearLayout(act);
        l.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * act.getResources().getDisplayMetrics().density);
        l.setPadding(pad, pad / 2, pad, 0);
        for (View c : children) l.addView(c);
        return l;
    }

    private BigDecimal sum(List<Coin> coins) {
        BigDecimal t = BigDecimal.ZERO;
        for (Coin c : coins) {
            try { t = t.add(new BigDecimal(c.amount)); } catch (Exception ignored) {}
        }
        return t;
    }

    private String tokenNameFor(String tokenid) {
        if (Util.isMinima(tokenid)) return "Minima";
        for (TokenBalance b : act.balances()) if (b.tokenid.equals(tokenid)) return b.name;
        for (Coin c : act.coins()) if (c.tokenid.equals(tokenid)) return c.tokenName;
        return "Token";
    }

    private void runBuilder(List<Coin> inputs, List<TxnBuilder.Out> outs, String tokenid,
                            String internalid, String okLabel) {
        status.show("Posting…", true);
        new TxnBuilder(act, inputs, outs, tokenid, new TxnBuilder.Done() {
            @Override public void onPosted(String txpowid, List<TxnBuilder.OutCoin> outputs) {
                // Don't store the txnsign-response txpowid (not the on-chain id) — the resolver fills
                // the real txpowid via the stored input coinids and marks it confirmed.
                act.history().update(internalid, HistoryDb.STATUS_POSTED, null, "");
                status.show(okLabel + " — sent!", true);
                act.clearSelection();
                act.reload();
            }
            @Override public void onFailed(String message) {
                if (NodeApi.ERR_NOT_ENABLED.equals(message)) message = "Enable this wallet in Minima Core → Apps first.";
                act.history().update(internalid, HistoryDb.STATUS_ERROR, null, message);
                status.show("Failed: " + message, false);
            }
        }).run();
    }

    // ---- Split ----

    public void showSplit() {
        final List<Coin> sel = act.selectedCoins();
        if (sel.isEmpty()) { status.show("Select coins in the list first, then Split.", false); return; }
        final EditText count = input("Number of coins (2–15)", "4", false);
        new AlertDialog.Builder(act)
                .setTitle("Split selected coins")
                .setView(box(count))
                .setPositiveButton("Split", (d, w) -> doSplit(sel, count.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void doSplit(List<Coin> sel, String cStr) {
        int n;
        try { n = Integer.parseInt(cStr.trim()); } catch (Exception e) { status.show("Enter a valid number.", false); return; }
        if (n < 2 || n > 15) { status.show("Choose between 2 and 15 coins.", false); return; }

        final String tokenid = act.selectedTokenid();
        int dec = act.tokenDecimals(tokenid);
        int scale = dec >= 0 ? dec : SPLIT_SCALE;
        BigDecimal total = sum(sel);
        BigDecimal each = total.divide(new BigDecimal(n), scale, RoundingMode.DOWN);
        if (each.signum() <= 0) { status.show("Amount too small to split into " + n + ".", false); return; }
        final BigDecimal last = total.subtract(each.multiply(new BigDecimal(n - 1)));
        final BigDecimal eachF = each;
        final int fn = n;
        final String tokenName = sel.get(0).tokenName;

        status.show("Fetching addresses…", true);
        TxnUtil.fetchAddresses(act, n, new TxnUtil.AddrList() {
            @Override public void onAddresses(List<String> addrs) {
                List<TxnBuilder.Out> outs = new ArrayList<>();
                for (int i = 0; i < fn; i++) {
                    BigDecimal amt = (i == fn - 1) ? last : eachF;
                    outs.add(new TxnBuilder.Out(addrs.get(i), amt.stripTrailingZeros().toPlainString()));
                }
                String internalid = TxnUtil.recordPosting(act, "Split → " + fn + " coins",
                        Util.tidyAmount(sum(sel).toPlainString()), tokenid, tokenName, sel, outs, null, null);
                runBuilder(sel, outs, tokenid, internalid, "Split into " + fn + " coins");
            }
            @Override public void onError(String message) { status.show("Failed: " + message, false); }
        });
    }

    // ---- Consolidate ----
    // No selection: run the node's auto-consolidate on Minima (0x00).
    // 2+ coins selected: merge exactly those coins into one.

    public void showConsolidate() {
        final List<Coin> sel = act.selectedCoins();
        if (sel.isEmpty()) {
            new AlertDialog.Builder(act)
                    .setTitle("Consolidate Minima")
                    .setMessage("Merge your Minima coins together automatically (consolidate tokenid:0x00)?")
                    .setPositiveButton("Consolidate", (d, w) -> autoConsolidate(Util.MINIMA_TOKENID))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        if (sel.size() < 2) {
            status.show("Select 2+ coins to merge them, or clear the selection to auto-consolidate Minima.", false);
            return;
        }
        final String tokenName = sel.get(0).tokenName;
        new AlertDialog.Builder(act)
                .setTitle("Consolidate selected")
                .setMessage("Merge the " + sel.size() + " selected " + tokenName + " coins into one?")
                .setPositiveButton("Merge", (d, w) -> mergeSelected(sel))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void autoConsolidate(String tokenid) {
        final String internalid = TxnUtil.recordPosting(act, "Consolidate " + tokenNameFor(tokenid),
                "—", tokenid, tokenNameFor(tokenid));
        status.show("Consolidating…", true);
        act.node().cmd("consolidate tokenid:" + tokenid, new NodeApi.Cb() {
            @Override public void onResult(JSONObject json) {
                boolean ok = json.optBoolean("status", true);
                // Node-driven consolidate picks its own inputs, so the txpowid can't be resolved by
                // input match — store null rather than the unreliable response id.
                act.history().update(internalid,
                        ok ? HistoryDb.STATUS_POSTED : HistoryDb.STATUS_ERROR, null, "");
                status.show(ok ? "Consolidate submitted." : "Nothing to consolidate.", ok);
                act.reload();
            }
            @Override public void onError(String message) {
                if (NodeApi.ERR_NOT_ENABLED.equals(message)) message = "Enable this wallet in Minima Core → Apps first.";
                act.history().update(internalid, HistoryDb.STATUS_ERROR, null, message);
                status.show("Failed: " + message, false);
            }
        });
    }

    /** Merge exactly the selected coins into a single coin at a fresh address. */
    private void mergeSelected(final List<Coin> sel) {
        final String tokenid = act.selectedTokenid();
        final String tokenName = sel.get(0).tokenName;
        final BigDecimal total = sum(sel);
        status.show("Fetching address…", true);
        act.node().cmd("getaddress", new NodeApi.Cb() {
            @Override public void onResult(JSONObject json) {
                JSONObject r = json.optJSONObject("response");
                String addr = r == null ? "" : r.optString("miniaddress", r.optString("address", ""));
                if (addr.isEmpty()) { status.show("Could not get an address.", false); return; }
                List<TxnBuilder.Out> outs = new ArrayList<>();
                outs.add(new TxnBuilder.Out(addr, total.stripTrailingZeros().toPlainString()));
                String internalid = TxnUtil.recordPosting(act, "Consolidate → 1 coin",
                        Util.tidyAmount(total.toPlainString()), tokenid, tokenName, sel, outs, null, null);
                runBuilder(sel, outs, tokenid, internalid, "Merged " + sel.size() + " coins");
            }
            @Override public void onError(String message) { status.show("Failed: " + message, false); }
        });
    }

    // ---- Distribute ----

    public void showDistribute() {
        final List<Coin> sel = act.selectedCoins();
        if (sel.isEmpty()) { status.show("Select coins in the list first, then Distribute.", false); return; }
        if (act.distribute() != null && act.distribute().isBusy()) {
            status.show("A distribute job is already running.", false); return;
        }
        final EditText count = input("Number of addresses (2–56)", "20", false);
        final EditText amount = input("Amount to each", "", true);
        new AlertDialog.Builder(act)
                .setTitle("Distribute to my addresses")
                .setView(box(count, amount))
                .setPositiveButton("Distribute", (d, w) ->
                        doDistribute(sel, count.getText().toString(), amount.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void doDistribute(final List<Coin> sel, String cStr, String aStr) {
        int n;
        try { n = Integer.parseInt(cStr.trim()); } catch (Exception e) { status.show("Enter a valid number.", false); return; }
        if (n < 2 || n > 56) { status.show("Choose 2–56 addresses (auto-chained in up to 4 transactions).", false); return; }

        final BigDecimal per;
        try { per = new BigDecimal(aStr.trim()); } catch (Exception e) { status.show("Enter a valid amount.", false); return; }
        if (per.signum() <= 0) { status.show("Amount must be greater than zero.", false); return; }

        final String tokenid = act.selectedTokenid();
        int dec = act.tokenDecimals(tokenid);
        if (dec >= 0 && Util.decimalPlaces(per) > dec) {
            status.show("This token supports at most " + dec + " decimal place" + (dec == 1 ? "" : "s") + ".", false);
            return;
        }

        BigDecimal total = sum(sel);
        BigDecimal required = per.multiply(new BigDecimal(n));
        if (required.compareTo(total) > 0) {
            status.show("Need " + Util.tidyAmount(required.toPlainString()) + " but only "
                    + Util.tidyAmount(total.toPlainString()) + " selected.", false);
            return;
        }
        final String perStr = per.stripTrailingZeros().toPlainString();
        final String tokenName = sel.get(0).tokenName;

        if (!act.distribute().reserve()) { status.show("A distribute job is already running.", false); return; }
        status.show("Fetching " + n + " addresses…", true);
        TxnUtil.fetchAddresses(act, n, new TxnUtil.AddrList() {
            @Override public void onAddresses(List<String> addrs) {
                act.distribute().start(sel, addrs, perStr, tokenid, tokenName);
                status.show("Distribute started.", true);
            }
            @Override public void onError(String message) {
                act.distribute().cancelReserve();
                status.show("Failed: " + message, false);
            }
        });
    }

    // ---- Untrack ----

    public void untrackSelected() {
        final List<Coin> sel = act.selectedCoins();
        if (sel.isEmpty()) { status.show("Select coins in the list first, then Untrack.", false); return; }
        new AlertDialog.Builder(act)
                .setTitle("Untrack " + sel.size() + " coin(s)?")
                .setMessage("They'll disappear from the wallet view but remain on-chain. Re-add later by coinid.")
                .setPositiveButton("Untrack", (d, w) -> untrackNext(sel, 0))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void untrackNext(final List<Coin> sel, final int i) {
        if (i >= sel.size()) {
            status.show("Untracked " + sel.size() + " coin(s).", true);
            act.clearSelection();
            act.reload();
            return;
        }
        status.show("Untracking " + (i + 1) + "/" + sel.size() + "…", true);
        act.node().cmd("cointrack enable:false coinid:" + sel.get(i).coinid, new NodeApi.Cb() {
            @Override public void onResult(JSONObject json) { untrackNext(sel, i + 1); }
            @Override public void onError(String message) {
                if (NodeApi.ERR_NOT_ENABLED.equals(message)) message = "Enable this wallet in Minima Core → Apps first.";
                status.show("Untrack failed: " + message, false);
            }
        });
    }
}
