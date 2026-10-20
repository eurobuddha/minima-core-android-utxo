package com.eurobuddha.utxo;

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

    /** Callback for surfacing a one-line status message to the user (ok = success vs. error styling). */
    public interface Status {
        void show(String message, boolean ok);
    }

    private static final int SPLIT_SCALE = 11;

    private final MainActivity act;
    private final Status status;
    private long statusGeneration;

    public WalletTools(MainActivity act, Status status) {
        this.act = act;
        this.status = status;
    }

    /** Reuse SendView's literal selection comparison across confirmation and async lookup. */
    private final class Review {
        final List<Coin> coins = new ArrayList<>();
        final String[] snapshot;
        final String tokenid;
        private boolean accepted;
        private long statusOwner = -1;
        private void claimStatus() { if (statusOwner < 0) statusOwner = ++statusGeneration; }
        final Status status = (message, ok) -> {
            claimStatus();
            if (statusOwner == statusGeneration && !act.isFinishing() && !act.isDestroyed())
                WalletTools.this.status.show(message, ok);
        };
        Review() {
            List<Coin> selected = act.selectedCoins();
            snapshot = TxnUtil.selectionSnapshot(selected);
            for (Coin source : selected) {
                if (source == null) { coins.add(null); continue; }
                // Freeze the command and display fields; these tools do not replay NFT state.
                Coin c = new Coin();
                c.coinid=source.coinid;c.tokenid=source.tokenid;c.amount=source.amount;c.rawAmount=source.rawAmount;
                c.address=source.address;c.miniaddress=source.miniaddress;
                c.confirmed=source.confirmed;c.sendable=source.sendable;c.spent=source.spent;
                c.tokenName=source.tokenName;
                coins.add(c);
            }
            tokenid=coins.isEmpty()?Util.MINIMA_TOKENID:coins.get(0)==null?null:coins.get(0).tokenid;
        }
        boolean accept(boolean spend) {
            if (accepted) return false;
            accepted=true;
            claimStatus();
            return current(spend);
        }
        boolean current(boolean spend) {
            if (act.isFinishing() || act.isDestroyed()) return false;
            boolean valid=Util.isValidHexId(tokenid);
            java.util.Set<String> ids=new java.util.HashSet<>();
            for (Coin c:coins) {
                if (c==null || !Util.isValidHexId(c.coinid) || !TransactionFields.same(tokenid,c.tokenid)
                        || !ids.add(c.coinid.toLowerCase(java.util.Locale.ROOT))
                        || (spend && (!c.isWellFormed() || !c.confirmed || !c.sendable || c.spent))) {valid=false;break;}
            }
            if (valid && java.util.Arrays.equals(snapshot,TxnUtil.selectionSnapshot(act.selectedCoins()))) return true;
            status.show("Selected coins changed or are unavailable. Review the selection and try again.",false);
            return false;
        }
    }

    // ---- shared helpers ----

    /** Views placed inside a dialog must be built on the DIALOG's themed context, not the activity's
     *  DayNight one — otherwise an EditText on a Light dialog inherits the phone's dark-theme text colour
     *  (light grey on white; seen on the S23). */
    private android.content.Context dialogCtx() {
        return new android.view.ContextThemeWrapper(act, Design.dialogTheme());
    }

    /** Build a numeric EditText (decimal toggles the decimal-point flag) for the dialog forms. */
    private EditText input(String hint, String preset, boolean decimal) {
        EditText e = new EditText(dialogCtx());
        e.setTextColor(Design.text());
        e.setHintTextColor(Design.dim());
        e.setHint(hint);
        e.setText(preset);
        e.setInputType(decimal
                ? (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL)
                : InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    /** Wrap dialog children in a padded vertical layout. */
    private LinearLayout box(View... children) {
        LinearLayout l = new LinearLayout(dialogCtx());
        l.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * act.getResources().getDisplayMetrics().density);
        l.setPadding(pad, pad / 2, pad, 0);
        for (View c : children) l.addView(c);
        return l;
    }

    /** Total the amounts of a coin list (bad amounts are skipped). */
    private BigDecimal sum(List<Coin> coins) {
        BigDecimal t = BigDecimal.ZERO;
        for (Coin c : coins) {
            try { t = t.add(new BigDecimal(c.amount)); } catch (Exception ignored) {}
        }
        return t;
    }

    /** Resolve a human token name from balances/coins, falling back to "Minima"/"Token". */
    private String tokenNameFor(String tokenid) {
        if (Util.isMinima(tokenid)) return "Minima";
        for (TokenBalance b : act.balances()) if (b.tokenid.equals(tokenid)) return b.name;
        for (Coin c : act.coins()) if (c.tokenid.equals(tokenid)) return c.tokenName;
        return "Token";
    }

    /** Post a custom transaction via {@link TxnBuilder} and reflect the outcome on the history row. */
    private void runBuilder(List<Coin> inputs, List<TxnBuilder.Out> outs, String tokenid,
                            String internalid, String okLabel, final Status status) {
        final String[] submittedCoins=TxnUtil.selectionSnapshot(inputs);
        status.show("Posting…", true);
        new TxnBuilder(act, inputs, outs, tokenid, new TxnBuilder.Done() {
            @Override public void onPosted(String txpowid, List<TxnBuilder.OutCoin> outputs) {
                // Don't store the txnsign-response txpowid (not the on-chain id) — the resolver fills
                // the real txpowid via the stored input coinids and marks it confirmed.
                act.history().update(internalid, HistoryDb.STATUS_POSTED, null, "");
                status.show(okLabel + " — sent!", true);
                if(java.util.Arrays.equals(submittedCoins,TxnUtil.selectionSnapshot(act.selectedCoins()))) act.clearSelection();
                act.reload();
            }
            @Override public void onFailed(String message) {
                if (NodeApi.ERR_NOT_ENABLED.equals(message)) message = "Enable this wallet in Minima Core → Apps first.";
                act.history().update(internalid, HistoryDb.STATUS_ERROR, null, message);
                status.show("Failed: " + message, false);
            }
            @Override public void onUnknown(String message) {
                act.history().update(internalid, HistoryDb.STATUS_UNKNOWN, null, message);
                status.show(message, false);
                act.reload();
            }
        }).record(internalid).run();
    }

    // ---- Split ----

    /** Prompt for a coin count, then split the selected coins into that many equal coins. */
    public void showSplit() {
        final Review review = new Review();
        final Status status = review.status;
        final List<Coin> sel = review.coins;
        if (sel.isEmpty()) { status.show("Select coins in the list first, then Split.", false); return; }
        final EditText count = input("Number of coins (2–15)", "4", false);
        Design.dialog(act)
                .setTitle("Split selected coins")
                .setView(box(count))
                .setPositiveButton("Split", (d, w) -> doSplit(review, count.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Validate the count, compute equal slices, fetch one address per coin, then post the split. */
    private void doSplit(Review review, String cStr) {
        final Status status = review.status;
        if (!review.accept(true)) return;
        final List<Coin> sel = review.coins;
        int n;
        try { n = Integer.parseInt(cStr.trim()); } catch (Exception e) { status.show("Enter a valid number.", false); return; }
        if (n < 2 || n > 15) { status.show("Choose between 2 and 15 coins.", false); return; }

        final String tokenid = review.tokenid;
        int dec = act.tokenDecimals(tokenid);
        int scale = dec >= 0 ? dec : SPLIT_SCALE;
        BigDecimal total = sum(sel);
        BigDecimal each = total.divide(new BigDecimal(n), scale, RoundingMode.DOWN);
        if (each.signum() <= 0) { status.show("Amount too small to split into " + n + ".", false); return; }
        // Rounding goes DOWN, so the last coin absorbs the remainder to keep inputs == outputs (no burn).
        final BigDecimal last = total.subtract(each.multiply(new BigDecimal(n - 1)));
        final BigDecimal eachF = each;
        final int fn = n;
        final String tokenName = sel.get(0).tokenName;

        status.show("Fetching addresses…", true);
        TxnUtil.fetchAddresses(act, n, new TxnUtil.AddrList() {
            @Override public void onAddresses(List<String> addrs) {
                if (!review.current(true)) return;
                List<TxnBuilder.Out> outs = new ArrayList<>();
                for (int i = 0; i < fn; i++) {
                    BigDecimal amt = (i == fn - 1) ? last : eachF;
                    outs.add(new TxnBuilder.Out(addrs.get(i), amt.stripTrailingZeros().toPlainString()));
                }
                String internalid = TxnUtil.recordPosting(act, "Split → " + fn + " coins",
                        Util.tidyAmount(sum(sel).toPlainString()), tokenid, tokenName, sel, outs, null, null);
                runBuilder(sel, outs, tokenid, internalid, "Split into " + fn + " coins", status);
            }
            @Override public void onError(String message) { status.show("Failed: " + message, false); }
        });
    }

    // ---- Consolidate ----
    // No selection: run the node's auto-consolidate on Minima (0x00).
    // 2+ coins selected: merge exactly those coins into one.

    /** Entry point: no selection ⇒ node auto-consolidate Minima; 2+ selected ⇒ merge exactly those. */
    public void showConsolidate() {
        final Review review = new Review();
        final Status status = review.status;
        final List<Coin> sel = review.coins;
        if (sel.isEmpty()) {
            Design.dialog(act)
                    .setTitle("Consolidate Minima")
                    .setMessage("Merge your Minima coins together automatically (consolidate tokenid:0x00)?")
                    .setPositiveButton("Consolidate", (d, w) -> { if(review.accept(false)) autoConsolidate(Util.MINIMA_TOKENID, status); })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        if (sel.size() < 2) {
            status.show("Select 2+ coins to merge them, or clear the selection to auto-consolidate Minima.", false);
            return;
        }
        final String tokenName = sel.get(0).tokenName;
        Design.dialog(act)
                .setTitle("Consolidate selected")
                .setMessage("Merge the " + sel.size() + " selected " + tokenName + " coins into one?")
                .setPositiveButton("Merge", (d, w) -> mergeSelected(review))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Run the node's native "consolidate" command (it picks its own inputs). */
    private void autoConsolidate(String tokenid, final Status status) {
        if (!TxnUtil.canUseNativeFunds(act,tokenid,null)) { status.show(TransactionEvidence.RECORD_FAILED,false);return; }
        final String internalid = TxnUtil.recordPosting(act, "Consolidate " + tokenNameFor(tokenid),
                "—", tokenid, tokenNameFor(tokenid));
        if (internalid == null) { status.show(TransactionEvidence.SAVE_FAILED,false);return; }
        status.show("Consolidating…", true);
        new PreparedConsolidation(act,tokenid,internalid,new TxnBuilder.Done() {
            @Override public void onPosted(String txpowid,List<TxnBuilder.OutCoin> outputs) {
                act.history().update(internalid,HistoryDb.STATUS_POSTED,null,"");
                status.show("Consolidate submitted.",true);act.reload();
            }
            @Override public void onFailed(String message) {
                if(NodeApi.ERR_NOT_ENABLED.equals(message))message="Enable this wallet in Minima Core → Apps first.";
                act.history().update(internalid,HistoryDb.STATUS_ERROR,null,message);
                status.show("Failed: " + message,false);
            }
            @Override public void onUnknown(String message) {
                act.history().update(internalid,HistoryDb.STATUS_UNKNOWN,null,message);
                status.show(message,false);act.reload();
            }
        }).run();
    }

    /** Merge exactly the selected coins into a single coin at a fresh address. */
    private void mergeSelected(Review review) {
        final Status status = review.status;
        if (!review.accept(true)) return;
        final List<Coin> sel = review.coins;
        final String tokenid = review.tokenid;
        final String tokenName = sel.get(0).tokenName;
        final BigDecimal total = sum(sel);
        status.show("Fetching address…", true);
        act.node().cmd("getaddress", new NodeApi.Cb() {
            @Override public void onResult(JSONObject json) {
                if (!review.current(true)) return;
                JSONObject r = json.optJSONObject("response");
                String addr = r == null ? "" : r.optString("miniaddress", r.optString("address", ""));
                if (addr.isEmpty()) { status.show("Could not get an address.", false); return; }
                List<TxnBuilder.Out> outs = new ArrayList<>();
                outs.add(new TxnBuilder.Out(addr, total.stripTrailingZeros().toPlainString()));
                String internalid = TxnUtil.recordPosting(act, "Consolidate → 1 coin",
                        Util.tidyAmount(total.toPlainString()), tokenid, tokenName, sel, outs, null, null);
                runBuilder(sel, outs, tokenid, internalid, "Merged " + sel.size() + " coins", status);
            }
            @Override public void onError(String message) { status.show("Failed: " + message, false); }
        });
    }

    // ---- Distribute ----

    /** Prompt for address count + per-address amount, then kick off the multi-batch DistributeManager. */
    public void showDistribute() {
        final Review review = new Review();
        final Status status = review.status;
        final List<Coin> sel = review.coins;
        if (sel.isEmpty()) { status.show("Select coins in the list first, then Distribute.", false); return; }
        if (act.distribute() != null && act.distribute().isBusy()) {
            status.show("A distribute job is already running.", false); return;
        }
        final EditText count = input("Number of addresses (2–56)", "20", false);
        final EditText amount = input("Amount to each", "", true);
        Design.dialog(act)
                .setTitle("Distribute to my addresses")
                .setView(box(count, amount))
                .setPositiveButton("Distribute", (d, w) ->
                        doDistribute(review, count.getText().toString(), amount.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Validate count/amount/decimals/funds, reserve the job slot, fetch addresses, then start. */
    private void doDistribute(Review review, String cStr, String aStr) {
        final Status status = review.status;
        if (!review.accept(true)) return;
        final List<Coin> sel = review.coins;
        int n;
        try { n = Integer.parseInt(cStr.trim()); } catch (Exception e) { status.show("Enter a valid number.", false); return; }
        if (n < 2 || n > 56) { status.show("Choose 2–56 addresses (auto-chained in up to 4 transactions).", false); return; }

        final BigDecimal per;
        try { per = new BigDecimal(aStr.trim()); } catch (Exception e) { status.show("Enter a valid amount.", false); return; }
        if (per.signum() <= 0) { status.show("Amount must be greater than zero.", false); return; }

        final String tokenid = review.tokenid;
        int dec = act.tokenDecimals(tokenid);
        if (dec >= 0 && Util.decimalPlaces(per) > dec) {
            status.show("This token supports at most " + dec + " decimal place" + (dec == 1 ? "" : "s") + ".", false);
            return;
        }

        BigDecimal total = sum(sel);
        BigDecimal required = per.multiply(new BigDecimal(n));
        if (required.compareTo(total) > 0) {
            status.show("Need " + Util.tidyAmount(required.toPlainString()) + " but only " + Util.tidyAmount(total.toPlainString()) + " selected.", false);
            return;
        }
        final String perStr = per.stripTrailingZeros().toPlainString();
        final String tokenName = sel.get(0).tokenName;

        // Reserve before the async address fetch so a second tap can't start a parallel job.
        if (!act.distribute().reserve()) { status.show("A distribute job is already running.", false); return; }
        status.show("Fetching " + n + " addresses…", true);
        TxnUtil.fetchAddresses(act, n, new TxnUtil.AddrList() {
            @Override public void onAddresses(List<String> addrs) {
                if (!review.current(true)) { act.distribute().cancelReserve(); return; }
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

    /** Confirm, then stop tracking the selected coins (they stay on-chain, just hidden locally). */
    public void untrackSelected() {
        final Review review = new Review();
        final Status status = review.status;
        final List<Coin> sel = review.coins;
        if (sel.isEmpty()) { status.show("Select coins in the list first, then Untrack.", false); return; }
        Design.dialog(act)
                .setTitle("Untrack " + sel.size() + " coin(s)?")
                .setMessage("They'll disappear from the wallet view but remain on-chain. Re-add later by coinid.")
                .setPositiveButton("Untrack", (d, w) -> { if(review.accept(false)) untrackNext(sel, 0, status); })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Untrack one coin at a time, recursing on success — the node has no batch cointrack call.
    /** Sequentially `cointrack enable:false` each selected coin, recursing through the list. */
    private void untrackNext(final List<Coin> sel, final int i, final Status status) {
        if (i >= sel.size()) {
            status.show("Untracked " + sel.size() + " coin(s).", true);
            if(java.util.Arrays.equals(TxnUtil.selectionSnapshot(sel),TxnUtil.selectionSnapshot(act.selectedCoins()))) act.clearSelection();
            act.reload();
            return;
        }
        status.show("Untracking " + (i + 1) + "/" + sel.size() + "…", true);
        // Belt-and-braces: coins are already vetted at ingestion (Coin.isWellFormed), but this id
        // goes straight into a command, so never take that on trust.
        if (!Util.isValidHexId(sel.get(i).coinid)) { untrackNext(sel, i + 1, status); return; }
        act.node().cmd("cointrack enable:false coinid:" + sel.get(i).coinid, new NodeApi.Cb() {
            @Override public void onResult(JSONObject json) { untrackNext(sel, i + 1, status); }
            @Override public void onError(String message) {
                if (NodeApi.ERR_NOT_ENABLED.equals(message)) message = "Enable this wallet in Minima Core → Apps first.";
                status.show("Untrack failed: " + message, false);
            }
        });
    }
}
