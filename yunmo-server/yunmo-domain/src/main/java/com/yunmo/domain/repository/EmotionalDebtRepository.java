package com.yunmo.domain.repository;

import com.yunmo.domain.entity.EmotionalDebt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 情感债 Repository
 */
@Repository
public interface EmotionalDebtRepository extends JpaRepository<EmotionalDebt, String> {

    /** 按小说查询所有情感债 */
    List<EmotionalDebt> findByNovelId(String novelId);

    /** 按小说和状态查询 */
    List<EmotionalDebt> findByNovelIdAndStatus(String novelId, EmotionalDebt.DebtStatus status);

    /** 按小说和债务类型查询 */
    List<EmotionalDebt> findByNovelIdAndDebtType(String novelId, EmotionalDebt.DebtType debtType);

    /** 查询某角色作为债务人的情感债 */
    List<EmotionalDebt> findByNovelIdAndDebtor(String novelId, String debtor);

    /** 查询某角色作为债权人的情感债 */
    List<EmotionalDebt> findByNovelIdAndCreditor(String novelId, String creditor);

    /** 查询在某章产生的情感债 */
    List<EmotionalDebt> findByNovelIdAndCreatedChapter(String novelId, Integer createdChapter);

    /** 查询仍未偿还的情感债 */
    List<EmotionalDebt> findByNovelIdAndStatusNotOrderByCreatedChapterAsc(String novelId, EmotionalDebt.DebtStatus status);
}
