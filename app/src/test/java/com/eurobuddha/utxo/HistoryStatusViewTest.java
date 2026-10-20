package com.eurobuddha.utxo;
import android.widget.*;
import org.junit.*;
import org.mockito.*;
import java.lang.reflect.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Actual history row/detail methods with the established widget doubles. */
public class HistoryStatusViewTest {
    HistoryPagingTest f;
    @Before public void setup()throws Exception{f=new HistoryPagingTest();f.setup();}
    @After public void close(){f.close();}
    java.util.List<String> texts() {
        java.util.List<String> out=new java.util.ArrayList<>();
        for(TextView label:f.labels.constructed())for(org.mockito.invocation.Invocation call:mockingDetails(label).getInvocations())
            if(call.getMethod().getName().equals("setText") && call.getArguments().length==1)out.add(String.valueOf((Object)call.getArgument(0)));
        return out;
    }
    NodeTx tx(){NodeTx n=new NodeTx();n.txpowid="0x"+"a".repeat(64);n.block=100;n.direction="received";n.incoming=true;n.tokenid="0x00";n.tokenName="Minima";n.amount="1";n.counterparty="";n.deltas="{}";n.inputs=n.outputs="[]";return n;}
    void call(String name,Class<?> type,Object arg)throws Exception{Method m=HistoryView.class.getDeclaredMethod(name,type);m.setAccessible(true);m.invoke(f.view,arg);}
    @Test public void cachedRowsAreExplicitlyLabelledSaved()throws Exception {
        call("row",NodeTx.class,tx());assertTrue(texts().contains("Saved\n#100"));
    }
    @Test public void openingACachedRowRequestsItsCurrentInclusion()throws Exception {
        try(MockedStatic<HistoryChainCheck> checks=mockStatic(HistoryChainCheck.class);MockedConstruction<ScrollView> scroll=mockConstruction(ScrollView.class)){
            androidx.appcompat.app.AlertDialog.Builder dialog=mock(androidx.appcompat.app.AlertDialog.Builder.class,RETURNS_SELF);f.design.when(()->Design.dialog(f.act)).thenReturn(dialog);
            NodeTx n=tx();call("showDetail",NodeTx.class,n);
            checks.verify(()->HistoryChainCheck.show(eq(f.act),any(LinearLayout.class),eq(n.txpowid)));
            assertTrue(texts().contains("Recorded block:  100"));
        }
    }
    @Test public void localReceiptDetailsAlsoRequestFreshEvidence()throws Exception {
        try(MockedStatic<HistoryChainCheck> checks=mockStatic(HistoryChainCheck.class);MockedConstruction<ScrollView> scroll=mockConstruction(ScrollView.class)){
            androidx.appcompat.app.AlertDialog.Builder dialog=mock(androidx.appcompat.app.AlertDialog.Builder.class,RETURNS_SELF);f.design.when(()->Design.dialog(f.act)).thenReturn(dialog);
            HistoryRow r=new HistoryRow();r.internalid="local";r.txnid=tx().txpowid;r.status=HistoryDb.STATUS_CONFIRMED;r.tokenid="0x00";r.tokenName="Minima";r.amount="1";r.inputs=r.outputs="[]";r.recipient="";
            call("showLocalDetail",HistoryRow.class,r);
            checks.verify(()->HistoryChainCheck.show(eq(f.act),any(LinearLayout.class),eq(r.txnid)));
            verify(f.act,never()).node();
        }
    }
}
