package com.example.springai;

import java.util.Objects;

/**
 * 相似度计算工具类
 *
 * 提供多种向量相似度计算方法:
 * - 余弦相似度 (Cosine Similarity)
 * - 欧几里得距离 (Euclidean Distance)
 * - 点积 (Dot Product)
 *
 * @author Spring AI Tutorials
 */
public class SimilarityUtils {

    /**
     * 计算余弦相似度
     *
     * 余弦相似度衡量两个向量之间的夹角,范围 [-1, 1]
     * - 1: 方向相同（不表示原文本相同）
     * - 0: 正交(无关)
     * - -1: 完全相反
     *
     * 公式: cos(θ) = (A · B) / (||A|| * ||B||)
     *
     * @param a 向量 A
     * @param b 向量 B
     * @return 余弦相似度值 [-1, 1]
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        validateVectors(a, b);

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 计算欧几里得距离
     *
     * 欧几里得距离衡量两个向量在空间中的直线距离
     * - 0: 完全相同
     * - 值越大: 差异越大
     *
     * 公式: d = sqrt(Σ(ai - bi)²)
     *
     * @param a 向量 A
     * @param b 向量 B
     * @return 欧几里得距离(非负数)
     */
    public static double euclideanDistance(float[] a, float[] b) {
        validateVectors(a, b);

        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }

        return Math.sqrt(sum);
    }

    /**
     * 计算点积(内积)
     *
     * 点积是两个向量对应元素乘积的和
     * - 值越大: 向量越相似
     *
     * 公式: A · B = Σ(ai * bi)
     *
     * @param a 向量 A
     * @param b 向量 B
     * @return 点积值
     */
    public static double dotProduct(float[] a, float[] b) {
        validateVectors(a, b);

        double product = 0.0;
        for (int i = 0; i < a.length; i++) {
            product += (double) a[i] * b[i];
        }

        return product;
    }

    /**
     * 将欧几里得距离转换为相似度分数
     *
     * 距离越小,相似度越高
     * 使用 1 / (1 + distance) 转换为 [0, 1] 范围
     *
     * @param distance 欧几里得距离
     * @return 相似度分数 [0, 1]
     */
    public static double distanceToSimilarity(double distance) {
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException("距离必须是非负有限数");
        }
        return 1.0 / (1.0 + distance);
    }

    /**
     * 归一化向量(使其长度为 1)
     *
     * @param vector 输入向量
     * @return 归一化后的向量
     */
    public static float[] normalize(float[] vector) {
        Objects.requireNonNull(vector, "向量不能为空");
        double norm = 0.0;
        for (float v : vector) {
            if (!Float.isFinite(v)) {
                throw new IllegalArgumentException("向量必须只包含有限数值");
            }
            norm += (double) v * v;
        }
        norm = Math.sqrt(norm);

        if (norm == 0.0) {
            return vector.clone();
        }

        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }

        return normalized;
    }

    private static void validateVectors(float[] a, float[] b) {
        Objects.requireNonNull(a, "向量 A 不能为空");
        Objects.requireNonNull(b, "向量 B 不能为空");
        if (a.length != b.length) {
            throw new IllegalArgumentException("向量维度必须相同");
        }
        for (int i = 0; i < a.length; i++) {
            if (!Float.isFinite(a[i]) || !Float.isFinite(b[i])) {
                throw new IllegalArgumentException("向量必须只包含有限数值");
            }
        }
    }
}
