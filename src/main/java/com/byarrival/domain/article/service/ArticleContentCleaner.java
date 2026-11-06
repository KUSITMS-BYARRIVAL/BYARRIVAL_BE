package com.byarrival.domain.article.service;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 원본 기사 본문에서 HTML과 불필요한 문자열을 지워 가공용 텍스트를 만든다.
 */
@Component
public class ArticleContentCleaner {

    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern SCRIPT_OR_STYLE = Pattern.compile(
        "<(script|style)\\b[^>]*>.*?</\\1\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern LINE_BREAK_TAG = Pattern.compile(
        "<(br|/p|/div|/li|/h[1-6]|/tr|/blockquote)\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<[/!]?[a-zA-Z][^>]*>");
    private static final Pattern ENTITY = Pattern.compile("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,31});");
    private static final Pattern HORIZONTAL_SPACES = Pattern.compile("[ \\t\\x0B\\f\\u00A0\\u3000]+");
    private static final Pattern ZERO_WIDTH = Pattern.compile("[\\u200B\\u200C\\u200D\\uFEFF]");
    private static final Map<String, String> NAMED_ENTITIES = Map.ofEntries(
        Map.entry("amp", "&"), Map.entry("lt", "<"), Map.entry("gt", ">"), Map.entry("quot", "\""),
        Map.entry("apos", "'"), Map.entry("nbsp", " "), Map.entry("middot", "·"), Map.entry("hellip", "…"),
        Map.entry("lsquo", "‘"), Map.entry("rsquo", "’"), Map.entry("ldquo", "“"), Map.entry("rdquo", "”"),
        Map.entry("ndash", "–"), Map.entry("mdash", "—"), Map.entry("bull", "•"), Map.entry("laquo", "«"),
        Map.entry("raquo", "»"), Map.entry("copy", "©"), Map.entry("reg", "®"), Map.entry("trade", "™"),
        Map.entry("times", "×"), Map.entry("deg", "°"));

    /**
     * @return 정제한 본문. 원본이 없거나 정제 결과가 비면 empty
     */
    public Optional<String> clean(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return Optional.empty();
        }
        String text = COMMENT.matcher(rawContent).replaceAll("");
        text = SCRIPT_OR_STYLE.matcher(text).replaceAll("");
        text = LINE_BREAK_TAG.matcher(text).replaceAll("\n");
        text = TAG.matcher(text).replaceAll("");
        // 태그를 지운 뒤 디코딩해야 본문에 쓰인 "&lt;b&gt;" 같은 글자가 태그로 지워지지 않는다
        text = ENTITY.matcher(text).replaceAll(match -> Matcher.quoteReplacement(decode(match)));
        text = normalizeWhitespace(text);
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    private String decode(MatchResult match) {
        String entity = match.group(1);
        if (entity.charAt(0) != '#') {
            return NAMED_ENTITIES.getOrDefault(entity, match.group());
        }
        boolean hex = entity.length() > 1 && (entity.charAt(1) == 'x' || entity.charAt(1) == 'X');
        int codePoint = Integer.parseInt(entity.substring(hex ? 2 : 1), hex ? 16 : 10);
        if (codePoint == 0 || !Character.isValidCodePoint(codePoint)) {
            return match.group();
        }
        return new String(Character.toChars(codePoint));
    }

    private String normalizeWhitespace(String text) {
        String normalized = ZERO_WIDTH.matcher(text.replace("\r\n", "\n").replace('\r', '\n')).replaceAll("");
        return Arrays.stream(normalized.split("\n"))
            .map(line -> HORIZONTAL_SPACES.matcher(line).replaceAll(" ").strip())
            .filter(line -> !line.isEmpty())
            .collect(Collectors.joining("\n"));
    }
}
