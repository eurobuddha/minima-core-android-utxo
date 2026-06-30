package org.minimarex.utxo;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.math.BigDecimal;
import java.util.List;

/** Balances tab: per-token aggregates with icon graphics and full token/coin data. */
public class BalancesView extends BaseView {

    private final LinearLayout container;

    /** Inflates the balances container and renders the first listing. */
    public BalancesView(MainActivity a) {
        super(a, R.layout.view_balances);
        container = find(R.id.balancesContainer);
        root.setBackgroundColor(Design.bg());
        refresh();
    }

    /** Rebuilds one rich card per token from the aggregated balances. */
    @Override
    public void refresh() {
        container.removeAllViews();
        List<TokenBalance> balances = act.balances();
        if (balances.isEmpty()) {
            TextView tv = new TextView(act);
            tv.setText("No balances yet.");
            tv.setTextColor(Design.dim());
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, dp(40), 0, 0);
            container.addView(tv);
            return;
        }

        LayoutInflater inf = LayoutInflater.from(act);
        for (TokenBalance b : balances) {
            View card = inf.inflate(R.layout.view_balance_row, container, false);
            card.setBackgroundColor(Design.surface());

            // Icon: the app launcher icon for native Minima, otherwise the token's own iconUrl
            // (async, with a placeholder while loading / on failure).
            ImageView icon = card.findViewById(R.id.balIcon);
            if (Util.isMinima(b.tokenid)) {
                icon.setImageResource(R.mipmap.ic_launcher);
            } else {
                ImageLoader.load(act, b.meta.iconUrl, icon, R.drawable.ic_coin_placeholder);
            }

            TextView name = card.findViewById(R.id.balName);
            name.setText(b.name); name.setTextColor(Design.accent());

            // Ticker (optional) + an NFT badge for total-1 tokens.
            TextView ticker = card.findViewById(R.id.balTicker);
            ticker.setTextColor(b.isNft() ? Design.accent() : Design.dim());
            String tk = (b.meta.ticker != null && !b.meta.ticker.isEmpty()) ? "· " + b.meta.ticker : "";
            if (b.isNft()) tk = (tk.isEmpty() ? "" : tk + "   ") + "· NFT";
            if (!tk.isEmpty()) ticker.setText(tk); else ticker.setVisibility(View.GONE);

            // Front and centre: the SENDABLE (spendable) balance — NOT the token's total supply.
            TextView total = card.findViewById(R.id.balTotal);
            total.setText(Util.tidyAmount(b.sendable)); total.setTextColor(Design.text());

            // Under it: a "Sendable" caption, plus the contract-locked and pending parts when nonzero.
            // locked = confirmed − sendable — coins held but not signable here (contract-locked / watch-only).
            String breakdown = "Sendable";
            String locked = lockedAmount(b);
            if (positive(locked)) breakdown += "  ·  Locked " + Util.tidyAmount(locked);
            if (positive(b.unconfirmed)) breakdown += "  ·  Pending " + Util.tidyAmount(b.unconfirmed);
            TextView bd = card.findViewById(R.id.balBreakdown);
            bd.setText(breakdown); bd.setTextColor(Design.dim());

            // Small print: UTXO count, declared decimals, and the SUPPLY (demoted from headline). For Minima
            // use the LIVE circulating amount (status.minima = 1bn − burnt) as a whole number, no decimals;
            // for tokens, their fixed total supply.
            String meta = b.coins + (b.coins == 1 ? " coin" : " coins");
            if (b.meta.decimals != null && !b.meta.decimals.isEmpty()) meta += "  ·  " + b.meta.decimals + " decimals";
            String supply = (Util.isMinima(b.tokenid) && !act.circulatingSupply().isEmpty())
                    ? wholeNumber(act.circulatingSupply()) : Util.tidyAmount(b.total);
            meta += "  ·  Supply " + supply;
            TextView mt = card.findViewById(R.id.balMeta);
            mt.setText(meta); mt.setTextColor(Design.dim());

            TextView tid = card.findViewById(R.id.balTokenId);
            tid.setText(b.tokenid); tid.setTextColor(Design.dim());

            // Description is optional free-text metadata — only revealed when present.
            TextView desc = card.findViewById(R.id.balDesc);
            desc.setTextColor(Design.dim());
            if (b.meta.description != null && !b.meta.description.isEmpty()) {
                desc.setVisibility(View.VISIBLE);
                desc.setText(b.meta.description);
            }

            card.setOnClickListener(v -> showTokenDetail(b));   // tap → full detail
            container.addView(card);
        }
    }

    /** Full-detail dialog for one token: large icon (tap → full-res), all metadata, and a Receive action. */
    private void showTokenDetail(TokenBalance b) {
        android.widget.ScrollView sv = new android.widget.ScrollView(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(16));
        box.setBackgroundColor(Design.bg());
        sv.addView(box);

        ImageView big = new ImageView(act);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(128), dp(128));
        ip.gravity = Gravity.CENTER_HORIZONTAL; ip.bottomMargin = dp(6); big.setLayoutParams(ip);
        if (b.isMinima()) big.setImageResource(R.mipmap.ic_launcher);
        else ImageLoader.load(act, b.meta.iconUrl, big, R.drawable.ic_coin_placeholder);
        box.addView(big);
        if (b.hasIcon()) {
            big.setOnClickListener(v -> showImageFull(b.meta.iconUrl));
            TextView hint = new TextView(act);
            hint.setText(b.isNft() ? "Tap image for full resolution" : "Tap image to enlarge");
            hint.setTextColor(Design.dim()); hint.setTextSize(11f); hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, 0, 0, dp(8)); box.addView(hint);
        }

        TextView title = new TextView(act);
        title.setText(b.name + (b.isNft() ? "   ·  NFT" : ""));
        title.setTextColor(Design.accent()); title.setTextSize(18f);
        title.setGravity(Gravity.CENTER); title.setTypeface(Design.typeface(), android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(8)); box.addView(title);

        addKv(box, "Sendable", Util.tidyAmount(b.sendable));
        addKv(box, "Confirmed", Util.tidyAmount(b.confirmed));
        if (positive(b.unconfirmed)) addKv(box, "Pending", Util.tidyAmount(b.unconfirmed));
        String locked = lockedAmount(b);
        if (positive(locked)) addKv(box, "Locked", Util.tidyAmount(locked));
        addKv(box, "Coins", String.valueOf(b.coins));
        String supply = (b.isMinima() && !act.circulatingSupply().isEmpty())
                ? wholeNumber(act.circulatingSupply()) : Util.tidyAmount(b.total);
        addKv(box, "Supply", supply);
        if (b.meta.ticker != null && !b.meta.ticker.isEmpty()) addKv(box, "Ticker", b.meta.ticker);
        if (b.meta.decimals != null && !b.meta.decimals.isEmpty()) addKv(box, "Decimals", b.meta.decimals);
        if (notEmpty(b.meta.owner)) addKv(box, "Owner", b.meta.owner);

        box.addView(sectionLabel("Token ID"));
        TextView idv = new TextView(act);
        idv.setText(b.tokenid); idv.setTextColor(Design.text()); idv.setTextSize(12f);
        idv.setTextIsSelectable(true); idv.setTypeface(android.graphics.Typeface.MONOSPACE);
        box.addView(idv);

        if (notEmpty(b.meta.description)) {
            box.addView(sectionLabel("Description"));
            TextView d = new TextView(act);
            d.setText(b.meta.description); d.setTextColor(Design.dim()); d.setTextSize(13f);
            box.addView(d);
        }
        if (notEmpty(b.meta.externalUrl)) box.addView(linkRow("Website", b.meta.externalUrl));
        if (notEmpty(b.meta.webvalidate)) box.addView(linkRow("Web validation", b.meta.webvalidate));

        new androidx.appcompat.app.AlertDialog.Builder(act)
                .setView(sv)
                .setPositiveButton("Receive", (d, w) -> act.goToTab(MainActivity.TAB_RECEIVE))
                .setNegativeButton("Close", null)
                .show();
    }

    /** Full-screen, full-resolution image (for NFT art). Tap to dismiss. */
    private void showImageFull(String url) {
        ImageView iv = new ImageView(act);
        iv.setAdjustViewBounds(true);
        iv.setBackgroundColor(0xFF000000);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ImageLoader.loadFull(act, url, iv, R.drawable.ic_coin_placeholder);
        androidx.appcompat.app.AlertDialog dlg = new androidx.appcompat.app.AlertDialog.Builder(act).setView(iv).create();
        iv.setOnClickListener(v -> dlg.dismiss());
        dlg.show();
    }

    private void addKv(LinearLayout box, String label, String value) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL); r.setPadding(0, dp(4), 0, dp(4));
        TextView l = new TextView(act); l.setText(label); l.setTextColor(Design.dim()); l.setTextSize(13f);
        TextView v = new TextView(act); v.setText(value); v.setTextColor(Design.text()); v.setTextSize(13f); v.setGravity(Gravity.END);
        r.addView(l, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(v, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.6f));
        box.addView(r);
    }

    private TextView sectionLabel(String s) {
        TextView t = new TextView(act);
        t.setText(s); t.setAllCaps(true); t.setTextColor(Design.dim()); t.setTextSize(11f);
        t.setPadding(0, dp(12), 0, dp(2));
        return t;
    }

    private LinearLayout linkRow(String label, String url) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL); r.setPadding(0, dp(8), 0, dp(8));
        TextView l = new TextView(act); l.setText(label); l.setTextColor(Design.dim()); l.setTextSize(13f);
        TextView v = new TextView(act); v.setText("↗ open"); v.setTextColor(Design.accent()); v.setTextSize(13f); v.setGravity(Gravity.END);
        r.addView(l, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(v, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        r.setOnClickListener(x -> {
            try { act.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))); }
            catch (Exception ignore) {}
        });
        return r;
    }

    private static boolean notEmpty(String s) { return s != null && !s.isEmpty(); }

    /** True if the amount string parses to a strictly positive number; false on null/garbage. */
    private boolean positive(String amt) {
        try { return new BigDecimal(amt).signum() > 0; } catch (Exception e) { return false; }
    }

    /** Whole-number format with thousands separators and no decimals (e.g. 999,978,181). */
    private static String wholeNumber(String amt) {
        try {
            java.math.BigInteger bi = new BigDecimal(amt).toBigInteger();
            return java.text.NumberFormat.getInstance(java.util.Locale.US).format(bi);
        } catch (Exception e) { return amt; }
    }

    /** Contract-locked / watch-only balance = max(0, confirmed − sendable); "0" on parse failure. */
    private static String lockedAmount(TokenBalance b) {
        try {
            BigDecimal l = new BigDecimal(b.confirmed).subtract(new BigDecimal(b.sendable));
            return l.signum() > 0 ? l.stripTrailingZeros().toPlainString() : "0";
        } catch (Exception e) { return "0"; }
    }

    /** Converts density-independent pixels to raw pixels for this device. */
    private int dp(int v) {
        return (int) (v * act.getResources().getDisplayMetrics().density);
    }
}
