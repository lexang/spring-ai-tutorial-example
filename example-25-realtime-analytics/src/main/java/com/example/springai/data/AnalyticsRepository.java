package com.example.springai.data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.example.springai.model.AnalyticsModels.DataPoint;
import com.example.springai.model.AnalyticsModels.EventRequest;
import com.example.springai.service.AnalyticsSqlBuilder.SqlQuery;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalyticsRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AnalyticsRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DataPoint> query(SqlQuery query) {
        return this.jdbcTemplate.query(query.sql(), query.parameters(), (resultSet, rowNum) -> {
            Object rawValue = resultSet.getObject("metric_value");
            BigDecimal value = rawValue instanceof BigDecimal decimal
                    ? decimal : new BigDecimal(rawValue.toString());
            return new DataPoint(resultSet.getString("label"), value);
        });
    }

    public int insert(EventRequest event) {
        String sql = """
            INSERT INTO sales_event (sale_date, region, product, channel, units, revenue)
            VALUES (:saleDate, :region, :product, :channel, :units, :revenue)
            """;
        Map<String, Object> parameters = Map.of(
                "saleDate", event.saleDate(),
                "region", event.region(),
                "product", event.product(),
                "channel", event.channel(),
                "units", event.units(),
                "revenue", event.revenue());
        return this.jdbcTemplate.update(sql, parameters);
    }

    public long count() {
        Long count = this.jdbcTemplate.getJdbcTemplate()
            .queryForObject("SELECT COUNT(*) FROM sales_event", Long.class);
        return count == null ? 0 : count;
    }

}
