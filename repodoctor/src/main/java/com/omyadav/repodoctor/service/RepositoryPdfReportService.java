package com.omyadav.repodoctor.service;

import com.lowagie.text.Chunk;
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

    // Modern professional color palette
    private static final Color BRAND_PRIMARY = new Color(41, 98, 255);
    private static final Color TEXT_MAIN = new Color(33, 33, 33);
    private static final Color TEXT_MUTED = new Color(117, 117, 117);
    private static final Color TABLE_HEADER_BG = new Color(245, 247, 250);
    private static final Color BORDER_COLOR = new Color(224, 224, 224);
    
    // Priority Colors
    private static final Color PRIORITY_CRITICAL = new Color(211, 47, 47);
    private static final Color PRIORITY_HIGH = new Color(245, 124, 0);
    private static final Color PRIORITY_MEDIUM = new Color(251, 192, 45);
    private static final Color PRIORITY_LOW = new Color(56, 142, 60);

    // Modern Fonts (Using built-in Helvetica as base but styled cleanly)
    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 26, TEXT_MAIN);
    private static final Font SUBTITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA, 14, TEXT_MUTED);
    private static final Font SECTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BRAND_PRIMARY);
    private static final Font CARD_TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, TEXT_MAIN);
    private static final Font BODY_FONT = FontFactory.getFont(FontFactory.HELVETICA, 11, TEXT_MAIN);
    private static final Font BOLD_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, TEXT_MAIN);
    private static final Font SMALL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10, TEXT_MUTED);

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

            // Page 1: Title + Info + Breakdown + Chart
            addCoverPage(document, latest, owner, repositoryName);
            addScoreBreakdown(document, latest);
            addChart(document, latest);

            // Page 2: Recommendations
            document.newPage();
            addRecommendations(document, latest);
            
            // Page 3: Reasoning Details (if available)
            addReasoning(document, latest);

            // Footer
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

    private void addCoverPage(Document document, AnalysisResult analysis, String owner, String repositoryName) throws DocumentException {
        // Title spacing
        for(int i = 0; i < 5; i++) document.add(new Paragraph(" "));
        
        Paragraph title = new Paragraph("RepoDoctor Analysis Report", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 32, BRAND_PRIMARY));
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        Paragraph subtitle = new Paragraph(owner + " / " + repositoryName, FontFactory.getFont(FontFactory.HELVETICA, 18, TEXT_MAIN));
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingBefore(10);
        subtitle.setSpacingAfter(40);
        document.add(subtitle);

        // Summary Card
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(80);
        table.setWidths(new float[]{50, 50});
        
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(0);
        leftCell.setPadding(20);
        leftCell.setBackgroundColor(TABLE_HEADER_BG);
        
        String health = formatLabel(analysis.getRepositoryHealth());
        leftCell.addElement(new Paragraph("OVERALL SCORE", SMALL_FONT));
        Paragraph scorePara = new Paragraph(analysis.getOverallScore() + " / 100", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 36, getHealthColor(health)));
        scorePara.setSpacingBefore(5);
        leftCell.addElement(scorePara);
        
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(0);
        rightCell.setPadding(20);
        rightCell.setBackgroundColor(TABLE_HEADER_BG);
        
        rightCell.addElement(new Paragraph("GRADE: " + analysis.getGrade(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, TEXT_MAIN)));
        rightCell.addElement(new Paragraph("HEALTH: " + health, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, getHealthColor(health))));
        String repositoryType = "UNKNOWN";
        if (analysis.getAnalysisSnapshot() != null) {
            Object overallObj = analysis.getAnalysisSnapshot().get("overall");
            if (overallObj instanceof Map) {
                Object typeObj = ((Map<?, ?>) overallObj).get("repositoryType");
                if (typeObj != null) {
                    repositoryType = typeObj.toString();
                }
            } else if (analysis.getAnalysisSnapshot().get("repositoryType") != null) {
                repositoryType = analysis.getAnalysisSnapshot().get("repositoryType").toString();
            }
        }
        rightCell.addElement(new Paragraph("TYPE: " + formatLabel(repositoryType), BODY_FONT));
        rightCell.addElement(new Paragraph("DATE: " + (analysis.getAnalysisDate() != null
                ? analysis.getAnalysisDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")) : "N/A"), BODY_FONT));
        
        table.addCell(leftCell);
        table.addCell(rightCell);
        
        document.add(table);
        document.newPage();
    }

    private void addScoreBreakdown(Document document, AnalysisResult analysis) throws DocumentException {
        addSectionTitle(document, "Score Breakdown");

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{40, 30, 30});

        // Header
        addHeaderCell(table, "Dimension");
        addHeaderCell(table, "Score");
        addHeaderCell(table, "Rating");

        // Rows
        addScoreRow(table, "Repository Hygiene", analysis.getHygieneScore());
        addScoreRow(table, "README Quality", analysis.getReadmeScore());
        addScoreRow(table, "Project Structure", analysis.getStructureScore());
        addScoreRow(table, "Commit Quality", analysis.getCommitQualityScore());
        addScoreRow(table, "Documentation", analysis.getDocumentationScore());
        addScoreRow(table, "Code Quality", analysis.getCodeQualityScore());

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
                    null, null, null,
                    dataset,
                    PlotOrientation.VERTICAL,
                    false, true, false);

            barChart.setBackgroundPaint(Color.WHITE);
            barChart.getPlot().setBackgroundPaint(Color.WHITE);

            BufferedImage bufferedImage = barChart.createBufferedImage(500, 250);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "png", baos);
            Image image = Image.getInstance(baos.toByteArray());
            image.scaleToFit(500, 250);
            image.setAlignment(Element.ALIGN_CENTER);
            document.add(image);

        } catch (Exception e) {
            document.add(new Paragraph("Chart generation unavailable.", SMALL_FONT));
        }
    }

    @SuppressWarnings("unchecked")
    private void addRecommendations(Document document, AnalysisResult analysis) throws DocumentException {
        addSectionTitle(document, "Actionable Recommendations");

        Map<String, Object> snapshot = analysis.getAnalysisSnapshot();
        if (snapshot == null || !snapshot.containsKey("recommendations")) {
            document.add(new Paragraph("No recommendations generated. Repository meets standards.", BODY_FONT));
            return;
        }

        Object recommendationsObj = snapshot.get("recommendations");
        if (!(recommendationsObj instanceof List<?> recommendationsList) || recommendationsList.isEmpty()) {
            document.add(new Paragraph("No recommendations generated. Repository meets standards.", BODY_FONT));
            return;
        }

        for (Object recObj : recommendationsList) {
            if (!(recObj instanceof Map<?, ?> rec)) continue;

            String title = rec.get("title") != null ? rec.get("title").toString() : "Recommendation";
            String priority = rec.get("priority") != null ? rec.get("priority").toString() : "MEDIUM";
            String description = rec.get("description") != null ? rec.get("description").toString() : "";
            String category = rec.get("category") != null ? rec.get("category").toString() : "";
            String reason = rec.get("reason") != null ? rec.get("reason").toString() : "";

            PdfPTable card = new PdfPTable(1);
            card.setWidthPercentage(100);
            
            PdfPCell cell = new PdfPCell();
            cell.setBorderColor(BORDER_COLOR);
            cell.setPadding(15);
            cell.setPaddingBottom(20);
            
            // Title & Badge
            Paragraph titlePara = new Paragraph();
            titlePara.add(new Chunk(title, CARD_TITLE_FONT));
            titlePara.add(new Chunk("   [" + priority + "]", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, getPriorityColor(priority))));
            titlePara.setSpacingAfter(8);
            cell.addElement(titlePara);
            
            if (!reason.isEmpty()) {
                Paragraph reasonPara = new Paragraph("Evidence: " + reason, SMALL_FONT);
                reasonPara.setSpacingAfter(8);
                cell.addElement(reasonPara);
            }
            
            Paragraph descPara = new Paragraph(description, BODY_FONT);
            cell.addElement(descPara);
            
            card.addCell(cell);
            document.add(card);
            document.add(new Paragraph(" "));
        }
    }
    
    @SuppressWarnings("unchecked")
    private void addReasoning(Document document, AnalysisResult analysis) throws DocumentException {
        Map<String, Object> snapshot = analysis.getAnalysisSnapshot();
        if (snapshot == null || !snapshot.containsKey("reasoning")) return;
        
        Object reasoningObj = snapshot.get("reasoning");
        if (!(reasoningObj instanceof List<?> reasoningList) || reasoningList.isEmpty()) return;
        
        document.newPage();
        addSectionTitle(document, "Analysis Evidence Details");
        
        for (Object item : reasoningList) {
            if (item == null) continue;
            String reason = item.toString();
            Font font = BODY_FONT;
            if (reason.contains("✔")) {
                font = FontFactory.getFont(FontFactory.HELVETICA, 11, new Color(46, 125, 50));
            } else if (reason.contains("✘")) {
                font = FontFactory.getFont(FontFactory.HELVETICA, 11, new Color(198, 40, 40));
            }
            Paragraph p = new Paragraph(reason, font);
            p.setSpacingAfter(4);
            document.add(p);
        }
    }

    private void addAnalysisFooter(Document document, AnalysisResult analysis) throws DocumentException {
        addDivider(document);
        Paragraph footer = new Paragraph("Generated by RepoDoctor — GitHub Repository Health Analyzer", SMALL_FONT);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(10);
        document.add(footer);
    }

    // ---- Helper methods ----

    private void addSectionTitle(Document document, String title) throws DocumentException {
        Paragraph sectionTitle = new Paragraph(title, SECTION_FONT);
        sectionTitle.setSpacingBefore(15);
        sectionTitle.setSpacingAfter(15);
        document.add(sectionTitle);
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, TEXT_MAIN)));
        cell.setPadding(10);
        cell.setBackgroundColor(TABLE_HEADER_BG);
        cell.setBorderColor(BORDER_COLOR);
        table.addCell(cell);
    }

    private void addScoreRow(PdfPTable table, String area, Integer score) {
        PdfPCell labelCell = new PdfPCell(new Phrase(area, BODY_FONT));
        labelCell.setPadding(8);
        labelCell.setBorderColor(BORDER_COLOR);
        table.addCell(labelCell);

        String scoreText = score != null ? score + " / 100" : "N/A";
        PdfPCell scoreCell = new PdfPCell(new Phrase(scoreText, BOLD_FONT));
        scoreCell.setPadding(8);
        scoreCell.setBorderColor(BORDER_COLOR);
        table.addCell(scoreCell);

        String rating = getRating(score);
        PdfPCell ratingCell = new PdfPCell(new Phrase(rating, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, getHealthColor(rating))));
        ratingCell.setPadding(8);
        ratingCell.setBorderColor(BORDER_COLOR);
        table.addCell(ratingCell);
    }

    private void addSpacing(Document document) throws DocumentException {
        document.add(new Paragraph(" "));
    }

    private void addDivider(Document document) throws DocumentException {
        Paragraph divider = new Paragraph(" ", SMALL_FONT);
        divider.setSpacingAfter(15);
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
    
    private Color getPriorityColor(String priority) {
        return switch (priority.toUpperCase()) {
            case "CRITICAL" -> PRIORITY_CRITICAL;
            case "HIGH" -> PRIORITY_HIGH;
            case "MEDIUM" -> PRIORITY_MEDIUM;
            case "LOW" -> PRIORITY_LOW;
            default -> TEXT_MAIN;
        };
    }
    
    private Color getHealthColor(String health) {
        String h = health.toUpperCase();
        if (h.contains("EXCELLENT") || h.contains("VERY GOOD")) return PRIORITY_LOW;
        if (h.contains("GOOD")) return new Color(139, 195, 74);
        if (h.contains("FAIR") || h.contains("NEEDS IMPROVEMENT")) return PRIORITY_MEDIUM;
        if (h.contains("POOR") || h.contains("CRITICAL")) return PRIORITY_CRITICAL;
        return TEXT_MAIN;
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