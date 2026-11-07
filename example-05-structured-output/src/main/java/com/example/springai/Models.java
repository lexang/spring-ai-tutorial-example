package com.example.springai;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * 示例项目使用的结构化输出模型和请求模型。
 */
public class Models {

    /** 原生输出顶层对象中的电影记录。 */
    public record ActorsFilms(@NotBlank(message = "演员不能为空") String actor,
            @NotEmpty(message = "电影列表不能为空") List<String> movies) {
    }

    /** 顶层对象包装列表，避免使用 OpenAI 不支持的顶层数组 Schema。 */
    public record FilmographyList(@NotEmpty(message = "作品列表不能为空")
            List<@Valid ActorsFilms> films) {
    }

    /** 人员信息，同时演示模型输出后的 Jakarta Validation。 */
    public record Person(
            @NotBlank(message = "姓名不能为空") String name,
            @Min(value = 0, message = "年龄不能小于 0")
            @Max(value = 150, message = "年龄不能大于 150") int age,
            String job
    ) {
    }

    /** 地址信息。 */
    public record Address(String city, String street, String zipCode) {
    }

    /** 使用 JsonPropertyOrder 演示 Schema 属性顺序。 */
    @JsonPropertyOrder({ "name", "age", "department", "salary", "address", "skills" })
    public record Employee(
            String name,
            int age,
            String department,
            double salary,
            Address address,
            List<String> skills
    ) {
    }

    /** 产品信息。 */
    public record Product(
            String name,
            double price,
            List<String> features,
            Map<String, String> specifications
    ) {
    }

    /** 简历教育经历。 */
    public record Education(
            String school,
            String degree,
            String major,
            int graduationYear
    ) {
    }

    /** 简历工作经历。 */
    public record Experience(
            String company,
            String position,
            String duration,
            List<String> responsibilities
    ) {
    }

    /** 简历。邮箱和手机号是模型输出后的业务约束示例。 */
    public record Resume(
            String name,
            @Email(message = "邮箱格式不正确") String email,
            @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确") String phone,
            List<Education> education,
            List<Experience> experience,
            List<String> skills
    ) {
    }

    /** 产品提取请求。 */
    public record DescriptionRequest(
            @NotBlank(message = "description 不能为空") String description
    ) {
    }

    /** 简历解析请求。 */
    public record ResumeRequest(
            @NotBlank(message = "resumeText 不能为空") String resumeText
    ) {
    }

}
