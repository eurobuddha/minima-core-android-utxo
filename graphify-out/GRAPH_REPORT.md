# Graph Report - utxo  (2026-09-07)

## Corpus Check
- 37 files · ~31,123 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 496 nodes · 1303 edges · 25 communities (21 shown, 4 thin omitted)
- Extraction: 92% EXTRACTED · 8% INFERRED · 0% AMBIGUOUS · INFERRED: 107 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `e2d56c0c`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Design
- Coin
- TxnBuilder
- HistoryView
- .buildCard
- SendView
- MainActivity
- WalletTools
- ImageLoader
- BaseView
- CoinLoader
- utxoWallet — Native Android clone: figma-style mapping & build blueprint
- .parse
- User instructions — AUTHORITATIVE. These override default behavior and must be followed exactly.
- gradlew
- install.sh
- Minima UTXO Wallet (native Android)
- pre-commit
- NodeTx
- NodeApi
- .doDistribute
- Cb

## God Nodes (most connected - your core abstractions)
1. `MainActivity` - 73 edges
2. `Design` - 32 edges
3. `Coin` - 28 edges
4. `HistoryView` - 27 edges
5. `SendView` - 26 edges
6. `BalancesView` - 23 edges
7. `DistributeManager` - 23 edges
8. `Cb` - 23 edges
9. `NodeTx` - 21 edges
10. `WalletTools` - 20 edges

## Surprising Connections (you probably didn't know these)
- `BalancesView` --inherits--> `BaseView`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/utxo/BalancesView.java → app/src/main/java/com/eurobuddha/utxo/BaseView.java
- `BaseView` --references--> `MainActivity`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/utxo/BaseView.java → app/src/main/java/com/eurobuddha/utxo/MainActivity.java
- `HistoryView` --inherits--> `BaseView`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/utxo/HistoryView.java → app/src/main/java/com/eurobuddha/utxo/BaseView.java
- `ReceiveView` --inherits--> `BaseView`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/utxo/ReceiveView.java → app/src/main/java/com/eurobuddha/utxo/BaseView.java
- `SendView` --inherits--> `BaseView`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/utxo/SendView.java → app/src/main/java/com/eurobuddha/utxo/BaseView.java

## Import Cycles
- None detected.

## Communities (25 total, 4 thin omitted)

### Community 0 - "Design"
Cohesion: 0.09
Nodes (15): android.graphics.drawable.Drawable, android.graphics.drawable.GradientDrawable, android.graphics.Typeface, android.view.View, Design, Mode, CURRENT, ORIGINAL_DARK (+7 more)

### Community 1 - "Coin"
Cohesion: 0.11
Nodes (4): Coin, DistributeManager, Out, TxnUtil

### Community 2 - "TxnBuilder"
Cohesion: 0.17
Nodes (4): Done, OutCoin, Progress, TxnBuilder

### Community 3 - "HistoryView"
Cohesion: 0.14
Nodes (7): android.widget.LinearLayout, HistoryRow, HistoryView, LinearLayout, Override, Util, JSONArray

### Community 4 - ".buildCard"
Cohesion: 0.14
Nodes (6): BalancesView, LinearLayout, Override, TextView, TokenBalance, WebValidate

### Community 5 - "SendView"
Cohesion: 0.16
Nodes (7): android.widget.Button, android.widget.EditText, android.widget.TextView, LinearLayout, Override, TextView, SendView

### Community 6 - "MainActivity"
Cohesion: 0.10
Nodes (6): android.content.BroadcastReceiver, android.os.Bundle, androidx.appcompat.app.AppCompatActivity, androidx.viewpager.widget.ViewPager, Override, MainActivity

### Community 7 - "WalletTools"
Cohesion: 0.16
Nodes (5): EditText, LinearLayout, Status, WalletTools, Builder

### Community 8 - "ImageLoader"
Cohesion: 0.09
Nodes (12): android.graphics.Bitmap, android.graphics.Canvas, android.graphics.Paint, android.util.LruCache, android.widget.ImageView, Identicon, ImageLoader, NetFetch (+4 more)

