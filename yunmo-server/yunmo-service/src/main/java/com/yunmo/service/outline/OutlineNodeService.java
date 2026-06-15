package com.yunmo.service.outline;

import com.yunmo.domain.entity.OutlineNode;
import com.yunmo.domain.repository.OutlineNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 大纲节点服务 — CRUD + 树查询 + 排序
 */
@Service
public class OutlineNodeService {

    private static final Logger log = LoggerFactory.getLogger(OutlineNodeService.class);
    private final OutlineNodeRepository repo;

    public OutlineNodeService(OutlineNodeRepository repo) {
        this.repo = repo;
    }

    /** 获取小说的完整大纲树 */
    public List<OutlineNode> getTree(String novelId) {
        return repo.findByNovelIdOrderBySequenceOrderAsc(novelId);
    }

    /** 获取任意节点的子节点 */
    public List<OutlineNode> getChildren(String novelId, String parentId) {
        if (parentId == null) {
            return repo.findByNovelIdAndParentIdIsNullOrderBySequenceOrderAsc(novelId);
        }
        return repo.findByNovelIdAndParentIdOrderBySequenceOrderAsc(novelId, parentId);
    }

    /** 获取指定层级的所有节点 */
    public List<OutlineNode> getByLevel(String novelId, int level) {
        return repo.findByNovelIdAndLevelOrderBySequenceOrderAsc(novelId, level);
    }

    /** 新增大纲节点 */
    @Transactional
    public OutlineNode create(String novelId, String parentId, String title, int level,
                               String causalSentence, String outlineContent, Integer wordCountTarget) {
        OutlineNode node = new OutlineNode();
        node.setNovelId(novelId);
        node.setParentId(parentId);
        node.setTitle(title);
        node.setLevel(level);
        node.setCausalSentence(causalSentence);
        node.setOutlineContent(outlineContent);
        node.setWordCountTarget(wordCountTarget);
        node.setStatus("draft");

        // 计算当前最大排序号（parentId=null 时用 findBy...ParentIdIsNull 避免 SQL NULL 陷阱）
        long count = parentId != null
                ? repo.countByNovelIdAndParentId(novelId, parentId)
                : repo.findByNovelIdAndParentIdIsNullOrderBySequenceOrderAsc(novelId).size();
        node.setSequenceOrder((int) count + 1);

        log.info("新增大纲节点: novel={}, title={}, level={}, parent={}", novelId, title, level, parentId);
        return repo.save(node);
    }

    /** 更新大纲节点 */
    @Transactional
    public OutlineNode update(String nodeId, String title, String causalSentence,
                               String outlineContent, Integer wordCountTarget, String status,
                               Integer chapterNumber) {
        OutlineNode node = repo.findById(nodeId)
                .orElseThrow(() -> new NoSuchElementException("大纲节点不存在: " + nodeId));

        if (title != null) node.setTitle(title);
        if (causalSentence != null) node.setCausalSentence(causalSentence);
        if (outlineContent != null) node.setOutlineContent(outlineContent);
        if (wordCountTarget != null) node.setWordCountTarget(wordCountTarget);
        if (status != null) node.setStatus(status);
        if (chapterNumber != null) node.setChapterNumber(chapterNumber);

        log.info("更新大纲节点: {}", nodeId);
        return repo.save(node);
    }

    /** 删除节点及其所有子孙节点 */
    @Transactional
    public int deleteCascade(String nodeId) {
        OutlineNode node = repo.findById(nodeId).orElse(null);
        if (node == null) return 0;

        int count = 1;
        List<OutlineNode> children = repo.findByNovelIdAndParentIdOrderBySequenceOrderAsc(
                node.getNovelId(), nodeId);
        for (OutlineNode child : children) {
            count += deleteCascade(child.getId());
        }
        repo.delete(node);
        log.info("删除大纲节点 {} 及子孙，共 {} 个", nodeId, count);
        return count;
    }

    /** 批量更新排序（拖拽后调用） */
    @Transactional
    public void reorder(String novelId, List<Map<String, Object>> items) {
        for (Map<String, Object> item : items) {
            String id = (String) item.get("id");
            int order = ((Number) item.get("sequenceOrder")).intValue();
            String newParentId = (String) item.get("parentId");
            repo.findById(id).ifPresent(node -> {
                node.setSequenceOrder(order);
                if (newParentId != null) node.setParentId(newParentId);
                repo.save(node);
            });
        }
        log.info("大纲重排序: novel={}, {} 个节点", novelId, items.size());
    }

    /** 将大纲节点绑定到章节 */
    @Transactional
    public void bindChapter(String nodeId, int chapterNumber) {
        repo.findById(nodeId).ifPresent(node -> {
            node.setChapterNumber(chapterNumber);
            node.setStatus("confirmed");
            repo.save(node);
        });
    }
}
