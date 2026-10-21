package com.ticket2h.identity.dev;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cỡ dữ liệu sinh ({@code ticket2h.datagen.*}), mặc định đúng quy mô lộ trình M4 (2 triệu khách). Phải giống lúc chạy
 * công cụ sinh của core-service (infra/scripts/datagen.sh truyền cùng giá trị cho cả hai).
 */
@ConfigurationProperties("ticket2h.datagen")
public record DatagenProperties(
        @DefaultValue("2000000") int customers,
        @DefaultValue("200") int organizers,
        @DefaultValue("600") int staff) {}
