package com.example.springai.model;

/**
 * 客户数据模型
 */
public record Customer(
        Long id,
        String name,
        String email,
        String tenantId
) {}
