package com.example.springai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库查询工具类
 *
 * 演示数据库查询工具的实现
 * 注意: 此示例使用内存数据模拟数据库,实际应用中应使用 JdbcTemplate 或 JPA
 */
@Component
public class DatabaseTools {

    private static final Logger log = LoggerFactory.getLogger(DatabaseTools.class);

    // 模拟用户数据库 (实际应用中使用真实的数据库)
    private final Map<Long, User> userDatabase = new ConcurrentHashMap<>();

    /**
     * 构造函数 - 初始化示例数据
     */
    public DatabaseTools() {
        // 添加一些示例用户
        userDatabase.put(1L, new User(1L, "张三", "zhangsan@example.com", "13800138001"));
        userDatabase.put(2L, new User(2L, "李四", "lisi@example.com", "13800138002"));
        userDatabase.put(3L, new User(3L, "王五", "wangwu@example.com", "13800138003"));
        userDatabase.put(123L, new User(123L, "赵六", "zhaoliu@example.com", "13800138123"));

        log.info("DatabaseTools 初始化完成,已加载 {} 个示例用户", userDatabase.size());
    }

    /**
     * 根据用户 ID 查询用户信息
     *
     * @param userId 用户 ID
     * @return 用户信息的文本描述
     */
    @Tool(description = "根据用户 ID 查询用户的详细信息,包括姓名、邮箱和电话")
    public String getUserById(
            @ToolParam(description = "用户 ID,必须是正整数")
            Long userId) {

        log.info("工具被调用: getUserById, 参数: userId={}", userId);

        // 参数验证
        if (userId == null || userId <= 0) {
            return "错误: 用户 ID 必须是正整数";
        }

        // 查询用户
        User user = userDatabase.get(userId);
        if (user == null) {
            log.info("未找到用户 ID: {}", userId);
            return "未找到用户 ID: " + userId;
            //     throw new CustomerNotFoundException("Customer " + id + " not found");
        }

        String result = String.format("""
                用户信息:
                ID: %d
                姓名: %s
                邮箱: %s
                电话: %s
                """, user.id(), user.name(), user.email(), user.phone());

        log.info("查询用户成功: userId={}", userId);
        return result;
    }

    /**
     * 根据姓名搜索用户
     *
     * @param name 用户姓名,支持模糊查询
     * @return 匹配的用户列表
     */
    @Tool(description = "根据姓名搜索用户,支持模糊匹配,返回所有匹配的用户列表")
    public String searchUsersByName(
            @ToolParam(description = "用户姓名,支持部分匹配,例如: 张")
            String name) {

        log.info("工具被调用: searchUsersByName, 参数: name={}", name);

        // 参数验证
        if (name == null || name.trim().isEmpty()) {
            return "错误: 姓名不能为空";
        }

        // 搜索用户
        List<User> matchedUsers = userDatabase.values().stream()
                .filter(user -> user.name().contains(name))
                .sorted(Comparator.comparing(User::id))
                .toList();

        if (matchedUsers.isEmpty()) {
            log.info("未找到匹配的用户: name={}", name);
            return "未找到姓名包含 \"" + name + "\" 的用户";
        }

        StringBuilder result = new StringBuilder();
        result.append("找到 ").append(matchedUsers.size()).append(" 个匹配的用户:\n\n");

        for (User user : matchedUsers) {
            result.append(String.format("ID: %d, 姓名: %s, 邮箱: %s, 电话: %s\n",
                    user.id(), user.name(), user.email(), user.phone()));
        }

        log.info("搜索用户成功: name={}, 找到 {} 个用户", name, matchedUsers.size());
        return result.toString();
    }

    /**
     * 统计用户总数
     *
     * @return 用户总数
     */
    @Tool(description = "统计系统中的用户总数")
    public int countUsers() {
        log.info("工具被调用: countUsers");

        int count = userDatabase.size();
        log.info("用户总数: {}", count);
        return count;
    }

    /**
     * 列出所有用户
     *
     * @return 所有用户的列表
     */
    @Tool(description = "列出系统中所有用户的信息")
    public String listAllUsers() {
        log.info("工具被调用: listAllUsers");

        List<User> allUsers = userDatabase.values().stream()
                .sorted(Comparator.comparing(User::id))
                .toList();

        if (allUsers.isEmpty()) {
            return "系统中没有用户";
        }

        StringBuilder result = new StringBuilder();
        result.append("系统中共有 ").append(allUsers.size()).append(" 个用户:\n\n");

        for (User user : allUsers) {
            result.append(String.format("ID: %d, 姓名: %s, 邮箱: %s\n",
                    user.id(), user.name(), user.email()));
        }

        log.info("列出所有用户成功,共 {} 个", allUsers.size());
        return result.toString();
    }

    /**
     * 用户数据模型
     */
    public record User(Long id, String name, String email, String phone) {}
}
