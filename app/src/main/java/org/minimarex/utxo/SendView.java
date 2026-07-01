package org.minimarex.utxo;

import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Send tab — full UTXO transaction construction, mirroring the dapp's Send + Confirm flow:
 * FROM (the selected input coins), recipient, amount (+ max), an editable change address (+ next,
 * pre-filled by rotating the node's default addresses), and an optional burn (Minima only). Preview
 * opens a Confirm breakdown (Inputs / Outputs / Change / Burn) before signing & posting.
 */
public class SendView extends BaseView {

    private final LinearLayout fromList;
    private final EditText addrInput, amountInput, changeInput, burnInput;
    private final TextView addrNote, amountNote, changeNote, burnNote, statusView;
    private final Button previewBtn;

    private boolean changeTouched = false;     // user edited the change field
    private boolean settingChange = false;     // guard for programmatic change-field writes
    private boolean changeFetching = false;

    /** Binds the form fields, wires Max/Next/Preview, and tracks manual edits to the change field. */
    public SendView(MainActivity a) {
        super(a, R.layout.view_send);
        fromList = find(R.id.sendFromList);
        addrInput = find(R.id.sendAddress);
        amountInput = find(R.id.sendAmount);
        changeInput = find(R.id.sendChange);
        burnInput = find(R.id.sendBurn);
        addrNote = find(R.id.sendAddrNote);
        amountNote = find(R.id.sendAmountNote);
        changeNote = find(R.id.sendChangeNote);
        burnNote = find(R.id.sendBurnNote);
        statusView = find(R.id.sendStatus);
        previewBtn = find(R.id.previewBtn);

        ((TextView) find(R.id.maxBtn)).setOnClickListener(v -> onMax());
        ((TextView) find(R.id.nextBtn)).setOnClickListener(v -> fetchChange(true));
        previewBtn.setOnClickListener(v -> onPreview());

        // Mark the change field "touched" once the user types, so we stop auto-prefilling it.
        changeInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { if (!settingChange) changeTouched = true; }
        });
        applyDesign();
        refresh();
    }

    /** Paints the view and all input fields with the active theme. */
    private void applyDesign() {
        root.setBackgroundColor(Design.bg());
        fromList.setBackgroundColor(Design.surface());
        for (EditText e : new EditText[]{addrInput, amountInput, changeInput, burnInput}) {
            e.setBackgroundColor(Design.surface2());
            e.setTextColor(Design.text());
        }
        previewBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Design.accent()));
        previewBtn.setTextColor(Design.onAccent());
    }

    /** Rebuilds the FROM (selected-input) list and total, gates burn to Minima, and prefills change. */
    @Override
    public void refresh() {
        List<Coin> sel = act.selectedCoins();
        fromList.removeAllViews();

        if (sel.isEmpty()) {
            TextView t = new TextView(act);
            t.setText("Select coins in the Wallet tab, then come here to send.");
            t.setTextColor(Design.dim());
            t.setTextSize(13f);
            fromList.addView(t);
            previewBtn.setEnabled(false);
            return;
        }
        previewBtn.setEnabled(true);

        String tokenName = sel.get(0).tokenName;
        boolean minima = Util.isMinima(act.selectedTokenid());

        // FROM list: one row per selected input coin, accumulating the spendable total as we go.
        BigDecimal total = BigDecimal.ZERO;
        for (Coin c : sel) {
            try { total = total.add(new BigDecimal(c.amount)); } catch (Exception ignored) {}
            fromList.addView(row("• " + Util.shorten(c.coinid), Util.tidyAmount(c.amount), false));
        }
        View totalRow = row("Total in", Util.tidyAmount(total.toPlainString()) + " " + tokenName, true);
        fromList.addView(totalRow);

        // Burn is paid in Minima, so it's only offered when sending the Minima token.
        burnInput.setEnabled(minima);
        if (!minima) {
            burnInput.setText("");
            setNote(burnNote, "Burn is only for Minima sends.", R.color.ux_subtext);
        } else {
            burnNote.setVisibility(View.GONE);
        }

        prefillChangeIfNeeded();
    }

    /** Snapshot the typed fields (recipient, amount, change, burn) — used to survive a theme recreate(). */
    public String[] fieldValues() {
        return new String[]{ addrInput.getText().toString(), amountInput.getText().toString(),
                changeInput.getText().toString(), burnInput.getText().toString() };
    }

    /** Restore a fieldValues() snapshot after recreate(). */
    public void setFieldValues(String[] v) {
        if (v == null || v.length < 4) return;
        addrInput.setText(v[0]); amountInput.setText(v[1]);
        changeInput.setText(v[2]); burnInput.setText(v[3]);
    }

    // ----- field helpers -----

    /** Fills the amount with the full spendable total; for Minima sends the burn is reserved first. */
    private void onMax() {
        List<Coin> sel = act.selectedCoins();
        if (sel.isEmpty()) return;
        BigDecimal total = sum(sel);
        BigDecimal burn = parseBurn();
        // Minima: max = total − burn (burn is spent from the same coins); other tokens: whole total.
        BigDecimal max = Util.isMinima(act.selectedTokenid()) ? total.subtract(burn) : total;
        if (max.signum() < 0) max = BigDecimal.ZERO;
        amountInput.setText(Util.tidyAmount(max.toPlainString()));
    }

    /** Auto-fills the change address once, unless the user has edited it or a fetch is in flight. */
    private void prefillChangeIfNeeded() {
        if (changeTouched || changeFetching) return;
        if (!changeInput.getText().toString().trim().isEmpty()) return;
        fetchChange(false);
    }

    /**
     * Rotate: fetch the node's next default address into the change field.
     * userInitiated true = the NEXT button (a deliberate rotation, so it counts as "touched");
     * false = the silent prefill (stays a default, so prefill can still re-run later).
     */
    private void fetchChange(boolean userInitiated) {
        if (act.selectedCoins().isEmpty()) return;
        changeFetching = true;
        act.node().cmd("getaddress", new NodeApi.Cb() {
            @Override public void onResult(JSONObject json) {
                changeFetching = false;
                JSONObject r = json.optJSONObject("response");
                String addr = r == null ? "" : r.optString("miniaddress", r.optString("address", ""));
                if (!addr.isEmpty()) {
                    settingChange = true;
                    changeInput.setText(addr);
                    settingChange = false;
                    if (!userInitiated) changeTouched = false;   // still a default
                }
            }
            @Override public void onError(String message) { changeFetching = false; }
        });
    }

    // ----- preview / validate -----

    /** Validates every field, computes change, then asks the node to confirm the recipient before Confirm. */
    private void onPreview() {
        final List<Coin> sel = act.selectedCoins();
        if (sel.isEmpty()) { status("Select coins in the Wallet tab first.", false); return; }

        final String recipient = addrInput.getText().toString().trim();
        if (recipient.isEmpty()) { setNote(addrNote, "Enter a recipient address.", R.color.ux_error); return; }
        if (!Util.isValidAddress(recipient)) { setNote(addrNote, "Invalid address format.", R.color.ux_error); return; }
        addrNote.setVisibility(View.GONE);

        final String tokenid = act.selectedTokenid();
        final boolean minima = Util.isMinima(tokenid);
        final String tokenName = sel.get(0).tokenName;
        final BigDecimal total = sum(sel);
        final BigDecimal burn = minima ? parseBurn() : BigDecimal.ZERO;
        if (burn.signum() < 0) { setNote(burnNote, "Invalid burn amount.", R.color.ux_error); return; }
        burnNote.setVisibility(minima ? View.GONE : burnNote.getVisibility());

        BigDecimal maxSpendable = minima ? total.subtract(burn) : total;
        if (maxSpendable.signum() <= 0) { setNote(amountNote, "Burn exceeds total — nothing left to send.", R.color.ux_error); return; }

        final BigDecimal amount;
        try { amount = new BigDecimal(amountInput.getText().toString().trim()); }
        catch (Exception e) { setNote(amountNote, "Enter a valid amount.", R.color.ux_error); return; }
        if (amount.signum() <= 0) { setNote(amountNote, "Amount must be greater than zero.", R.color.ux_error); return; }
        if (amount.compareTo(maxSpendable) > 0) { setNote(amountNote, "Max sendable is " + Util.tidyAmount(maxSpendable.toPlainString()) + ".", R.color.ux_error); return; }
        int dec = act.tokenDecimals(tokenid);
        if (dec >= 0 && Util.decimalPlaces(amount) > dec) { setNote(amountNote, "This token supports at most " + dec + " decimals.", R.color.ux_error); return; }
        amountNote.setVisibility(View.GONE);

        // Change = inputs − amount − burn; only require/validate a change address when there's any left.
        final BigDecimal change = total.subtract(amount).subtract(burn);
        final boolean hasChange = change.signum() > 0;
        final String changeStr = hasChange ? change.stripTrailingZeros().toPlainString() : null;
        final String changeAddr = changeInput.getText().toString().trim();
        if (hasChange) {
            if (changeAddr.isEmpty()) { setNote(changeNote, "Change address required.", R.color.ux_error); return; }
            if (!Util.isValidAddress(changeAddr)) { setNote(changeNote, "Invalid change address.", R.color.ux_error); return; }
        }
        changeNote.setVisibility(View.GONE);

        final String burnStr = burn.signum() > 0 ? burn.stripTrailingZeros().toPlainString() : null;

        // Confirm the recipient with the node before showing the breakdown.
        previewBtn.setEnabled(false);
        status("Checking address…", true);
        act.node().cmd("checkaddress address:" + recipient, new NodeApi.Cb() {
            @Override public void onResult(JSONObject json) {
                previewBtn.setEnabled(true);
                if (!json.optBoolean("status", false)) {
                    setNote(addrNote, "That isn't a valid Minima address.", R.color.ux_error);
                    statusView.setVisibility(View.GONE);
                    return;
                }
                statusView.setVisibility(View.GONE);
                showConfirm(sel, recipient, amount.stripTrailingZeros().toPlainString(),
                        hasChange ? changeAddr : null, changeStr, burnStr, tokenid, tokenName, total);
            }
            @Override public void onError(String message) {
                previewBtn.setEnabled(true);
                setNote(addrNote, NodeApi.ERR_NOT_ENABLED.equals(message)
                        ? "Enable this wallet in Minima Core → Apps first." : "Could not validate address.", R.color.ux_error);
                statusView.setVisibility(View.GONE);
            }
        });
    }

    // ----- confirm breakdown -----

    /** The Confirm step: a read-only Inputs / Outputs / Change / Burn breakdown before signing. */
    private void showConfirm(final List<Coin> sel, final String recipient, final String amountStr,
                             final String changeAddr, final String changeStr, final String burnStr,
                             final String tokenid, final String tokenName, BigDecimal total) {
        LinearLayout body = new LinearLayout(act);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundColor(Design.bg());   // match the current theme (dialog frame is fixed; content must adapt)
        int pad = dp(18);
        body.setPadding(pad, dp(8), pad, 0);

        body.addView(sectionLabel("INPUTS"));
        for (Coin c : sel) body.addView(row("• " + Util.shorten(c.coinid), Util.tidyAmount(c.amount), false));
        body.addView(row("Total in", Util.tidyAmount(total.toPlainString()) + " " + tokenName, true));

        body.addView(sectionLabel("OUTPUTS"));
        body.addView(row("→ " + Util.shorten(recipient) + (isMine(recipient) ? "  (yours)" : ""),
                Util.tidyAmount(amountStr) + " " + tokenName, false));
        if (changeStr != null) {
            // Flag a change address we don't recognise as ours with "(verify)" — guards against typos
            // in a hand-edited change field sending the remainder to a stranger.
            body.addView(row("↩ " + Util.shorten(changeAddr) + (isMine(changeAddr) ? "  (yours)" : "  (verify)"),
                    Util.tidyAmount(changeStr) + " " + tokenName, false));
        }
        if (burnStr != null) {
            body.addView(row("🔥 burn", Util.tidyAmount(burnStr) + " MINIMA", false));
        }

        TextView warn = new TextView(act);
        warn.setText("Once signed and posted, this cannot be undone. Verify the recipient address before continuing.");
        warn.setTextColor(Design.red());
        warn.setTextSize(12f);
        warn.setPadding(0, dp(14), 0, 0);
        body.addView(warn);

        ScrollView sv = new ScrollView(act);
        sv.addView(body);

        new AlertDialog.Builder(act)
                .setTitle("Confirm transaction")
                .setView(sv)
                .setNegativeButton("Back", null)
                .setPositiveButton("Sign & Post →", (d, w) ->
                        buildSend(sel, recipient, amountStr, changeAddr, changeStr, burnStr, tokenid, tokenName))
                .show();
    }

    /** Records a History row, builds/signs/posts the txn, then updates that row by outcome. */
    private void buildSend(List<Coin> sel, final String recipient, final String amountStr,
                           String changeAddr, String changeStr, String burnStr,
                           final String tokenid, final String tokenName) {
        previewBtn.setEnabled(false);
        status("Recording audit row…", true);

        // Outputs = recipient, plus the change output when there's any change to return.
        List<TxnBuilder.Out> outs = new ArrayList<>();
        outs.add(new TxnBuilder.Out(recipient, amountStr));
        if (changeStr != null && changeAddr != null && !changeAddr.isEmpty()) {
            outs.add(new TxnBuilder.Out(changeAddr, changeStr));
        }
        // Persist the inputs/outputs to History up-front (status PENDING) so the send is auditable
        // even if signing/posting fails; internalid links this row to the outcome callbacks below.
        final String internalid = TxnUtil.recordPosting(act, recipient, amountStr, tokenid, tokenName,
                sel, outs, (changeStr != null ? changeAddr : null), burnStr);

        new TxnBuilder(act, sel, outs, tokenid, burnStr, new TxnBuilder.Done() {
            @Override public void onPosted(String txpowid, List<TxnBuilder.OutCoin> outputs) {
                // Do NOT store the txnsign-response txpowid — it is not the on-chain id. The resolver
                // fills the real txpowid (and marks confirmed) once the transaction is mined.
                act.history().update(internalid, HistoryDb.STATUS_POSTED, null, "");
                status("✓ Posted — " + Util.tidyAmount(amountStr) + " " + tokenName
                        + " sent. The Explorer link appears in History once it confirms on-chain.", true);
                addrInput.setText("");
                amountInput.setText("");
                burnInput.setText("");
                settingChange = true; changeInput.setText(""); settingChange = false;
                changeTouched = false;
                previewBtn.setEnabled(true);
                act.clearSelection();
                act.reload();
            }
            @Override public void onFailed(String message) {
                if (NodeApi.ERR_NOT_ENABLED.equals(message)) message = "Enable this wallet in Minima Core → Apps first.";
                act.history().update(internalid, HistoryDb.STATUS_ERROR, null, message);
                status("Failed: " + message, false);
                previewBtn.setEnabled(true);
            }
        }).onProgress(label -> status(label, true)).run();
    }

    // ----- small helpers -----

    /** Builds a monospaced left-label / right-value row; bold marks a total/heading. */
    private View row(String left, String right, boolean bold) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, dp(4), 0, dp(4));

        TextView l = new TextView(act);
        l.setText(left);
        l.setTextColor(bold ? Design.text() : Design.dim());
        l.setTextSize(12f);
        l.setTypeface(android.graphics.Typeface.MONOSPACE, bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        l.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(l);

        TextView rt = new TextView(act);
        rt.setText(right);
        rt.setTextColor(Design.text());
        rt.setTextSize(12f);
        rt.setTypeface(android.graphics.Typeface.MONOSPACE, bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        r.addView(rt);
        return r;
    }

    /** Builds a small dim, letter-spaced section heading (INPUTS / OUTPUTS) for the Confirm dialog. */
    private TextView sectionLabel(String text) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextColor(Design.dim());
        t.setTextSize(11f);
        t.setLetterSpacing(0.12f);
        t.setPadding(0, dp(14), 0, dp(4));
        return t;
    }

    /** True if the address (hex or miniaddress form) belongs to this wallet. */
    private boolean isMine(String addr) {
        for (String[] a : act.myAddresses()) if (addr.equals(a[0]) || addr.equals(a[1])) return true;
        return false;
    }

    /** Sums coin amounts, skipping any that fail to parse. */
    private BigDecimal sum(List<Coin> coins) {
        BigDecimal t = BigDecimal.ZERO;
        for (Coin c : coins) { try { t = t.add(new BigDecimal(c.amount)); } catch (Exception ignored) {} }
        return t;
    }

    /** Parses the burn field: empty = 0; unparseable = -1 (a sentinel callers reject as invalid). */
    private BigDecimal parseBurn() {
        String s = burnInput.getText().toString().trim();
        if (s.isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(s); } catch (Exception e) { return new BigDecimal("-1"); }
    }

    /** Shows an inline validation note under a field in the given color. */
    private void setNote(TextView note, String msg, int colorRes) {
        note.setVisibility(View.VISIBLE);
        note.setText(msg);
        note.setTextColor(act.getColor(colorRes));
    }

    /** Shows the bottom status line; green on ok, red on failure. */
    private void status(String msg, boolean ok) {
        statusView.setVisibility(View.VISIBLE);
        statusView.setText(msg);
        statusView.setTextColor(ok ? Design.success() : Design.red());
    }

    /** Converts density-independent pixels to raw pixels for this device. */
    private int dp(int v) { return (int) (v * act.getResources().getDisplayMetrics().density); }
}
