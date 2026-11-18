package com.carddemo.trantype.repository;

/**
 * CTLI filter host variables: WS-TYPE-CD-FILTER (exact) and WS-TYPE-DESC-FILTER ('%x%').
 * A null member means the filter is not active (WS-EDIT-*-FLAG &lt;&gt; '1').
 */
public record TypeFilters(String typeCode, String descLike) {

    public static final TypeFilters NONE = new TypeFilters(null, null);

    public boolean active() {
        return typeCode != null || descLike != null;
    }
}
