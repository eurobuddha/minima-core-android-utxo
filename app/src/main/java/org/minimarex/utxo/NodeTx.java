package org.minimarex.utxo;

/** A persisted on-chain history entry (from the node's `history`), keyed by txpowid. Lets the History
 *  tab accumulate + survive restarts, matching the standalone Minima History app. */
public class NodeTx {
    public String txpowid;
    public int block;
    public long time;
    public String direction;     // in | out | self
    public String net;           // signed display amount, e.g. "+1.5" / "−0.001"
    public String token;         // primary token label
    public String counterparty;
    public String inputs;        // JSON array string
    public String outputs;       // JSON array string
    public int multiMore;        // extra tokens moved beyond the primary
}
