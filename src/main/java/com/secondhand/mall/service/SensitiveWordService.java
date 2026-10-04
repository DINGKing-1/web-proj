package com.secondhand.mall.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/** 简易敏感词过滤 */
@Service
public class SensitiveWordService {

    private static final List<String> WORDS = List.of(
        "诈骗", "赌博", "毒品", "枪支", "色情", "代开发票", "办证",
        "洗钱", "传销", "法轮", "自杀", "爆炸物", "假币"
    );

    private static final Pattern PATTERN = Pattern.compile(
        String.join("|", WORDS.stream().map(w -> Pattern.quote(w)).toList()),
        Pattern.CASE_INSENSITIVE
    );

    /** 命中则返回第一个敏感词，否则 null */
    public String findSensitive(String text) {
        if (text == null || text.isBlank()) return null;
        var m = PATTERN.matcher(text);
        return m.find() ? m.group() : null;
    }

    public void check(String... texts) {
        for (String t : texts) {
            String hit = findSensitive(t);
            if (hit != null) {
                throw new RuntimeException("内容包含敏感词「" + hit + "」，请修改后重试");
            }
        }
    }

    public String filter(String text) {
        if (text == null) return null;
        return PATTERN.matcher(text).replaceAll("**");
    }
}