### Community 9 - "BaseView"
Cohesion: 0.14
Nodes (7): android.view.ViewGroup, androidx.annotation.NonNull, androidx.viewpager.widget.PagerAdapter, BaseView, Override, MainPager, SuppressWarnings

### Community 10 - "CoinLoader"
Cohesion: 0.23
Nodes (4): CoinLoader, Done, Fail, Ok

### Community 11 - "utxoWallet — Native Android clone: figma-style mapping & build blueprint"
Cohesion: 0.08
Nodes (24): 0. Design languages (runtime toggle), 1.1 ORIGINAL — light (`:root`, default), 1.2 ORIGINAL — dark (`:root[data-theme="dark"]`), 1.3 CURRENT (existing native dark), 1.4 Type & metrics (ORIGINAL), 1.5 Component note colors (`.field-note`, `.toast`, pills), 1. Design tokens, 2. Component catalog (ORIGINAL; CURRENT = Material equivalents) (+16 more)

### Community 12 - ".parse"
Cohesion: 0.24
Nodes (3): IconResolver, TokenMeta, java.util.regex.Pattern

### Community 13 - "User instructions — AUTHORITATIVE. These override default behavior and must be followed exactly."
Cohesion: 0.50
Nodes (3): RULE 0 (highest priority) — Follow the user's explicit instructions. They are BLOCKING, not suggestions., User instructions — AUTHORITATIVE. These override default behavior and must be followed exactly., Versioning guardrail — every code change ships with a version bump

### Community 14 - "gradlew"
Cohesion: 0.60
Nodes (3): gradlew script, die(), warn()

### Community 19 - "Minima UTXO Wallet (native Android)"
Cohesion: 0.40
Nodes (4): Build, Features, Minima UTXO Wallet (native Android), Releases

### Community 21 - "NodeTx"
Cohesion: 0.07
Nodes (11): android.content.Context, android.database.sqlite.SQLiteDatabase, android.database.sqlite.SQLiteOpenHelper, DistributeJob, JSONObject, HistoryDb, Override, JSONObject (+3 more)

### Community 22 - "NodeApi"
Cohesion: 0.21
Nodes (5): android.os.Handler, NodeApi, PairingListener, MinimaAPI, org.minimarex.minimaapi.MinimaAPI

## Knowledge Gaps
- **29 isolated node(s):** `install.sh script`, `ORIGINAL_LIGHT`, `ORIGINAL_DARK`, `CURRENT`, `RULE 0 (highest priority) — Follow the user's explicit instructions. They are BLOCKING, not suggestions.` (+24 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **4 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `MainActivity` connect `MainActivity` to `Design`, `Coin`, `TxnBuilder`, `HistoryView`, `.buildCard`, `SendView`, `WalletTools`, `ImageLoader`, `BaseView`, `CoinLoader`, `NodeTx`, `NodeApi`, `.doDistribute`, `Cb`?**
  _High betweenness centrality (0.331) - this node is a cross-community bridge._
- **Why does `Coin` connect `Coin` to `Design`, `TxnBuilder`, `HistoryView`, `SendView`, `MainActivity`, `WalletTools`, `CoinLoader`, `NodeTx`, `.doDistribute`, `Cb`?**
  _High betweenness centrality (0.062) - this node is a cross-community bridge._
- **Why does `HistoryDb` connect `NodeTx` to `Design`, `Coin`, `MainActivity`?**
  _High betweenness centrality (0.055) - this node is a cross-community bridge._
- **What connects `install.sh script`, `ORIGINAL_LIGHT`, `ORIGINAL_DARK` to the rest of the system?**
  _29 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Design` be split into smaller, more focused modules?**
  _Cohesion score 0.09265536723163842 - nodes in this community are weakly interconnected._
- **Should `Coin` be split into smaller, more focused modules?**
  _Cohesion score 0.11092436974789915 - nodes in this community are weakly interconnected._
- **Should `HistoryView` be split into smaller, more focused modules?**
  _Cohesion score 0.1406423034330011 - nodes in this community are weakly interconnected._