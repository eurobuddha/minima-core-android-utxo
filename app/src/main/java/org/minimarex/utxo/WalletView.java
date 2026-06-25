package org.minimarex.utxo;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.PopupMenu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wallet tab: every wallet address (with coins first, empties below) as a collapsible card with a
 * COPY button; UTXOs underneath with single-tokenid multi-select and tap-to-copy coinids. A
 * "Tools ▾" dropdown on the selection bar holds Split / Consolidate / Distribute / Untrack.
 */
public class WalletView extends BaseView {

    private final LinearLayout container;
    private final TextView selCount, selTotal, statusView;
    private final Button toolsBtn, clearBtn, sendBtn;

    private final WalletTools tools;
    private final Set<String> collapsed = new HashSet<>();   // address hexes collapsed by the user

    /** Wires up the selection bar, Tools dropdown, and renders the first address listing. */
    public WalletView(MainActivity a) {
        super(a, R.layout.view_wallet);
        container = find(R.id.walletContainer);
        selCount = find(R.id.selCount);
        selTotal = find(R.id.selTotal);
        statusView = find(R.id.walletStatus);
        toolsBtn = find(R.id.toolsBtn);
        clearBtn = find(R.id.clearBtn);
        sendBtn = find(R.id.sendBtn);

        tools = new WalletTools(act, this::setStatus);

        clearBtn.setOnClickListener(v -> act.clearSelection());
        sendBtn.setOnClickListener(v -> act.goToTab(MainActivity.TAB_SEND));
        toolsBtn.setOnClickListener(this::showToolsMenu);
        applyDesign();
        refresh();
    }

