package com.eurobuddha.utxo;

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
    private final Runnable reloadTask = this::reloadQuiet;

    // ----- wallet state -----
    private final List<Coin> coins = new ArrayList<>();
    private final Set<String> sendableIds = new HashSet<>();
    private final List<TokenBalance> balances = new ArrayList<>();
    // All of my wallet addresses (incl. zero-balance): each entry is {hexAddress, miniAddress}.
    private final List<String[]> myAddresses = new ArrayList<>();
    private final Set<String> myKeys = new HashSet<>();   // this node's public keys (for classifying own simple addresses)
    private String defaultMiniAddress = "";
    private int chainBlock = 0;
    private int lastScriptsBlock = -1;                 // throttle the ~27 KB scripts fetch
    private static final int SCRIPTS_EVERY = 20;       // blocks between scripts refreshes
    private static final long UNKNOWN_STALE_MS = 15 * 60 * 1000L;
    private static final int COINS_EVERY = 10;         // blocks between coin refreshes in sliced mode (old node)
    private String coinsMode = "whole";                // last CoinLoader mode
    private boolean coinsDirty = true;                 // something changed → refetch coins even in sliced mode
    private int lastCoinsBlock = -1;
    private String coinsNote = "";                     // Wallet-tab banner when coins came sliced / incomplete   // an unanswered txnsign whose inputs are still unspent after this = not posted
    private String circulatingSupply = "";             // status.minima — live total Minima (1bn − burnt)

    // ----- selection state (single tokenid at a time) -----
    private final LinkedHashSet<String> selectedCoinIds = new LinkedHashSet<>();
    private String selectedTokenid = null;

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putStringArrayList("sel_ids", new ArrayList<>(selectedCoinIds));
        if (selectedTokenid != null) out.putString("sel_tok", selectedTokenid);
        if (views != null) out.putStringArray("send_fields", ((SendView) views[TAB_SEND]).fieldValues());
        if (viewPager != null) out.putInt("cur_tab", viewPager.getCurrentItem());
    }

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
            androidx.core.graphics.Insets ime =
                    insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime());
            // Pad up by the keyboard when it's open so the Send fields sit above it (the ScrollView then scrolls).
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return insets;
        });

        historyDb = new HistoryDb(this);

        pairingBanner = findViewById(R.id.pairingBanner);
        blockNo = findViewById(R.id.blockNo);
        ((Button) findViewById(R.id.openNodeBtn)).setOnClickListener(v -> openMinimaCore());

        // Design-language toggle (Original Light → Original Dark → Native), cycles on tap.
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

        // The theme toggle uses recreate(); restore selection, typed Send fields, and the open tab across it.
        if (savedInstanceState != null) {
            java.util.ArrayList<String> sel = savedInstanceState.getStringArrayList("sel_ids");
            if (sel != null) { selectedCoinIds.clear(); selectedCoinIds.addAll(sel); }
            selectedTokenid = savedInstanceState.getString("sel_tok");
            ((SendView) views[TAB_SEND]).setFieldValues(savedInstanceState.getStringArray("send_fields"));
            int tab = savedInstanceState.getInt("cur_tab", 0);
            viewPager.setCurrentItem(tab, false);
            views[tab].refresh();   // paint the restored tab now; the rest refresh on tab-select / data reload
        }

        // Apply the chosen design language to the shell chrome.
        root.setBackgroundColor(Design.bg());
        viewPager.setBackgroundColor(Design.bg());
        ((TextView) findViewById(R.id.brandTitle)).setTextColor(Design.heading());   // dapp: title is --heading, not accent
        TextView brandSub = findViewById(R.id.brandSub);
        brandSub.setTextColor(Design.dim());
        String ver;
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { ver = "?"; }
        brandSub.setText("PICK YOUR COINS  ·  v" + ver);
        blockNo.setTextColor(Design.dim());
        designToggle.setTextColor(Design.accent());
        designToggle.setText("◐ " + designTag());
        pairingBanner.setBackgroundColor(Design.accentSoft());
        tabs.setBackgroundColor(Design.bg());
        tabs.setTabTextColors(Design.dim(), Design.heading());   // dapp: active tab text is --heading
        tabs.setSelectedTabIndicatorColor(Design.accent());
        tabs.setSelectedTabIndicatorHeight((int) (3 * getResources().getDisplayMetrics().density));  // 3px inset bar

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
                    if ("NEWBALANCE".equals(event)) coinsDirty = true;   // coins changed → refetch even in sliced mode
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
        coinsDirty = true;
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

    /** Explicit reload after a send / tool / user action: coins are refetched even in sliced mode. */
    public void reload() {
        coinsDirty = true;
        reloadQuiet();
    }

    /** Pull coins, sendable set, balances, default address and chain tip from the node. */
    private void reloadQuiet() {
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

        // Coin set. The node's coins command has no paging parameters (relevant/sendable/coinid/amount/
        // address/tokenid/coinage/checkmempool/order — core/minima-core coins.java), so on a pre-1.3.0 node
        // (no file hand-off, 256,000-char reply cap) CoinLoader slices by token, then by address. Sliced
        // mode is many IPC calls, so it only re-runs when something changed (NEWBALANCE, a send/tool,
        // onResume, an active Distribute) or every COINS_EVERY blocks — not on every block.
        boolean sliced = !"whole".equals(coinsMode);
        boolean fetchCoins = !sliced || coinsDirty || chainBlock - lastCoinsBlock >= COINS_EVERY
                || (distribute != null && distribute.isActive());
        if (fetchCoins) {
            coinsDirty = false;
            CoinLoader.load(this, new CoinLoader.Done() {
                @Override public void onCoins(List<Coin> got, Set<String> sendable, int expected, String mode) {
                    setPaired(true);
                    coinsMode = mode;
                    lastCoinsBlock = chainBlock;
                    coins.clear();
                    coins.addAll(got);
                    sendableIds.clear();
                    sendableIds.addAll(sendable);
                    int missing = expected >= 0 ? expected - coins.size() : 0;
                    if (missing > 0) {
                        coinsNote = missing + (missing == 1 ? " coin is" : " coins are") + " not shown: this Minima Core (<1.3.0) caps "
                                + "replies and one slice was still too big. Update Minima Core to see everything.";
                    } else if (!"whole".equals(mode)) {
                        coinsNote = "Coins loaded in slices (this Minima Core is <1.3.0 and caps replies) — refreshed on balance changes.";
                    } else {
                        coinsNote = "";
                    }
                    pruneSelection();
                    // Settle any "posted?" rows (txnsign timed out) against the live coin set.
                    Set<String> live = new HashSet<>();
                    for (Coin c : coins) live.add(c.coinid);
                    historyDb.reconcileUnknown(live, UNKNOWN_STALE_MS);
                    refreshAll();
                    // Advance any running multi-batch Distribute job (change coin may have confirmed).
                    if (distribute != null) distribute.onCoinsUpdated();
                }
                @Override public void onError(String message) { coinsDirty = true; handleErr(message); }
            });
        }

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

        // status carries the live circulating supply (status.minima = 1bn − burnt) shown on the Minima
        // balance card. Small summary response; refresh Balances once it lands.
        node.cmd("status", new NodeApi.Cb() {
            @Override public void onResult(JSONObject json) {
                JSONObject r = json.optJSONObject("response");
                if (r != null) {
                    String m = r.optString("minima", "");
                    if (!m.isEmpty()) { circulatingSupply = m; views[TAB_BALANCES].refresh(); }
                }
            }
            @Override public void onError(String message) {}
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
        // Fetch this node's public keys first, so we can recognise our OWN simple single-key addresses that
        // aren't in the default-64 pool (e.g. a newaddress-minted address like a PandaPools pool payout $OADR)
        // and NOT mislabel them "CONTRACT". Keys are small + stable; refreshed on the same ~20-block cadence.
        node.cmd("keys", new NodeApi.Cb() {
            @Override public void onResult(JSONObject kj) { collectKeys(kj); loadScripts(); }
            @Override public void onError(String m) { loadScripts(); }   // no keys → fall back to default-only classification
        });
    }

    private void collectKeys(JSONObject kj) {
        myKeys.clear();
        Object resp = kj.opt("response");
        JSONArray arr = null;
        if (resp instanceof JSONArray) arr = (JSONArray) resp;
        else if (resp instanceof JSONObject) arr = ((JSONObject) resp).optJSONArray("keys");
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            JSONObject k = arr.optJSONObject(i);
            if (k != null) { String pk = k.optString("publickey", ""); if (!pk.isEmpty()) myKeys.add(pk.toLowerCase()); }
        }
    }

    private void loadScripts() {
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
                        // A wallet address = a default address, OR a simple single-key address whose key is OURS
                        // (the node holds it, e.g. a newaddress-minted $OADR). Both are fully spendable by this
                        // node, so neither should be flagged "CONTRACT". A covenant is not simple (publickey 0x00),
                        // so it stays classified as a contract.
                        String pk = s.optString("publickey", "").toLowerCase();
                        boolean wallet = s.optBoolean("default", false)
                                || (s.optBoolean("simple", false) && !pk.isEmpty() && myKeys.contains(pk));
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
    /** Non-empty when the coin list came sliced or incomplete because the node caps replies (see CoinLoader). */
    public String coinsNote() { return coinsNote; }
    public List<TokenBalance> balances() { return balances; }
    public List<String[]> myAddresses() { return myAddresses; }
    /** Live circulating Minima supply (status.minima = 1bn − burnt), or "" until status has loaded. */
    public String circulatingSupply() { return circulatingSupply; }
    public String defaultAddress() { return defaultMiniAddress; }
    public boolean isSelected(String coinid) { return selectedCoinIds.contains(coinid); }
    public String selectedTokenid() { return selectedTokenid; }
    public void goToTab(int pos) { viewPager.setCurrentItem(pos); }
    private String designTag() {
        switch (Design.mode()) {
            case ORIGINAL_LIGHT: return "LIGHT";     // the dapp's brutalist light palette
            case ORIGINAL_DARK:  return "DARK";      // its photo-negative
            default:             return "NATIVE";    // the native dark look
        }
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
