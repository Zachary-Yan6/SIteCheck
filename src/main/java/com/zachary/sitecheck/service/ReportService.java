package com.zachary.sitecheck.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.zachary.sitecheck.client.PhotoFiles;
import com.zachary.sitecheck.entity.Photo;
import com.zachary.sitecheck.entity.PhotoStatus;
import com.zachary.sitecheck.entity.Site;
import com.zachary.sitecheck.exception.ApiException;
import com.zachary.sitecheck.mapper.ManagerMapper;
import com.zachary.sitecheck.mapper.PhotoMapper;
import com.zachary.sitecheck.mapper.ReportInspectionRow;
import com.zachary.sitecheck.mapper.ReportResultRow;
import com.zachary.sitecheck.mapper.SiteMapper;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Site inspection report as PDF (FR-31): every inspection of one site in a period, with each
 * item's result and note, the evidence photos of failed items and the inspector's signature -
 * the written record the Construction Regulations ask for.
 */
@Service
public class ReportService {

    /** Keeps a report (and the memory to build it) a sensible size; a shorter period finds the rest. */
    static final int MAX_INSPECTIONS = 100;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    private static final Font TITLE = new Font(Font.HELVETICA, 18, Font.BOLD);
    private static final Font HEADING = new Font(Font.HELVETICA, 12, Font.BOLD);
    private static final Font BODY = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private static final Font SMALL = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY);
    private static final Font TABLE_HEADER = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font FAIL = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(0xB7, 0x1C, 0x1C));
    private static final Color HEADER_BACKGROUND = new Color(0x15, 0x65, 0xC0);
    private static final Color FAIL_BACKGROUND = new Color(0xFD, 0xEC, 0xEA);

    private final SiteMapper siteMapper;
    private final ManagerMapper managerMapper;
    private final PhotoMapper photoMapper;
    private final PhotoFiles photoFiles;

    public ReportService(SiteMapper siteMapper, ManagerMapper managerMapper, PhotoMapper photoMapper,
                         PhotoFiles photoFiles) {
        this.siteMapper = siteMapper;
        this.managerMapper = managerMapper;
        this.photoMapper = photoMapper;
        this.photoFiles = photoFiles;
    }

    public record ReportFile(String fileName, byte[] bytes) {
    }

    /** @param from first day, @param to last day (both inclusive, Irish calendar days) */
    @Transactional(readOnly = true)
    public ReportFile siteReport(Long siteId, LocalDate from, LocalDate to) {
        ManagerService.checkRange(from, to);
        Site site = siteMapper.selectById(siteId);
        if (site == null) {
            throw ApiException.notFound("Site not found");
        }

        List<ReportInspectionRow> inspections = managerMapper.selectReportInspections(
                siteId,
                from.atStartOfDay(ManagerService.SITE_ZONE).toOffsetDateTime(),
                to.plusDays(1).atStartOfDay(ManagerService.SITE_ZONE).toOffsetDateTime(),
                MAX_INSPECTIONS + 1); // one extra tells us whether the list was cut off
        boolean truncated = inspections.size() > MAX_INSPECTIONS;
        if (truncated) {
            inspections = inspections.subList(0, MAX_INSPECTIONS);
        }

        List<UUID> ids = inspections.stream().map(ReportInspectionRow::id).toList();
        Map<UUID, List<ReportResultRow>> resultsByInspection = ids.isEmpty() ? Map.of()
                : managerMapper.selectReportResults(ids).stream()
                        .collect(Collectors.groupingBy(ReportResultRow::inspectionId));
        Map<UUID, List<Photo>> photosByInspection = ids.isEmpty() ? Map.of()
                : photoMapper.selectList(Wrappers.<Photo>lambdaQuery()
                                .in(Photo::getInspectionId, ids)
                                .eq(Photo::getStatus, PhotoStatus.UPLOADED))
                        .stream()
                        .sorted(Comparator.comparing(Photo::getTakenAt))
                        .collect(Collectors.groupingBy(Photo::getInspectionId));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(document, out);
            document.open();
            writeHeader(document, site, from, to, inspections, resultsByInspection, truncated);
            for (ReportInspectionRow inspection : inspections) {
                writeInspection(document, inspection,
                        resultsByInspection.getOrDefault(inspection.id(), List.of()),
                        photosByInspection.getOrDefault(inspection.id(), List.of()));
            }
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not build the PDF report", e);
        } finally {
            document.close();
        }

        String fileName = "sitecheck-%s-%s-to-%s.pdf".formatted(slug(site.getName()), from, to);
        return new ReportFile(fileName, out.toByteArray());
    }

    private void writeHeader(Document document, Site site, LocalDate from, LocalDate to,
                             List<ReportInspectionRow> inspections,
                             Map<UUID, List<ReportResultRow>> results, boolean truncated) throws DocumentException {
        document.add(new Paragraph("Site inspection report", TITLE));
        document.add(new Paragraph(site.getName(), HEADING));
        document.add(new Paragraph(site.getAddress(), BODY));

        long failed = results.values().stream().flatMap(List::stream).filter(r -> "FAIL".equals(r.result())).count();
        Paragraph summary = new Paragraph("Period: %s to %s  ·  %d inspection(s)  ·  %d failed item(s)"
                .formatted(DAY.format(from), DAY.format(to), inspections.size(), failed), BODY);
        summary.setSpacingBefore(6);
        document.add(summary);
        document.add(new Paragraph("Generated " + DAY_TIME.format(OffsetDateTime.now(ManagerService.SITE_ZONE))
                + " (Irish time) by SiteCheck", SMALL));
        if (truncated) {
            document.add(new Paragraph("Only the first " + MAX_INSPECTIONS
                    + " inspections are included. Choose a shorter period to see the rest.", FAIL));
        }
        if (inspections.isEmpty()) {
            Paragraph none = new Paragraph("No inspections were submitted for this site in this period.", BODY);
            none.setSpacingBefore(18);
            document.add(none);
        }
    }

    private void writeInspection(Document document, ReportInspectionRow inspection,
                                 List<ReportResultRow> results, List<Photo> photos) throws DocumentException {
        Paragraph heading = new Paragraph(inspection.templateName(), HEADING);
        heading.setSpacingBefore(18);
        document.add(heading);
        document.add(new Paragraph("%s  ·  %s".formatted(
                DAY_TIME.format(inspection.submittedAt().atZoneSameInstant(ManagerService.SITE_ZONE)),
                inspection.inspectorName()), SMALL));

        PdfPTable table = new PdfPTable(new float[] {0.6f, 6f, 1.4f, 6f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        table.setHeaderRows(1);
        for (String header : List.of("#", "Item", "Result", "Note")) {
            PdfPCell cell = new PdfPCell(new Phrase(header, TABLE_HEADER));
            cell.setBackgroundColor(HEADER_BACKGROUND);
            cell.setPadding(4);
            table.addCell(cell);
        }
        for (ReportResultRow result : results) {
            boolean fail = "FAIL".equals(result.result());
            Font font = fail ? FAIL : BODY;
            for (String text : List.of(String.valueOf(result.position()), result.title(),
                    label(result.result()), result.note() == null ? "" : result.note())) {
                PdfPCell cell = new PdfPCell(new Phrase(text, font));
                cell.setPadding(4);
                if (fail) cell.setBackgroundColor(FAIL_BACKGROUND);
                table.addCell(cell);
            }
        }
        document.add(table);

        if (!photos.isEmpty()) {
            writePhotos(document, results, photos);
        }
        writeSignature(document, inspection);
    }

    /** Evidence photos, three per row, each captioned with its item number. */
    private void writePhotos(Document document, List<ReportResultRow> results, List<Photo> photos)
            throws DocumentException {
        Map<Long, Integer> positionByItem = results.stream()
                .collect(Collectors.toMap(ReportResultRow::templateItemId, ReportResultRow::position));

        PdfPTable grid = new PdfPTable(3);
        grid.setWidthPercentage(100);
        grid.setSpacingBefore(8);
        for (Photo photo : photos) {
            PdfPCell cell = new PdfPCell();
            cell.setBorder(PdfPCell.NO_BORDER);
            cell.setPadding(4);
            Optional<Image> image = photoFiles.read(photo.getS3Key()).flatMap(ReportService::toImage);
            if (image.isPresent()) {
                image.get().scaleToFit(160, 160);
                cell.addElement(image.get());
            } else {
                cell.addElement(new Paragraph("[photo unavailable]", SMALL));
            }
            cell.addElement(new Paragraph("Item " + positionByItem.getOrDefault(photo.getTemplateItemId(), 0)
                    + " · " + DAY_TIME.format(photo.getTakenAt().atZoneSameInstant(ManagerService.SITE_ZONE)), SMALL));
            grid.addCell(cell);
        }
        grid.completeRow();
        document.add(grid);
    }

    private void writeSignature(Document document, ReportInspectionRow inspection) throws DocumentException {
        Paragraph signedBy = new Paragraph("Signed by " + inspection.inspectorName(), SMALL);
        signedBy.setSpacingBefore(6);
        document.add(signedBy);
        Optional<Image> signature = toImage(inspection.signaturePng());
        if (signature.isPresent()) {
            signature.get().scaleToFit(150, 60);
            signature.get().setAlignment(Element.ALIGN_LEFT);
            document.add(signature.get());
        } else {
            document.add(new Paragraph("[signature could not be displayed]", SMALL));
        }
    }

    /** A broken or unreadable image must not stop the whole report. */
    private static Optional<Image> toImage(byte[] bytes) {
        try {
            return Optional.of(Image.getInstance(bytes));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String label(String result) {
        return switch (result) {
            case "PASS" -> "Pass";
            case "FAIL" -> "FAIL";
            case "NA" -> "N/A";
            default -> result;
        };
    }

    /** "Cork Docklands Office Block" -> "cork-docklands-office-block", safe for a file name. */
    private static String slug(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
