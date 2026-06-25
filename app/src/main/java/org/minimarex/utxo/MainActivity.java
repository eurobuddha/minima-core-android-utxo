package org.minimarex.utxo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import org.minimarex.minimaapi.MinimaAPI;
import org.minimarex.minimaapi.MinimaAPIMessages;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Native UTXO wallet. Tabs: Wallet / Balances / Receive / Send / History.
 * Talks to the local Minima Core node over the broadcast-Intent IPC ({@link NodeApi}).
 */
public class MainActivity extends AppCompatActivity {

    public static final String NODE_PKG = "org.minimarex.minimacore";

    public static final int TAB_WALLET = 0, TAB_BALANCES = 1, TAB_RECEIVE = 2,
            TAB_SEND = 3, TAB_HISTORY = 4;

    private NodeApi node;
    private HistoryDb historyDb;

    private BaseView[] views;
    private MainPager pager;
    private ViewPager viewPager;
    private View pairingBanner;
    private TextView blockNo;
    private BroadcastReceiver notifyReceiver;
    private DistributeManager distribute;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable reloadTask = this::reload;

    // ----- wallet state -----
    private final List<Coin> coins = new ArrayList<>();
    private final Set<String> sendableIds = new HashSet<>();
    private final List<TokenBalance> balances = new ArrayList<>();
    // All of my wallet addresses (incl. zero-balance): each entry is {hexAddress, miniAddress}.
    private final List<String[]> myAddresses = new ArrayList<>();
    private String defaultMiniAddress = "";
    private int chainBlock = 0;
    private int lastScriptsBlock = -1;                 // throttle the ~27 KB scripts fetch
    private static final int SCRIPTS_EVERY = 20;       // blocks between scripts refreshes

    // ----- selection state (single tokenid at a time) -----
    private final LinkedHashSet<String> selectedCoinIds = new LinkedHashSet<>();
    private String selectedTokenid = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Design.load(this);                 // must precede view construction (views read Design)
        setContentView(R.layout.activity_main);

        // Edge-to-edge is forced on targetSdk 35 — pad the root by the status/nav bar insets so the
        // top bar isn't drawn under the status bar.
        View root = findViewById(R.id.main);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars =
                    insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        historyDb = new HistoryDb(this);

        pairingBanner = findViewById(R.id.pairingBanner);
        blockNo = findViewById(R.id.blockNo);
        ((Button) findViewById(R.id.openNodeBtn)).setOnClickListener(v -> openMinimaCore());

        // Design-language toggle (Original Light → Original Dark → Current).
        TextView designToggle = findViewById(R.id.designToggle);
        designToggle.setOnClickListener(v -> { Design.set(this, Design.next()); recreate(); });

        views = new BaseView[]{
                new WalletView(this),
                new BalancesView(this),
                new ReceiveView(this),
                new SendView(this),
                new HistoryView(this)
        };
        pager = new MainPager(views, new String[]{"Wallet", "Balances", "Receive", "Send", "History"});

        viewPager = findViewById(R.id.pager);
        viewPager.setOffscreenPageLimit(4);
        viewPager.setAdapter(pager);

