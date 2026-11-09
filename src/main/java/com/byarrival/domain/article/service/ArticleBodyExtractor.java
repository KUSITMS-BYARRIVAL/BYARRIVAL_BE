package com.byarrival.domain.article.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 언론사 기사 페이지에서 본문 영역의 HTML을 찾는다. 다음 순서로 찾는다.
 * <ol>
 *   <li>schema.org 본문 표시(itemprop="articleBody")가 있는 영역</li>
 *   <li>Arc XP(조선일보 등)처럼 본문을 스크립트의 JSON(Fusion.globalContent)으로 내려주는 페이지는 그 JSON의 문단</li>
 *   <li>사이트별 선택자 대신 직접 담고 있는 글자가 가장 많은 블록. 링크 글자가 많은 블록(메뉴, 관련 기사)은 점수를 깎는다.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class ArticleBodyExtractor {

    // form은 지우지 않는다. ASP.NET 사이트(노컷뉴스 등)는 페이지 전체를 form으로 감싼다.
    private static final String NOISE = "script, style, noscript, template, iframe, svg, header, footer, nav, aside, "
        + "button, select, figcaption, [aria-hidden=true]";
    private static final String ARC_CONTENT_PREFIX = "Fusion.globalContent=";
    private static final Set<String> BLOCKS = Set.of("article", "section", "main", "div", "td");
    // 블록이 직접 담은 글자로 세는 자식. 문단과 글자 꾸밈 태그만 센다.
    private static final Set<String> TEXT_CHILDREN = Set.of(
        "p", "span", "b", "strong", "em", "i", "u", "font", "mark", "sub", "sup", "br");

    private final JsonMapper jsonMapper;

    public Optional<String> extract(byte[] html, String pageUrl) {
        return extract(html, null, pageUrl);
    }

    /**
     * @param html    응답 본문
     * @param charset 응답 헤더의 charset. null이면 meta 태그로 판단하고, 없으면 UTF-8로 읽는다.
     * @param pageUrl 상대 주소를 풀 기준 주소
     * @return 본문 영역의 HTML. 찾지 못하면 empty
     */
    public Optional<String> extract(byte[] html, String charset, String pageUrl) {
        Document document = parse(html, charset, pageUrl);
        Optional<String> arcBody = arcBody(document);
        document.select(NOISE).remove();

        Element schemaBody = document.selectFirst("[itemprop=articleBody]");
        if (schemaBody != null && !schemaBody.text().isBlank()) {
            return Optional.of(schemaBody.html());
        }
        if (arcBody.isPresent()) {
            return arcBody;
        }
        return densestBlock(document);
    }

    private Optional<String> densestBlock(Document document) {
        Element best = null;
        double bestScore = 0;
        for (Element element : document.body().getAllElements()) {
            if (!BLOCKS.contains(element.normalName())) {
                continue;
            }
            double score = score(element);
            if (score > bestScore) {
                best = element;
                bestScore = score;
            }
        }
        return Optional.ofNullable(best).map(Element::html);
    }

    private Document parse(byte[] html, String charset, String pageUrl) {
        try {
            return Jsoup.parse(new ByteArrayInputStream(html), charset, pageUrl);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // content_elements 중 type이 text인 문단을 이어 붙인다. JSON이 없거나 형식이 다르면 empty
    private Optional<String> arcBody(Document document) {
        for (Element script : document.select("script")) {
            String data = script.data();
            int start = data.indexOf(ARC_CONTENT_PREFIX);
            if (start < 0) {
                continue;
            }
            try {
                // JSON 뒤에 이어지는 스크립트(";Fusion...")는 무시한다
                JsonNode content = jsonMapper.readerFor(JsonNode.class)
                    .without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(data.substring(start + ARC_CONTENT_PREFIX.length()));
                StringBuilder body = new StringBuilder();
                for (JsonNode element : content.path("content_elements")) {
                    if ("text".equals(element.path("type").asString())) {
                        body.append("<p>").append(element.path("content").asString()).append("</p>");
                    }
                }
                return body.isEmpty() ? Optional.empty() : Optional.of(body.toString());
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private double score(Element block) {
        int ownText = 0;
        for (Node child : block.childNodes()) {
            if (child instanceof TextNode text) {
                ownText += text.text().strip().length();
            } else if (child instanceof Element element && TEXT_CHILDREN.contains(element.normalName())) {
                ownText += element.text().strip().length();
            }
        }
        if (ownText == 0) {
            return 0;
        }
        int linkText = block.select("a").text().length();
        double linkDensity = Math.min(1.0, (double) linkText / Math.max(1, block.text().length()));
        return ownText * (1 - linkDensity);
    }
}
