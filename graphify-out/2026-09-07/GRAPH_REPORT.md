# Graph Report - utxo  (2026-09-07)

## Corpus Check
- 37 files · ~32,152 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 534 nodes · 1336 edges · 34 communities (22 shown, 12 thin omitted)
- Extraction: 92% EXTRACTED · 8% INFERRED · 0% AMBIGUOUS · INFERRED: 103 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `ee49c46c`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Design
- DistributeManager
- org.json.JSONObject
- HistoryView
- .buildCard
- SendView
- MainActivity
- WalletTools
- ImageLoader
- BaseView
- HistoryDb
- utxoWallet — Native Android clone: figma-style mapping & build blueprint
- Override
- User instructions — AUTHORITATIVE. These override default behavior and must be followed exactly.
- gradlew
- install.sh
- Minima UTXO Wallet (native Android)
- pre-commit
- JSONObject
- .recordPosting
- EditText
- HistoryRow
- .parse
- HistoryRow.java
- NodeTx
- Override
- CoinLoader
- WebValidate
- Out
- MainActivity
- LinearLayout

## God Nodes (most connected - your core abstractions)
1. `MainActivity` - 66 edges
2. `Design` - 32 edges
3. `HistoryView` - 28 edges
4. `SendView` - 26 edges
5. `Cb` - 23 edges
6. `BalancesView` - 23 edges
7. `WalletTools` - 21 edges
8. `DistributeManager` - 21 edges
9. `HistoryDb` - 20 edges
10. `WalletView` - 20 edges

## Surprising Connections (you probably didn't know these)
- `BaseView` --references--> `MainActivity`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/utxo/BaseView.java → app/src/main/java/com/eurobuddha/utxo/MainActivity.java
- `DistributeManager` --references--> `MainActivity`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/utxo/DistributeManager.java → app/src/main/java/com/eurobuddha/utxo/MainActivity.java
- `TxnBuilder` --references--> `MainActivity`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/utxo/TxnBuilder.java → app/src/main/java/com/eurobuddha/utxo/MainActivity.java
- `WalletView` --references--> `WalletTools`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/utxo/WalletView.java → app/src/main/java/com/eurobuddha/utxo/WalletTools.java
- `BalancesView` --inherits--> `BaseView`  [EXTRACTED]
  app/src/main/java/com/eurobuddha/utxo/BalancesView.java → app/src/main/java/com/eurobuddha/utxo/BaseView.java

## Import Cycles
- None detected.

## Communities (34 total, 12 thin omitted)

### Community 0 - "Design"
Cohesion: 0.10
Nodes (14): android.graphics.drawable.Drawable, android.graphics.drawable.GradientDrawable, android.graphics.Typeface, android.view.View, Design, Mode, CURRENT, ORIGINAL_DARK (+6 more)

### Community 1 - "DistributeManager"
Cohesion: 0.13
Nodes (5): Coin, DistributeJob, JSONObject, DistributeManager, HistoryDb

### Community 2 - "org.json.JSONObject"
Cohesion: 0.10
Nodes (7): Done, Out, OutCoin, Progress, TxnBuilder, org.json.JSONArray, org.json.JSONObject

### Community 3 - "HistoryView"
Cohesion: 0.10
Nodes (10): android.widget.LinearLayout, HistoryView, MainActivity, NodeTx, Util, HistoryRow, JSONArray, LinearLayout (+2 more)

### Community 4 - ".buildCard"
Cohesion: 0.20
Nodes (5): BalancesView, LinearLayout, Override, TextView, TokenBalance

### Community 5 - "SendView"
Cohesion: 0.16
Nodes (8): android.widget.Button, android.widget.EditText, android.widget.TextView, LinearLayout, Override, TextView, SendView, EditText

### Community 6 - "MainActivity"
Cohesion: 0.05
Nodes (18): android.content.BroadcastReceiver, android.content.Context, android.os.Bundle, android.os.Handler, androidx.appcompat.app.AppCompatActivity, androidx.viewpager.widget.ViewPager, MainActivity, Cb (+10 more)

### Community 7 - "WalletTools"
Cohesion: 0.11
Nodes (9): Coin, Context, LinearLayout, MainActivity, Out, Status, WalletTools, Builder (+1 more)

