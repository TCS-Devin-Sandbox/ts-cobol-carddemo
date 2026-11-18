package com.carddemo.trantype.repository;

import com.carddemo.trantype.domain.TransactionType;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * SQL equivalents of COTRTLIC / COTRTUPC. Filter flags mirror the cursor predicate
 * {@code ((:WS-EDIT-TYPE-FLAG = '1' AND TR_TYPE = :filter) OR :WS-EDIT-TYPE-FLAG <> '1')}.
 */
public interface TransactionTypeRepository extends JpaRepository<TransactionType, String> {

    /** C-TR-TYPE-FORWARD: {@code TR_TYPE >= :WS-START-KEY ... ORDER BY TR_TYPE}. */
    @Query("""
            select t from TransactionType t
             where t.trType >= :startKey
               and (:typeFlag = false or t.trType = :typeFilter)
               and (:descFlag = false or t.description like :descFilter)
             order by t.trType asc""")
    List<TransactionType> findForward(@Param("startKey") String startKey,
                                      @Param("typeFlag") boolean typeFlag,
                                      @Param("typeFilter") String typeFilter,
                                      @Param("descFlag") boolean descFlag,
                                      @Param("descFilter") String descFilter,
                                      Limit limit);

    /** C-TR-TYPE-BACKWARD: {@code TR_TYPE < :WS-START-KEY ... ORDER BY TR_TYPE DESC}. */
    @Query("""
            select t from TransactionType t
             where t.trType < :startKey
               and (:typeFlag = false or t.trType = :typeFilter)
               and (:descFlag = false or t.description like :descFilter)
             order by t.trType desc""")
    List<TransactionType> findBackward(@Param("startKey") String startKey,
                                       @Param("typeFlag") boolean typeFlag,
                                       @Param("typeFilter") String typeFilter,
                                       @Param("descFlag") boolean descFlag,
                                       @Param("descFilter") String descFilter,
                                       Limit limit);

    /** 9200-UPDATE-RECORD / 9600-WRITE-PROCESSING. Returns rows updated (0 = SQLCODE +100). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE transaction_type SET tr_description = :description WHERE tr_type = :trType",
            nativeQuery = true)
    int updateDescription(@Param("trType") String trType, @Param("description") String description);

    /** 9700-INSERT-RECORD. Duplicate key raises DataIntegrityViolationException (SQLCODE -803). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO transaction_type (tr_type, tr_description) VALUES (:trType, :description)",
            nativeQuery = true)
    int insert(@Param("trType") String trType, @Param("description") String description);

    /** 9300-DELETE-RECORD / 9800-DELETE-PROCESSING. FK restrict raises DataIntegrityViolationException (-532). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM transaction_type WHERE tr_type = :trType", nativeQuery = true)
    int deleteByTrType(@Param("trType") String trType);
}
