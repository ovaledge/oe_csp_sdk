package com.ovaledge.csp.validation;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for {@link ServerTypeNormalizer}. */
class ServerTypeNormalizerTest {

    @Test
    void normalize_shouldLowercaseAndStripNonAlphanumeric() {
        assertEquals("qliksense", ServerTypeNormalizer.normalize("Qlik Sense"));
        assertEquals("qliksense", ServerTypeNormalizer.normalize("qlik-sense"));
        assertEquals("quickbooksonline", ServerTypeNormalizer.normalize(" quickbooks---online "));
        assertEquals("awsglue", ServerTypeNormalizer.normalize("Aws@Glue"));
        assertEquals("monetdb", ServerTypeNormalizer.normalize("Monet DB"));
        assertEquals("mysql", ServerTypeNormalizer.normalize("mysql"));
        assertEquals("", ServerTypeNormalizer.normalize(null));
    }

    @Test
    void toPascalCase_shouldCapitalizeFirstCharacter() {
        assertEquals("Qliksense", ServerTypeNormalizer.toPascalCase("qliksense"));
        assertEquals("Mysql", ServerTypeNormalizer.toPascalCase("mysql"));
        assertEquals("Mysql2", ServerTypeNormalizer.toPascalCase("mysql2"));
        assertEquals("", ServerTypeNormalizer.toPascalCase(""));
    }

    @Test
    void isValidConnectorId_shouldAcceptLowercaseAlphanumericStartingWithLetter() {
        assertTrue(ServerTypeNormalizer.isValidConnectorId("mysql"));
        assertTrue(ServerTypeNormalizer.isValidConnectorId("mysql2"));
        assertFalse(ServerTypeNormalizer.isValidConnectorId("2mysql"));
        assertFalse(ServerTypeNormalizer.isValidConnectorId("my-connector"));
        assertFalse(ServerTypeNormalizer.isValidConnectorId("MyConnector"));
        assertFalse(ServerTypeNormalizer.isValidConnectorId(""));
    }

    @Test
    void hasDisallowedCharacters_shouldRejectInvalidConnectorIds() {
        assertTrue(ServerTypeNormalizer.hasDisallowedCharacters("aws@glue"));
        assertTrue(ServerTypeNormalizer.hasDisallowedCharacters("foo.bar"));
        assertTrue(ServerTypeNormalizer.hasDisallowedCharacters("my-connector"));
        assertTrue(ServerTypeNormalizer.hasDisallowedCharacters("Monet DB"));
        assertFalse(ServerTypeNormalizer.hasDisallowedCharacters("myconnector"));
    }

    @Test
    void suggestAlternate_withoutBlockedSet_shouldDefaultToV2() {
        assertEquals("mysqlv2", ServerTypeNormalizer.suggestAlternate("mysql"));
        assertEquals("myconnectorv2", ServerTypeNormalizer.suggestAlternate("myconnector"));
    }

    @Test
    void suggestAlternate_shouldIncrementVersionWhenVariantsExist() {
        Set<String> blocked = Set.of("mysql", "mysqlv2");
        assertEquals("mysqlv3", ServerTypeNormalizer.suggestAlternate("mysql", blocked));
        assertEquals("mysqlv3", ServerTypeNormalizer.suggestAlternate("mysqlv2", blocked));
    }

    @Test
    void suggestAlternate_shouldSkipTakenVersionVariants() {
        Set<String> blocked = Set.of("monetdb");
        assertEquals("monetdbv2", ServerTypeNormalizer.suggestAlternate("monetdb", blocked));
        Set<String> withV2 = Set.of("monetdb", "monetdbv2");
        assertEquals("monetdbv3", ServerTypeNormalizer.suggestAlternate("monetdb", withV2));
    }

    @Test
    void stripTrailingVersionSuffix_shouldStripOnlyWhenBlockedContext() {
        Set<String> blocked = Set.of("mysql", "mysqlv2");
        assertEquals("mysql", ServerTypeNormalizer.stripTrailingVersionSuffix("mysqlv2", blocked));
        assertEquals("mysql", ServerTypeNormalizer.stripTrailingVersionSuffix("mysql", blocked));

        Set<String> empty = Set.of();
        assertEquals("archivev2", ServerTypeNormalizer.stripTrailingVersionSuffix("archivev2", empty));

        Set<String> archiveBlocked = Set.of("archivev2");
        assertEquals("archive", ServerTypeNormalizer.stripTrailingVersionSuffix("archivev2", archiveBlocked));
    }
}
