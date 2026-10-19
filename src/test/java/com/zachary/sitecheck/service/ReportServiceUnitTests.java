package com.zachary.sitecheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
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
import com.zachary.sitecheck.support.MybatisLambdaSupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * PDF layout rules with mocked data: the 100-inspection cut-off, N/A and unknown results, and
 * photos or signatures that can't be shown. The real-S3 happy path is in ReportControllerTests.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceUnitTests {

    private static final long SITE_ID = 3L;
    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 13);
    private static final OffsetDateTime SUBMITTED = OffsetDateTime.parse("2026-10-06T09:30:00+01:00");

    @Mock
    private SiteMapper siteMapper;
    @Mock
    private ManagerMapper managerMapper;
    @Mock
    private PhotoMapper photoMapper;
    @Mock
    private PhotoFiles photoFiles;
    @InjectMocks
    private ReportService service;

    @BeforeAll
    static void registerEntities() {
        MybatisLambdaSupport.registerEntities(Photo.class);
    }

    @BeforeEach
    void corkExists() {
        Site site = new Site();
        site.setId(SITE_ID);
        site.setName("Cork Docklands Office Block");
        site.setAddress("Kennedy Quay, Cork");
        lenient().when(siteMapper.selectById(SITE_ID)).thenReturn(site);
    }

    @Test
    void unknownSiteIsNotFound() {
        assertThatThrownBy(() -> service.siteReport(99L, FROM, TO))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void emptyPeriodSaysSoAndSkipsTheDetailQueries() throws IOException {
        when(managerMapper.selectReportInspections(eq(SITE_ID), any(), any(), anyInt())).thenReturn(List.of());

        ReportService.ReportFile report = service.siteReport(SITE_ID, FROM, TO);

        assertThat(report.fileName()).isEqualTo("sitecheck-cork-docklands-office-block-2026-10-01-to-2026-10-13.pdf");
        assertThat(text(report)).contains("No inspections were submitted for this site in this period.");
        verify(managerMapper, never()).selectReportResults(any());
        verify(photoMapper, never()).selectList(any());
    }

    @Test
    void moreThanTheLimitIsCutOffWithAWarning() throws IOException {
        List<ReportInspectionRow> rows = IntStream.range(0, ReportService.MAX_INSPECTIONS + 1)
                .mapToObj(i -> inspection(UUID.randomUUID(), png()))
                .toList();
        when(managerMapper.selectReportInspections(eq(SITE_ID), any(), any(), eq(ReportService.MAX_INSPECTIONS + 1)))
                .thenReturn(rows);
        when(managerMapper.selectReportResults(any())).thenReturn(List.of());
        when(photoMapper.selectList(any())).thenReturn(List.of());

        String text = text(service.siteReport(SITE_ID, FROM, TO));

        assertThat(text).contains("100 inspection(s)").contains("Only the first 100 inspections are included");
    }

    @Test
    void labelsEveryResultAndCopesWithMissingImages() throws IOException {
        UUID id = UUID.randomUUID();
        when(managerMapper.selectReportInspections(eq(SITE_ID), any(), any(), anyInt()))
                .thenReturn(List.of(inspection(id, new byte[] {1, 2, 3})));  // not an image
        when(managerMapper.selectReportResults(List.of(id))).thenReturn(List.of(
                result(id, 10L, 1, "Base plates", "PASS", null),
                result(id, 11L, 2, "Guard rails", "FAIL", "Missing on level 3"),
                result(id, 12L, 3, "Ladder access", "NA", null),
                result(id, 13L, 4, "Toe boards", "LATER", null)));
        when(photoMapper.selectList(any())).thenReturn(List.of(
                photo(id, 11L, "photos/gone.jpg"),
                photo(id, 99L, "photos/other-item.jpg")));
        when(photoFiles.read("photos/gone.jpg")).thenReturn(Optional.empty());
        when(photoFiles.read("photos/other-item.jpg")).thenReturn(Optional.of(jpeg()));

        String text = text(service.siteReport(SITE_ID, FROM, TO));

        assertThat(text)
                .contains("1 failed item(s)")
                .contains("Pass").contains("FAIL").contains("N/A").contains("LATER")
                .contains("Missing on level 3")
                .contains("[photo unavailable]")
                .contains("Item 2 ·").contains("Item 0 ·")  // 99 is not an item of this inspection
                .contains("[signature could not be displayed]");
    }

    // ---------------------------------------------------------------- helpers

    private static ReportInspectionRow inspection(UUID id, byte[] signature) {
        return new ReportInspectionRow(id, "Scaffold Inspection", "Aoife Murphy", SUBMITTED, signature);
    }

    private static ReportResultRow result(UUID id, long itemId, int position, String title, String result, String note) {
        return new ReportResultRow(id, itemId, position, title, result, note);
    }

    private static Photo photo(UUID inspectionId, long itemId, String key) {
        Photo photo = new Photo();
        photo.setId(UUID.randomUUID());
        photo.setInspectionId(inspectionId);
        photo.setTemplateItemId(itemId);
        photo.setS3Key(key);
        photo.setStatus(PhotoStatus.UPLOADED);
        photo.setTakenAt(SUBMITTED.minusMinutes(10));
        return photo;
    }

    private static byte[] png() {
        return image("png");
    }

    private static byte[] jpeg() {
        return image("jpg");
    }

    private static byte[] image(String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(ReportService.ReportFile report) throws IOException {
        try (PdfReader reader = new PdfReader(report.bytes())) {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
            return text.toString();
        }
    }
}
