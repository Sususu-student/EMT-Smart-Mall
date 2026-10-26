package com.smartmall.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MySQL 启动时按需创建查询索引。
 * Spring 的 schema.sql 只负责建表，索引在这里幂等补齐，避免重复启动报错。
 */
@Slf4j
@Component
@Order(0)
@Profile("mysql")
@RequiredArgsConstructor
public class DatabaseIndexInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    private static final List<IndexDef> INDEXES = List.of(
            new IndexDef("product", "idx_product_category", "category_id"),
            new IndexDef("product", "idx_product_status_price", "status, price"),
            new IndexDef("product", "idx_product_sales", "sales"),
            new IndexDef("product", "idx_product_created", "created_at"),
            new IndexDef("orders", "idx_orders_user", "user_id"),
            new IndexDef("orders", "idx_orders_status_created", "status, created_at"),
            new IndexDef("order_item", "idx_order_item_order", "order_id"),
            new IndexDef("order_item", "idx_order_item_seller", "seller_id, ship_status"),
            new IndexDef("product_review", "idx_review_product_status", "product_id, status"),
            new IndexDef("conversation", "idx_conversation_user_updated", "user_id, updated_at"),
            new IndexDef("dialogue_turn", "idx_turn_conversation", "conversation_id"),
            new IndexDef("agent_execution_log", "idx_agent_log_task", "task_id"),
            new IndexDef("agent_execution_log", "idx_agent_log_conversation", "conversation_id"),
            new IndexDef("notification", "idx_notification_user_read", "user_id, read_status, created_at"),
            new IndexDef("payment_record", "idx_payment_order", "order_id"),
            new IndexDef("payment_record", "idx_payment_user", "user_id"),
            new IndexDef("after_sale", "idx_after_sale_order", "order_id"),
            new IndexDef("after_sale", "idx_after_sale_user_status", "user_id, status"),
            new IndexDef("user_address", "idx_address_user", "user_id"),
            new IndexDef("browse_history", "idx_browse_user_created", "user_id, created_at"),
            new IndexDef("cart_item", "idx_cart_user", "user_id")
    );

    @Override
    public void run(ApplicationArguments args) {
        migrateColumns();
        int created = 0;
        for (IndexDef index : INDEXES) {
            try {
                Integer exists = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM information_schema.statistics
                        WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?
                        """, Integer.class, index.table(), index.name());
                if (exists != null && exists > 0) {
                    continue;
                }
                jdbcTemplate.execute("CREATE INDEX `" + index.name() + "` ON `"
                        + index.table() + "` (" + index.columns() + ")");
                created++;
            } catch (Exception e) {
                log.warn("创建 MySQL 索引 {}.{} 失败: {}", index.table(), index.name(), e.getMessage());
            }
        }
        if (created > 0) {
            log.info("MySQL 索引初始化完成，新增 {} 个索引", created);
        }
    }

    /**
     * 幂等补齐历史库缺失的列。
     * schema.sql 用的是 CREATE TABLE IF NOT EXISTS，已存在的表不会再加新列，
     * 因此这里显式 ALTER，保证老库升级后也能按卖家拆分发货。
     */
    private void migrateColumns() {
        int added = 0;
        for (ColumnDef column : COLUMNS) {
            try {
                Integer exists = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                        """, Integer.class, column.table(), column.name());
                if (exists != null && exists > 0) {
                    continue;
                }
                jdbcTemplate.execute("ALTER TABLE `" + column.table() + "` ADD COLUMN "
                        + column.definition());
                added++;
                log.info("补充字段 {}.{}", column.table(), column.name());
            } catch (Exception e) {
                log.warn("补充字段 {}.{} 失败: {}", column.table(), column.name(), e.getMessage());
            }
        }
        if (added > 0) {
            backfillSellerId();
            log.info("MySQL 字段迁移完成，新增 {} 列", added);
        }
    }

    /** 把历史 order_item 的 seller_id 按商品反查回填，并在缺省时标记为平台自营 */
    private void backfillSellerId() {
        try {
            // 商品有归属卖家时按商品回填
            int updated = jdbcTemplate.update("""
                    UPDATE order_item oi
                    JOIN product p ON p.id = oi.product_id
                    SET oi.seller_id = p.seller_id
                    WHERE oi.seller_id IS NULL AND p.seller_id IS NOT NULL
                    """);
            // 平台自营商品没有 seller_id，用固定标识占位，便于查询与展示
            int platform = jdbcTemplate.update("""
                    UPDATE order_item SET seller_id = '__platform__'
                    WHERE seller_id IS NULL
                    """);
            log.info("回填 order_item.seller_id：卖家商品 {} 条，平台自营 {} 条", updated, platform);
        } catch (Exception e) {
            log.warn("回填 order_item.seller_id 失败: {}", e.getMessage());
        }
    }

    private static final List<ColumnDef> COLUMNS = List.of(
            new ColumnDef("order_item", "seller_id", "`seller_id` VARCHAR(32) NULL"),
            new ColumnDef("order_item", "ship_status", "`ship_status` VARCHAR(32) DEFAULT 'PENDING'"),
            new ColumnDef("order_item", "carrier", "`carrier` VARCHAR(64) NULL"),
            new ColumnDef("order_item", "tracking_no", "`tracking_no` VARCHAR(64) NULL"),
            new ColumnDef("order_item", "shipped_at", "`shipped_at` DATETIME NULL"),
            new ColumnDef("order_item", "refund_status", "`refund_status` VARCHAR(32) DEFAULT 'NONE'")
    );

    private record ColumnDef(String table, String name, String definition) {
    }

    private record IndexDef(String table, String name, String columns) {
    }
}
