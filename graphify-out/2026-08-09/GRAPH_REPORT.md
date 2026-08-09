# Graph Report - utxo  (2026-08-09)

## Corpus Check
- 33 files · ~27,607 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 445 nodes · 1109 edges · 21 communities (17 shown, 4 thin omitted)
- Extraction: 92% EXTRACTED · 8% INFERRED · 0% AMBIGUOUS · INFERRED: 90 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `4fd965e4`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Design
- WalletTools
- TxnBuilder
- HistoryView
- .buildCard
- SendView
- MainActivity
- Coin
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

## God Nodes (most connected - your core abstractions)
1. `MainActivity` - 67 edges
2. `Design` - 36 edges
3. `Coin` - 26 edges
4. `SendView` - 25 edges
5. `BalancesView` - 23 edges
6. `DistributeManager` - 23 edges
7. `HistoryView` - 22 edges
8. `WalletTools` - 20 edges
9. `WalletView` - 20 edges
10. `Cb` - 19 edges

## Surprising Connections (you probably didn't know these)
- `BalancesView` --inherits--> `BaseView`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/utxo/BalancesView.java → app/src/main/java/com/eurobuddha/utxo/BaseView.java
- `BaseView` --references--> `MainActivity`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/utxo/BaseView.java → app/src/main/java/com/eurobuddha/utxo/MainActivity.java
- `SendView` --inherits--> `BaseView`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/utxo/SendView.java → app/src/main/java/com/eurobuddha/utxo/BaseView.java
- `WalletView` --inherits--> `BaseView`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/utxo/WalletView.java → app/src/main/java/com/eurobuddha/utxo/BaseView.java
- `MainActivity` --references--> `Coin`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/utxo/MainActivity.java → app/src/main/java/com/eurobuddha/utxo/Coin.java

## Import Cycles
- None detected.

## Communities (21 total, 4 thin omitted)

### Community 0 - "Design"
Cohesion: 0.09
Nodes (15): Design, Context, Mode, CURRENT, ORIGINAL_DARK, ORIGINAL_LIGHT, Button, Drawable (+7 more)

### Community 1 - "WalletTools"
Cohesion: 0.15
Nodes (5): AddrList, EditText, LinearLayout, Status, WalletTools

### Community 2 - "TxnBuilder"
Cohesion: 0.15
Nodes (6): Done, JSONObject, Out, OutCoin, Progress, TxnBuilder

### Community 3 - "HistoryView"
Cohesion: 0.20
Nodes (7): HistoryView, BaseView, LinearLayout, MainActivity, NodeTx, Override, View

### Community 4 - ".buildCard"
Cohesion: 0.15
Nodes (9): BalancesView, Bitmap, Drawable, LinearLayout, Override, TextView, View, JSONObject (+1 more)

### Community 5 - "SendView"
Cohesion: 0.11
Nodes (10): JSONObject, Button, EditText, LinearLayout, Override, TextView, View, SendView (+2 more)

### Community 6 - "MainActivity"
Cohesion: 0.07
Nodes (17): Handler, JSONObject, Override, TextView, View, MainActivity, Cb, Context (+9 more)

### Community 7 - "Coin"
Cohesion: 0.10
Nodes (5): Coin, DistributeJob, Context, JSONObject, DistributeManager

### Community 8 - "ImageLoader"
Cohesion: 0.15
Nodes (5): ImageLoader, Bitmap, ImageView, WebValidate, LruCache

### Community 9 - "BaseView"
Cohesion: 0.09
Nodes (13): BaseView, View, Override, View, MainPager, ImageView, Override, TextView (+5 more)

### Community 10 - "HistoryDb"
Cohesion: 0.09
Nodes (10): HistoryDb, Context, Override, HistoryRow, JSONObject, NodeTx, TxnUtil, JSONArray (+2 more)

### Community 11 - "Identicon"
Cohesion: 0.41
Nodes (4): Identicon, Bitmap, Canvas, Paint

### Community 12 - ".parse"
Cohesion: 0.27
Nodes (3): IconResolver, TokenMeta, Pattern

### Community 14 - "gradlew"
Cohesion: 0.60
Nodes (3): gradlew script, die(), warn()

## Knowledge Gaps
- **4 isolated node(s):** `RULE 0 (highest priority) — Follow the user's explicit instructions. They are BLOCKING, not suggestions.`, `ORIGINAL_LIGHT`, `ORIGINAL_DARK`, `CURRENT`
  These have ≤1 connection - possible missing edges or undocumented components.
- **4 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `MainActivity` connect `MainActivity` to `Design`, `WalletTools`, `TxnBuilder`, `.buildCard`, `SendView`, `Coin`, `ImageLoader`, `BaseView`, `HistoryDb`?**
  _High betweenness centrality (0.385) - this node is a cross-community bridge._
- **Why does `Coin` connect `Coin` to `Design`, `WalletTools`, `TxnBuilder`, `SendView`, `MainActivity`, `HistoryDb`?**
  _High betweenness centrality (0.080) - this node is a cross-community bridge._
- **Why does `Design` connect `Design` to `.buildCard`, `SendView`, `MainActivity`?**
  _High betweenness centrality (0.079) - this node is a cross-community bridge._
- **What connects `RULE 0 (highest priority) — Follow the user's explicit instructions. They are BLOCKING, not suggestions.`, `ORIGINAL_LIGHT`, `ORIGINAL_DARK` to the rest of the system?**
  _4 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Design` be split into smaller, more focused modules?**
  _Cohesion score 0.09000584453535944 - nodes in this community are weakly interconnected._
- **Should `.buildCard` be split into smaller, more focused modules?**
  _Cohesion score 0.14864864864864866 - nodes in this community are weakly interconnected._
- **Should `SendView` be split into smaller, more focused modules?**
  _Cohesion score 0.1073170731707317 - nodes in this community are weakly interconnected._