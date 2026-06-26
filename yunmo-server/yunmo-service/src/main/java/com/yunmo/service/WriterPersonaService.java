package com.yunmo.service;

import com.yunmo.service.style.StyleModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Writer 人设服务 — 根据小说类型自动选择写手 persona
 * 不同赛道（热血爽文/悬疑/短句网感/女频情感）用不同的写作指导
 */
@Service
public class WriterPersonaService {

    private static final Logger log = LoggerFactory.getLogger(WriterPersonaService.class);
    private static final String PERSONAS_DIR = "writer-personas";

    private final Map<String, String> personaCache = new HashMap<>();

    /** genre → persona 文件名映射 */
    private static final Map<String, String> GENRE_PERSONA_MAP = Map.ofEntries(
        Map.entry("xianxia", "hot-blood-shuangwen.md"),
        Map.entry("xuanhuan", "hot-blood-shuangwen.md"),
        Map.entry("qihuan", "hot-blood-shuangwen.md"),
        Map.entry("wuxia", "hot-blood-shuangwen.md"),
        Map.entry("dushi", "urban-short-sentence.md"),
        Map.entry("xuanyi", "suspense-info.md"),
        Map.entry("qingxiaoshuo", "hot-blood-shuangwen.md"),
        Map.entry("tongren", "hot-blood-shuangwen.md"),
        Map.entry("duanpian", "urban-short-sentence.md")
    );

    public WriterPersonaService() {
        loadAllPersonas();
    }

    private void loadAllPersonas() {
        Set<String> files = new HashSet<>(GENRE_PERSONA_MAP.values());
        for (String fileName : files) {
            try {
                String content = loadFromClasspath(fileName);
                if (content != null) {
                    personaCache.put(fileName, content);
                    log.info("Writer persona 已加载: {} ({} 字)", fileName, content.length());
                }
            } catch (Exception e) {
                log.warn("Writer persona 加载失败: {}", fileName, e.getMessage());
            }
        }
    }

    private String loadFromClasspath(String fileName) {
        try {
            var is = getClass().getClassLoader()
                .getResourceAsStream(PERSONAS_DIR + "/" + fileName);
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            // fallback
        }
        // 尝试从文件系统加载
        try {
            Path path = Path.of("yunmo-server", "yunmo-server-app", "src", "main", "resources",
                PERSONAS_DIR, fileName);
            if (Files.exists(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
            path = Path.of("src", "main", "resources", PERSONAS_DIR, fileName);
            if (Files.exists(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
        } catch (IOException e) { /* ignore */ }
        return null;
    }

    /**
     * 根据 genre 获取对应的 writer persona 提示词
     * @param genreId 小说类型 ID
     * @return persona 文本，若未匹配则返回空字符串
     */
    public String getPersona(String genreId) {
        if (genreId == null || genreId.isBlank()) return "";
        String fileName = GENRE_PERSONA_MAP.get(genreId);
        if (fileName == null) return "";
        return personaCache.getOrDefault(fileName, "");
    }

    /** 获取所有已加载的 persona 文件名 */
    public Set<String> loadedPersonas() {
        return personaCache.keySet();
    }

    /**
     * 根据风格模块返回对应的写手人设
     * 风格人设是体裁人设的补充 — 体裁人设提供"谁在写"，风格人设提供"怎么写"
     *
     * @param style 风格模块
     * @return 写手人设描述文本
     */
    public String getStylePersona(StyleModule style) {
        if (style == null) return "";
        return switch (style) {
            case HUMOR -> "擅长反差制造和冷幽默时机把控的段子手。懂得笑点不在段子而在角色在压力下的窘迫反应，" +
                    "每次笑点都要改变场面/关系/地位或读者对人物的理解，笑后有真实的余波。";
            case SUSPENSE -> "精于信息差设计和压力递增的悬疑大师。让读者知道得越多越不安，" +
                    "每次不给答案都让代价更清晰，延迟揭示必须有故事内的理由。";
            case MYSTERY -> "证据管理和线索公平性的推理专家。真线索尽早出现但可被合理误读，" +
                    "红鲱鱼自身必须合理，读者应随时可能比侦探更早猜到真相。";
            case ROMANCE -> "善于用欲望、恐惧、自尊和情感债在场景中持续摩擦的情感写手。" +
                    "关系推进必须经由压力，吸引力和风险同时存在，每进一步都付出代价或暴露脆弱。";
            case HORROR -> "让熟悉之物变得不可靠的恐怖工匠。逐层剥离安全感而非依赖跳吓，" +
                    "每一处不对劲都能被感到，每种安全解释都被逐个否定。";
            case FANTASY -> "异质规则与日常生活互相咬合的世界构建者。规则服务冲突而非取代冲突，" +
                    "每条超自然规则都带来不可逆的代价或限制。";
            case LITERARY -> "让语言、人物和主题在场景里互相施压的文学工匠。主题压在人物身上，" +
                    "好句子是冲突的副产品而非修辞竞赛，语言让读者更靠近经验而非评论。";
        };
    }
}
