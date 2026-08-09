# Graph Report - utxo  (2026-08-09)

## Corpus Check
- 33 files · ~27,702 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 452 nodes · 1103 edges · 27 communities (19 shown, 8 thin omitted)
- Extraction: 92% EXTRACTED · 8% INFERRED · 0% AMBIGUOUS · INFERRED: 90 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `540e32e0`
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
- DistributeManager
- ImageLoader
- BaseView
- HistoryDb
- Identicon
- .parse
- User instructions — AUTHORITATIVE. These override default behavior and must be followed exactly.
- gradlew
- LinearLayout
- Override
- View
- NodeTx
- ReceiveView
- Override
- TextView
- View
- Context

## God Nodes (most connected - your core abstractions)
1. `MainActivity` - 69 edges
2. `Design` - 36 edges
3. `SendView` - 25 edges
4. `BalancesView` - 23 edges
5. `HistoryView` - 22 edges
6. `Coin` - 22 edges
7. `DistributeManager` - 21 edges
8. `WalletTools` - 20 edges
9. `WalletView` - 20 edges
10. `Cb` - 19 edges

## Surprising Connections (you probably didn't know these)
- `BaseView` --references--> `MainActivity`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/utxo/BaseView.java → app/src/main/java/com/eurobuddha/utxo/MainActivity.java
- `DistributeManager` --references--> `MainActivity`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/utxo/DistributeManager.java → app/src/main/java/com/eurobuddha/utxo/MainActivity.java
- `TxnBuilder` --references--> `MainActivity`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/utxo/TxnBuilder.java → app/src/main/java/com/eurobuddha/utxo/MainActivity.java
- `WalletTools` --references--> `MainActivity`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/utxo/WalletTools.java → app/src/main/java/com/eurobuddha/utxo/MainActivity.java
- `BalancesView` --inherits--> `BaseView`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/utxo/BalancesView.java → app/src/main/java/com/eurobuddha/utxo/BaseView.java

## Import Cycles
- None detected.

## Communities (27 total, 8 thin omitted)

### Community 0 - "Design"
Cohesion: 0.09
Nodes (15): Design, Context, Mode, CURRENT, ORIGINAL_DARK, ORIGINAL_LIGHT, Button, Drawable (+7 more)

### Community 1 - "Coin"
Cohesion: 0.11
Nodes (8): Coin, Out, AddrList, TxnUtil, EditText, LinearLayout, Status, WalletTools

### Community 2 - "TxnBuilder"
Cohesion: 0.17
Nodes (5): Done, JSONObject, OutCoin, Progress, TxnBuilder

### Community 3 - "HistoryView"
Cohesion: 0.21
Nodes (6): HistoryView, LinearLayout, MainActivity, NodeTx, Override, View

### Community 4 - ".buildCard"
Cohesion: 0.15
Nodes (9): BalancesView, Bitmap, Drawable, LinearLayout, Override, TextView, View, JSONObject (+1 more)

### Community 5 - "SendView"
Cohesion: 0.10
Nodes (10): JSONObject, Button, EditText, LinearLayout, Override, TextView, View, SendView (+2 more)

### Community 6 - "MainActivity"
Cohesion: 0.06
Nodes (21): Handler, JSONObject, MainActivity, Cb, Handler, JSONObject, NodeApi, PairingListener (+13 more)

### Community 7 - "DistributeManager"
Cohesion: 0.13
Nodes (4): DistributeJob, Context, JSONObject, DistributeManager

### Community 8 - "ImageLoader"
Cohesion: 0.16
Nodes (5): ImageLoader, Bitmap, ImageView, WebValidate, LruCache

### Community 9 - "BaseView"
Cohesion: 0.13
Nodes (9): BaseView, View, Override, View, MainPager, NonNull, PagerAdapter, SuppressWarnings (+1 more)

### Community 10 - "HistoryDb"
Cohesion: 0.16
Nodes (6): HistoryDb, Context, Override, HistoryRow, SQLiteDatabase, SQLiteOpenHelper

### Community 11 - "Identicon"
Cohesion: 0.41
Nodes (4): Identicon, Bitmap, Canvas, Paint

### Community 12 - ".parse"
Cohesion: 0.27
Nodes (3): IconResolver, TokenMeta, Pattern

### Community 14 - "gradlew"
Cohesion: 0.60
Nodes (3): gradlew script, die(), warn()

### Community 21 - "NodeTx"
Cohesion: 0.25
Nodes (3): JSONObject, NodeTx, JSONArray

### Community 22 - "ReceiveView"
Cohesion: 0.31
Nodes (4): ImageView, Override, TextView, ReceiveView

## Knowledge Gaps
- **4 isolated node(s):** `RULE 0 (highest priority) — Follow the user's explicit instructions. They are BLOCKING, not suggestions.`, `ORIGINAL_LIGHT`, `ORIGINAL_DARK`, `CURRENT`
  These have ≤1 connection - possible missing edges or undocumented components.
- **8 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `MainActivity` connect `MainActivity` to `Design`, `Coin`, `TxnBuilder`, `HistoryView`, `.buildCard`, `SendView`, `DistributeManager`, `ImageLoader`, `BaseView`?**
  _High betweenness centrality (0.411) - this node is a cross-community bridge._
- **Why does `BaseView` connect `BaseView` to `Design`, `.buildCard`, `SendView`, `MainActivity`, `ReceiveView`?**
  _High betweenness centrality (0.123) - this node is a cross-community bridge._
- **Why does `Design` connect `Design` to `.buildCard`, `SendView`?**
  _High betweenness centrality (0.085) - this node is a cross-community bridge._
- **What connects `RULE 0 (highest priority) — Follow the user's explicit instructions. They are BLOCKING, not suggestions.`, `ORIGINAL_LIGHT`, `ORIGINAL_DARK` to the rest of the system?**
  _4 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Design` be split into smaller, more focused modules?**
  _Cohesion score 0.08961748633879782 - nodes in this community are weakly interconnected._
- **Should `Coin` be split into smaller, more focused modules?**
  _Cohesion score 0.11282051282051282 - nodes in this community are weakly interconnected._
- **Should `.buildCard` be split into smaller, more focused modules?**
  _Cohesion score 0.14864864864864866 - nodes in this community are weakly interconnected._