package com.carddemo.interest.codec;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** INT-MAP X/D/Z/B boundary. CP037 is synthetic lab coverage, not host certification. */
public final class FixedRecordCodec {
    private FixedRecordCodec() {}

    public enum Profile {
        LAB_ASCII("US-ASCII"), SYNTHETIC_CP037("IBM037");
        private final Charset charset;
        Profile(String charset) { this.charset = Charset.forName(charset); }
    }

    public record Field(String row, String name, char kind, int width) {}

    public enum Layout {
        ACCOUNT(300, 11, List.of(new Field("A01", "acctId", 'd', 11), new Field("A02", "activeStatus", 'x', 1), new Field("A03", "currentBalance", 'z', 12), new Field("A04", "creditLimit", 'z', 12), new Field("A05", "cashCreditLimit", 'z', 12), new Field("A06", "openDate", 'x', 10), new Field("A07", "expirationDate", 'x', 10), new Field("A08", "reissueDate", 'x', 10), new Field("A09", "cycleCredit", 'z', 12), new Field("A10", "cycleDebit", 'z', 12), new Field("A11", "addrZip", 'x', 10), new Field("A12", "groupId", 'x', 10), new Field("A13", "filler", 'b', 178))),
        XREF(50, 16, List.of(new Field("X01", "cardNumber", 'x', 16), new Field("X02", "customerId", 'd', 9), new Field("X03", "acctId", 'd', 11), new Field("X04", "filler", 'b', 14))),
        TCATBAL(50, 17, List.of(new Field("B01", "acctId", 'd', 11), new Field("B02", "txnType", 'x', 2), new Field("B03", "categoryCode", 'd', 4), new Field("B04", "balance", 'z', 11), new Field("B05", "filler", 'b', 22))),
        DISCGRP(50, 16, List.of(new Field("D01", "groupId", 'x', 10), new Field("D02", "txnType", 'x', 2), new Field("D03", "categoryCode", 'd', 4), new Field("D04", "annualPercentRate", 'z', 6), new Field("D05", "filler", 'b', 28))),
        TRAN(350, 16, List.of(new Field("T01", "txnId", 'x', 16), new Field("T02", "txnType", 'x', 2), new Field("T03", "categoryCode", 'd', 4), new Field("T04", "source", 'x', 10), new Field("T05", "description", 'x', 100), new Field("T06", "amount", 'z', 11), new Field("T07", "merchantId", 'd', 9), new Field("T08", "merchantName", 'x', 50), new Field("T09", "merchantCity", 'x', 50), new Field("T10", "merchantZip", 'x', 10), new Field("T11", "cardNumber", 'x', 16), new Field("T12", "origTs", 'x', 26), new Field("T13", "procTs", 'x', 26), new Field("T14", "filler", 'b', 20)));
        private final int length;
        private final int keyLength;
        private final List<Field> fields;
        Layout(int length, int keyLength, List<Field> fields) {
            this.length=length; this.keyLength=keyLength; this.fields=fields;
        }
        public int length() { return length; }
        public List<Field> fields() { return fields; }
    }

    public static final class Bytes {
        private final byte[] value;
        public Bytes(byte[] value) { this.value=Objects.requireNonNull(value).clone(); }
        public byte[] array() { return value.clone(); }
        @Override public boolean equals(Object other) {
            return other instanceof Bytes b && Arrays.equals(value,b.value);
        }
        @Override public int hashCode() { return Arrays.hashCode(value); }
    }

    public sealed interface BusinessRecord permits Account, CardXref, CategoryBalance, DisclosureRate, InterestTransaction {
        List<Object> values();
    }
    public record Account(String acctId, String activeStatus, BigDecimal currentBalance, BigDecimal creditLimit, BigDecimal cashCreditLimit, String openDate, String expirationDate, String reissueDate, BigDecimal cycleCredit, BigDecimal cycleDebit, String addrZip, String groupId, Bytes filler) implements BusinessRecord {
        public List<Object> values() { return List.of(acctId, activeStatus, currentBalance, creditLimit, cashCreditLimit, openDate, expirationDate, reissueDate, cycleCredit, cycleDebit, addrZip, groupId, filler); }
    }
    public record CardXref(String cardNumber, String customerId, String acctId, Bytes filler) implements BusinessRecord {
        public List<Object> values() { return List.of(cardNumber, customerId, acctId, filler); }
    }
    public record CategoryBalance(String acctId, String txnType, String categoryCode, BigDecimal balance, Bytes filler) implements BusinessRecord {
        public List<Object> values() { return List.of(acctId, txnType, categoryCode, balance, filler); }
    }
    public record DisclosureRate(String groupId, String txnType, String categoryCode, BigDecimal annualPercentRate, Bytes filler) implements BusinessRecord {
        public List<Object> values() { return List.of(groupId, txnType, categoryCode, annualPercentRate, filler); }
    }
    public record InterestTransaction(String txnId, String txnType, String categoryCode, String source, String description, BigDecimal amount, String merchantId, String merchantName, String merchantCity, String merchantZip, String cardNumber, String origTs, String procTs, Bytes filler) implements BusinessRecord {
        public List<Object> values() { return List.of(txnId, txnType, categoryCode, source, description, amount, merchantId, merchantName, merchantCity, merchantZip, cardNumber, origTs, procTs, filler); }
    }

