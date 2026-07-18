package com.omyadav.repodoctor.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.omyadav.repodoctor.entity.AnalysisResult;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class RepositoryPdfReportService {

    private final AnalysisHistoryService analysisHistoryService;

    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, new Color(30, 30, 30));
    private static final Font SECTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(50, 50, 50));
    private static final Font BODY_FONT = FontFactory.getFont(FontFactory.HELVETICA, 11, new Color(60, 60, 60));
    private static final Font BOLD_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(40, 40, 40));
    private static final Font SMALL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(120, 120, 120));

    public RepositoryPdfReportService(AnalysisHistoryService analysisHistoryService) {
        this.analysisHistoryService = analysisHistoryService;
    }

    public byte[] generateReport(String owner, String repositoryName) {

        List<AnalysisResult> history = analysisHistoryService.getRepositoryHistory(owner, repositoryName);

        if (history.isEmpty()) {
            throw new com.omyadav.repodoctor.exception.RepositoryNotFoundException("No saved analysis found for " + owner + "/" + repositoryName);
        }

        AnalysisResult latest = history.get(0);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Document document = new Document();

        try {
            PdfWriter.getInstance(document, outputStream);
            document.open();

            // Page 1: Title + Repository Info + Overall Score
            addTitle(document);
            addRepositoryInformation(document, latest, owner, repositoryName);
            addOverallScore(document, latest);

            // Page 2: Score Breakdown + Chart
            document.newPage();
            addScoreBreakdown(document, latest);
            addChart(document, latest);

            // Page 3-4: Recommendations
            document.newPage();
            addRecommendations(document, latest);

            // Final: Analysis metadata
            addAnalysisFooter(document, latest);

        } catch (DocumentException exception) {
            throw new IllegalStateException("Unable to generate PDF report", exception);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }

        return outputStream.toByteArray();
    }

    private void addTitle(Document document) throws DocumentException {
        Paragraph title = new Paragraph("RepoDoctor", TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        Paragraph subtitle = new Paragraph("Repository Health Analysis Report", SECTION_FONT);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(25);
        document.add(subtitle);

        addDivider(document);
    }

    private void addRepositoryInformation(Document document, AnalysisResult analysis,
            String owner, String repositoryName) throws DocumentException {

        addSectionTitle(document, "Repository Information");

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{35, 65});

        addInfoRow(table, "Repository", owner + " / " + repositoryName);
        addInfoRow(table, "URL", analysis.getRepositoryUrl() != null
                ? analysis.getRepositoryUrl() : "https://github.com/" + owner + "/" + repositoryName);
        addInfoRow(table, "Overall Score", (analysis.getOverallScore() != null
                ? analysis.getOverallScore() + " / 100" : "N/A"));
        addInfoRow(table, "Grade", analysis.getGrade() != null ? analysis.getGrade() : "N/A");
        addInfoRow(table, "Repository Health", formatLabel(analysis.getRepositoryHealth()));
        addInfoRow(table, "Analysis Date", analysis.getAnalysisDate() != null
                ? analysis.getAnalysisDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")) : "N/A");

        document.add(table);
        addSpacing(document);
    }

    private void addOverallScore(Document document, AnalysisResult analysis) throws DocumentException {
        addSectionTitle(document, "Executive Summary");

        String grade = analysis.getGrade() != null ? analysis.getGrade() : "N/A";
        String score = analysis.getOverallScore() != null ? String.valueOf(analysis.getOverallScore()) : "N/A";
        String health = formatLabel(analysis.getRepositoryHealth());

        String summary = String.format(
                "This repository received a grade of %s with an overall score of %s/100. " +
                "The repository health status is classified as '%s'. " +
                "The evaluation considers code quality, repository hygiene, documentation, " +
                "project structure, commit quality, and README quality.",
                grade, score, health);

        document.add(new Paragraph(summary, BODY_FONT));
        addSpacing(document);
    }

    private void addScoreBreakdown(Document document, AnalysisResult analysis) throws DocumentException {
        addSectionTitle(document, "Score Breakdown");

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{45, 25, 30});

        // Header
        addHeaderCell(table, "Analysis Dimension");
        addHeaderCell(table, "Score");
        addHeaderCell(table, "Rating");

        // Rows
        addScoreRow(table, "Repository Hygiene", analysis.getHygieneScore());
        addScoreRow(table, "README Quality", analysis.getReadmeScore());
        addScoreRow(table, "Project Structure", analysis.getStructureScore());
        addScoreRow(table, "Commit Quality", analysis.getCommitQualityScore());
        addScoreRow(table, "Documentation", analysis.getDocumentationScore());
        addScoreRow(table, "Code Quality", analysis.getCodeQualityScore());

        // Overall
        PdfPCell overallLabel = new PdfPCell(new Phrase("Overall Score", BOLD_FONT));
        overallLabel.setPadding(8);
        overallLabel.setBackgroundColor(new Color(240, 240, 245));
        table.addCell(overallLabel);

        PdfPCell overallScore = new PdfPCell(new Phrase(
                analysis.getOverallScore() != null ? analysis.getOverallScore() + " / 100" : "N/A", BOLD_FONT));
        overallScore.setPadding(8);
        overallScore.setBackgroundColor(new Color(240, 240, 245));
        overallScore.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(overallScore);

        PdfPCell overallGrade = new PdfPCell(new Phrase(
                analysis.getGrade() != null ? analysis.getGrade() : "N/A", BOLD_FONT));
        overallGrade.setPadding(8);
        overallGrade.setBackgroundColor(new Color(240, 240, 245));
        overallGrade.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(overallGrade);

        document.add(table);
        addSpacing(document);
    }

    private void addChart(Document document, AnalysisResult analysis) throws DocumentException {
        try {
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();
            dataset.addValue(safeScore(analysis.getHygieneScore()), "Score", "Hygiene");
            dataset.addValue(safeScore(analysis.getReadmeScore()), "Score", "README");
            dataset.addValue(safeScore(analysis.getStructureScore()), "Score", "Structure");
            dataset.addValue(safeScore(analysis.getCommitQualityScore()), "Score", "Commits");
            dataset.addValue(safeScore(analysis.getDocumentationScore()), "Score", "Docs");
            dataset.addValue(safeScore(analysis.getCodeQualityScore()), "Score", "Code");

            JFreeChart barChart = ChartFactory.createBarChart(
                    "Score Distribution by Dimension",
                    "Dimension",
                    "Score (0-100)",
                    dataset,
                    PlotOrientation.VERTICAL,
                    false, true, false);

            barChart.setBackgroundPaint(Color.WHITE);

            BufferedImage bufferedImage = barChart.createBufferedImage(500, 280);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "png", baos);
            Image image = Image.getInstance(baos.toByteArray());
            image.scaleToFit(480, 280);
            image.setAlignment(Element.ALIGN_CENTER);
            document.add(image);
            addSpacing(document);

        } catch (Exception e) {
            document.add(new Paragraph("Chart generation unavailable.", SMALL_FONT));
        }
    }

    @SuppressWarnings("unchecked")
    private void addRecommendations(Document document, AnalysisResult analysis) throws DocumentException {
        addSectionTitle(document, "Recommendations");

        Map<String, Object> snapshot = analysis.getAnalysisSnapshot();
        if (snapshot == null || !snapshot.containsKey("recommendations")) {
            document.add(new Paragraph("No recommendations available.", BODY_FONT));
            addSpacing(document);
            return;
        }

        Object recommendationsObj = snapshot.get("recommendations");
        if (!(recommendationsObj instanceof List<?> recommendationsList) || recommendationsList.isEmpty()) {
            document.add(new Paragraph("No recommendations available.", BODY_FONT));
            addSpacing(document);
            return;
        }

        int count = 0;
        for (Object recObj : recommendationsList) {
            if (!(recObj instanceof Map<?, ?> rec)) continue;
            count++;
            if (count > 15) break; // Limit to keep PDF reasonable size

            String title = rec.get("title") != null ? rec.get("title").toString() : "Recommendation";
            String priority = rec.get("priority") != null ? rec.get("priority").toString() : "MEDIUM";
            String description = rec.get("description") != null ? rec.get("description").toString() : "";
            String category = rec.get("category") != null ? rec.get("category").toString() : "";

            Paragraph recTitle = new Paragraph(count + ". " + title, BOLD_FONT);
            recTitle.setSpacingBefore(8);
            document.add(recTitle);

            document.add(new Paragraph("Priority: " + formatLabel(priority)
                    + (category.isEmpty() ? "" : "  |  Category: " + formatLabel(category)), SMALL_FONT));

            if (!description.isEmpty()) {
                Paragraph desc = new Paragraph(description, BODY_FONT);
                desc.setSpacingAfter(4);
                document.add(desc);
            }
        }

        addSpacing(document);
    }

    private void addAnalysisFooter(Document document, AnalysisResult analysis) throws DocumentException {
        addDivider(document);

        Paragraph footer = new Paragraph("Analysis Information", SECTION_FONT);
        footer.setSpacingBefore(10);
        document.add(footer);

        document.add(new Paragraph("Analysis ID: #" + analysis.getId(), SMALL_FONT));
        document.add(new Paragraph("Analyzed: " + (analysis.getAnalysisDate() != null
                ? analysis.getAnalysisDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss")) : "N/A"),
                SMALL_FONT));
        document.add(new Paragraph("Generated by RepoDoctor — GitHub Repository Health Analyzer", SMALL_FONT));
    }

    // ---- Helper methods ----

    private void addSectionTitle(Document document, String title) throws DocumentException {
        Paragraph sectionTitle = new Paragraph(title, SECTION_FONT);
        sectionTitle.setSpacingBefore(12);
        sectionTitle.setSpacingAfter(8);
        document.add(sectionTitle);
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, BOLD_FONT));
        cell.setPadding(8);
        cell.setBackgroundColor(new Color(60, 60, 80));
        cell.setPhrase(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE)));
        table.addCell(cell);
    }

    private void addScoreRow(PdfPTable table, String area, Integer score) {
        PdfPCell labelCell = new PdfPCell(new Phrase(area, BODY_FONT));
        labelCell.setPadding(6);
        table.addCell(labelCell);

        String scoreText = score != null ? score + " / 100" : "N/A";
        PdfPCell scoreCell = new PdfPCell(new Phrase(scoreText, BODY_FONT));
        scoreCell.setPadding(6);
        scoreCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(scoreCell);

        String rating = getRating(score);
        PdfPCell ratingCell = new PdfPCell(new Phrase(rating, BODY_FONT));
        ratingCell.setPadding(6);
        ratingCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(ratingCell);
    }

    private void addInfoRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, BOLD_FONT));
        labelCell.setPadding(6);
        labelCell.setBorderColor(new Color(220, 220, 220));
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value != null ? value : "N/A", BODY_FONT));
        valueCell.setPadding(6);
        valueCell.setBorderColor(new Color(220, 220, 220));
        table.addCell(valueCell);
    }

    private void addSpacing(Document document) throws DocumentException {
        document.add(new Paragraph(" "));
    }

    private void addDivider(Document document) throws DocumentException {
        Paragraph divider = new Paragraph("─────────────────────────────────────────────────────────", SMALL_FONT);
        divider.setSpacingBefore(8);
        divider.setSpacingAfter(8);
        document.add(divider);
    }

    private String getRating(Integer score) {
        if (score == null) return "N/A";
        if (score >= 90) return "Excellent";
        if (score >= 80) return "Very Good";
        if (score >= 70) return "Good";
        if (score >= 60) return "Fair";
        if (score >= 50) return "Needs Work";
        return "Poor";
    }

    private int safeScore(Integer score) {
        return score == null ? 0 : score;
    }

    private String formatLabel(String value) {
        if (value == null || value.isBlank()) return "N/A";
        return value.replace("_", " ").substring(0, 1).toUpperCase()
                + value.replace("_", " ").substring(1).toLowerCase();
    }
}