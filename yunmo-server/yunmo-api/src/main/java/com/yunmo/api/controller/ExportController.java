package com.yunmo.api.controller;

import com.yunmo.service.EpubExportService;
import com.yunmo.service.ExportService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/novels/{novelId}")
public class ExportController {

    private final ExportService exportService;
    private final EpubExportService epubService;

    public ExportController(ExportService exportService, EpubExportService epubService) {
        this.exportService = exportService;
        this.epubService = epubService;
    }

    @GetMapping("/export/txt")
    public Mono<ResponseEntity<Resource>> exportTxt(@PathVariable String novelId) {
        return Mono.fromCallable(() -> {
            String content = exportService.exportTxt(novelId);
            String filename = URLEncoder.encode("novel.txt", StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body((Resource) new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/export/epub")
    public Mono<ResponseEntity<Resource>> exportEpub(@PathVariable String novelId) {
        return Mono.fromCallable(() -> {
            byte[] epub = epubService.export(novelId);
            String filename = URLEncoder.encode("novel.epub", StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                    .contentType(MediaType.parseMediaType("application/epub+zip"))
                    .body((Resource) new ByteArrayResource(epub));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/export/html")
    public Mono<ResponseEntity<Resource>> exportHtml(@PathVariable String novelId) {
        return Mono.fromCallable(() -> {
            String content = exportService.exportHtml(novelId);
            String filename = URLEncoder.encode("novel.html", StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                    .contentType(MediaType.TEXT_HTML)
                    .body((Resource) new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/export/md")
    public Mono<ResponseEntity<Resource>> exportMarkdown(@PathVariable String novelId) {
        return Mono.fromCallable(() -> {
            String content = exportService.exportMarkdown(novelId);
            String filename = URLEncoder.encode("novel.md", StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                    .contentType(MediaType.parseMediaType("text/markdown"))
                    .body((Resource) new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
