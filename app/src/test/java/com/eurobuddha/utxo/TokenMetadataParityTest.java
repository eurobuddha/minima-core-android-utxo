package com.eurobuddha.utxo;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
/** Fixtures reused from Pandamonium UtxoTokenNamesTest / NftTokenNamesTest. */
public class TokenMetadataParityTest {
    private JSONObject name(Object value)throws Exception{return new JSONObject().put("name",value);}
    @Test public void serializedMetadataKeepsNameIconAndOriginalPayload()throws Exception{
        String image="data:image/png;base64,"+String.join("",java.util.Collections.nCopies(32768,"AAAA"));
        JSONObject details=name("Колекція 中文").put("url",image).put("ticker","NFT");
        for(Object token:new Object[]{details.toString(),name(details).toString(),name(details.toString()),name(details.toString()).toString()}){
            String before=token.toString();TokenMeta meta=TokenMeta.parse(token,"0x1234");
            assertEquals("Колекція 中文",meta.name);assertEquals(meta.name,Util.tokenName(token,"0x1234"));
            assertEquals(image,meta.iconUrl);assertEquals("NFT",meta.ticker);assertEquals(before,token.toString());
        }
    }
    @Test public void missingSerializedNamesDoNotDisplayJson()throws Exception{
        for(Object token:new Object[]{"{}",name(new JSONObject()).toString(),name("{}"),name(JSONObject.NULL).toString()}){
            assertEquals("Token",TokenMeta.parse(token,"0x1234").name);assertEquals("Token",Util.tokenName(token,"0x1234"));
        }
        assertEquals("{unfinished",TokenMeta.parse("{unfinished","0x1234").name);
    }
    @Test public void authoredNamesRemainLiteral()throws Exception{
        for(String literal:new String[]{"Token","Send","中文 Русский Українська {amount} $1","[1,2]"})
            for(Object token:new Object[]{literal,name(literal),name(name(literal))}){
                assertEquals(literal,TokenMeta.parse(token,"0x1234").name);assertEquals(literal,Util.tokenName(token,"0x1234"));
            }
    }

}
