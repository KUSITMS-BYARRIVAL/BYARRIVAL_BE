package com.byarrival.domain.feed.service;

import com.byarrival.common.config.ClockConfig;
import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.feed.dto.RssItem;
import com.byarrival.domain.feed.exception.FeedErrorCode;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * RSS 2.0 원문(XML)을 정규화 기사 데이터로 바꾼다. 필수값(제목, 원문 URL, 발행일)이 없는 Item은 제외한다.
 */
@Slf4j
@Component
public class RssParser {

    private static final String CONTENT_NAMESPACE = "http://purl.org/rss/1.0/modules/content/";
    private static final String DUBLIN_CORE_NAMESPACE = "http://purl.org/dc/elements/1.1/";
    private static final ZoneId KST = ZoneId.of(ClockConfig.KST);
    // 시간대가 없는 날짜(예: 2026-11-06 10:00:00)는 KST로 본다
    private static final DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd[ ]['T']HH:mm[:ss]");

    private final XMLInputFactory xmlInputFactory;

    public RssParser() {
        xmlInputFactory = XMLInputFactory.newFactory();
        // 외부 엔티티·DTD를 처리하지 않는다 (XXE 차단)
        xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        xmlInputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        xmlInputFactory.setProperty(XMLInputFactory.IS_COALESCING, true);
    }

    /**
     * @throws BusinessException XML이 아니거나 RSS 2.0 문서가 아니면 {@link FeedErrorCode#FEED_PARSE_FAILED}
     */
    public List<RssItem> parse(Long feedId, byte[] content) {
        XMLStreamReader reader = null;
        try {
            reader = xmlInputFactory.createXMLStreamReader(new ByteArrayInputStream(content));
            moveToRoot(reader);
            if (!isRss(reader, "rss")) {
                throw failed("RSS 문서가 아닙니다. root=" + reader.getLocalName());
            }
            return readItems(feedId, reader);
        } catch (XMLStreamException | RuntimeException e) {
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw failed(e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            close(reader);
        }
    }

    private void moveToRoot(XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                return;
            }
        }
        throw failed("root 요소가 없습니다.");
    }

    private List<RssItem> readItems(Long feedId, XMLStreamReader reader) throws XMLStreamException {
        List<RssItem> items = new ArrayList<>();
        int skipped = 0;
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT && isRss(reader, "item")) {
                Optional<RssItem> item = readItem(feedId, reader);
                if (item.isPresent()) {
                    items.add(item.get());
                } else {
                    skipped++;
                }
            }
        }
        if (skipped > 0) {
            log.warn("[RssParse] feedId={} 필수값 누락 Item {}건 제외", feedId, skipped);
        }
        return items;
    }

    private Optional<RssItem> readItem(Long feedId, XMLStreamReader reader) throws XMLStreamException {
        String title = null;
        String link = null;
        String guid = null;
        boolean guidIsPermaLink = true;
        String description = null;
        String encodedContent = null;
        String pubDate = null;
        String dcDate = null;
        String source = null;
        List<String> categories = new ArrayList<>();

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            String namespace = namespaceOf(reader);
            String name = reader.getLocalName();
            String isPermaLink = reader.getAttributeValue(null, "isPermaLink");
            String text = readText(reader);
            if (namespace.isEmpty()) {
                switch (name) {
                    case "title" -> title = text;
                    case "link" -> link = text;
                    case "guid" -> {
                        guid = text;
                        guidIsPermaLink = !"false".equalsIgnoreCase(isPermaLink);
                    }
                    case "description" -> description = text;
                    case "pubDate" -> pubDate = text;
                    case "source" -> source = text;
                    case "category" -> {
                        if (text != null) {
                            categories.add(text);
                        }
                    }
                    default -> {
                    }
                }
            } else if (CONTENT_NAMESPACE.equals(namespace) && "encoded".equals(name)) {
                encodedContent = text;
            } else if (DUBLIN_CORE_NAMESPACE.equals(namespace) && "date".equals(name)) {
                dcDate = text;
            }
        }

        // RSS 2.0에서 isPermaLink가 false가 아닌 guid는 원문 URL이다
        if (link == null && guidIsPermaLink && isHttpUrl(guid)) {
            link = guid;
        }
        LocalDateTime publishedAt = parseDate(pubDate);
        if (publishedAt == null) {
            publishedAt = parseDate(dcDate);
        }
        if (title == null || !isHttpUrl(link) || publishedAt == null) {
            log.warn("[RssParse] feedId={} 필수값 누락 - title={}, link={}, pubDate={}", feedId, title, link,
                pubDate != null ? pubDate : dcDate);
            return Optional.empty();
        }
        String content = encodedContent != null ? encodedContent : description;
        return Optional.of(new RssItem(guid, link, stripSourceSuffix(title, source), content, publishedAt,
            categories, source));
    }

    // 구글 뉴스는 제목 끝에 " - 언론사"를 붙인다. source와 같을 때만 뗀다.
    private String stripSourceSuffix(String title, String source) {
        if (source == null) {
            return title;
        }
        String suffix = " - " + source;
        if (title.endsWith(suffix) && title.length() > suffix.length()) {
            return title.substring(0, title.length() - suffix.length()).strip();
        }
        return title;
    }

    // 하위 요소의 텍스트까지 모두 이어 붙인다. 끝나면 reader는 이 요소의 END_ELEMENT에 있다.
    private String readText(XMLStreamReader reader) throws XMLStreamException {
        StringBuilder text = new StringBuilder();
        int depth = 1;
        while (depth > 0) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT -> depth++;
                case XMLStreamConstants.END_ELEMENT -> depth--;
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE ->
                    text.append(reader.getText());
                default -> {
                }
            }
        }
        String stripped = text.toString().strip();
        return stripped.isEmpty() ? null : stripped;
    }

    static LocalDateTime parseDate(String value) {
        if (value == null) {
            return null;
        }
        String date = value.strip();
        if (date.endsWith(" KST")) {
            date = date.substring(0, date.length() - " KST".length()) + " +0900";
        }
        try {
            return ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME)
                .withZoneSameInstant(KST).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // 다음 형식으로 시도한다
        }
        try {
            return OffsetDateTime.parse(date, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .atZoneSameInstant(KST).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // 다음 형식으로 시도한다
        }
        try {
            return LocalDateTime.parse(date, LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private boolean isHttpUrl(String value) {
        if (value == null) {
            return false;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            return uri.getHost() != null
                && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private boolean isRss(XMLStreamReader reader, String localName) {
        return namespaceOf(reader).isEmpty() && localName.equals(reader.getLocalName());
    }

    private String namespaceOf(XMLStreamReader reader) {
        String namespace = reader.getNamespaceURI();
        return namespace == null ? "" : namespace;
    }

    private void close(XMLStreamReader reader) {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (XMLStreamException e) {
            log.debug("[RssParse] reader 닫기 실패 - {}", e.getMessage());
        }
    }

    private BusinessException failed(String message) {
        return new BusinessException(FeedErrorCode.FEED_PARSE_FAILED, message);
    }
}
