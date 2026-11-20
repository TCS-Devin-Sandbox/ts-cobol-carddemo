package com.carddemo.interest.codec;

import com.carddemo.interest.codec.FixedRecordCodec.*;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FixedRecordCodecTest {
    static final Path FIXTURES=Path.of("../../..").toAbsolutePath().normalize().resolve("fixtures/interest");
    static final Charset CP037=Charset.forName("IBM037");
    static final Map<Layout,String> FILES=Map.of(
            Layout.ACCOUNT,"ACCTDATA",Layout.XREF,"CARDXREF",Layout.TCATBAL,"TCATBALF",
            Layout.DISCGRP,"DISCGRP",Layout.TRAN,"SYSTRAN");
    static byte[] ascii(String value) { return value.getBytes(StandardCharsets.US_ASCII); }

    // Committed harness text omits trailing storage spaces. The test framing adapter,
    // not the fixed-record codec, expands that documented fixture representation.
    static List<byte[]> records(String fixture,Layout layout) throws Exception {
        Path dir=FIXTURES.resolve(fixture).resolve(layout==Layout.TRAN?"expected/datasets":"input");
        Path file;
        try(var paths=Files.list(dir)) {
            file=paths.filter(p->p.getFileName().toString().contains("."+FILES.get(layout)+".")).findFirst().orElseThrow();
        }
        return Files.readAllLines(file,StandardCharsets.US_ASCII).stream().map(line->{
            assertTrue(line.length()<=layout.length());
            return ascii(line+" ".repeat(layout.length()-line.length()));
        }).toList();
    }
    static byte[] first(Layout l) throws Exception { return records("i01-baseline",l).getFirst(); }
    static Decoded decoded(Layout l) throws Exception { return FixedRecordCodec.decode(l,Profile.LAB_ASCII,first(l)); }
    static void reject(Layout l,String row,int offset,String reason,Runnable action) {
        Rejection e=assertThrows(Rejection.class,action::run);
        assertEquals(l,e.layout()); assertEquals(row,e.row());
        assertEquals(offset,e.offset()); assertEquals(reason,e.reason());
    }

    @Test void TN_06_INT_Q03_i01_i06_allFiveLayoutsRoundTripBothProfiles() throws Exception {
        int count=0;
        for(String fixture:List.of("i01-baseline","i06-negative-balance")) {
            for(Layout l:Layout.values()) {
                for(byte[] raw:records(fixture,l)) {
                    Decoded d=FixedRecordCodec.decode(l,Profile.LAB_ASCII,raw);
                    assertArrayEquals(raw,FixedRecordCodec.encode(d));
                    assertArrayEquals(raw,FixedRecordCodec.encode(l,Profile.LAB_ASCII,d.fields()));
                    byte[] ebcdic=new String(raw,StandardCharsets.US_ASCII).getBytes(CP037);
                    Decoded e=FixedRecordCodec.decode(l,Profile.SYNTHETIC_CP037,ebcdic);
                    assertArrayEquals(ebcdic,FixedRecordCodec.encode(e));
                    assertArrayEquals(ebcdic,FixedRecordCodec.encode(l,Profile.SYNTHETIC_CP037,e.fields()));
                    List<Object> av=d.fields().values(), ev=e.fields().values();
                    assertEquals(av.subList(0,av.size()-1),ev.subList(0,ev.size()-1));
                    count++;
                }
            }
        }
        assertTrue(count>30);
    }

    @Test void INT_Q03_T06_negativeAmountAndLeadingZeroFieldsIndependent() throws Exception {
        for(Profile p:Profile.values()) {
            byte[] raw=records("i06-negative-balance",Layout.TRAN).getFirst();
            if(p==Profile.SYNTHETIC_CP037) raw=new String(raw,StandardCharsets.US_ASCII).getBytes(CP037);
            Decoded d=FixedRecordCodec.decode(Layout.TRAN,p,raw);
            InterestTransaction tx=(InterestTransaction)d.fields();
            assertEquals(new BigDecimal("-3.00"),tx.amount());
            assertEquals("2022071800000001",tx.txnId());
            assertEquals("0005",tx.categoryCode());
            assertEquals("000000000",tx.merchantId());
            assertEquals("4000000000002008",tx.cardNumber());
            assertEquals(" ".repeat(50),tx.merchantName());
            assertArrayEquals(raw,FixedRecordCodec.encode(d));
        }
        CategoryBalance balance=(CategoryBalance)FixedRecordCodec.decode(Layout.TCATBAL,Profile.LAB_ASCII,
                records("i06-negative-balance",Layout.TCATBAL).getFirst()).fields();
        assertEquals(new BigDecimal("-300.00"),balance.balance());
        Account account=(Account)decoded(Layout.ACCOUNT).fields();
        assertEquals("00000002001",account.acctId());
        assertEquals(new BigDecimal("1000.00"),account.currentBalance());
    }

    @Test void INT_R08_INT_Q05_TN_02_opaqueDatesIdsAndTimestamps() throws Exception {
        Decoded d=decoded(Layout.TRAN);
        InterestTransaction tx=(InterestTransaction)d.fields();
        assertEquals("2022071800000001",tx.txnId());
        assertEquals("System    ",tx.source());
        assertEquals("Int. for a/c 00000002001"+" ".repeat(76),tx.description());
        assertEquals("2022-07-05-10.00.00.000000",tx.origTs());
        assertEquals(tx.origTs(),tx.procTs());
        byte[] patched=FixedRecordCodec.encode(decoded(Layout.ACCOUNT),Map.of("openDate","not-a-date"));
        assertEquals("not-a-date",((Account)FixedRecordCodec.decode(Layout.ACCOUNT,Profile.LAB_ASCII,patched).fields()).openDate());
    }

    @Test void INT_R09_INT_H03_encodedKeysAndUnsignedLocaleOrder() throws Exception {
        for(Profile p:Profile.values()) {
            byte[] raw=first(Layout.TCATBAL);
            if(p==Profile.SYNTHETIC_CP037) raw=new String(raw,StandardCharsets.US_ASCII).getBytes(CP037);
            Decoded d=FixedRecordCodec.decode(Layout.TCATBAL,p,raw);
            byte[] a=FixedRecordCodec.encode(d,Map.of("txnType","A1"));
            byte[] zero=FixedRecordCodec.encode(d,Map.of("txnType","01"));
            byte[] ka=FixedRecordCodec.decode(Layout.TCATBAL,p,a).legacyKey();
            byte[] kz=FixedRecordCodec.decode(Layout.TCATBAL,p,zero).legacyKey();
            assertArrayEquals(("00000002001A10001").getBytes(p==Profile.LAB_ASCII?StandardCharsets.US_ASCII:CP037),ka);
            assertTrue(p==Profile.LAB_ASCII?FixedRecordCodec.compareKeys(kz,ka)<0:FixedRecordCodec.compareKeys(ka,kz)<0);
        }
        assertTrue(FixedRecordCodec.compareKeys(new byte[]{0x7f},new byte[]{(byte)0x80})<0);
        assertArrayEquals(ascii("4000000000002001"),decoded(Layout.XREF).legacyKey());
        assertArrayEquals(ascii("00000002001"),decoded(Layout.XREF).alternateKey());
        byte[] rate=FixedRecordCodec.encode(decoded(Layout.DISCGRP),Map.of("groupId","DEFAULT   "));
        assertArrayEquals(ascii("DEFAULT   010001"),FixedRecordCodec.decode(Layout.DISCGRP,Profile.LAB_ASCII,rate).legacyKey());
    }

    @Test void TN_06_signedZeroAndImmutableOriginalFiller() throws Exception {
        for(Profile p:Profile.values()) {
            byte[] raw=first(Layout.TCATBAL);
            if(p==Profile.SYNTHETIC_CP037) raw=new String(raw,StandardCharsets.US_ASCII).getBytes(CP037);
            Arrays.fill(raw,17,27,p==Profile.LAB_ASCII?(byte)'0':(byte)0xf0);
            raw[27]=p==Profile.LAB_ASCII?(byte)'}':(byte)0xd0;
            raw[28]=(byte)0xff; raw[49]=0x01;
            byte[] expected=raw.clone();
            Decoded d=FixedRecordCodec.decode(Layout.TCATBAL,p,raw);
            assertEquals(new BigDecimal("0.00"),((CategoryBalance)d.fields()).balance());
            raw[28]=0;
            d.original()[29]=0;
            ((CategoryBalance)d.fields()).filler().array()[30-28]=0;
            d.legacyKey()[0]=0;
            assertArrayEquals(expected,FixedRecordCodec.encode(d));
            byte[] changed=FixedRecordCodec.encode(d,Map.of("txnType","A1"));
            assertEquals(expected[27],changed[27]);
            assertArrayEquals(Arrays.copyOfRange(expected,28,50),Arrays.copyOfRange(changed,28,50));
        }
    }

    @Test void INT_R06_TN_06_accountPatchOnlyChangesThreeReceivers() throws Exception {
        for(Profile p:Profile.values()) {
            byte[] original=first(Layout.ACCOUNT);
            if(p==Profile.SYNTHETIC_CP037) original=new String(original,StandardCharsets.US_ASCII).getBytes(CP037);
            for(int i=122;i<300;i++) original[i]=(byte)i;
            byte[] expected=original.clone();
            Decoded d=FixedRecordCodec.decode(Layout.ACCOUNT,p,original);
            byte[] patched=FixedRecordCodec.encode(d,Map.of("currentBalance",new BigDecimal("1010.00"),
                    "cycleCredit",new BigDecimal("0.00"),"cycleDebit",new BigDecimal("0.00")));
            Account after=(Account)FixedRecordCodec.decode(Layout.ACCOUNT,p,patched).fields();
            assertEquals(new BigDecimal("1010.00"),after.currentBalance());
            assertEquals(new BigDecimal("0.00"),after.cycleCredit());
            assertEquals(new BigDecimal("0.00"),after.cycleDebit());
            for(int i=0;i<300;i++) if(!(i>=12&&i<24) && !(i>=78&&i<102))
                assertEquals(expected[i],patched[i],"unchanged offset "+i);
            assertArrayEquals(expected,d.original());
        }
    }

    @Test void TN_01_TN_06_exactFramingEmptyAndWrongPaddingRejections() throws Exception {
        for(Layout l:Layout.values()) {
            byte[] raw=first(l);
            assertEquals(l==Layout.ACCOUNT?300:l==Layout.TRAN?350:50,raw.length);
            reject(l,"RECORD",0,"LENGTH",()->FixedRecordCodec.decode(l,Profile.LAB_ASCII,new byte[0]));
            reject(l,"RECORD",l.length()-1,"LENGTH",()->FixedRecordCodec.decode(l,Profile.LAB_ASCII,Arrays.copyOf(raw,raw.length-1)));
            reject(l,"RECORD",l.length(),"LENGTH",()->FixedRecordCodec.decode(l,Profile.LAB_ASCII,Arrays.copyOf(raw,raw.length+1)));
        }
        reject(Layout.DISCGRP,"D01",0,"WIDTH",()->FixedRecordCodec.encode(decodedUnchecked(Layout.DISCGRP),Map.of("groupId","DEFAULT")));
        reject(Layout.DISCGRP,"D01",0,"WIDTH",()->FixedRecordCodec.encode(decodedUnchecked(Layout.DISCGRP),Map.of("groupId","DEFAULT    ")));
    }
    static Decoded decodedUnchecked(Layout l) {
        try { return decoded(l); } catch(Exception e) { throw new AssertionError(e); }
    }

    @Test void INT_R10_badDigitsOverpunchCodePageAndUnmappableStructured() throws Exception {
        for(Profile p:Profile.values()) {
            byte[] raw=first(Layout.TCATBAL);
            if(p==Profile.SYNTHETIC_CP037) raw=new String(raw,StandardCharsets.US_ASCII).getBytes(CP037);
            byte[] bad=raw.clone(); bad[27]=p==Profile.LAB_ASCII?(byte)'0':(byte)0xf0;
            reject(Layout.TCATBAL,"B04",27,"OVERPUNCH",()->FixedRecordCodec.decode(Layout.TCATBAL,p,bad));
            byte[] digit=raw.clone(); digit[2]=p==Profile.LAB_ASCII?(byte)'A':(byte)0xc1;
            reject(Layout.TCATBAL,"B01",2,"DIGITS",()->FixedRecordCodec.decode(Layout.TCATBAL,p,digit));
            byte[] money=raw.clone(); money[18]=p==Profile.LAB_ASCII?(byte)' ':(byte)0x40;
            reject(Layout.TCATBAL,"B04",18,"DIGITS",()->FixedRecordCodec.decode(Layout.TCATBAL,p,money));
        }
        reject(Layout.ACCOUNT,"RECORD",0,"INVALID_PROFILE",()->FixedRecordCodec.decode(Layout.ACCOUNT,"UTF-8",new byte[300]));
        byte[] unmappable=first(Layout.ACCOUNT); unmappable[11]=(byte)0xff;
        reject(Layout.ACCOUNT,"A02",11,"UNMAPPABLE",()->FixedRecordCodec.decode(Layout.ACCOUNT,Profile.LAB_ASCII,unmappable));
        for(Profile p:Profile.values()) {
            byte[] raw=first(Layout.ACCOUNT);
            if(p==Profile.SYNTHETIC_CP037) raw=new String(raw,StandardCharsets.US_ASCII).getBytes(CP037);
            Decoded d=FixedRecordCodec.decode(Layout.ACCOUNT,p,raw);
            reject(Layout.ACCOUNT,"A12",112,"UNMAPPABLE",()->FixedRecordCodec.encode(d,Map.of("groupId","☃"+" ".repeat(9))));
            reject(Layout.ACCOUNT,"A03",12,"SCALE",()->FixedRecordCodec.encode(d,Map.of("currentBalance",new BigDecimal("1.001"))));
            reject(Layout.ACCOUNT,"A03",12,"WIDTH",()->FixedRecordCodec.encode(d,Map.of("currentBalance",new BigDecimal("10000000000.00"))));
        }
    }
}