    public static final class Decoded {
        private final Layout layout;
        private final Profile profile;
        private final BusinessRecord fields;
        private final Bytes original;
        private Decoded(Layout layout, Profile profile, BusinessRecord fields, byte[] original) {
            this.layout=layout; this.profile=profile; this.fields=fields; this.original=new Bytes(original);
        }
        public Layout layout() { return layout; }
        public Profile profile() { return profile; }
        public BusinessRecord fields() { return fields; }
        public byte[] original() { return original.array(); }
        /** INT-R09: unsigned encoded bytes, never decoded-text collation. */
        public byte[] legacyKey() { return Arrays.copyOf(original.array(),layout.keyLength); }
        /** INT-H03: alternate key only; this codec never chooses an AIX-first card. */
        public byte[] alternateKey() {
            if(layout!=Layout.XREF) throw failure(layout,"X03",25,"NO_ALTERNATE_KEY");
            return Arrays.copyOfRange(original.array(),25,36);
        }
    }

    public static final class Rejection extends IllegalArgumentException {
        private final Layout layout;
        private final String row;
        private final int offset;
        private final String reason;
        private Rejection(Layout layout, String row, int offset, String reason) {
            super(layout+"/"+row+" offset="+offset+" reason="+reason);
            this.layout=layout; this.row=row; this.offset=offset; this.reason=reason;
        }
        public Layout layout() { return layout; }
        public String row() { return row; }
        public int offset() { return offset; }
        public String reason() { return reason; }
    }
    private static Rejection failure(Layout l,String row,int offset,String reason) {
        return new Rejection(l,row,offset,reason);
    }

    public static Decoded decode(Layout layout, String profile, byte[] raw) {
        Profile selected;
        try { selected=Profile.valueOf(profile); }
        catch(RuntimeException e) { throw failure(layout,"RECORD",0,"INVALID_PROFILE"); }
        return decode(layout,selected,raw);
    }

    public static Decoded decode(Layout layout, Profile profile, byte[] raw) {
        Objects.requireNonNull(layout);
        if(profile==null) throw failure(layout,"RECORD",0,"INVALID_PROFILE");
        if(raw==null || raw.length!=layout.length) throw failure(layout,"RECORD",raw==null?0:Math.min(raw.length,layout.length),"LENGTH");
        byte[] copy=raw.clone();
        var values=new java.util.ArrayList<Object>();
        int offset=0;
        for(Field f:layout.fields) {
            byte[] slice=Arrays.copyOfRange(copy,offset,offset+f.width);
            if(f.kind=='b') values.add(new Bytes(slice));
            else if(f.kind=='z') values.add(decimal(layout,profile,f,offset,slice));
            else {
                String text=text(layout,profile,f,offset,slice);
                if(f.kind=='d') digits(layout,f,offset,text);
                values.add(text);
            }
            offset+=f.width;
        }
        return new Decoded(layout,profile,business(layout,values),copy);
    }

    private static BusinessRecord business(Layout layout,List<Object> v) {
        return switch(layout) {
            case ACCOUNT -> new Account((String) v.get(0), (String) v.get(1), (BigDecimal) v.get(2), (BigDecimal) v.get(3), (BigDecimal) v.get(4), (String) v.get(5), (String) v.get(6), (String) v.get(7), (BigDecimal) v.get(8), (BigDecimal) v.get(9), (String) v.get(10), (String) v.get(11), (Bytes) v.get(12));
            case XREF -> new CardXref((String) v.get(0), (String) v.get(1), (String) v.get(2), (Bytes) v.get(3));
            case TCATBAL -> new CategoryBalance((String) v.get(0), (String) v.get(1), (String) v.get(2), (BigDecimal) v.get(3), (Bytes) v.get(4));
            case DISCGRP -> new DisclosureRate((String) v.get(0), (String) v.get(1), (String) v.get(2), (BigDecimal) v.get(3), (Bytes) v.get(4));
            case TRAN -> new InterestTransaction((String) v.get(0), (String) v.get(1), (String) v.get(2), (String) v.get(3), (String) v.get(4), (BigDecimal) v.get(5), (String) v.get(6), (String) v.get(7), (String) v.get(8), (String) v.get(9), (String) v.get(10), (String) v.get(11), (String) v.get(12), (Bytes) v.get(13));
        };
    }

    private static String text(Layout l,Profile p,Field f,int offset,byte[] bytes) {
        try {
            String result=p.charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            for(int i=0;i<result.length();i++) if(Character.isISOControl(result.charAt(i)))
                throw failure(l,f.row,offset+i,"INVALID_TEXT");
            return result;
        } catch(CharacterCodingException e) { throw failure(l,f.row,offset,"UNMAPPABLE"); }
    }
    private static void digits(Layout l,Field f,int offset,String text) {
        for(int i=0;i<text.length();i++) if(text.charAt(i)<'0'||text.charAt(i)>'9')
            throw failure(l,f.row,offset+i,"DIGITS");
    }

