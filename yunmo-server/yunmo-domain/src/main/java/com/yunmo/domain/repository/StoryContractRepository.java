package com.yunmo.domain.repository;

import com.yunmo.common.enums.ContractType;
import com.yunmo.domain.entity.StoryContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 故事合同 Repository — 管理三层合同架构的持久化
 */
@Repository
public interface StoryContractRepository extends JpaRepository<StoryContract, String> {

    /** 查找指定小说的所有合同（按合同类型和版本排序） */
    List<StoryContract> findByNovelIdOrderByContractTypeAscContractVersionDesc(String novelId);

    /** 查找指定小说的指定类型所有合同 */
    List<StoryContract> findByNovelIdAndContractTypeOrderByContractVersionDesc(
            String novelId, ContractType contractType);

    /** 获取指定小说、指定类型的当前活跃合同 */
    Optional<StoryContract> findFirstByNovelIdAndContractTypeAndStatusOrderByContractVersionDesc(
            String novelId, ContractType contractType, StoryContract.ContractStatus status);

    /** 获取指定章节的活跃合同 */
    Optional<StoryContract> findFirstByNovelIdAndContractTypeAndChapterNumberAndStatusOrderByContractVersionDesc(
            String novelId, ContractType contractType, Integer chapterNumber, StoryContract.ContractStatus status);

    /** 获取指定卷号的活跃合同 */
    Optional<StoryContract> findFirstByNovelIdAndContractTypeAndVolumeNumberAndStatusOrderByContractVersionDesc(
            String novelId, ContractType contractType, Integer volumeNumber, StoryContract.ContractStatus status);

    /** 将指定合同置为已取代（旧版本废除） */
    @Modifying
    @Query("UPDATE StoryContract c SET c.status = 'SUPERSEDED' WHERE c.id = :contractId")
    void supersede(@Param("contractId") String contractId);

    /** 将指定小说指定类型的所有活跃合同置为已取代 */
    @Modifying
    @Query("UPDATE StoryContract c SET c.status = 'SUPERSEDED' " +
           "WHERE c.novelId = :novelId AND c.contractType = :contractType AND c.status = 'ACTIVE'")
    void supersedeAllActive(@Param("novelId") String novelId,
                            @Param("contractType") ContractType contractType);

    /** 获取指定小说的全书合同（MASTER类型） */
    default Optional<StoryContract> findActiveMasterContract(String novelId) {
        return findFirstByNovelIdAndContractTypeAndStatusOrderByContractVersionDesc(
                novelId, ContractType.MASTER, StoryContract.ContractStatus.ACTIVE);
    }

    /** 获取指定章节的活跃合同（CHAPTER类型） */
    default Optional<StoryContract> findActiveChapterContract(String novelId, int chapterNumber) {
        return findFirstByNovelIdAndContractTypeAndChapterNumberAndStatusOrderByContractVersionDesc(
                novelId, ContractType.CHAPTER, chapterNumber, StoryContract.ContractStatus.ACTIVE);
    }
}
