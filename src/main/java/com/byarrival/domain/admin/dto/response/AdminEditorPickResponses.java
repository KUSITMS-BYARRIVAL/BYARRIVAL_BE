package com.byarrival.domain.admin.dto.response;

import com.byarrival.domain.admin.entity.AdminEditorPickStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 어드민 에디터 픽 API 응답 모음(EDITOR-01·03·04·05·07).
 */
public final class AdminEditorPickResponses {

    private AdminEditorPickResponses() {
    }

    /**
     * @param editable 지난 노출일이면 false. 지난 에디터 픽은 바꿀 수 없다
     * @param picks    앱과 같은 순서. 숨긴 것은 넣고 삭제한 것은 뺀다
     */
    public record EditorPickList(LocalDate serviceDate, boolean editable, List<Item> picks) {
    }

    /**
     * @param order     1부터 시작하는 순서. 홈 미리보기와 16시 알림(1번)이 이 순서를 따른다
     * @param hook      건네는 한마디
     * @param status    노출 중(EXPOSED)·수정함(EDITED)·숨김(HIDDEN)
     * @param hidden    운영자가 숨겼는지. 연결 기사가 숨겨져 숨김인 경우와 구분한다
     * @param createdBy 직접 추가한 운영자 이메일. 배치가 만들었으면 null
     * @param editedAt  마지막으로 수정한 시각. 수정하지 않았으면 null
     * @param editedBy  마지막으로 수정한 운영자 이메일
     */
    public record Item(Long editorPickId, int order, String hook, AdminEditorPickStatus status, boolean hidden,
                       LocalDate serviceDate, PickArticle article, String createdBy, LocalDateTime editedAt,
                       String editedBy) {
    }

    /**
     * @param available 앱에서 볼 수 있는 기사인지. 숨김·삭제되면 false이고 에디터 픽도 앱에 보이지 않는다
     * @param linkUrl   원문 주소
     */
    public record PickArticle(Long articleId, String title, String pressName, String categoryName,
                          LocalDate serviceDate, boolean available, String linkUrl) {
    }

    /**
     * 연결할 수 있는 기사. 오늘 노출 중이거나 노출일 당일에 노출할 기사 중 아직 에디터 픽에 쓰지 않은 것.
     */
    public record LinkableArticle(Long articleId, String title, String pressName, String categoryName,
                                  LocalDate serviceDate) {
    }
}