    /** Paints the view with the active theme (so Dark/Light toggle takes effect). */
    private void applyDesign() {
        android.view.ViewGroup rootVg = (android.view.ViewGroup) root;
        rootVg.setBackgroundColor(Design.bg());
        rootVg.getChildAt(0).setBackgroundColor(Design.surface());   // selection bar
        selCount.setTextColor(Design.text());
        selTotal.setTextColor(Design.accent());
        sendBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Design.accent()));
        sendBtn.setTextColor(Design.onAccent());
        toolsBtn.setTextColor(Design.accent());
        clearBtn.setTextColor(Design.accent());
    }

    /** Shows a one-line status message under the selection bar; green on ok, red on failure. */
    private void setStatus(String msg, boolean ok) {
        statusView.setVisibility(View.VISIBLE);
        statusView.setText(msg);
        statusView.setTextColor(ok ? Design.success() : Design.red());
    }

    /** The "Tools ▾" dropdown: Split / Consolidate / Distribute / Untrack on the current selection. */
    private void showToolsMenu(View anchor) {
        PopupMenu menu = new PopupMenu(act, anchor);
        menu.getMenu().add(0, 1, 0, "Split selected…");
        menu.getMenu().add(0, 2, 1, "Consolidate");
        menu.getMenu().add(0, 3, 2, "Distribute selected…");
        menu.getMenu().add(0, 4, 3, "Untrack selected");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: tools.showSplit(); return true;
                case 2: tools.showConsolidate(); return true;
                case 3: tools.showDistribute(); return true;
                case 4: tools.untrackSelected(); return true;
                default: return false;
            }
        });
        menu.show();
    }

    /** Rebuilds the selection summary and the full address-grouped coin listing. */
    @Override
    public void refresh() {
        List<Coin> sel = act.selectedCoins();
        if (sel.isEmpty()) {
            selCount.setText("Pick coins to send");
            selTotal.setText("");
            sendBtn.setEnabled(false);
            clearBtn.setEnabled(false);
        } else {
            selCount.setText(sel.size() + (sel.size() == 1 ? " coin selected" : " coins selected"));
            selTotal.setText(act.selectedTotalString() + " " + sel.get(0).tokenName);
            sendBtn.setEnabled(true);
            clearBtn.setEnabled(true);
        }

        // Running multi-batch Distribute progress takes over the status line while active.
        DistributeManager dm = act.distribute();
        if (dm != null && dm.isActive()) {
            statusView.setVisibility(View.VISIBLE);
            statusView.setText(dm.statusLine());
            statusView.setTextColor(Design.accent());
        }

        container.removeAllViews();

        // Coins grouped by their (hex) address.
        LinkedHashMap<String, List<Coin>> byAddr = new LinkedHashMap<>();
        for (Coin c : act.coins()) {
            List<Coin> l = byAddr.get(c.address);
            if (l == null) { l = new ArrayList<>(); byAddr.put(c.address, l); }
            l.add(c);
        }

        // hex -> miniaddress lookup across coins and my wallet addresses.
        Map<String, String> miniOf = new HashMap<>();
        for (Coin c : act.coins()) if (!miniOf.containsKey(c.address)) miniOf.put(c.address, c.miniaddress);
        for (String[] a : act.myAddresses()) if (!miniOf.containsKey(a[0])) miniOf.put(a[0], a[1]);

        // Order: addresses WITH coins first (in coin order), then empty wallet addresses below.
        LinkedHashMap<String, String> order = new LinkedHashMap<>(); // hex -> mini
        for (String hex : byAddr.keySet()) order.put(hex, miniOf.get(hex));
        for (String[] a : act.myAddresses()) if (!order.containsKey(a[0])) order.put(a[0], a[1]);

        if (order.isEmpty()) {
            addNote("No addresses yet. Make sure the wallet is enabled in Minima Core.");
            return;
        }

        LayoutInflater inf = LayoutInflater.from(act);
        for (Map.Entry<String, String> e : order.entrySet()) {
            String hex = e.getKey();
            String mini = (e.getValue() != null && !e.getValue().isEmpty()) ? e.getValue() : hex;
            List<Coin> addrCoins = byAddr.get(hex);
            int count = addrCoins == null ? 0 : addrCoins.size();
            boolean isCollapsed = collapsed.contains(hex);

            container.addView(buildHeader(hex, mini, count, isCollapsed));

            if (isCollapsed) continue;

            if (count == 0) {
                TextView empty = new TextView(act);
                empty.setText("—");
                empty.setTextColor(Design.dim());
                empty.setTextSize(12f);
                empty.setPadding(0, 0, 0, dp(4));
                container.addView(empty);
                continue;
            }

            for (Coin c : addrCoins) container.addView(buildCoinRow(inf, c));
        }
    }

    /** Builds one collapsible address card header: caret + short address + coin count + COPY. */
    private View buildHeader(String hex, String mini, int count, boolean isCollapsed) {
        LinearLayout header = new LinearLayout(act);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(14), 0, dp(4));

        TextView caret = new TextView(act);
        caret.setText(isCollapsed ? "▸ " : "▾ ");
        caret.setTextColor(Design.dim());
        caret.setTextSize(12f);
        header.addView(caret);

        TextView addr = new TextView(act);
        addr.setText(Util.shorten(mini));
        addr.setTextColor(Design.text());
        addr.setTextSize(13f);
        addr.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(addr);

        TextView cnt = new TextView(act);
        cnt.setText(count == 0 ? "0 coins" : (count + (count == 1 ? " coin" : " coins")));
        cnt.setTextColor(act.getColor(count == 0 ? R.color.ux_subtext : R.color.ux_accent));
        cnt.setTextSize(12f);
        cnt.setPadding(0, 0, dp(8), 0);
        header.addView(cnt);

        Button copy = new Button(act, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        copy.setText("COPY");
        copy.setTextColor(Design.accent());
        copy.setTextSize(11f);
        copy.setMinWidth(0);
        copy.setMinimumWidth(0);
        copy.setPadding(dp(10), 0, dp(10), 0);
        copy.setOnClickListener(v -> copyToClipboard("Address", mini));
        header.addView(copy);

        // Tapping the header (not the COPY button) collapses/expands the card.
        header.setOnClickListener(v -> {
            if (isCollapsed) collapsed.remove(hex); else collapsed.add(hex);
            refresh();
        });
        return header;
    }

    /** Builds one UTXO row: checkbox (only if confirmed+sendable), amount, copyable coinid, status. */
    private View buildCoinRow(LayoutInflater inf, Coin c) {
        View row = inf.inflate(R.layout.view_coin_row, container, false);
        CheckBox cb = row.findViewById(R.id.coinCheck);
        TextView amt = row.findViewById(R.id.coinAmount);
        TextView cid = row.findViewById(R.id.coinId);
        TextView st = row.findViewById(R.id.coinStatus);

        amt.setText(Util.tidyAmount(c.amount) + "  " + c.tokenName);
        amt.setTextColor(Design.text());
        cid.setText(Util.shorten(c.coinid));
        cid.setTextColor(Design.dim());
        st.setTextColor(Design.dim());

        boolean usable = c.confirmed && c.sendable;
        cb.setEnabled(usable);
        cb.setChecked(act.isSelected(c.coinid));
        st.setText(!c.confirmed ? "pending" : (c.sendable ? "" : "watch"));

        View.OnClickListener toggle = v -> { if (usable) act.toggleCoin(c); };
        cb.setOnClickListener(toggle);
        row.setOnClickListener(toggle);
        // Tap the coinid to copy it (consumes the click, so it doesn't toggle selection).
        cid.setOnClickListener(v -> copyToClipboard("Coin id", c.coinid));
        return row;
    }

    /** Copies text to the clipboard and toasts confirmation; no-op on empty. */
    private void copyToClipboard(String label, String text) {
        if (text == null || text.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(act, label + " copied", Toast.LENGTH_SHORT).show();
    }

    /** Adds a centered dim placeholder note (e.g. the "no addresses yet" message). */
    private void addNote(String text) {
        TextView tv = new TextView(act);
        tv.setText(text);
        tv.setTextColor(Design.dim());
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(40), 0, 0);
        container.addView(tv);
    }

    /** Converts density-independent pixels to raw pixels for this device. */
    private int dp(int v) {
        return (int) (v * act.getResources().getDisplayMetrics().density);
    }
}
