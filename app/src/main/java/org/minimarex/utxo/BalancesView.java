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

            // Ticker is optional metadata — hide the field entirely when the token has none.
            TextView ticker = card.findViewById(R.id.balTicker);
            ticker.setTextColor(Design.dim());
            if (b.meta.ticker != null && !b.meta.ticker.isEmpty()) {
                ticker.setText("· " + b.meta.ticker);
            } else {
                ticker.setVisibility(View.GONE);
            }

            TextView total = card.findViewById(R.id.balTotal);
            total.setText("Total " + Util.tidyAmount(b.total)); total.setTextColor(Design.text());

            // Breakdown: always show sendable; append the locked (unconfirmed) part only when nonzero.
            String breakdown = "Sendable " + Util.tidyAmount(b.sendable);
            if (positive(b.unconfirmed)) breakdown += "  ·  Locked " + Util.tidyAmount(b.unconfirmed);
            TextView bd = card.findViewById(R.id.balBreakdown);
            bd.setText(breakdown); bd.setTextColor(Design.dim());

            // Meta line: UTXO count for this token, plus its declared decimals when known.
            String meta = b.coins + (b.coins == 1 ? " coin" : " coins");
            if (b.meta.decimals != null && !b.meta.decimals.isEmpty()) meta += "  ·  " + b.meta.decimals + " decimals";
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

            container.addView(card);
        }
    }

    /** True if the amount string parses to a strictly positive number; false on null/garbage. */
    private boolean positive(String amt) {
        try { return new BigDecimal(amt).signum() > 0; } catch (Exception e) { return false; }
    }

    /** Converts density-independent pixels to raw pixels for this device. */
    private int dp(int v) {
        return (int) (v * act.getResources().getDisplayMetrics().density);
    }
}
