package com.api.v1.newssystem.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommentRulesTest {

    @Test
    void countsHttpWwwBareDomainAndShortLinks() {
        assertThat(CommentRules.countLinks(null)).isZero();
        assertThat(CommentRules.countLinks("no links here")).isZero();
        assertThat(CommentRules.countLinks("HTTPS://A.EXAMPLE/x")).isEqualTo(1);
        assertThat(CommentRules.countLinks("www.a.example and http://b.example")).isEqualTo(2);
        assertThat(CommentRules.countLinks("promo.com/free, t.me/channel, bit.ly/x")).isEqualTo(3);
    }

    @Test
    void aLinkIsCountedOnce() {
        assertThat(CommentRules.countLinks("https://www.example.com/page?x=1")).isEqualTo(1);
    }

    @Test
    void sameTextIgnoresCaseSpacesAndInvisibleCharacters() {
        assertThat(CommentRules.isSameText("Hello  World", " hello world ")).isTrue();
        assertThat(CommentRules.isSameText("ab​c", "abc")).isTrue();
        assertThat(CommentRules.isSameText("Hello", "Hello!")).isFalse();
        assertThat(CommentRules.isSameText(null, "Hello")).isFalse();
    }

    @Test
    void blockedWordsAreOnePerLine_blankLinesAndSpacesIgnored() {
        assertThat(CommentRules.parseBlockedWords("  casino \r\n\r\nfree money\nល្បែង\n   ")).containsExactly("casino", "free money", "ល្បែង");
        assertThat(CommentRules.parseBlockedWords("")).isEmpty();
        assertThat(CommentRules.parseBlockedWords(null)).isEmpty();
    }

    @Test
    void anEnglishBlockedWordMatchesWithoutCase_andAsAPhraseAcrossSpaces() {
        List<String> words = List.of("free money", "CASINO");

        assertThat(CommentRules.findBlockedWord("get Free   MONEY now", words)).isEqualTo("free money");
        assertThat(CommentRules.findBlockedWord("best casino ever", words)).isEqualTo("CASINO");
        assertThat(CommentRules.findBlockedWord("a normal comment", words)).isNull();
    }

    @Test
    void aKhmerBlockedWordMatchesInsideAnUnspacedSentence() {
        List<String> words = List.of("ល្បែង");

        assertThat(CommentRules.findBlockedWord("សូមចូលលេងល្បែងនៅទីនេះ", words)).isEqualTo("ល្បែង");
        assertThat(CommentRules.findBlockedWord("អត្ថបទល្អណាស់", words)).isNull();
    }

    @Test
    void zeroWidthCharactersCannotHideAWord() {
        assertThat(CommentRules.findBlockedWord("ca​si‍no", List.of("casino"))).isEqualTo("casino");
        assertThat(CommentRules.findBlockedWord("សូមចូលលេង​ល្បែង​នៅទីនេះ", List.of("ល្បែង"))).isEqualTo("ល្បែង");
    }

    @Test
    void noWordsMeansNothingIsBlocked() {
        assertThat(CommentRules.findBlockedWord("anything", List.of())).isNull();
        assertThat(CommentRules.findBlockedWord("anything", null)).isNull();
    }
}
