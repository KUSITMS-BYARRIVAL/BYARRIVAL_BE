package com.byarrival.domain.admin.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.time.ServiceDay;
import com.byarrival.domain.admin.dto.response.AdminEditorPickResponses.EditorPickList;
import com.byarrival.domain.admin.dto.response.AdminEditorPickResponses.Item;
import com.byarrival.domain.admin.dto.response.AdminEditorPickResponses.LinkableArticle;
import com.byarrival.domain.admin.dto.response.AdminEditorPickResponses.PickArticle;
import com.byarrival.domain.admin.entity.AdminAccount;
import com.byarrival.domain.admin.entity.AdminEditorPickStatus;
import com.byarrival.domain.admin.exception.AdminErrorCode;
import com.byarrival.domain.admin.repository.AdminAccountRepository;
import com.byarrival.domain.admin.repository.AdminEditorPickRepository;
import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.service.ArticleQueryService;
import com.byarrival.domain.category.service.CategoryMappingService;
import com.byarrival.domain.smalltalk.entity.Smalltalk;
import com.byarrival.domain.smalltalk.service.SmalltalkEditService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 어드민 에디터 픽(스몰토크) 관리(EDITOR-01·03·04·05·07).
 * <ul>
 *     <li>배치가 만든 에디터 픽은 승인 없이 노출되고, 운영자가 고친 내용이 우선한다. 배치는 이미 있는 것을 건드리지 않는다.</li>
 *     <li>지난 노출일의 에디터 픽은 바꿀 수 없다. 오늘 것은 바꾸면 바로 앱에 반영된다.</li>
 *     <li>연결 기사는 오늘 노출 중이거나 노출일 당일에 노출할 기사만 고를 수 있고, 기사당 에디터 픽은 하나다.</li>
 *     <li>16시 알림은 하루 한 번이라, 보낸 뒤에 고쳐도 다시 보내지 않는다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AdminEditorPickService {

    private final SmalltalkEditService smalltalkEditService;
    private final ArticleQueryService articleQueryService;
    private final CategoryMappingService categoryMappingService;
    private final AdminEditorPickRepository editorPickRepository;
    private final AdminAccountRepository adminAccountRepository;
    private final Clock clock;

    /**
     * @param serviceDate 노출일. 비어 있으면 내일
     */
    @Transactional(readOnly = true)
    public EditorPickList getEditorPicks(LocalDate serviceDate) {
        LocalDate date = serviceDate == null ? defaultServiceDate() : serviceDate;
        return listOf(date);
    }

    /**
     * @param serviceDate 노출일. 비어 있으면 내일
     */
    @Transactional(readOnly = true)
    public List<LinkableArticle> getLinkableArticles(LocalDate serviceDate, String keyword) {
        LocalDate date = serviceDate == null ? defaultServiceDate() : serviceDate;
        Map<Long, String> categoryNames = categoryMappingService.getActiveCategoryNames();
        return editorPickRepository.findLinkable(ServiceDay.today(clock), date, keyword).stream()
            .map(row -> new LinkableArticle(row.id(), row.title(), row.pressName(),
                categoryNames.get(row.categoryId()), row.serviceDate()))
            .toList();
    }

    @Transactional
    public Item create(Long adminId, LocalDate serviceDate, Long articleId, String hook) {
        LocalDate date = serviceDate == null ? defaultServiceDate() : serviceDate;
        requireEditable(date);
        requireLinkable(articleId, date);
        if (smalltalkEditService.isArticleTaken(articleId)) {
            throw new BusinessException(AdminErrorCode.ADMIN_EDITOR_PICK_ARTICLE_TAKEN);
        }
        Smalltalk created = smalltalkEditService.create(articleId, date, hook.strip(), adminId, now());
        return itemOf(created.getId(), date);
    }

    @Transactional
    public Item update(Long adminId, Long editorPickId, LocalDate serviceDate, Long articleId, String hook) {
        Smalltalk pick = getEditable(editorPickId);
        requireEditable(serviceDate);
        if (!pick.getArticleId().equals(articleId)) {
            requireLinkable(articleId, serviceDate);
            if (smalltalkEditService.isArticleTaken(articleId)) {
                throw new BusinessException(AdminErrorCode.ADMIN_EDITOR_PICK_ARTICLE_TAKEN);
            }
        } else if (!pick.getServiceDate().equals(serviceDate)) {
            // 기사는 그대로 두고 노출일만 옮겨도 옮긴 날에 연결할 수 있는 기사인지 다시 본다
            requireLinkable(articleId, serviceDate);
        }
        pick.edit(articleId, serviceDate, hook.strip(), adminId, now());
        return itemOf(pick.getId(), serviceDate);
    }

    /**
     * 이미 숨겼어도 성공이다.
     */
    @Transactional
    public Item hide(Long editorPickId) {
        Smalltalk pick = getEditable(editorPickId);
        pick.hide();
        return itemOf(pick.getId(), pick.getServiceDate());
    }

    /**
     * 숨기지 않았어도 성공이다. 연결 기사가 숨겨져 있으면 여전히 숨김으로 보인다.
     */
    @Transactional
    public Item restore(Long editorPickId) {
        Smalltalk pick = getEditable(editorPickId);
        pick.restore();
        return itemOf(pick.getId(), pick.getServiceDate());
    }

    /**
     * 앱에서 바로 사라진다. 삭제한 자리는 배치가 다시 채우지 않는다.
     */
    @Transactional
    public void delete(Long adminId, Long editorPickId) {
        getEditable(editorPickId).delete(adminId, now());
    }

    @Transactional
    public EditorPickList reorder(LocalDate serviceDate, List<Long> editorPickIds) {
        requireEditable(serviceDate);
        List<Smalltalk> picks = smalltalkEditService.findEditorPicks(serviceDate);
        Map<Long, Smalltalk> byId = picks.stream().collect(Collectors.toMap(Smalltalk::getId, Function.identity()));
        if (editorPickIds.size() != picks.size() || !byId.keySet().equals(new HashSet<>(editorPickIds))) {
            throw new BusinessException(AdminErrorCode.ADMIN_EDITOR_PICK_ORDER_MISMATCH);
        }
        smalltalkEditService.reorder(editorPickIds.stream().map(byId::get).toList());
        return listOf(serviceDate);
    }

    private EditorPickList listOf(LocalDate serviceDate) {
        List<Smalltalk> picks = smalltalkEditService.findEditorPicks(serviceDate);
        Map<Long, Article> articles = articleQueryService.findAllByIds(
            picks.stream().map(Smalltalk::getArticleId).toList());
        Set<Long> unavailable = articleQueryService.findUnavailableIds(articles.keySet());
        Map<Long, String> categoryNames = categoryMappingService.getActiveCategoryNames();
        Map<Long, String> adminEmails = findAdminEmails(picks);
        List<Item> items = new ArrayList<>();
        for (Smalltalk pick : picks) {
            items.add(toItem(pick, items.size() + 1, articles.get(pick.getArticleId()), unavailable, categoryNames,
                adminEmails));
        }
        return new EditorPickList(serviceDate, isEditable(serviceDate), items);
    }

    private Item itemOf(Long editorPickId, LocalDate serviceDate) {
        return listOf(serviceDate).picks().stream()
            .filter(item -> item.editorPickId().equals(editorPickId))
            .findFirst()
            .orElseThrow(() -> new BusinessException(AdminErrorCode.ADMIN_EDITOR_PICK_NOT_FOUND));
    }

    private static Item toItem(Smalltalk pick, int order, Article article, Set<Long> unavailable,
        Map<Long, String> categoryNames, Map<Long, String> adminEmails) {
        boolean articleAvailable = article != null && !unavailable.contains(article.getId());
        AdminEditorPickStatus status;
        if (pick.isHidden() || !articleAvailable) {
            status = AdminEditorPickStatus.HIDDEN;
        } else if (pick.isEditedByAdmin()) {
            status = AdminEditorPickStatus.EDITED;
        } else {
            status = AdminEditorPickStatus.EXPOSED;
        }
        PickArticle articleResponse = article == null ? null
            : new PickArticle(article.getId(),
                article.getTitle(), article.getPressName(), categoryNames.get(article.getCategoryId()),
                article.getServiceDate(), articleAvailable, article.linkUrl());
        return new Item(pick.getId(), order, pick.getHook(), status, pick.isHidden(), pick.getServiceDate(),
            articleResponse, adminEmails.get(pick.getCreatedByAdminId()), pick.getEditedAt(),
            adminEmails.get(pick.getEditedByAdminId()));
    }

    private Smalltalk getEditable(Long editorPickId) {
        Smalltalk pick = smalltalkEditService.findEditorPick(editorPickId)
            .orElseThrow(() -> new BusinessException(AdminErrorCode.ADMIN_EDITOR_PICK_NOT_FOUND));
        requireEditable(pick.getServiceDate());
        return pick;
    }

    private void requireEditable(LocalDate serviceDate) {
        if (!isEditable(serviceDate)) {
            throw new BusinessException(AdminErrorCode.ADMIN_EDITOR_PICK_PAST);
        }
    }

    private boolean isEditable(LocalDate serviceDate) {
        return !serviceDate.isBefore(ServiceDay.today(clock));
    }

    // 오늘 노출 중이거나 노출일 당일에 노출할 추천 후보 기사만 연결한다
    private void requireLinkable(Long articleId, LocalDate serviceDate) {
        Article article = articleQueryService.findAllByIds(List.of(articleId)).get(articleId);
        boolean linkable = article != null
            && article.isRecommendable()
            && article.getCategoryId() != null
            && (article.getServiceDate().equals(ServiceDay.today(clock)) || article.getServiceDate().equals(serviceDate))
            && articleQueryService.findUnavailableIds(List.of(articleId)).isEmpty();
        if (!linkable) {
            throw new BusinessException(AdminErrorCode.ADMIN_EDITOR_PICK_ARTICLE_UNAVAILABLE);
        }
    }

    private Map<Long, String> findAdminEmails(List<Smalltalk> picks) {
        Set<Long> adminIds = picks.stream()
            .flatMap(pick -> Stream.of(pick.getCreatedByAdminId(), pick.getEditedByAdminId()))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        return adminAccountRepository.findAllById(adminIds).stream()
            .collect(Collectors.toMap(AdminAccount::getId, AdminAccount::getEmail));
    }

    private LocalDate defaultServiceDate() {
        return ServiceDay.today(clock).plusDays(1);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
