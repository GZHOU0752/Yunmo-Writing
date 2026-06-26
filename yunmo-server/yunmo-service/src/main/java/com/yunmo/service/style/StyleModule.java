package com.yunmo.service.style;

import java.util.List;

/**
 * 风格模块定义 — 7大风格模块，按需加载
 * 每次最多2主风格 + 1辅助风格
 *
 * 参考 Novel Control Station 风格模块系统设计
 */
public enum StyleModule {

    HUMOR("幽默", "角色窘迫/冷幽默反差/关系互怼",
            "角色越想维持体面/权威/冷静/正确，现实越不给面子。笑点必须改变压力/关系/地位或读者对人物的理解。",
            "伪幽默警示：不要把幽默等同于'角色讲笑话'或'刻意搞笑'。真正的幽默来自角色在压力下的窘迫反应和认知错位，而非段子堆砌。",
            List.of("1次笑点改变场面/关系/地位", "1段对话比上一段更有辨识度", "1个笑后余波（不让笑点无后果地消失）")),

    SUSPENSE("悬念", "惊悚高压/心理悬念/慢烧/生存逃亡",
            "悬念靠'问题越来越贵'成立，不靠'信息越来越少'成立。延迟揭示必须有理由，每次不给答案都应让代价更清晰。",
            "伪悬念警示：不要用'先不告诉你'制造悬念。信息遮蔽本身不是悬念——读者必须在知道部分信息后感到'知道得越多越不安'。",
            List.of("1个能说清的主未知", "1次代价更清的推进", "1个让下一章更有必要的余拉点")),

    MYSTERY("推理", "证据管理/线索公平性",
            "推理的根是证据管理。真线索应尽早出现但可被合理误读，红鲱鱼必须自身合理且之后能被解释。读者应随时可能比侦探更早猜到真相。",
            "伪推理警示：不要把推理等同于'聪明人突然知道答案'。推理的高潮不是揭晓答案，而是读者发现自己漏掉了早就出现的线索。",
            List.of("1项新证据或旧证据新读法", "1个合理但错误的解释被建立/加深/拆除", "1步调查推进带来关系变化")),

    ROMANCE("爱情", "情感债/关系摩擦/小动作",
            "爱情线靠欲望、恐惧、自尊、照顾、误判和情感债在场景里持续摩擦。关系推进必须经由压力——每进一步都要付出代价或暴露脆弱。",
            "伪爱情警示：不要把爱情写成'互相说好话'或'气氛到了自然在一起'。吸引力和风险应同时存在——靠近一个人的每一步都在赌。",
            List.of("1次关系状态可感位移", "1次情感债留下", "1处吸引与风险同时更清楚")),

    HORROR("恐怖", "熟悉变不可靠/持续不祥感",
            "恐怖是让熟悉之物变得不再可靠，并且这种不可靠越来越难解释、难承受、难活过。安全感被逐层剥离，而非靠跳吓维持。",
            "伪恐怖警示：不要把恐怖等同于'突然出现吓人的东西'。真正的恐怖来自安全解释被逐一否定——角色和读者同时意识到'没有安全的地方了'。",
            List.of("1处能被感到的不对劲", "1次安全解释被削弱/失效", "1项进入下一章的余波/损伤/污染")),

    FANTASY("奇幻", "规则承重/世界厚度/代价",
            "异质规则、能力体系、历史残留和日常生活必须互相咬合。规则要服务冲突而非取代冲突——能力越强，代价和限制应越具体。",
            "伪奇幻警示：不要把奇幻等同于'想怎样就怎样'。每一条超自然规则都应带来不可逆的代价或限制，否则规则只是装饰。",
            List.of("1条真正承重的规则被看见", "1处世界厚度显形", "1项能力/身份/制度成本改变行动或关系")),

    LITERARY("文学", "主题施压/语言精确/形式意义",
            "文学性不是高腔调，而是语言、人物和主题在场景里互相施压。主题应压在人物身上——让角色为价值冲突付出实际代价，而非旁白替读者总结。",
            "伪文学警示：不要把文学等同于'写得美'或'有哲理'。真正的文学性是让语言、人物和主题在场景里彼此挤压——好句子是冲突的副产品，不是修辞竞赛。",
            List.of("1项价值冲突被逼到人物身上", "1个意象/动作/结构回返真正承重", "1段语言让读者更靠近经验而非评论"));

    private final String chineseName;
    private final String subTypes;
    private final String corePrinciple;
    private final String pseudoStyleWarning;
    private final List<String> chapterPriorities;

    StyleModule(String chineseName, String subTypes, String corePrinciple,
                String pseudoStyleWarning, List<String> chapterPriorities) {
        this.chineseName = chineseName;
        this.subTypes = subTypes;
        this.corePrinciple = corePrinciple;
        this.pseudoStyleWarning = pseudoStyleWarning;
        this.chapterPriorities = List.copyOf(chapterPriorities);
    }

    public String getChineseName() {
        return chineseName;
    }

    public String getSubTypes() {
        return subTypes;
    }

    public String getCorePrinciple() {
        return corePrinciple;
    }

    public String getPseudoStyleWarning() {
        return pseudoStyleWarning;
    }

    public List<String> getChapterPriorities() {
        return chapterPriorities;
    }
}
