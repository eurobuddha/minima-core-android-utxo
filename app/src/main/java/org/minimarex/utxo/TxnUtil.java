package org.minimarex.utxo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Shared helpers for the transaction tools (Send / Split / Distribute). */
public final class TxnUtil {

    private TxnUtil() {}

    public interface AddrList {
        void onAddresses(List<String> addresses);
        void onError(String message);
    }

    /** Insert a pending history row and return its internal id. */
    public static String recordPosting(MainActivity act, String recipient, String amount,
                                       String tokenid, String tokenName) {
        return recordPosting(act, recipient, amount, tokenid, tokenName, null, null, null, null);
    }

    /** Insert a pending history row with full FROM/TO/CHANGE/BURN detail for the History view. */
    public static String recordPosting(MainActivity act, String recipient, String amount,
                                       String tokenid, String tokenName, List<Coin> inputs,
                                       List<TxnBuilder.Out> outputs, String changeaddr, String burn) {
        long ts = System.currentTimeMillis();
        String internalid = "uw_" + ts + "_" + Integer.toHexString(new Random().nextInt(0x1000000));
        HistoryRow r = new HistoryRow();
        r.internalid = internalid;
        r.txnid = internalid;
        r.status = HistoryDb.STATUS_POSTING;
        r.recipient = recipient;
        r.amount = amount;
        r.tokenid = tokenid;
        r.tokenName = tokenName;
        r.ts = ts;
        r.note = "";
        r.inputs = inputsJson(inputs);
        r.outputs = outputsJson(outputs);
        r.changeaddr = changeaddr == null ? "" : changeaddr;
        r.burn = burn == null ? "" : burn;
        act.history().insertPosting(r);
        return internalid;
    }

    private static String inputsJson(List<Coin> inputs) {
        if (inputs == null) return "";
        JSONArray arr = new JSONArray();
        for (Coin c : inputs) {
            try {
                JSONObject o = new JSONObject();
                o.put("coinid", c.coinid);
                o.put("address", c.miniaddress != null && !c.miniaddress.isEmpty() ? c.miniaddress : c.address);
                o.put("amount", c.amount);
                arr.put(o);
            } catch (Exception ignored) {}
        }
        return arr.toString();
    }

    private static String outputsJson(List<TxnBuilder.Out> outputs) {
        if (outputs == null) return "";
        JSONArray arr = new JSONArray();
        for (TxnBuilder.Out o : outputs) {
            try {
                JSONObject j = new JSONObject();
                j.put("address", o.address);
                j.put("amount", o.amount);
                arr.put(j);
            } catch (Exception ignored) {}
        }
        return arr.toString();
    }

    /** Fetch n addresses by calling getaddress sequentially (the node cycles its defaults). */
    public static void fetchAddresses(MainActivity act, int n, AddrList cb) {
        fetchNext(act, n, new ArrayList<>(), cb);
    }

    private static void fetchNext(MainActivity act, int n, List<String> out, AddrList cb) {
        if (out.size() >= n) { cb.onAddresses(out); return; }
        act.node().cmd("getaddress", new NodeApi.Cb() {
            @Override public void onResult(JSONObject json) {
                JSONObject r = json.optJSONObject("response");
                String a = r == null ? "" : r.optString("miniaddress", r.optString("address", ""));
                if (a.isEmpty()) { cb.onError("Could not fetch an address."); return; }
                out.add(a);
                fetchNext(act, n, out, cb);
            }
            @Override public void onError(String message) { cb.onError(message); }
        });
    }
}
