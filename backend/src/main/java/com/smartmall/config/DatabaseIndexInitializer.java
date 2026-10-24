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

    private record IndexDef(String table, String name, String columns) {
    }
}
