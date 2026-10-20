package com.eurobuddha.utxo;

import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;

/** Explorer's on-demand proof display, with the wallet's strict inclusion parser.
 * Read-only: never changes stored receipts, reservations or transaction outcomes. */
final class HistoryChainCheck {
    static final class Result {
        final JSONObject proof;
        final boolean missing;
        Result(JSONObject proof,boolean missing){this.proof=proof;this.missing=missing;}
    }
    static Result result(JSONObject reply) {
        if(reply==null||!Boolean.TRUE.equals(reply.opt("status"))||Boolean.TRUE.equals(reply.opt("pending")))throw TransactionEvidence.invalid();
        JSONObject response=reply.optJSONObject("response");
        if(response!=null&&Boolean.FALSE.equals(response.opt("found")))return new Result(null,true);
        JSONObject proof=TransactionEvidence.observedInclusion(reply);
        if(!proof.optString("blockid").matches("0x[0-9a-fA-F]{64}"))throw TransactionEvidence.invalid();
        return new Result(proof,false);
    }
    static void show(MainActivity act,LinearLayout parent,String txpowid) {
        TextView status=new TextView(act);
        status.setTextSize(12f);status.setTextColor(Design.dim());
        status.setText("Checking on-chain…");parent.addView(status);
        if(txpowid==null||!txpowid.matches("(?i)0x[0-9a-f]{64}")
                ||new java.math.BigInteger(txpowid.substring(2),16).signum()==0) {
            status.setText("Outcome unverified");return;
        }
        final boolean[] answered={false};
        act.node().cmd("txpow onchain:"+txpowid,new NodeApi.Cb(){
            private boolean accept(){
                if(answered[0]||act.isFinishing()||act.isDestroyed())return false;
                answered[0]=true;return true;
            }
            public void onResult(JSONObject reply){
                if(!accept())return;
                try {
                    Result result=result(reply);
                    CharSequence evidence=result.missing
                            ?"Not found on this node · Outcome unverified"
                            :"✓ on chain · block " + result.proof.optString("block") + " · confirmations: " + result.proof.optString("confirmations");
                    status.setText(evidence + "\nLast node check: " + new java.text.SimpleDateFormat("dd MMM yyyy  HH:mm:ss", java.util.Locale.ENGLISH).format(new java.util.Date()));
                }catch(RuntimeException malformed){status.setText("Outcome unverified");}
            }
            public void onError(String message){
                if(accept())status.setText("Outcome unverified");
            }
        });
    }
}
