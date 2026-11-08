package com.byarrival.domain.article.service;

import com.byarrival.domain.article.config.ArticleCrawlProperties;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 제목과 본문이 서비스에 내보낼 만한 한국어 글인지 본다. 외국어 기사와 깨진 제목을 걸러 AI 요약에 잡음이 들어가지 않게 한다.
 */
@Component
@RequiredArgsConstructor
public class ArticleTextInspector {

    private static final Set<Character.UnicodeScript> CHARACTER_SCRIPTS = Set.of(
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA);

    // 제목의 [사진]·[포토]·【화보】 같은 말머리. 사진 위주 기사라 본문이 없어 요약할 내용이 없다.
    private static final Pattern PHOTO_TAG = Pattern.compile(
        "[\\[【(]\\s*(사진|포토|화보|포토뉴스|포토슬라이드|photo)\\s*[]】)]", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_REMNANT = Pattern.compile(
        "<[/!]?[a-zA-Z][^>]*>|&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,31});");

    private final ArticleCrawlProperties properties;

    /**
     * 깨진 글자(U+FFFD), 제어 문자, HTML 태그·엔티티가 남아 있거나 한글 비율이 낮으면 읽을 수 없는 제목으로 본다.
     */
    public boolean isReadableTitle(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        boolean broken = title.codePoints()
            .anyMatch(c -> c == 0xFFFD || (Character.isISOControl(c) && c != '\t'));
        return !broken && !HTML_REMNANT.matcher(title).find() && isKorean(title);
    }

    public boolean isPhotoArticle(String title) {
        return title != null && PHOTO_TAG.matcher(title).find();
    }

    public boolean isKorean(String text) {
        return hangulRatio(text) >= properties.minHangulRatio();
    }

    // 한글은 음절 하나를, 외국어는 이어진 단어 하나를 한 단위로 센 한글 비율. 숫자·기호는 세지 않는다.
    // "Discord, Electron을 버리고"처럼 영어 단어가 섞인 한국어 제목이 글자 수 때문에 외국어로 보이지 않게 한다.
    // 한자·가나는 띄어쓰기 없이 이어 쓰므로 글자 하나를 한 단위로 센다(중국어·일본어 본문이 한 단어로 세지지 않게).
    static double hangulRatio(String text) {
        long units = 0;
        long hangul = 0;
        boolean inForeignWord = false;
        for (int c : text.codePoints().toArray()) {
            if (!Character.isLetter(c)) {
                inForeignWord = false;
                continue;
            }
            Character.UnicodeScript script = Character.UnicodeScript.of(c);
            if (script == Character.UnicodeScript.HANGUL) {
                units++;
                hangul++;
                inForeignWord = false;
            } else if (CHARACTER_SCRIPTS.contains(script)) {
                units++;
                inForeignWord = false;
            } else if (!inForeignWord) {
                units++;
                inForeignWord = true;
            }
        }
        return units == 0 ? 0 : (double) hangul / units;
    }
}
