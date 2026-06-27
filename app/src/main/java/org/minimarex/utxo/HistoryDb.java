package org.minimarex.utxo;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Local transaction history. The web utxoWallet kept this in MDS.sql; the native IPC API only runs
 * node commands, so we keep our own SQLite table. Stores the full FROM/TO/CHANGE/BURN detail so the
 * History tab can show an expandable breakdown like the dapp.
 */
public class HistoryDb extends SQLiteOpenHelper {

    public static final String STATUS_POSTING   = "posting";
    public static final String STATUS_POSTED    = "posted";    // broadcast, awaiting confirmation
    public static final String STATUS_CONFIRMED = "confirmed"; // real on-chain txpowid resolved
    public static final String STATUS_ERROR     = "error";

    private static final String DB_NAME = "utxo_history.db";
    private static final int    DB_VERSION = 3;     // v3: + nodetx (persisted on-chain history)
    private static final String TABLE = "history";

    public HistoryDb(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    /** Fresh install: create the full v2 schema. internalid is the app-side key; txnid holds the
     *  on-chain txpowid once resolved. inputs/outputs are JSON arrays so the breakdown can be rebuilt. */
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "internalid TEXT UNIQUE," +
                "txnid TEXT," +
                "status TEXT," +
                "recipient TEXT," +
                "amount TEXT," +
                "tokenid TEXT," +
                "tokenname TEXT," +
                "ts INTEGER," +
                "note TEXT," +
                "inputs TEXT," +        // JSON array [{coinid,address,amount}]
                "outputs TEXT," +       // JSON array [{address,amount}]
                "changeaddr TEXT," +
                "burn TEXT)");
        // Persisted on-chain history (from the node's `history`), keyed by txpowid — accumulates + survives
        // restarts, like the standalone Minima History app.
        db.execSQL("CREATE TABLE IF NOT EXISTS nodetx (" +
                "txpowid TEXT PRIMARY KEY, block INTEGER, time INTEGER," +
                "direction TEXT, net TEXT, token TEXT, counterparty TEXT," +
                "inputs TEXT, outputs TEXT, multimore INTEGER)");
    }

    /** Non-destructive migration: ensure the table exists, then ALTER in the v2 columns. Each ALTER
     *  is wrapped so a "column already exists" error on a partially-migrated db is ignored. */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // Preserve history; add new columns. Never drop the table.
        onCreate(db);
        for (String col : new String[]{"inputs TEXT", "outputs TEXT", "changeaddr TEXT", "burn TEXT"}) {
            try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + col); } catch (Exception ignored) {}
        }
    }

    /** Insert (or replace on internalid conflict) a history row — used for new pending postings. */
    public void insertPosting(HistoryRow r) {
        ContentValues v = new ContentValues();
        v.put("internalid", r.internalid);
        v.put("txnid", r.txnid);
        v.put("status", r.status);
        v.put("recipient", r.recipient);
        v.put("amount", r.amount);
        v.put("tokenid", r.tokenid);
        v.put("tokenname", r.tokenName);
        v.put("ts", r.ts);
        v.put("note", r.note);
        v.put("inputs", r.inputs);
        v.put("outputs", r.outputs);
        v.put("changeaddr", r.changeaddr);
        v.put("burn", r.burn);
        getWritableDatabase().insertWithOnConflict(TABLE, null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Patch status (and optionally txnid/note) of an existing row keyed by internalid. */
    public void update(String internalid, String status, String txnid, String note) {
        ContentValues v = new ContentValues();
        v.put("status", status);
        if (txnid != null) v.put("txnid", txnid);
        if (note != null) v.put("note", note);
        getWritableDatabase().update(TABLE, v, "internalid=?", new String[]{internalid});
    }

    /** Remove a row (e.g. user dismisses a failed send). */
    public void delete(String internalid) {
        getWritableDatabase().delete(TABLE, "internalid=?", new String[]{internalid});
    }

    /** Return the most recent rows (newest id first), capped at limit. */
    public List<HistoryRow> list(int limit) {
        List<HistoryRow> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,internalid,txnid,status,recipient,amount,tokenid,tokenname,ts,note," +
                        "inputs,outputs,changeaddr,burn " +
                        "FROM " + TABLE + " ORDER BY id DESC LIMIT " + limit, null);
        try {
            while (c.moveToNext()) {
                HistoryRow r = new HistoryRow();
                r.id         = c.getLong(0);
                r.internalid = c.getString(1);
                r.txnid      = c.getString(2);
                r.status     = c.getString(3);
                r.recipient  = c.getString(4);
                r.amount     = c.getString(5);
                r.tokenid    = c.getString(6);
                r.tokenName  = c.getString(7);
                r.ts         = c.getLong(8);
                r.note       = c.getString(9);
                r.inputs     = c.getString(10);
                r.outputs    = c.getString(11);
                r.changeaddr = c.getString(12);
                r.burn       = c.getString(13);
                out.add(r);
            }
        } finally {
            c.close();
        }
        return out;
    }

    // ----- persisted on-chain history (nodetx) -----

    /** Insert a node history row; returns true if NEW (false if this txpowid was already stored). */
    public boolean upsertNodeTx(NodeTx n) {
        ContentValues v = new ContentValues();
        v.put("txpowid", n.txpowid); v.put("block", n.block); v.put("time", n.time);
        v.put("direction", n.direction); v.put("net", n.net); v.put("token", n.token);
        v.put("counterparty", n.counterparty); v.put("inputs", n.inputs); v.put("outputs", n.outputs);
        v.put("multimore", n.multiMore);
        long rid = getWritableDatabase().insertWithOnConflict("nodetx", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        return rid != -1;
    }

    /** Newest-first persisted on-chain history, capped at limit. */
    public List<NodeTx> loadNodeTx(int limit) {
        List<NodeTx> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT txpowid,block,time,direction,net,token,counterparty,inputs,outputs,multimore FROM nodetx " +
                        "ORDER BY block DESC, time DESC LIMIT " + limit, null);
        try {
            while (c.moveToNext()) {
                NodeTx n = new NodeTx();
                n.txpowid = c.getString(0); n.block = c.getInt(1); n.time = c.getLong(2);
                n.direction = c.getString(3); n.net = c.getString(4); n.token = c.getString(5);
                n.counterparty = c.getString(6); n.inputs = c.getString(7); n.outputs = c.getString(8);
                n.multiMore = c.getInt(9);
                out.add(n);
            }
        } finally { c.close(); }
        return out;
    }

    public int nodeTxCount() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM nodetx", null);
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }
}
