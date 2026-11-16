package com.byarrival.domain.user.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.user.exception.UserErrorCode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.util.exif.ExifFilterUtils;
import net.coobird.thumbnailator.util.exif.ExifUtils;
import net.coobird.thumbnailator.util.exif.Orientation;
import org.springframework.stereotype.Component;

/**
 * 프로필 이미지를 검증하고 저장용 JPG로 바꾼다(MY-02).
 * JPG·PNG만 받고, 확장자·Content-Type 대신 파일 내용으로 형식을 확인한 뒤 실제로 디코딩한다.
 * 촬영 방향(EXIF)대로 돌리고 긴 변을 1024px 이하로 줄인다. 다시 인코딩하므로 위치정보 같은 메타데이터는 남지 않는다.
 * 큰 사진은 줄여 읽어서, 서버 메모리(-Xmx768m)에서 여러 장을 동시에 받아도 버틴다.
 */
@Component
public class ProfileImageProcessor {

    static final int MAX_LONG_SIDE = 1024;
    // 디코딩 메모리를 막는 상한. 4800만 화소 폰 사진(8064×6048)까지 받는다
    static final long MAX_PIXELS = 50_000_000L;
    private static final float JPEG_QUALITY = 0.85f;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    public byte[] toProfileJpeg(byte[] content) {
        if (!isJpeg(content) && !isPng(content)) {
            throw invalid();
        }
        try {
            BufferedImage decoded = decode(content);
            BufferedImage resized = Math.max(decoded.getWidth(), decoded.getHeight()) > MAX_LONG_SIDE
                ? Thumbnails.of(decoded).size(MAX_LONG_SIDE, MAX_LONG_SIDE).asBufferedImage()
                : decoded;
            return writeJpeg(onWhite(resized));
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw invalid();
        }
    }

    // 헤더로 크기를 먼저 확인하고, 큰 사진은 건너뛰며 읽어(서브샘플링) 메모리를 줄인다. 촬영 방향(EXIF)대로 돌린다
    private static BufferedImage decode(byte[] content) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalid();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, false);
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width * height > MAX_PIXELS) {
                    throw invalid();
                }
                // 줄인 뒤에도 긴 변이 최종 크기의 2배 이상 남도록 건너뛴다
                int step = (int) Math.max(1, Math.max(width, height) / (MAX_LONG_SIDE * 2L));
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(step, step, 0, 0);
                Orientation orientation = orientationOf(reader);
                BufferedImage image = reader.read(0, param);
                return orientation == null ? image : ExifFilterUtils.getFilterForOrientation(orientation).apply(image);
            } finally {
                reader.dispose();
            }
        }
    }

    // EXIF가 없거나 읽지 못하면 돌리지 않는다
    private static Orientation orientationOf(ImageReader reader) {
        try {
            return ExifUtils.getExifOrientation(reader, 0);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // JPG에는 투명도가 없어서 PNG의 투명한 부분은 흰색으로 채운다
    private static BufferedImage onWhite(BufferedImage image) {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgb;
    }

    private static byte[] writeJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static boolean isJpeg(byte[] content) {
        return content.length > 3 && (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8
            && (content[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] content) {
        if (content.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (content[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private static BusinessException invalid() {
        return new BusinessException(UserErrorCode.INVALID_PROFILE_IMAGE);
    }
}