        TabLayout tabs = findViewById(R.id.tabs);
        tabs.setupWithViewPager(viewPager);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                views[tab.getPosition()].refresh();
                views[tab.getPosition()].onShown();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {
                views[tab.getPosition()].refresh();
                views[tab.getPosition()].onShown();
            }
        });

        // Apply the chosen design language to the shell chrome.
        root.setBackgroundColor(Design.bg());
        viewPager.setBackgroundColor(Design.bg());
        ((TextView) findViewById(R.id.brandTitle)).setTextColor(Design.accent());
        ((TextView) findViewById(R.id.brandSub)).setTextColor(Design.dim());
        blockNo.setTextColor(Design.dim());
        designToggle.setTextColor(Design.accent());
        designToggle.setText("◐ " + designTag());
        pairingBanner.setBackgroundColor(Design.accentSoft());
        tabs.setBackgroundColor(Design.bg());
        tabs.setTabTextColors(Design.dim(), Design.accent());
        tabs.setSelectedTabIndicatorColor(Design.accent());

        // Construct the IPC: register reply drives the pairing banner.
        node = new NodeApi(this, enabled -> {
            if (enabled) {
                setPaired(true);
                requestReload();
            } else {
                setPaired(false);
            }
        });

        // Live updates: the node broadcasts {event,data} to enabled apps. Refresh on
        // NEWBLOCK / NEWBALANCE.
        notifyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                if (!MinimaAPI.checkMinimaID(MainActivity.this, intent)) return;
                String data = intent.getStringExtra(MinimaAPIMessages.MINIMA_API_NOTIFY_DATA);
                if (data == null) return;
                try {
                    String event = new org.json.JSONObject(data).optString("event", "");
                    if ("NEWBLOCK".equals(event) || "NEWBALANCE".equals(event)) {
                        requestReload();
                    }
                } catch (Exception ignored) {}
            }
        };
        ContextCompat.registerReceiver(this, notifyReceiver,
                new IntentFilter(MinimaAPIMessages.MINIMA_API_NOTIFY), ContextCompat.RECEIVER_EXPORTED);

        // Resumes any persisted multi-batch Distribute job.
        distribute = new DistributeManager(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh from the node (also re-checks enablement after returning from Minima Core).
        requestReload();
        // If the user returns while parked on History, refetch it (otherwise it waits for the next block).
        if (currentTab() == TAB_HISTORY) views[TAB_HISTORY].onShown();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacks(reloadTask);
        if (node != null) node.onDestroy();
        if (notifyReceiver != null) {
            try { unregisterReceiver(notifyReceiver); } catch (Exception ignored) {}
        }
    }

    // ===== loading =====

    /** Pull coins, sendable set, balances, default address and chain tip from the node. */
    public void reload() {
        node.cmd("block", new NodeApi.Cb() {
            @Override public void onResult(JSONObject json) {
                setPaired(true);
                JSONObject r = json.optJSONObject("response");
                if (r != null) {
                    String b = r.optString("block", "");
                    if (b.isEmpty()) {
                        JSONObject h = r.optJSONObject("header");
                        if (h != null) b = h.optString("block", "");
                    }
                    try { chainBlock = Integer.parseInt(b); } catch (Exception ignored) {}
                    blockNo.setText("#" + chainBlock);
                    // History self-fetches (bounded) only when it's the visible tab.
                    views[TAB_HISTORY].onNewBlock();
                }
            }
            @Override public void onError(String message) { handleErr(message); }
        });

        node.cmd("coins relevant:true", new NodeApi.Cb() {
            @Override public void onResult(JSONObject json) {
                setPaired(true);
                coins.clear();
                JSONArray arr = json.optJSONArray("response");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject c = arr.optJSONObject(i);
                        if (c != null) coins.add(Coin.from(c));
                    }
                }
                // then the sendable subset
                node.cmd("coins relevant:true sendable:true", new NodeApi.Cb() {
                    @Override public void onResult(JSONObject json2) {
                        sendableIds.clear();
                        JSONArray a2 = json2.optJSONArray("response");
                        if (a2 != null) {
                            for (int i = 0; i < a2.length(); i++) {
                                JSONObject c = a2.optJSONObject(i);
                                if (c != null) sendableIds.add(c.optString("coinid", ""));
                            }
                        }
                        for (Coin c : coins) c.sendable = sendableIds.contains(c.coinid);
                        pruneSelection();
                        refreshAll();
                        // Advance any running multi-batch Distribute job (change coin may have confirmed).
                        if (distribute != null) distribute.onCoinsUpdated();
                    }
                    @Override public void onError(String message) { refreshAll(); }
                });
            }
            @Override public void onError(String message) { handleErr(message); }
        });

        node.cmd("balance", new NodeApi.Cb() {
            @Override public void onResult(JSONObject json) {
                balances.clear();
                JSONArray arr = json.optJSONArray("response");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject b = arr.optJSONObject(i);
                        if (b != null) balances.add(TokenBalance.from(b));
                    }
                }
                views[TAB_BALANCES].refresh();
                views[TAB_SEND].refresh();
            }
            @Override public void onError(String message) { handleErr(message); }
        });

        // getaddress (~0.5 KB) is only needed for the Receive tab — fetched there on demand, not every
        // reload. scripts (~27 KB) lists the wallet's addresses, which barely change — fetched rarely.
        maybeRefreshScripts();
    }

    /** scripts (~27 KB) lists the wallet's stable default-address pool (64 pre-generated), so it barely
     *  changes — fetch on first load and then only every ~20 blocks, not every reload. Populates
     *  myAddresses (used by Wallet zero-balance rows + History direction classification) + refreshes Wallet. */
    private void maybeRefreshScripts() {
        if (!myAddresses.isEmpty() && chainBlock - lastScriptsBlock < SCRIPTS_EVERY) return;
        lastScriptsBlock = chainBlock;
        node.cmd("scripts", new NodeApi.Cb() {
            @Override public void onResult(JSONObject json) {
                myAddresses.clear();
                JSONArray arr = json.optJSONArray("response");
                if (arr != null) {
                    java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject s = arr.optJSONObject(i);
                        if (s == null) continue;
                        String hex = s.optString("address", "");
                        String mini = s.optString("miniaddress", hex);
                        // A script entry is one of the wallet's own default addresses iff default==true.
                        boolean wallet = s.optBoolean("default", false);
                        if (!hex.isEmpty() && wallet && seen.add(hex)) {
                            myAddresses.add(new String[]{hex, mini});
                        }
                    }
                }
                views[TAB_WALLET].refresh();
            }
            @Override public void onError(String message) { handleErr(message); }
        });
    }

    /** Set by the Receive tab when it fetches a getaddress (no longer fetched on every reload). */
    public void setDefaultAddress(String addr) {
        if (addr != null && !addr.isEmpty()) defaultMiniAddress = addr;
    }

    /** Re-render the History tab. */
    public void refreshHistory() {
        views[TAB_HISTORY].refresh();
    }

    private void handleErr(String message) {
        if (NodeApi.ERR_NOT_ENABLED.equals(message)) {
            setPaired(false);
        } else {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    public void refreshAll() {
        for (BaseView v : views) v.refresh();
    }

    // ===== pairing UX =====

    private void setPaired(boolean paired) {
        pairingBanner.setVisibility(paired ? View.GONE : View.VISIBLE);
    }

    private void openMinimaCore() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(NODE_PKG);
        if (launch != null) startActivity(launch);
        else Toast.makeText(this, "Minima Core is not installed.", Toast.LENGTH_LONG).show();
    }

    // ===== selection =====

    public void toggleCoin(Coin c) {
        if (selectedCoinIds.contains(c.coinid)) {
            selectedCoinIds.remove(c.coinid);
            if (selectedCoinIds.isEmpty()) selectedTokenid = null;
        } else {
            if (selectedTokenid != null && !selectedTokenid.equals(c.tokenid)) {
                selectedCoinIds.clear();
            }
            selectedTokenid = c.tokenid;
            selectedCoinIds.add(c.coinid);
        }
        views[TAB_WALLET].refresh();
    }

    public void clearSelection() {
        selectedCoinIds.clear();
        selectedTokenid = null;
        views[TAB_WALLET].refresh();
    }

    /** Drop selected coins that no longer exist (e.g. spent). */
    private void pruneSelection() {
        Set<String> live = new HashSet<>();
        for (Coin c : coins) live.add(c.coinid);
        selectedCoinIds.retainAll(live);
        if (selectedCoinIds.isEmpty()) selectedTokenid = null;
    }

    public List<Coin> selectedCoins() {
        List<Coin> out = new ArrayList<>();
        for (Coin c : coins) if (selectedCoinIds.contains(c.coinid)) out.add(c);
        return out;
    }

    public String selectedTotalString() {
        BigDecimal sum = BigDecimal.ZERO;
        for (Coin c : selectedCoins()) {
            try { sum = sum.add(new BigDecimal(c.amount)); } catch (Exception ignored) {}
        }
        return Util.tidyAmount(sum.toPlainString());
    }

    // ===== accessors for views =====

    public NodeApi node() { return node; }
    public HistoryDb history() { return historyDb; }
    public List<Coin> coins() { return coins; }
    public List<TokenBalance> balances() { return balances; }
    public List<String[]> myAddresses() { return myAddresses; }
    public String defaultAddress() { return defaultMiniAddress; }
    public boolean isSelected(String coinid) { return selectedCoinIds.contains(coinid); }
    public String selectedTokenid() { return selectedTokenid; }
    public void goToTab(int pos) { viewPager.setCurrentItem(pos); }
    private String designTag() {
        // Current is our "Dark"; the original brutalist palette is "Light".
        return Design.mode() == Design.Mode.ORIGINAL_LIGHT ? "LIGHT" : "DARK";
    }

    public int chainBlock() { return chainBlock; }

    /** The currently visible tab index (ViewPager page). */
    public int currentTab() { return viewPager.getCurrentItem(); }
    public DistributeManager distribute() { return distribute; }
    /** Distribute progress is shown on the Wallet tab now (Tools is a dropdown there). */
    public void refreshTools() { views[TAB_WALLET].refresh(); }

    /** Coalesce bursts of NEWBLOCK/NEWBALANCE/onResume into a single reload. */
    public void requestReload() {
        ui.removeCallbacks(reloadTask);
        // 400 ms coalesces the NEWBLOCK + NEWBALANCE burst into a single reload (less node load).
        ui.postDelayed(reloadTask, 400);
    }

    /** Max decimal places a token allows: -1 for Minima or an unknown token (no clamp). */
    public int tokenDecimals(String tokenid) {
        if (Util.isMinima(tokenid)) return -1;
        for (TokenBalance b : balances) {
            if (b.tokenid.equals(tokenid)) {
                try { return Integer.parseInt(b.meta.decimals.trim()); }
                catch (Exception e) { return -1; }
            }
        }
        return -1;
    }
}
