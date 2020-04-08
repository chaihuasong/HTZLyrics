package com.chs.htz.lyrics.utils;

public class LrcTextUtils {
    public static String trimLricText(String lrcText) {
        String regexBegin = "^[，！；。、].*";
        String regexEnd = ".*[，！；。、]$";
        boolean matchesBegin = lrcText.matches(regexBegin);
        boolean matchesEnd = lrcText.matches(regexEnd);
        if (matchesBegin) {
            if (lrcText.length() == 1) return "";
            lrcText = lrcText.substring(1);
        }

        if (matchesEnd) {
            if (lrcText.length() == 1) return "";
            lrcText = lrcText.substring(0, lrcText.length() - 1);
        }

        return lrcText;
    }
}
