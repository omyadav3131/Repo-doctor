package com.omyadav.repodoctor.controller;

import com.omyadav.repodoctor.service.RepositoryPdfReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analyses/{owner}/{repositoryName}")
public class RepositoryPdfReportController {

    private final RepositoryPdfReportService repositoryPdfReportService;

    public RepositoryPdfReportController(
            RepositoryPdfReportService repositoryPdfReportService) {

        this.repositoryPdfReportService = repositoryPdfReportService;
    }

    @GetMapping(
            value = "/report/pdf",
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<byte[]> downloadPdfReport(
            @PathVariable String owner,
            @PathVariable String repositoryName) {

        byte[] pdfReport = repositoryPdfReportService.generateReport(owner, repositoryName);

        String fileName = "RepoDoctor-" + repositoryName + "-Report.pdf";

        return ResponseEntity
                .ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\""
                )
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdfReport.length)
                .body(pdfReport);
    }
}