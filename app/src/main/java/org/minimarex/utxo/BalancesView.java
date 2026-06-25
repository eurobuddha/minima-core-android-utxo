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

    public BalancesView(MainActivity a) {
        super(a, R.layout.view_balances);
        container = find(R.id.balancesContainer);
        root.setBackgroundColor(Design.bg());
        refresh();
    }

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

            ImageView icon = card.findViewById(R.id.balIcon);
            if (Util.isMinima(b.tokenid)) {
                icon.setImageResource(R.mipmap.ic_launcher);
            } else {
                ImageLoader.load(act, b.meta.iconUrl, icon, R.drawable.ic_coin_placeholder);
            }

            TextView name = card.findViewById(R.id.balName);
            name.setText(b.name); name.setTextColor(Design.accent());

            TextView ticker = card.findViewById(R.id.balTicker);
            ticker.setTextColor(Design.dim());
            if (b.meta.ticker != null && !b.meta.ticker.isEmpty()) {
                ticker.setText("· " + b.meta.ticker);
            } else {
                ticker.setVisibility(View.GONE);
            }

            TextView total = card.findViewById(R.id.balTotal);
            total.setText("Total " + Util.tidyAmount(b.total)); total.setTextColor(Design.text());

            String breakdown = "Sendable " + Util.tidyAmount(b.sendable);
            if (positive(b.unconfirmed)) breakdown += "  ·  Locked " + Util.tidyAmount(b.unconfirmed);
            TextView bd = card.findViewById(R.id.balBreakdown);
            bd.setText(breakdown); bd.setTextColor(Design.dim());

            String meta = b.coins + (b.coins == 1 ? " coin" : " coins");
            if (b.meta.decimals != null && !b.meta.decimals.isEmpty()) meta += "  ·  " + b.meta.decimals + " decimals";
            TextView mt = card.findViewById(R.id.balMeta);
            mt.setText(meta); mt.setTextColor(Design.dim());

            TextView tid = card.findViewById(R.id.balTokenId);
            tid.setText(b.tokenid); tid.setTextColor(Design.dim());

            TextView desc = card.findViewById(R.id.balDesc);
            desc.setTextColor(Design.dim());
            if (b.meta.description != null && !b.meta.description.isEmpty()) {
                desc.setVisibility(View.VISIBLE);
                desc.setText(b.meta.description);
            }

            container.addView(card);
        }
    }

    private boolean positive(String amt) {
        try { return new BigDecimal(amt).signum() > 0; } catch (Exception e) { return false; }
    }

    private int dp(int v) {
        return (int) (v * act.getResources().getDisplayMetrics().density);
    }
}
