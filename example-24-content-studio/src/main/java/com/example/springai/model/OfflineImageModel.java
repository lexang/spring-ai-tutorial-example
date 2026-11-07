package com.example.springai.model;

import java.util.Base64;
import java.util.List;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;

/** 返回可解码的一像素 PNG，仅用于离线媒体契约，不是生成式图片。 */
public final class OfflineImageModel implements ImageModel {

    private static final String PNG = createPng();

    private static String createPng() {
        try {
            BufferedImage pixel = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            pixel.setRGB(0, 0, 0x336699);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(pixel, "png", output)) throw new IllegalStateException("未找到 PNG 编码器");
            return Base64.getEncoder().encodeToString(output.toByteArray());
        }
        catch (java.io.IOException exception) { throw new IllegalStateException("离线 PNG 编码失败", exception); }
    }

    @Override
    public ImageResponse call(ImagePrompt request) {
        return new ImageResponse(List.of(new ImageGeneration(new Image(null, PNG))));
    }

}