    private static BigDecimal decimal(Layout l,Profile p,Field f,int offset,byte[] bytes) {
        String prefix=text(l,p,f,offset,Arrays.copyOf(bytes,bytes.length-1));
        digits(l,f,offset,prefix);
        int last=Byte.toUnsignedInt(bytes[bytes.length-1]);
        int digit; boolean negative;
        if(p==Profile.SYNTHETIC_CP037) {
            int zone=last>>>4; digit=last&15;
            if((zone!=12 && zone!=13)||digit>9) throw failure(l,f.row,offset+bytes.length-1,"OVERPUNCH");
            negative=zone==13;
        } else {
            int positive="{ABCDEFGHI".indexOf((char)last);
            int minus="}JKLMNOPQR".indexOf((char)last);
            if(positive<0 && minus<0) throw failure(l,f.row,offset+bytes.length-1,"OVERPUNCH");
            negative=minus>=0; digit=negative?minus:positive;
        }
        return new BigDecimal((negative?"-":"")+prefix+digit).movePointLeft(2);
    }

    public static byte[] encode(Decoded decoded) { return decoded.original(); }

    /** Validates every replacement; omitted fields (including signed zero/filler) remain byte-exact. */
    public static byte[] encode(Decoded decoded,Map<String,?> patch) {
        byte[] result=decoded.original();
        for(String name:patch.keySet()) if(decoded.layout.fields.stream().noneMatch(f->f.name.equals(name)))
            throw failure(decoded.layout,"RECORD",0,"UNKNOWN_FIELD");
        int offset=0;
        for(Field f:decoded.layout.fields) {
            if(patch.containsKey(f.name)) {
                byte[] bytes=encodeField(decoded.layout,decoded.profile,f,offset,patch.get(f.name));
                System.arraycopy(bytes,0,result,offset,bytes.length);
            }
            offset+=f.width;
        }
        decode(decoded.layout,decoded.profile,result);
        return result;
    }

    public static byte[] encode(Layout layout,Profile profile,BusinessRecord record) {
        if(record==null || businessType(layout)!=record.getClass()) throw failure(layout,"RECORD",0,"RECORD_TYPE");
        if(profile==null) throw failure(layout,"RECORD",0,"INVALID_PROFILE");
        byte[] result=new byte[layout.length];
        int offset=0,index=0;
        for(Field f:layout.fields) {
            byte[] bytes=encodeField(layout,profile,f,offset,record.values().get(index++));
            System.arraycopy(bytes,0,result,offset,bytes.length); offset+=f.width;
        }
        return result;
    }
    private static Class<?> businessType(Layout l) {
        return switch(l) {
            case ACCOUNT -> Account.class; case XREF -> CardXref.class;
            case TCATBAL -> CategoryBalance.class; case DISCGRP -> DisclosureRate.class;
            case TRAN -> InterestTransaction.class;
        };
    }

    private static byte[] encodeField(Layout l,Profile p,Field f,int offset,Object value) {
        if(f.kind=='b') {
            if(!(value instanceof Bytes bytes)) throw failure(l,f.row,offset,"TYPE");
            byte[] result=bytes.array();
            if(result.length!=f.width) throw failure(l,f.row,offset,"WIDTH");
            return result;
        }
        if(f.kind=='z') {
            if(!(value instanceof BigDecimal number)) throw failure(l,f.row,offset,"TYPE");
            String digits;
            try { digits=number.setScale(2,RoundingMode.UNNECESSARY).unscaledValue().abs().toString(); }
            catch(ArithmeticException e) { throw failure(l,f.row,offset,"SCALE"); }
            if(digits.length()>f.width) throw failure(l,f.row,offset,"WIDTH");
            digits="0".repeat(f.width-digits.length())+digits;
            byte[] result=encodedText(l,p,f,offset,digits);
            int last=digits.charAt(digits.length()-1)-'0';
            result[result.length-1]=(byte)(p==Profile.SYNTHETIC_CP037
                    ? (number.signum()<0?0xd0:0xc0)+last
                    : (number.signum()<0?"}JKLMNOPQR":"{ABCDEFGHI").charAt(last));
            return result;
        }
        if(!(value instanceof String text)) throw failure(l,f.row,offset,"TYPE");
        if(text.length()!=f.width) throw failure(l,f.row,offset,"WIDTH");
        if(f.kind=='d') digits(l,f,offset,text);
        byte[] result=encodedText(l,p,f,offset,text);
        text(l,p,f,offset,result);
        return result;
    }

    private static byte[] encodedText(Layout l,Profile p,Field f,int offset,String text) {
        try {
            ByteBuffer bytes=p.charset.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));
            byte[] result=new byte[bytes.remaining()]; bytes.get(result);
            if(result.length!=f.width) throw failure(l,f.row,offset,"WIDTH");
            return result;
        } catch(CharacterCodingException e) { throw failure(l,f.row,offset,"UNMAPPABLE"); }
    }

    public static int compareKeys(byte[] left,byte[] right) { return Arrays.compareUnsigned(left,right); }
}
