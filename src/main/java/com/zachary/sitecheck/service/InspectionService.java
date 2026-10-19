package com.zachary.sitecheck.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zachary.sitecheck.dto.InspectionReceipt;
import com.zachary.sitecheck.dto.SubmitInspectionRequest;
import com.zachary.sitecheck.dto.SubmitInspectionRequest.PhotoRequest;
import com.zachary.sitecheck.dto.SubmitInspectionRequest.ResultRequest;
import com.zachary.sitecheck.entity.Inspection;
import com.zachary.sitecheck.entity.InspectionResult;
import com.zachary.sitecheck.entity.ItemResult;
import com.zachary.sitecheck.entity.Photo;
import com.zachary.sitecheck.entity.PhotoStatus;
import com.zachary.sitecheck.entity.SiteTemplate;
import com.zachary.sitecheck.entity.TemplateItem;
import com.zachary.sitecheck.exception.ApiException;
import com.zachary.sitecheck.mapper.InspectionMapper;
import com.zachary.sitecheck.mapper.InspectionResultMapper;
import com.zachary.sitecheck.mapper.PhotoMapper;
import com.zachary.sitecheck.mapper.SiteTemplateMapper;
import com.zachary.sitecheck.mapper.TemplateItemMapper;
import com.zachary.sitecheck.security.AuthUser;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionService {

    private final InspectionMapper inspectionMapper;
    private final InspectionResultMapper resultMapper;
    private final PhotoMapper photoMapper;
    private final SiteTemplateMapper siteTemplateMapper;
    private final TemplateItemMapper templateItemMapper;
    private final SiteAccessService siteAccessService;

    public InspectionService(InspectionMapper inspectionMapper, InspectionResultMapper resultMapper,
                             PhotoMapper photoMapper, SiteTemplateMapper siteTemplateMapper,
                             TemplateItemMapper templateItemMapper, SiteAccessService siteAccessService) {
        this.inspectionMapper = inspectionMapper;
        this.resultMapper = resultMapper;
        this.photoMapper = photoMapper;
        this.siteTemplateMapper = siteTemplateMapper;
        this.templateItemMapper = templateItemMapper;
        this.siteAccessService = siteAccessService;
    }

    /**
     * Idempotent submit (FR-20, NFR-03). The first call stores the inspection; any retry with the
     * same id - e.g. after the response was lost on a bad connection - gets the same receipt back
     * and stores nothing. All rows are written in one transaction, so a crash half-way leaves nothing.
     */
    @Transactional
    public SubmitOutcome submit(AuthUser user, SubmitInspectionRequest request) {
        // 1. Retry of something we already have? Answer before re-validating, so a retry still
        //    succeeds even if, say, the inspector has been unassigned from the site since.
        Optional<InspectionReceipt> existing = findExisting(user, request.id());
        if (existing.isPresent()) {
            return new SubmitOutcome(existing.get(), false);
        }

        // 2. New inspection: check access and content.
        siteAccessService.requireAssigned(user, request.siteId());
        validate(request);

        // 3. Store it. 0 rows means a concurrent retry of the same request committed first.
        if (inspectionMapper.insertIfAbsent(toInspection(user, request)) == 0) {
            return new SubmitOutcome(findExisting(user, request.id()).orElseThrow(), false);
        }
        resultMapper.insert(request.results().stream().map(r -> toResult(request.id(), r)).toList());
        if (!request.photos().isEmpty()) {
            photoMapper.insert(request.photos().stream().map(p -> toPhoto(request.id(), p)).toList());
        }
        return new SubmitOutcome(findExisting(user, request.id()).orElseThrow(), true);
    }

    /** Receipt for an already stored inspection, or empty if there is none with that id. */
    private Optional<InspectionReceipt> findExisting(AuthUser user, UUID id) {
        Inspection inspection = inspectionMapper.selectOne(Wrappers.<Inspection>lambdaQuery()
                .select(Inspection::getId, Inspection::getInspectorId, Inspection::getReceivedAt)  // skip the signature bytes
                .eq(Inspection::getId, id));
        if (inspection == null) {
            return Optional.empty();
        }
        if (!inspection.getInspectorId().equals(user.id())) {
            // Someone else's inspection already uses this UUID. Never happens with random UUIDs,
            // so this is a buggy or malicious client - refuse rather than leak or overwrite it.
            throw ApiException.conflict("Inspection id already used");
        }
        List<UUID> pendingPhotoIds = photoMapper.selectList(Wrappers.<Photo>lambdaQuery()
                        .select(Photo::getId)
                        .eq(Photo::getInspectionId, id)
                        .eq(Photo::getStatus, PhotoStatus.PENDING))
                .stream().map(Photo::getId).toList();
        return Optional.of(new InspectionReceipt(inspection.getId(), inspection.getReceivedAt(), pendingPhotoIds));
    }

    /**
     * Business rules the app also enforces; checked again here because the server can't trust the client.
     *
     * <p>Deliberately NOT checked: that every item of the template was answered. The inspection may have
     * been filled in offline days ago; if someone added an item to the template since, rejecting it
     * would lose the inspector's work for good. The app makes sure all items it knew about are answered.
     */
    private void validate(SubmitInspectionRequest request) {
        boolean templateUsedAtSite = siteTemplateMapper.exists(Wrappers.<SiteTemplate>lambdaQuery()
                .eq(SiteTemplate::getSiteId, request.siteId())
                .eq(SiteTemplate::getTemplateId, request.templateId()));
        if (!templateUsedAtSite) {
            throw ApiException.badRequest("Template " + request.templateId() + " is not used at this site");
        }

        Set<Long> templateItemIds = templateItemMapper.selectList(Wrappers.<TemplateItem>lambdaQuery()
                        .select(TemplateItem::getId)
                        .eq(TemplateItem::getTemplateId, request.templateId()))
                .stream().map(TemplateItem::getId).collect(Collectors.toSet());

        Set<Long> answeredItemIds = new HashSet<>();
        for (ResultRequest result : request.results()) {
            if (!templateItemIds.contains(result.templateItemId())) {
                throw ApiException.badRequest("Item " + result.templateItemId() + " is not in this template");
            }
            if (!answeredItemIds.add(result.templateItemId())) {
                throw ApiException.badRequest("Item " + result.templateItemId() + " is answered twice");
            }
        }

        Set<UUID> photoIds = new HashSet<>();
        Set<Long> itemsWithPhotos = new HashSet<>();
        for (PhotoRequest photo : request.photos()) {
            if (!answeredItemIds.contains(photo.templateItemId())) {
                throw ApiException.badRequest("Photo " + photo.id() + " is for an item that was not answered");
            }
            if (!photoIds.add(photo.id())) {
                throw ApiException.badRequest("Photo " + photo.id() + " is listed twice");
            }
            itemsWithPhotos.add(photo.templateItemId());
        }

        // FR-12: a FAIL needs a note and at least one photo.
        for (ResultRequest result : request.results()) {
            if (result.result() == ItemResult.FAIL) {
                if (result.note() == null || result.note().isBlank()) {
                    throw ApiException.badRequest("Item " + result.templateItemId() + " failed but has no note");
                }
                if (!itemsWithPhotos.contains(result.templateItemId())) {
                    throw ApiException.badRequest("Item " + result.templateItemId() + " failed but has no photo");
                }
            }
        }
    }

    private static Inspection toInspection(AuthUser user, SubmitInspectionRequest request) {
        Inspection inspection = new Inspection();
        inspection.setId(request.id());
        inspection.setSiteId(request.siteId());
        inspection.setTemplateId(request.templateId());
        inspection.setInspectorId(user.id());  // from the token, never from the request body
        inspection.setStartedAt(request.startedAt());
        inspection.setSubmittedAt(request.submittedAt());
        inspection.setSignaturePng(request.signaturePng());
        return inspection;
    }

    private static InspectionResult toResult(UUID inspectionId, ResultRequest request) {
        InspectionResult result = new InspectionResult();
        result.setInspectionId(inspectionId);
        result.setTemplateItemId(request.templateItemId());
        result.setResult(request.result());
        result.setNote(request.note());
        return result;
    }

    private static Photo toPhoto(UUID inspectionId, PhotoRequest request) {
        Photo photo = new Photo();
        photo.setId(request.id());
        photo.setInspectionId(inspectionId);
        photo.setTemplateItemId(request.templateItemId());
        // Fixed key, decided by the server: the upload step can only ever write to this exact object.
        photo.setS3Key("photos/" + inspectionId + "/" + request.id() + ".jpg");
        photo.setStatus(PhotoStatus.PENDING);
        photo.setTakenAt(request.takenAt());
        photo.setLatitude(request.latitude());
        photo.setLongitude(request.longitude());
        return photo;
    }

    /** @param created true for the first upload (201), false for a retry of a stored one (200) */
    public record SubmitOutcome(InspectionReceipt receipt, boolean created) {
    }
}
