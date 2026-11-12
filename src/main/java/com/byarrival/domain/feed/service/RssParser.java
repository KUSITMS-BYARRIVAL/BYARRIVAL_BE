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
import java.util.Set;
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
    // RSS 2.0은 0.91·0.92와 호환된다. version이 없는 문서는 막지 않는다.
    private static final Set<String> SUPPORTED_VERSIONS = Set.of("2.0", "0.91", "0.92");
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
            String version = reader.getAttributeValue(null, "version");
            if (version != null && !SUPPORTED_VERSIONS.contains(version.strip())) {
                throw failed("지원하지 않는 RSS 버전입니다. version=" + version);
            }
            return readChannel(feedId, reader);
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

    // rss 바로 아래의 channel에서 channel 바로 아래의 item만 읽는다. channel이 없으면 RSS 문서로 보지 않는다.
    private List<RssItem> readChannel(Long feedId, XMLStreamReader reader) throws XMLStreamException {
        List<RssItem> items = new ArrayList<>();
        int skipped = 0;
        boolean hasChannel = false;
        boolean inChannel = false;
        int depth = 0;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT) {
                if (depth == 0) {
                    break;
                }
                if (depth == 1) {
                    inChannel = false;
                }
                depth--;
                continue;
            }
            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            depth++;
            if (depth == 1 && isRss(reader, "channel")) {
                hasChannel = true;
                inChannel = true;
            } else if (depth == 2 && inChannel && isRss(reader, "item")) {
                // readItem은 item의 END_ELEMENT까지 읽는다
                depth--;
                Optional<RssItem> item = readItem(feedId, reader);
                if (item.isPresent()) {
                    items.add(item.get());
                } else {
                    skipped++;
                }
            }
        }
        if (!hasChannel) {
            throw failed("rss 문서에 channel이 없습니다.");
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
    // 언론사가 원래 제목에 붙여 둔 " - 언론사"까지 겹쳐 오기도 하므로(예: "제목 - 머니투데이 - 머니투데이") 남지 않을 때까지 뗀다.
    private String stripSourceSuffix(String title, String source) {
        if (source == null) {
            return title;
        }
        String suffix = " - " + source;
        String stripped = title;
        while (stripped.endsWith(suffix) && stripped.length() > suffix.length()) {
            stripped = stripped.substring(0, stripped.length() - suffix.length()).strip();
        }
        return stripped;
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
