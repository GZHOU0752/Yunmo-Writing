package com.yunmo.service;

import com.yunmo.domain.entity.Career;
import com.yunmo.domain.repository.CareerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 职业体系服务
 */
@Service
public class CareerService {

    private static final Logger log = LoggerFactory.getLogger(CareerService.class);

    /** 内置职业模板 */
    private static final Map<String, List<Map<String, Object>>> GENRE_TEMPLATES;
    static {
        Map<String, List<Map<String, Object>>> templates = new LinkedHashMap<>();
        templates.put("xianxia", List.of(
            makeStage("炼气期", 1), makeStage("筑基期", 2),
            makeStage("金丹期", 3), makeStage("元婴期", 4),
            makeStage("化神期", 5), makeStage("炼虚期", 6),
            makeStage("合体期", 7), makeStage("大乘期", 8),
            makeStage("渡劫期", 9)
        ));
        templates.put("western-fantasy", List.of(
            makeStage("学徒", 1), makeStage("初级魔法师", 2),
            makeStage("中级魔法师", 3), makeStage("高级魔法师", 4),
            makeStage("大魔法师", 5), makeStage("魔导师", 6),
            makeStage("大魔导师", 7), makeStage("圣魔导师", 8),
            makeStage("法神", 9)
        ));
        templates.put("scifi-apocalypse", List.of(
            makeStage("F级觉醒者", 1), makeStage("E级觉醒者", 2),
            makeStage("D级觉醒者", 3), makeStage("C级觉醒者", 4),
            makeStage("B级觉醒者", 5), makeStage("A级觉醒者", 6),
            makeStage("S级觉醒者", 7), makeStage("SS级觉醒者", 8),
            makeStage("SSS级觉醒者", 9)
        ));
        templates.put("urban", List.of(
            makeStage("新人", 1), makeStage("资深", 2),
            makeStage("主管", 3), makeStage("经理", 4),
            makeStage("总监", 5), makeStage("副总裁", 6),
            makeStage("总裁", 7), makeStage("行业领袖", 8),
            makeStage("传奇人物", 9)
        ));
        GENRE_TEMPLATES = Collections.unmodifiableMap(templates);
    }

    private static Map<String, Object> makeStage(String name, int stage) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("stage", stage);
        return map;
    }

    private final CareerRepository careerRepo;

    public CareerService(CareerRepository careerRepo) {
        this.careerRepo = careerRepo;
    }

    /** 根据小说类型生成内置职业体系 */
    public Career createBuiltInCareer(String novelId, String genreId) {
        var stages = GENRE_TEMPLATES.getOrDefault(genreId, GENRE_TEMPLATES.get("xianxia"));

        Career career = new Career();
        career.setNovelId(novelId);
        career.setName(switch (genreId) {
            case "xianxia" -> "修仙境界";
            case "western-fantasy" -> "魔法师等级";
            case "scifi-apocalypse" -> "觉醒者等级";
            case "urban" -> "职场等级";
            default -> "境界体系";
        });
        career.setDescription(genreId + " 内置职业体系");
        @SuppressWarnings("unchecked")
        List<Object> stageList = (List<Object>) (List<?>) stages;
        career.setStages(stageList);
        career.setMaxStage(stages.size());

        log.info("创建职业体系: {} -> {}", novelId, career.getName());
        return careerRepo.save(career);
    }
}
