package com.carddemo.trantype.repository;

import com.carddemo.trantype.domain.TransactionType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Keyset queries mirror cursors C-TR-TYPE-FORWARD / C-TR-TYPE-BACKWARD and 9100-CHECK-FILTERS (COTRTLIC).
 * A null filter parameter means "filter not supplied" (WS-EDIT-*-FLAG &lt;&gt; '1').
 */
public interface TransactionTypeRepository extends JpaRepository<TransactionType, String> {

    String FILTERS = " and (:typeFilter is null or t.trType = :typeFilter)"
            + " and (:descPattern is null or t.trDescription like :descPattern)";

    @Query("select t from TransactionType t where (:cursor is null or t.trType >= :cursor)"
            + FILTERS + " order by t.trType asc")
    List<TransactionType> findPageForward(@Param("cursor") String cursor,
                                          @Param("typeFilter") String typeFilter,
                                          @Param("descPattern") String descPattern,
                                          Limit limit);

    @Query("select t from TransactionType t where t.trType < :cursor"
            + FILTERS + " order by t.trType desc")
    List<TransactionType> findPageBackward(@Param("cursor") String cursor,
                                           @Param("typeFilter") String typeFilter,
                                           @Param("descPattern") String descPattern,
                                           Limit limit);

    @Query("select count(t) from TransactionType t where t.trType < :key" + FILTERS)
    long countBefore(@Param("key") String key,
                     @Param("typeFilter") String typeFilter,
                     @Param("descPattern") String descPattern);

    @Query("select count(t) from TransactionType t where 1 = 1" + FILTERS)
    long countMatching(@Param("typeFilter") String typeFilter,
                       @Param("descPattern") String descPattern);

    /** 9200-UPDATE-RECORD / 9600-WRITE-PROCESSING. Returns the update count (0 = SQLCODE +100). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TransactionType t set t.trDescription = :description where t.trType = :trType")
    int updateDescription(@Param("trType") String trType, @Param("description") String description);

    /** 9700-INSERT-RECORD. Plain INSERT so an existing key raises a unique violation (SQLCODE -803). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "insert into transaction_type (tr_type, tr_description) values (:trType, :description)",
            nativeQuery = true)
    int insert(@Param("trType") String trType, @Param("description") String description);

    /** 9300-DELETE-RECORD / 9800-DELETE-PROCESSING. FK RESTRICT raises a violation (SQLCODE -532). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TransactionType t where t.trType = :trType")
    int deleteByTrType(@Param("trType") String trType);
}
