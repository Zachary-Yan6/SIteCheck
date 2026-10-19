package com.zachary.sitecheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zachary.sitecheck.dto.SubmitInspectionRequest;
import com.zachary.sitecheck.dto.SubmitInspectionRequest.PhotoRequest;
import com.zachary.sitecheck.dto.SubmitInspectionRequest.ResultRequest;
import com.zachary.sitecheck.entity.Inspection;
import com.zachary.sitecheck.entity.ItemResult;
import com.zachary.sitecheck.entity.Photo;
import com.zachary.sitecheck.entity.Role;
import com.zachary.sitecheck.entity.SiteTemplate;
import com.zachary.sitecheck.entity.TemplateItem;
import com.zachary.sitecheck.exception.ApiException;
import com.zachary.sitecheck.mapper.InspectionMapper;
import com.zachary.sitecheck.mapper.InspectionResultMapper;
import com.zachary.sitecheck.mapper.PhotoMapper;
import com.zachary.sitecheck.mapper.SiteTemplateMapper;
import com.zachary.sitecheck.mapper.TemplateItemMapper;
import com.zachary.sitecheck.security.AuthUser;
import com.zachary.sitecheck.support.MybatisLambdaSupport;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Submit rules with mocked mappers - the edge cases that are awkward to set up against the real
 * database (a concurrent retry winning the insert, an item or photo listed twice in one request).
 */
@ExtendWith(MockitoExtension.class)
class InspectionServiceUnitTests {

    private static final AuthUser AOIFE = new AuthUser(2L, "aoife@sitecheck.demo", Role.INSPECTOR);
    private static final long SITE = 3L;
    private static final long TEMPLATE = 1L;
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-13T09:30:00+01:00");

    @Mock
    private InspectionMapper inspectionMapper;
    @Mock
    private InspectionResultMapper resultMapper;
    @Mock
    private PhotoMapper photoMapper;
    @Mock
    private SiteTemplateMapper siteTemplateMapper;
    @Mock
    private TemplateItemMapper templateItemMapper;
    @Mock
    private SiteAccessService siteAccessService;
    @InjectMocks
    private InspectionService service;

    @BeforeAll
    static void registerEntities() {
        MybatisLambdaSupport.registerEntities(Inspection.class, Photo.class, SiteTemplate.class, TemplateItem.class);
    }

    @BeforeEach
    void templateHasItems10And11() {
        // lenient: tests that stop before validation never read these
        lenient().when(siteTemplateMapper.exists(any())).thenReturn(true);
        lenient().when(templateItemMapper.selectList(any())).thenReturn(List.of(item(10L), item(11L)));
    }

    @Test
    void storesANewInspectionWithoutPhotos() {
        SubmitInspectionRequest request = request(List.of(pass(10L), pass(11L)), List.of());
        when(inspectionMapper.selectOne(any())).thenReturn(null, stored(request.id(), AOIFE.id()));
        when(inspectionMapper.insertIfAbsent(any())).thenReturn(1);

        InspectionService.SubmitOutcome outcome = service.submit(AOIFE, request);

        assertThat(outcome.created()).isTrue();
        assertThat(outcome.receipt().id()).isEqualTo(request.id());
        verify(resultMapper).insert(anyList());
        verify(photoMapper, never()).insert(anyList());
    }

    @Test
    void concurrentRetryThatWinsTheInsertGetsTheSameReceipt() {
        SubmitInspectionRequest request = request(List.of(pass(10L), pass(11L)), List.of());
        // Not there when we first look, but another request stored it before our insert.
        when(inspectionMapper.selectOne(any())).thenReturn(null, stored(request.id(), AOIFE.id()));
        when(inspectionMapper.insertIfAbsent(any())).thenReturn(0);

        InspectionService.SubmitOutcome outcome = service.submit(AOIFE, request);

        assertThat(outcome.created()).isFalse();
        verify(resultMapper, never()).insert(anyList());
    }

    @Test
    void unassignedSiteIsRejectedBeforeAnythingIsStored() {
        SubmitInspectionRequest request = request(List.of(pass(10L)), List.of());
        doThrow(ApiException.forbidden("Not assigned")).when(siteAccessService).requireAssigned(AOIFE, SITE);

        assertThatThrownBy(() -> service.submit(AOIFE, request)).isInstanceOf(ApiException.class);
        verify(inspectionMapper, never()).insertIfAbsent(any());
    }

    @Test
    void itemAnsweredTwiceIsRejected() {
        expectBadRequest(request(List.of(pass(10L), pass(10L)), List.of()), "Item 10 is answered twice");
    }

    @Test
    void photoForAnUnansweredItemIsRejected() {
        PhotoRequest photo = photo(11L);
        expectBadRequest(request(List.of(pass(10L)), List.of(photo)),
                "Photo " + photo.id() + " is for an item that was not answered");
    }

    @Test
    void samePhotoListedTwiceIsRejected() {
        PhotoRequest photo = photo(10L);
        expectBadRequest(request(List.of(fail(10L, "Cracked")), List.of(photo, photo)),
                "Photo " + photo.id() + " is listed twice");
    }

    @Test
    void failWithoutANoteIsRejected() {
        expectBadRequest(request(List.of(fail(10L, null)), List.of(photo(10L))), "Item 10 failed but has no note");
    }

    @Test
    void failWithABlankNoteIsRejected() {
        expectBadRequest(request(List.of(fail(10L, "   ")), List.of(photo(10L))), "Item 10 failed but has no note");
    }

    @Test
    void reusedIdOfAnotherInspectorsInspectionIsConflict() {
        SubmitInspectionRequest request = request(List.of(pass(10L)), List.of());
        when(inspectionMapper.selectOne(any())).thenReturn(stored(request.id(), 99L));

        assertThatThrownBy(() -> service.submit(AOIFE, request))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    // ---------------------------------------------------------------- helpers

    private void expectBadRequest(SubmitInspectionRequest request, String message) {
        when(inspectionMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.submit(AOIFE, request))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo(message);
                });
        verify(inspectionMapper, never()).insertIfAbsent(any());
    }

    private static SubmitInspectionRequest request(List<ResultRequest> results, List<PhotoRequest> photos) {
        return new SubmitInspectionRequest(UUID.randomUUID(), SITE, TEMPLATE, NOW.minusMinutes(30), NOW,
                new byte[] {1}, results, photos);
    }

    private static ResultRequest pass(long itemId) {
        return new ResultRequest(itemId, ItemResult.PASS, null);
    }

    private static ResultRequest fail(long itemId, String note) {
        return new ResultRequest(itemId, ItemResult.FAIL, note);
    }

    private static PhotoRequest photo(long itemId) {
        return new PhotoRequest(UUID.randomUUID(), itemId, NOW, null, null);
    }

    private static TemplateItem item(long id) {
        TemplateItem item = new TemplateItem();
        item.setId(id);
        return item;
    }

    private static Inspection stored(UUID id, long inspectorId) {
        Inspection inspection = new Inspection();
        inspection.setId(id);
        inspection.setInspectorId(inspectorId);
        inspection.setReceivedAt(NOW);
        return inspection;
    }
}
