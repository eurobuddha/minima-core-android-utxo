# Graph Report - utxo  (2026-08-06)

## Corpus Check
- 33 files · ~27,607 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 442 nodes · 1107 edges · 21 communities (18 shown, 3 thin omitted)
- Extraction: 92% EXTRACTED · 8% INFERRED · 0% AMBIGUOUS · INFERRED: 90 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `00654c6f`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Design
- WalletTools
- NodeTx
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
- ReceiveView
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
- `ReceiveView` --inherits--> `BaseView`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/utxo/ReceiveView.java → app/src/main/java/com/eurobuddha/utxo/BaseView.java
- `SendView` --inherits--> `BaseView`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/utxo/SendView.java → app/src/main/java/com/eurobuddha/utxo/BaseView.java
- `WalletView` --inherits--> `BaseView`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/utxo/WalletView.java → app/src/main/java/com/eurobuddha/utxo/BaseView.java

## Import Cycles
- None detected.

## Communities (21 total, 3 thin omitted)

### Community 0 - "Design"
Cohesion: 0.08
Nodes (16): Design, Context, Mode, CURRENT, ORIGINAL_DARK, ORIGINAL_LIGHT, Button, Drawable (+8 more)

### Community 1 - "WalletTools"
Cohesion: 0.14
Nodes (6): AddrList, TxnUtil, EditText, LinearLayout, Status, WalletTools

### Community 2 - "NodeTx"
Cohesion: 0.25
Nodes (3): JSONObject, NodeTx, JSONArray

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
Cohesion: 0.06
Nodes (11): Coin, DistributeJob, Context, JSONObject, DistributeManager, Done, JSONObject, Out (+3 more)

### Community 8 - "ImageLoader"
Cohesion: 0.16
Nodes (5): ImageLoader, Bitmap, ImageView, WebValidate, LruCache

### Community 9 - "BaseView"
Cohesion: 0.15
Nodes (8): BaseView, View, Override, View, MainPager, NonNull, PagerAdapter, ViewGroup

### Community 10 - "HistoryDb"
Cohesion: 0.16
Nodes (6): HistoryDb, Context, Override, HistoryRow, SQLiteDatabase, SQLiteOpenHelper

### Community 11 - "Identicon"
Cohesion: 0.41
Nodes (4): Identicon, Bitmap, Canvas, Paint

### Community 12 - ".parse"
Cohesion: 0.27
Nodes (3): IconResolver, TokenMeta, Pattern

### Community 13 - "ReceiveView"
Cohesion: 0.31
Nodes (4): ImageView, Override, TextView, ReceiveView

### Community 14 - "gradlew"
Cohesion: 0.60
Nodes (3): gradlew script, die(), warn()

## Knowledge Gaps
- **3 isolated node(s):** `ORIGINAL_LIGHT`, `ORIGINAL_DARK`, `CURRENT`
  These have ≤1 connection - possible missing edges or undocumented components.
- **3 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `MainActivity` connect `MainActivity` to `Design`, `WalletTools`, `.buildCard`, `SendView`, `Coin`, `ImageLoader`, `BaseView`, `HistoryDb`?**
  _High betweenness centrality (0.390) - this node is a cross-community bridge._
- **Why does `Coin` connect `Coin` to `Design`, `WalletTools`, `SendView`, `MainActivity`?**
  _High betweenness centrality (0.081) - this node is a cross-community bridge._
- **Why does `Design` connect `Design` to `.buildCard`, `MainActivity`?**
  _High betweenness centrality (0.080) - this node is a cross-community bridge._
- **What connects `ORIGINAL_LIGHT`, `ORIGINAL_DARK`, `CURRENT` to the rest of the system?**
  _3 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Design` be split into smaller, more focused modules?**
  _Cohesion score 0.08065268065268065 - nodes in this community are weakly interconnected._
- **Should `WalletTools` be split into smaller, more focused modules?**
  _Cohesion score 0.13548387096774195 - nodes in this community are weakly interconnected._
- **Should `.buildCard` be split into smaller, more focused modules?**
  _Cohesion score 0.14864864864864866 - nodes in this community are weakly interconnected._