package com.ovaledge.csp.validation;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for {@link ServerTypeNormalizer}. */
class ServerTypeNormalizerTest {

    @Test
    void normalize_shouldAllowHyphensAndCollapseSpaces() {
        assertEquals("qlik-sense", ServerTypeNormalizer.normalize("Qlik Sense"));
        assertEquals("qlik-sense", ServerTypeNormalizer.normalize("qlik-sense"));
        assertEquals("quickbooks-online", ServerTypeNormalizer.normalize(" quickbooks---online "));
        assertEquals("awsglue", ServerTypeNormalizer.normalize("Aws@Glue"));
        assertEquals("monet-db", ServerTypeNormalizer.normalize("Monet DB"));
        assertEquals("mysql", ServerTypeNormalizer.normalize("mysql"));
        assertEquals("", ServerTypeNormalizer.normalize(null));
    }

    @Test
    void compact_shouldStripHyphensForPackageComparison() {
        assertEquals("monetdb", ServerTypeNormalizer.compact("Monet DB"));
        assertEquals("monetdb", ServerTypeNormalizer.compact("monetdb"));
    }

    @Test
    void hasDisallowedCharacters_shouldRejectSpecialCharsExceptHyphen() {
        assertTrue(ServerTypeNormalizer.hasDisallowedCharacters("aws@glue"));
        assertTrue(ServerTypeNormalizer.hasDisallowedCharacters("foo.bar"));
        assertFalse(ServerTypeNormalizer.hasDisallowedCharacters("my-connector"));
        assertFalse(ServerTypeNormalizer.hasDisallowedCharacters("Monet DB"));
    }

    @Test
    void suggestAlternate_withoutBlockedSet_shouldDefaultToV2() {
        assertEquals("mysql-v2", ServerTypeNormalizer.suggestAlternate("mysql"));
        assertEquals("my-connector-v2", ServerTypeNormalizer.suggestAlternate("my-connector"));
    }

    @Test
    void suggestAlternate_shouldIncrementVersionWhenVariantsExist() {
        Set<String> blocked = Set.of("mysql", "mysql-v2");
        assertEquals("mysql-v3", ServerTypeNormalizer.suggestAlternate("mysql", blocked));
        assertEquals("mysql-v3", ServerTypeNormalizer.suggestAlternate("mysql-v2", blocked));
    }

    @Test
    void suggestAlternate_shouldSkipPackageCompactCollisions() {
        Set<String> blocked = Set.of("monetdb");
        assertEquals("monet-db-v2", ServerTypeNormalizer.suggestAlternate("monet-db", blocked));
        Set<String> withV2 = Set.of("monetdb", "monet-db-v2");
        assertEquals("monet-db-v3", ServerTypeNormalizer.suggestAlternate("monet-db", withV2));
    }

    @Test
    void stripTrailingVersionSuffix_shouldRemoveVersionOnlyAtEnd() {
        assertEquals("mysql", ServerTypeNormalizer.stripTrailingVersionSuffix("mysql-v2"));
        assertEquals("my-connector", ServerTypeNormalizer.stripTrailingVersionSuffix("my-connector-v10"));
        assertEquals("mysql", ServerTypeNormalizer.stripTrailingVersionSuffix("mysql"));
    }
}
