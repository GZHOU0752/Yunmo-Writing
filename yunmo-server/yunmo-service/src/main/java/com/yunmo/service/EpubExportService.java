package com.yunmo.service;

import com.yunmo.domain.entity.Chapter;
import com.yunmo.domain.entity.Novel;
import com.yunmo.domain.repository.ChapterRepository;
import com.yunmo.domain.repository.NovelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;

/**
 * EPUB 导出服务 — 纯 Java 实现，零外部依赖
 */
@Service
public class EpubExportService {

    private static final Logger log = LoggerFactory.getLogger(EpubExportService.class);
    private final NovelRepository novelRepo;
    private final ChapterRepository chapterRepo;

    public EpubExportService(NovelRepository novelRepo, ChapterRepository chapterRepo) {
        this.novelRepo = novelRepo;
        this.chapterRepo = chapterRepo;
    }

    public byte[] export(String novelId) throws IOException {
        Novel novel = novelRepo.findById(novelId).orElseThrow(() -> new RuntimeException("小说不存在"));
        List<Chapter> chapters = chapterRepo.findByNovelIdOrderByChapterNumberAsc(novelId);
        String uuid = UUID.randomUUID().toString();
        String date = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            // mimetype — 必须未压缩且第一个条目
            ZipEntry mimetype = new ZipEntry("mimetype");
            mimetype.setMethod(ZipEntry.STORED);
            mimetype.setSize(20);
            mimetype.setCompressedSize(20);
            mimetype.setCrc(calcCrc("application/epub+zip".getBytes(StandardCharsets.US_ASCII)));
            zos.putNextEntry(mimetype);
            zos.write("application/epub+zip".getBytes(StandardCharsets.US_ASCII));
            zos.closeEntry();

            // META-INF/container.xml
            zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
            zos.write("<?xml version=\"1.0\"?><container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // OEBPS/content.opf
            String opf = buildOpf(novel, chapters, uuid, date);
            zos.putNextEntry(new ZipEntry("OEBPS/content.opf"));
            zos.write(opf.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // OEBPS/toc.ncx
            String ncx = buildNcx(novel, chapters, uuid);
            zos.putNextEntry(new ZipEntry("OEBPS/toc.ncx"));
            zos.write(ncx.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // OEBPS/style.css
            zos.putNextEntry(new ZipEntry("OEBPS/style.css"));
            zos.write("body{font-family:serif;line-height:1.8;margin:2em}p{text-indent:2em;margin:0.5em 0}h1{text-align:center;margin:1em 0}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 章节 HTML
            for (Chapter ch : chapters) {
                String html = buildChapterHtml(ch);
                zos.putNextEntry(new ZipEntry("OEBPS/chapter" + ch.getChapterNumber() + ".html"));
                zos.write(html.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        log.info("EPUB 导出完成: {}, {} 章, {} 字节", novel.getTitle(), chapters.size(), baos.size());
        return baos.toByteArray();
    }

    private String buildOpf(Novel novel, List<Chapter> chapters, String uuid, String date) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<package xmlns=\"http://www.idpf.org/2007/opf\" unique-identifier=\"book-id\" version=\"2.0\">\n");
        sb.append("<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n");
        sb.append("<dc:title>").append(esc(novel.getTitle())).append("</dc:title>\n");
        sb.append("<dc:creator>云墨</dc:creator>\n");
        sb.append("<dc:date>").append(date).append("</dc:date>\n");
        sb.append("<dc:identifier id=\"book-id\">urn:uuid:").append(uuid).append("</dc:identifier>\n");
        sb.append("<dc:language>zh-CN</dc:language>\n");
        sb.append("</metadata>\n<manifest>\n");
        sb.append("<item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n");
        sb.append("<item id=\"css\" href=\"style.css\" media-type=\"text/css\"/>\n");
        for (Chapter ch : chapters) {
            sb.append("<item id=\"ch").append(ch.getChapterNumber())
              .append("\" href=\"chapter").append(ch.getChapterNumber())
              .append(".html\" media-type=\"application/xhtml+xml\"/>\n");
        }
        sb.append("</manifest>\n<spine toc=\"ncx\">\n");
        for (Chapter ch : chapters) {
            sb.append("<itemref idref=\"ch").append(ch.getChapterNumber()).append("\"/>\n");
        }
        sb.append("</spine>\n</package>");
        return sb.toString();
    }

    private String buildNcx(Novel novel, List<Chapter> chapters, String uuid) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\">\n");
        sb.append("<head><meta name=\"dtb:uid\" content=\"urn:uuid:").append(uuid).append("\"/></head>\n");
        sb.append("<docTitle><text>").append(esc(novel.getTitle())).append("</text></docTitle>\n");
        sb.append("<navMap>\n");
        for (Chapter ch : chapters) {
            sb.append("<navPoint id=\"nav").append(ch.getChapterNumber()).append("\" playOrder=\"").append(ch.getChapterNumber()).append("\">\n");
            sb.append("<navLabel><text>").append(esc(ch.getTitle() != null ? ch.getTitle() : "第" + ch.getChapterNumber() + "章")).append("</text></navLabel>\n");
            sb.append("<content src=\"chapter").append(ch.getChapterNumber()).append(".html\"/>\n");
            sb.append("</navPoint>\n");
        }
        sb.append("</navMap>\n</ncx>");
        return sb.toString();
    }

    private String buildChapterHtml(Chapter ch) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>")
          .append(esc(ch.getTitle() != null ? ch.getTitle() : "第" + ch.getChapterNumber() + "章"))
          .append("</title><link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\"/></head><body>\n");
        sb.append("<h1>").append(esc(ch.getTitle() != null ? ch.getTitle() : "")).append("</h1>\n");
        // 将纯文本按段落包裹
        String content = ch.getContent() != null ? ch.getContent() : "";
        // 去除 HTML 标签
        content = content.replaceAll("<[^>]+>", "");
        for (String para : content.split("\n\n+")) {
            String trimmed = para.trim();
            if (!trimmed.isEmpty()) {
                sb.append("<p>").append(esc(trimmed)).append("</p>\n");
            }
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private long calcCrc(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }
}
