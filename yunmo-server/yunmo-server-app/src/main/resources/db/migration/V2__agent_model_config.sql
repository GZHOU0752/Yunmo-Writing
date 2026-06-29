-- V2__agent_model_config.sql
-- Agent 模型配置表：支持动态切换各 Agent 使用的 LLM provider/model

CREATE TABLE IF NOT EXISTS `agent_model_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `agent_type` VARCHAR(50) NOT NULL,
    `provider` VARCHAR(50) NOT NULL,
    `model` VARCHAR(100) NOT NULL,
    `enabled` BIT(1) NOT NULL DEFAULT TRUE,
    `sort_order` INT NOT NULL DEFAULT 0,
    `created_at` DATETIME(6),
    `updated_at` DATETIME(6),
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_agent_type` (`agent_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
