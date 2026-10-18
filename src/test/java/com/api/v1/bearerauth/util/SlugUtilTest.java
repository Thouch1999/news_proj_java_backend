package com.api.v1.bearerauth.util;

import com.api.v1.bearerauth.common.constant.MessageConstant;
import com.api.v1.bearerauth.common.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlugUtilTest {

    @Test
    void slugify_lowercasesAndJoinsWordsWithHyphens() {
        assertThat(SlugUtil.slugify("Technology News")).isEqualTo("technology-news");
        assertThat(SlugUtil.slugify("  A & B -- C  ")).isEqualTo("a-b-c");
        assertThat(SlugUtil.slugify("Top 10 of 2026")).isEqualTo("top-10-of-2026");
    }

    @Test
    void slugify_removesAccents() {
        assertThat(SlugUtil.slugify("Café Société")).isEqualTo("cafe-societe");
    }

    @Test
    void slugify_khmerOnlyNameGivesEmptySlug_andMixedNameKeepsTheLatinPart() {
        assertThat(SlugUtil.slugify("កីឡា")).isEmpty();
        assertThat(SlugUtil.slugify("Sports កីឡា")).isEqualTo("sports");
        assertThat(SlugUtil.slugify(null)).isEmpty();
    }

    @Test
    void resolve_addsNumberUntilUnique() {
        Set<String> taken = Set.of("sports", "sports-2");

        assertThat(SlugUtil.resolve(null, "Sports", taken::contains)).isEqualTo("sports-3");
        assertThat(SlugUtil.resolve("", "Health", taken::contains)).isEqualTo("health");
    }

    @Test
    void resolve_usesTheSlugSentByTheAdmin_andRejectsOneThatIsTaken() {
        assertThat(SlugUtil.resolve(" sports ", "កីឡា", slug -> false)).isEqualTo("sports");

        assertThatThrownBy(() -> SlugUtil.resolve("sports", "កីឡា", "sports"::equals))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo(MessageConstant.SLUG_ALREADY_EXISTS.getDefaultMessage());
                });
    }

    @Test
    void resolve_khmerOnlyNameWithoutASlug_asksForOne() {
        assertThatThrownBy(() -> SlugUtil.resolve(null, "កីឡា", slug -> false))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo(MessageConstant.SLUG_REQUIRED.getDefaultMessage());
                });
    }
}
