package com.byarrival.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.user.exception.UserErrorCode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ProfileImageProcessorTest {

    private final ProfileImageProcessor processor = new ProfileImageProcessor();

    @Test
    void 큰_사진은_비율을_유지한_채_긴_변_1024px_JPG로_줄인다() throws IOException {
        byte[] jpeg = processor.toProfileJpeg(image(4000, 3000, "jpg", BufferedImage.TYPE_INT_RGB));

        BufferedImage saved = decode(jpeg);
        assertThat(isJpeg(jpeg)).isTrue();
        assertThat(saved.getWidth()).isEqualTo(1024);
        assertThat(saved.getHeight()).isEqualTo(768);
    }

    @Test
    void 작은_사진은_키우지_않는다() throws IOException {
        BufferedImage saved = decode(processor.toProfileJpeg(image(300, 200, "jpg", BufferedImage.TYPE_INT_RGB)));

        assertThat(saved.getWidth()).isEqualTo(300);
        assertThat(saved.getHeight()).isEqualTo(200);
    }

    @Test
    void 촬영_방향_EXIF대로_돌려서_저장한다() throws IOException {
        // 가로 200×100으로 저장됐지만 EXIF 방향 6(시계 방향 90도)이라 화면에는 세로로 보여야 하는 사진
        byte[] rotated = withExifOrientation(image(200, 100, "jpg", BufferedImage.TYPE_INT_RGB), 6);

        BufferedImage saved = decode(processor.toProfileJpeg(rotated));

        assertThat(saved.getWidth()).isEqualTo(100);
        assertThat(saved.getHeight()).isEqualTo(200);
    }

    @Test
    void 폰_사진처럼_JFIF_없이_EXIF로_시작하는_JPG도_촬영_방향대로_돌린다() throws IOException {
        byte[] rotated = withExifOrientation(withoutJfif(image(200, 100, "jpg", BufferedImage.TYPE_INT_RGB)), 6);

        BufferedImage saved = decode(processor.toProfileJpeg(rotated));

        assertThat(saved.getWidth()).isEqualTo(100);
        assertThat(saved.getHeight()).isEqualTo(200);
    }

    @Test
    void 큰_사진도_줄여_읽어서_긴_변_1024px로_저장한다() throws IOException {
        BufferedImage saved = decode(processor.toProfileJpeg(image(6000, 4000, "jpg", BufferedImage.TYPE_INT_RGB)));

        assertThat(saved.getWidth()).isEqualTo(1024);
        assertThat(saved.getHeight()).isBetween(682, 683);
    }

    @Test
    void PNG는_JPG로_바꾸고_투명한_부분은_흰색으로_채운다() throws IOException {
        byte[] jpeg = processor.toProfileJpeg(image(100, 100, "png", BufferedImage.TYPE_INT_ARGB));

        BufferedImage saved = decode(jpeg);
        assertThat(isJpeg(jpeg)).isTrue();
        Color corner = new Color(saved.getRGB(0, 0));
        assertThat(corner.getRed()).isGreaterThan(240);
        assertThat(corner.getGreen()).isGreaterThan(240);
        assertThat(corner.getBlue()).isGreaterThan(240);
    }

    @Test
    void 이미지가_아니거나_JPG_PNG가_아니면_INVALID_PROFILE_IMAGE다() throws IOException {
        assertInvalid("hello".getBytes(StandardCharsets.UTF_8));
        assertInvalid(image(10, 10, "gif", BufferedImage.TYPE_INT_RGB));
        // 형식 표시만 JPG이고 내용이 깨진 파일
        assertInvalid(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02});
    }

    @Test
    void 화소_수가_너무_크면_디코딩하지_않고_거절한다() throws IOException {
        // 1비트 이미지라 만드는 데 메모리가 적게 들고, PNG로 압축하면 파일도 작다
        BufferedImage huge = new BufferedImage(7100, 7100, BufferedImage.TYPE_BYTE_BINARY);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(huge, "png", bytes);

        assertInvalid(bytes.toByteArray());
    }

    private void assertInvalid(byte[] content) {
        assertThatThrownBy(() -> processor.toProfileJpeg(content))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.INVALID_PROFILE_IMAGE));
    }

    private static byte[] image(int width, int height, String format, int type) throws IOException {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        try {
            // 가운데만 칠하고 PNG(ARGB)는 가장자리를 투명하게 둔다
            graphics.setColor(Color.RED);
            graphics.fillRect(width / 4, height / 4, width / 2, height / 2);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, format, bytes);
        return bytes.toByteArray();
    }

    private static byte[] withoutJfif(byte[] jpeg) {
        int end = 4 + (((jpeg[4] & 0xFF) << 8) | (jpeg[5] & 0xFF));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(jpeg, end, jpeg.length - end);
        return out.toByteArray();
    }

    // JFIF(APP0) 구간이 있으면 그 뒤에, 없으면 시작(SOI) 바로 뒤에 방향 태그 하나만 있는 EXIF(APP1) 구간을 넣는다
    private static byte[] withExifOrientation(byte[] jpeg, int orientation) {
        int insertAt = 2;
        if ((jpeg[2] & 0xFF) == 0xFF && (jpeg[3] & 0xFF) == 0xE0) {
            insertAt = 4 + (((jpeg[4] & 0xFF) << 8) | (jpeg[5] & 0xFF));
        }
        byte[] tiff = {
            'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,     // 빅엔디언 TIFF 헤더, 첫 IFD 위치 8
            0x00, 0x01,                                       // 항목 1개
            0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01,   // 0x0112 Orientation, SHORT, 1개
            0x00, (byte) orientation, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00                            // 다음 IFD 없음
        };
        byte[] exifHeader = {'E', 'x', 'i', 'f', 0x00, 0x00};
        int length = 2 + exifHeader.length + tiff.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, insertAt);
        out.write(0xFF);
        out.write(0xE1);
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.writeBytes(exifHeader);
        out.writeBytes(tiff);
        out.write(jpeg, insertAt, jpeg.length - insertAt);
        return out.toByteArray();
    }

    private static BufferedImage decode(byte[] content) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(content));
    }

    private static boolean isJpeg(byte[] content) {
        return (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8;
    }
}
