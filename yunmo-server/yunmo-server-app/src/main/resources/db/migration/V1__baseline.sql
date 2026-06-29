-- V1__baseline.sql
-- Flyway 基线迁移：根据 JPA 实体定义生成全部表结构
-- 所有表继承 BaseEntity 的标准字段：id(VARCHAR 36), created_at, updated_at, version(BIGINT)

-- ============================================================
-- 用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS `users` (
    `id` VARCHAR(36) NOT NULL,
    `created_at` DATETIME(6),
    `updated_at` DATETIME(6),
    `version` BIGINT NOT NULL DEFAULT 0,
    `email` VARCHAR(255) UNIQUE,
    `display_name` VARCHAR(255),
    `password_hash` VARCHAR(255),
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 小说表
-- ============================================================
CREATE TABLE IF NOT EXISTS `novels` (
    `id` VARCHAR(36) NOT NULL,
    `created_at` DATETIME(6),
    `updated_at` DATETIME(6),
    `version` BIGINT NOT NULL DEFAULT 0,
    `title` VARCHAR(255) NOT NULL,
    `genre_id` VARCHAR(255) NOT NULL,
    `writing_style` TEXT,
    `word_count` INT DEFAULT 0,
    `total_chapters` INT DEFAULT 0,
    `target_total_words` INT DEFAULT 1500000,
    `current_chapter` INT DEFAULT 0,
    `user_id` VARCHAR(255),
    `synopsis` TEXT,
    `outline` TEXT,
    `cover_image` VARCHAR(255),
    PRIMARY KEY (`id`),
    INDEX `idx_novels_user_created` (`user_id`, `created_at`),
    INDEX `idx_novels_genre` (`genre_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 角色表
-- ============================================================
CREATE TABLE IF NOT EXISTS `characters` (
    `id` VARCHAR(36) NOT NULL,
    `created_at` DATETIME(6),
    `updated_at` DATETIME(6),
    `version` BIGINT NOT NULL DEFAULT 0,
    `novel_id` VARCHAR(36) NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `role` VARCHAR(20) DEFAULT 'SUPPORTING',
    `description` TEXT,
    `layer1_worldview` TEXT,
    `layer2_identity` TEXT,
    `layer3_values` TEXT,
    `layer4_abilities` TEXT,
    `layer5_skills` TEXT,
    `layer6_environment` TEXT,
    `current_state` TEXT,
    `importance` INT NOT NULL DEFAULT 5,
    `last_appearance_chapter` INT,
    `cooldown_until_chapter` INT,
    `is_dead` BIT(1) DEFAULT FALSE,
    `career_id` VARCHAR(36),
    `organization_id` VARCHAR(36),
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 章节表
-- ============================================================
CREATE TABLE IF NOT EXISTS `chapters` (
    `id` VARCHAR(36) NOT NULL,
    `created_at` DATETIME(6),
    `updated_at` DATETIME(6),
    `version` BIGINT NOT NULL DEFAULT 0,
    `novel_id` VARCHAR(36) NOT NULL,
    `chapter_number` INT NOT NULL,
    `title` TEXT,
    `content` LONGTEXT,
    `summary` TEXT,
    `status` VARCHAR(32) DEFAULT 'OUTLINE',
    `word_count` INT DEFAULT 0,
    `target_word_count` INT DEFAULT 2500,
    `writing_plan` TEXT,
    `causal_sentence` TEXT,
    `context_snapshot_id` VARCHAR(36),
    `retry_count` INT DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_chapters_novel_number` (`novel_id`, `chapter_number`),
    INDEX `idx_chapters_novel_status` (`novel_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 大纲节点表
-- ============================================================
CREATE TABLE IF NOT EXISTS `outline_nodes` (
    `id` VARCHAR(36) NOT NULL,
    `created_at` DATETIME(6),
    `updated_at` DATETIME(6),
    `version` BIGINT NOT NULL DEFAULT 0,
    `novel_id` VARCHAR(36) NOT NULL,
    `title` VARCHAR(255) NOT NULL,
    `sequence_order` INT,
    `causal_sentence` TEXT,
    `structure` TEXT,
    `parent_id` VARCHAR(36),
    `chapter_number` INT,
    `level` INT NOT NULL,
    `status` VARCHAR(32),
    `outline_content` TEXT,
    `word_count_target` INT,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 世界观元素表
-- ============================================================
CREATE TABLE IF NOT EXISTS `world_elements` (
    `id` VARCHAR(36) NOT NULL,
    `created_at` DATETIME(6),
    `updated_at` DATETIME(6),
    `version` BIGINT NOT NULL DEFAULT 0,
    `novel_id` VARCHAR(36) NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `element_type` VARCHAR(32),
    `description` TEXT,
    `chapter_introduced` INT,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 世界规则表
-- ============================================================
CREATE TABLE IF NOT EXISTS `world_rules` (
    `id` VARCHAR(36) NOT NULL,
    `created_at` DATETIME(6),
    `updated_at` DATETIME(6),
    `version` BIGINT NOT NULL DEFAULT 0,
    `novel_id` VARCHAR(36) NOT NULL,
    `rule_name` VARCHAR(200) NOT NULL,
    `category` VARCHAR(32) DEFAULT 'OTHER',
    `description` TEXT,
    `revealed_chapter` INT,
    `status` VARCHAR(32) DEFAULT 'ACTIVE',
    `last_modified_chapter` INT,
    PRIMARY KEY (`id`),
    INDEX `idx_world_rules_novel_category` (`novel_id`, `category`),
    INDEX `idx_world_rules_novel_status` (`novel_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 记忆规则表
-- ============================================================
CREATE TABLE IF NOT EXISTS `memory_rules` (
    `id` VARCHAR(36) NOT NULL,
    `created_at` DATETIME(6),
    `updated_at` DATETIME(6),
    `version` BIGINT NOT NULL DEFAULT 0,
    `novel_id` VARCHAR(36) NOT NULL,
    `category` VARCHAR(32),
    `pattern` TEXT,
    `replacement` TEXT,
    `priority` INT NOT NULL DEFAULT 50,
    `source_chapter_id` VARCHAR(36),
    `use_count` INT DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 记忆快照表
-- ============================================================
CREATE TABLE IF NOT EXISTS `memory_snapshots` (
    `id` VARCHAR(36) NOT NULL,
    `created_at` DATETIME(6),
    `updated_at` DATETIME(6),
    `version` BIGINT NOT NULL DEFAULT 0,
    `novel_id` VARCHAR(36) NOT NULL,
    `memory_json` MEDIUMTEXT,
    `item_count` INT NOT NULL DEFAULT 0,
    `chapter_number` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 角色状态快照表
-- ============================================================
CREATE TABLE IF NOT EXISTS `character_states` (
    `id` VARCHAR(36) NOT NULL,
    `created_at` DATETIME(6),
    `updated_at` DATETIME(6),
    `version` BIGINT NOT NULL DEFAULT 0,
    `novel_id` VARCHAR(36) NOT NULL,
    `character_id` VARCHAR(36) NOT NULL,
    `chapter_number` INT NOT NULL,
    `location` VARCHAR(500),
    `realm` VARCHAR(200),
    `emotional_state` VARCHAR(200),
    `physical_state` VARCHAR(200),
    `relationship_changes` TEXT,
    `resources` TEXT,
    PRIMARY KEY (`id`),
    INDEX `idx_cs_novel_char_chapter` (`novel_id`, `character_id`, `chapter_number`),
    INDEX `idx_cs_novel_chapter` (`novel_id`, `chapter_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 伏笔表
-- ============================================================
CREATE TABLE IF NOT EXISTS `foreshadows` (
    `id` VARCHAR(36) NOT NULL,
    `created_at` DATETIME(6),
    `updated_at` DATETIME(6),
    `version` BIGINT NOT NULL DEFAULT 0,
    `novel_id` VARCHAR(36) NOT NULL,
    `title` VARCHAR(255) NOT NULL,
    `content` TEXT,
    `keywords` VARCHAR(1024),
    `status` VARCHAR(20) DEFAULT 'PLANNED',
    `stable_id` VARCHAR(32) UNIQUE,
    `planted_chapter` INT,
    `expected_resolve_chapter` INT,
    `resolved_chapter` INT,
    `urgency` INT DEFAULT 5,
    PRIMARY KEY (`id`),
    INDEX `idx_foreshadows_novel_status` (`novel_id`, `status`),
    INDEX `idx_foreshadows_stable_id` (`stable_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 伏笔追踪表
-- ============================================================
CREATE TABLE IF NOT EXISTS `foreshadow_trackings` (
    `id` VARCHAR(36) NOT NULL,
    `created_at` DATETIME(6),
    `updated_at` DATETIME(6),
    `version` BIGINT NOT NULL DEFAULT 0,
    `novel_id` VARCHAR(36) NOT NULL,
    `hook_id` VARCHAR(32) NOT NULL,
    `content` TEXT,
    `planted_chapter` INT,
    `expected_payoff_chapter` INT,
    `status` VARCHAR(32) DEFAULT 'PLANTED',
    `importance` VARCHAR(32) DEFAULT 'SUB',
    PRIMARY KEY (`id`),
    INDEX `idx_ft_novel_hook` (`novel_id`, `hook_id`),
    INDEX `idx_ft_novel_status` (`novel_id`, `status`),
    INDEX `idx_ft_novel_planted` (`novel_id`, `planted_chapter`),
    INDEX `idx_ft_novel_expected` (`novel_id`, `expected_payoff_chapter`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 故事合同表
-- ============================================================
CREATE TABLE IF NOT EXISTS `story_contracts` (
    `id` VARCHAR(36) NOT NULL,
    `created_at` DATETIME(6),
    `updated_at` DATETIME(6),
    `version` BIGINT NOT NULL DEFAULT 0,
    `novel_id` VARCHAR(36) NOT NULL,
    `contract_type` VARCHAR(20) NOT NULL,
    `volume_number` INT,
    `chapter_number` INT,
    `constraints_json` TEXT,
    `anti_patterns_json` TEXT,
    `dynamic_context_json` TEXT,
    `reasoning_text` TEXT,
    `must_cover_nodes_json` TEXT,
    `forbidden_zones_json` TEXT,
    `contract_version` INT NOT NULL DEFAULT 1,
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (`id`),
    INDEX `idx_sc_novel_type_chapter` (`novel_id`, `contract_type`, `chapter_number`),
    INDEX `idx_sc_novel_type_status` (`novel_id`, `contract_type`, `status`),
    INDEX `idx_sc_novel_type_volume` (`novel_id`, `contract_type`, `volume_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 情感债表
-- ============================================================
CREATE TABLE IF NOT EXISTS `emotional_debts` (
    `id` VARCHAR(36) NOT NULL,
    `created_at` DATETIME(6),
    `updated_at` DATETIME(6),
    `version` BIGINT NOT NULL DEFAULT 0,
    `novel_id` VARCHAR(36) NOT NULL,
    `debtor` VARCHAR(100) NOT NULL,
    `creditor` VARCHAR(100) NOT NULL,
    `debt_type` VARCHAR(32) DEFAULT 'PROMISE',
    `description` TEXT,
    `created_chapter` INT,
    `expected_resolution_chapter` INT,
    `resolved_chapter` INT,
    `status` VARCHAR(32) DEFAULT 'OUTSTANDING',
    PRIMARY KEY (`id`),
    INDEX `idx_ed_novel_debtor` (`novel_id`, `debtor`),
    INDEX `idx_ed_novel_creditor` (`novel_id`, `creditor`),
    INDEX `idx_ed_novel_status` (`novel_id`, `status`),
    INDEX `idx_ed_novel_type` (`novel_id`, `debt_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 上下文快照表
-- ============================================================
CREATE TABLE IF NOT EXISTS `context_snapshots` (
    `id` VARCHAR(36) NOT NULL,
    `created_at` DATETIME(6),
    `updated_at` DATETIME(6),
    `version` BIGINT NOT NULL DEFAULT 0,
    `novel_id` VARCHAR(36) NOT NULL,
    `chapter_id` VARCHAR(36),
    `layer1_bible` LONGTEXT,
    `layer2_active` LONGTEXT,
    `layer3_history` LONGTEXT,
    `layer4_plan` TEXT,
    `estimated_tokens` INT,
    `metadata` TEXT,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 章节版本历史表
-- ============================================================
CREATE TABLE IF NOT EXISTS `chapter_versions` (
    `id` VARCHAR(36) NOT NULL,
    `created_at` DATETIME(6),
    `updated_at` DATETIME(6),
    `version` BIGINT NOT NULL DEFAULT 0,
    `chapter_id` VARCHAR(36) NOT NULL,
    `version_number` INT NOT NULL,
    `content` LONGTEXT,
    `change_summary` TEXT,
    `word_count` INT,
    `branch_name` VARCHAR(64) DEFAULT 'main',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_chapter_version` (`chapter_id`, `version_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 参考素材表
-- ============================================================
CREATE TABLE IF NOT EXISTS `reference_materials` (
    `id` VARCHAR(36) NOT NULL,
    `created_at` DATETIME(6),
    `updated_at` DATETIME(6),
    `version` BIGINT NOT NULL DEFAULT 0,
    `novel_id` VARCHAR(36) NOT NULL,
    `file_name` VARCHAR(255) NOT NULL,
    `file_size` BIGINT,
    `chunk_count` INT,
    `status` VARCHAR(32),
    `trigger_mode` VARCHAR(16) DEFAULT 'MANUAL',
    `trigger_keywords` TEXT,
    `cooldown_chapters` INT DEFAULT 0,
    `priority` INT DEFAULT 0,
    `last_activated_chapter` INT,
    PRIMARY KEY (`id`),
    INDEX `idx_ref_novel_created` (`novel_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 每日写作统计表
-- ============================================================
CREATE TABLE IF NOT EXISTS `daily_writing_stats` (
    `id` VARCHAR(36) NOT NULL,
    `created_at` DATETIME(6),
    `updated_at` DATETIME(6),
    `version` BIGINT NOT NULL DEFAULT 0,
    `novel_id` VARCHAR(36) NOT NULL,
    `date` DATE NOT NULL,
    `word_count` INT DEFAULT 0,
    `target_word_count` INT DEFAULT 2000,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_dws_novel_date` (`novel_id`, `date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