### Community 8 - "ImageLoader"
Cohesion: 0.11
Nodes (11): android.graphics.Bitmap, android.graphics.Canvas, android.graphics.Paint, android.util.LruCache, android.widget.ImageView, Identicon, ImageLoader, Override (+3 more)

### Community 9 - "BaseView"
Cohesion: 0.14
Nodes (7): android.view.ViewGroup, androidx.annotation.NonNull, androidx.viewpager.widget.PagerAdapter, BaseView, Override, MainPager, SuppressWarnings

### Community 10 - "HistoryDb"
Cohesion: 0.17
Nodes (6): android.database.sqlite.SQLiteDatabase, android.database.sqlite.SQLiteOpenHelper, HistoryDb, HistoryRow, NodeTx, Override

### Community 11 - "utxoWallet — Native Android clone: figma-style mapping & build blueprint"
Cohesion: 0.08
Nodes (24): 0. Design languages (runtime toggle), 1.1 ORIGINAL — light (`:root`, default), 1.2 ORIGINAL — dark (`:root[data-theme="dark"]`), 1.3 CURRENT (existing native dark), 1.4 Type & metrics (ORIGINAL), 1.5 Component note colors (`.field-note`, `.toast`, pills), 1. Design tokens, 2. Component catalog (ORIGINAL; CURRENT = Material equivalents) (+16 more)

### Community 13 - "User instructions — AUTHORITATIVE. These override default behavior and must be followed exactly."
Cohesion: 0.50
Nodes (3): RULE 0 (highest priority) — Follow the user's explicit instructions. They are BLOCKING, not suggestions., User instructions — AUTHORITATIVE. These override default behavior and must be followed exactly., Versioning guardrail — every code change ships with a version bump

### Community 14 - "gradlew"
Cohesion: 0.60
Nodes (3): gradlew script, die(), warn()

### Community 19 - "Minima UTXO Wallet (native Android)"
Cohesion: 0.40
Nodes (4): Build, Features, Minima UTXO Wallet (native Android), Releases

### Community 22 - ".recordPosting"
Cohesion: 0.21
Nodes (6): AddrList, Coin, MainActivity, Out, TxnUtil, JSONObject

### Community 25 - ".parse"
Cohesion: 0.24
Nodes (3): IconResolver, TokenMeta, java.util.regex.Pattern

### Community 29 - "CoinLoader"
Cohesion: 0.19
Nodes (6): CoinLoader, Done, Fail, MainActivity, Ok, Coin

### Community 30 - "WebValidate"
Cohesion: 0.21
Nodes (3): NetFetch, WebValidate, java.net.InetAddress

## Knowledge Gaps
- **30 isolated node(s):** `install.sh script`, `ORIGINAL_LIGHT`, `ORIGINAL_DARK`, `CURRENT`, `HistoryRow` (+25 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **12 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `MainActivity` connect `MainActivity` to `Design`, `DistributeManager`, `org.json.JSONObject`, `.buildCard`, `SendView`, `WalletTools`, `ImageLoader`, `BaseView`, `CoinLoader`, `WebValidate`?**
  _High betweenness centrality (0.251) - this node is a cross-community bridge._
- **Why does `HistoryDb` connect `HistoryDb` to `MainActivity`?**
  _High betweenness centrality (0.062) - this node is a cross-community bridge._
- **Why does `Cb` connect `MainActivity` to `DistributeManager`, `org.json.JSONObject`, `HistoryView`, `SendView`, `WalletTools`, `ImageLoader`, `.recordPosting`, `CoinLoader`?**
  _High betweenness centrality (0.051) - this node is a cross-community bridge._
- **What connects `install.sh script`, `ORIGINAL_LIGHT`, `ORIGINAL_DARK` to the rest of the system?**
  _30 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Design` be split into smaller, more focused modules?**
  _Cohesion score 0.09899749373433583 - nodes in this community are weakly interconnected._
- **Should `DistributeManager` be split into smaller, more focused modules?**
  _Cohesion score 0.13118279569892474 - nodes in this community are weakly interconnected._
- **Should `org.json.JSONObject` be split into smaller, more focused modules?**
  _Cohesion score 0.10338680926916222 - nodes in this community are weakly interconnected._