package dev.duosight.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class LanguageSmoke {
    public static void validate(Minecraft mc) {
        Language original = Language.getInstance();
        var english = ClientLanguage.loadFrom(mc.getResourceManager(), List.of("en_us"), false);
        var chinese = ClientLanguage.loadFrom(mc.getResourceManager(), List.of("en_us", "zh_cn"), false);
        var keys = english.getLanguageData().keySet().stream().filter(key -> key.startsWith("duosight.")).toList();
        check(keys.size() >= 45, "complete base and control-book translations");
        try {
            for (String code : List.of("zh_cn", "zh_tw", "zh_hk", "en_us", "ja_jp", "fr_fr", "de_de")) {
                var language = ClientLanguage.loadFrom(mc.getResourceManager(), List.of("en_us", code), false);
                for (String key : keys) {
                    check(language.has(key), "missing " + code + " " + key);
                    if (!code.startsWith("zh_")) {
                        check(language.getOrDefault(key).equals(english.getOrDefault(key)), "English fallback " + code);
                    } else {
                        check(language.getOrDefault(key).equals(chinese.getOrDefault(key)), "Simplified Chinese " + code);
                    }
                }
                Language.inject(language);
                String expected = code.startsWith("zh_") ? "操作者" : "Driver";
                check(Component.translatable("duosight.driver").getString().equals(expected), "role language " + code);
                String status = Component.translatable("duosight.status", 120, 60).getString();
                check(status.contains("120") && status.contains("60") && !status.contains("%s"),
                        "formatted server message " + code);
                System.out.println("BE_MY_EYES_LANGUAGE_PASS: " + code);
            }
        } finally {
            Language.inject(original);
        }
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError("Be My Eyes language test: " + message);
    }

    private LanguageSmoke() {}
}
