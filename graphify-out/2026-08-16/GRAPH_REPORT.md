# Graph Report - utxo  (2026-08-13)

## Corpus Check
- 33 files · ~27,763 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 455 nodes · 1170 edges · 21 communities (19 shown, 2 thin omitted)
- Extraction: 91% EXTRACTED · 9% INFERRED · 0% AMBIGUOUS · INFERRED: 102 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `91d989b5`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Design
- Coin
- Cb
- HistoryView
- .buildCard
- SendView
- MainActivity
- DistributeManager
- ImageLoader
- android.view.View
- HistoryDb
- utxoWallet — Native Android clone: figma-style mapping & build blueprint
- .parse
- User instructions — AUTHORITATIVE. These override default behavior and must be followed exactly.
- gradlew
- WebValidate
- Minima UTXO Wallet (native Android)
- org.json.JSONObject

## God Nodes (most connected - your core abstractions)
1. `MainActivity` - 68 edges
2. `Design` - 36 edges
3. `Coin` - 26 edges
4. `SendView` - 25 edges
5. `BalancesView` - 23 edges
6. `DistributeManager` - 23 edges
7. `HistoryView` - 22 edges
8. `Cb` - 20 edges
9. `WalletTools` - 20 edges
10. `WalletView` - 20 edges

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

## Communities (21 total, 2 thin omitted)

### Community 0 - "Design"
Cohesion: 0.10
Nodes (9): android.graphics.drawable.Drawable, android.graphics.Typeface, Design, LinearLayout, Override, TextView, WalletView, GradientDrawable (+1 more)

### Community 1 - "Coin"
Cohesion: 0.09
Nodes (10): Coin, Done, Out, AddrList, TxnUtil, EditText, LinearLayout, Status (+2 more)

### Community 2 - "Cb"
Cohesion: 0.15
Nodes (4): Cb, OutCoin, Progress, TxnBuilder

### Community 3 - "HistoryView"
Cohesion: 0.21
Nodes (4): android.widget.LinearLayout, HistoryView, LinearLayout, Override

### Community 4 - ".buildCard"
Cohesion: 0.21
Nodes (5): BalancesView, LinearLayout, Override, TextView, TokenBalance

### Community 5 - "SendView"
Cohesion: 0.11
Nodes (9): android.graphics.drawable.GradientDrawable, android.widget.Button, android.widget.EditText, android.widget.TextView, LinearLayout, Override, TextView, SendView (+1 more)

### Community 6 - "MainActivity"
Cohesion: 0.06
Nodes (17): android.content.BroadcastReceiver, android.content.Context, android.os.Bundle, android.os.Handler, androidx.appcompat.app.AppCompatActivity, androidx.viewpager.widget.ViewPager, Mode, CURRENT (+9 more)

### Community 7 - "DistributeManager"
Cohesion: 0.13
Nodes (3): DistributeJob, JSONObject, DistributeManager

### Community 8 - "ImageLoader"
Cohesion: 0.10
Nodes (11): android.graphics.Bitmap, android.graphics.Canvas, android.graphics.Paint, android.util.LruCache, android.widget.ImageView, Identicon, ImageLoader, Override (+3 more)

### Community 9 - "android.view.View"
Cohesion: 0.16
Nodes (7): android.view.View, android.view.ViewGroup, androidx.annotation.NonNull, androidx.viewpager.widget.PagerAdapter, BaseView, Override, MainPager

### Community 10 - "HistoryDb"
Cohesion: 0.16
Nodes (5): android.database.sqlite.SQLiteDatabase, android.database.sqlite.SQLiteOpenHelper, HistoryDb, Override, HistoryRow

### Community 11 - "utxoWallet — Native Android clone: figma-style mapping & build blueprint"
Cohesion: 0.08
Nodes (24): 0. Design languages (runtime toggle), 1.1 ORIGINAL — light (`:root`, default), 1.2 ORIGINAL — dark (`:root[data-theme="dark"]`), 1.3 CURRENT (existing native dark), 1.4 Type & metrics (ORIGINAL), 1.5 Component note colors (`.field-note`, `.toast`, pills), 1. Design tokens, 2. Component catalog (ORIGINAL; CURRENT = Material equivalents) (+16 more)

### Community 12 - ".parse"
Cohesion: 0.24
Nodes (3): IconResolver, TokenMeta, java.util.regex.Pattern

### Community 14 - "gradlew"
Cohesion: 0.60
Nodes (3): gradlew script, die(), warn()

### Community 19 - "Minima UTXO Wallet (native Android)"
Cohesion: 0.40
Nodes (4): Build, Features, Minima UTXO Wallet (native Android), Releases

### Community 21 - "org.json.JSONObject"
Cohesion: 0.18
Nodes (4): JSONObject, NodeTx, org.json.JSONArray, org.json.JSONObject

## Knowledge Gaps
- **27 isolated node(s):** `ORIGINAL_LIGHT`, `ORIGINAL_DARK`, `CURRENT`, `RULE 0 (highest priority) — Follow the user's explicit instructions. They are BLOCKING, not suggestions.`, `0. Design languages (runtime toggle)` (+22 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **2 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `MainActivity` connect `MainActivity` to `Design`, `Coin`, `Cb`, `HistoryView`, `.buildCard`, `SendView`, `DistributeManager`, `ImageLoader`, `android.view.View`, `HistoryDb`, `WebValidate`?**
  _High betweenness centrality (0.317) - this node is a cross-community bridge._
- **Why does `Design` connect `Design` to `.buildCard`, `MainActivity`?**
  _High betweenness centrality (0.067) - this node is a cross-community bridge._
- **Why does `Coin` connect `Coin` to `Design`, `Cb`, `SendView`, `MainActivity`, `DistributeManager`, `org.json.JSONObject`?**
  _High betweenness centrality (0.057) - this node is a cross-community bridge._
- **What connects `ORIGINAL_LIGHT`, `ORIGINAL_DARK`, `CURRENT` to the rest of the system?**
  _27 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Design` be split into smaller, more focused modules?**
  _Cohesion score 0.09805194805194806 - nodes in this community are weakly interconnected._
- **Should `Coin` be split into smaller, more focused modules?**
  _Cohesion score 0.09343200740055504 - nodes in this community are weakly interconnected._
- **Should `Cb` be split into smaller, more focused modules?**
  _Cohesion score 0.14814814814814814 - nodes in this community are weakly interconnected._