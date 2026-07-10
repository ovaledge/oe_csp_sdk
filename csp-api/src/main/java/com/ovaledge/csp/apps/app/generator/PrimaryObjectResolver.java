package com.ovaledge.csp.apps.app.generator;

import com.ovaledge.csp.v3.core.apps.model.ObjectKind;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Resolves {@code connectorMaster.primaryObject} from selected {@link ObjectKind}s.
 *
 * <p>Values are platform primary-object codes ({@code TVC}, {@code R}, {@code DS}, {@code FF})
 * used during connector registration and crawl/service flows.
 */
public final class PrimaryObjectResolver {

    public static final String TVC = "TVC";
    public static final String REPORTS = "R";
    public static final String DATASETS = "DS";
    public static final String FILE_FOLDERS = "FF";

    private static final List<String> VALID_CODE_LIST = List.of(TVC, REPORTS, DATASETS, FILE_FOLDERS);
    private static final Set<String> VALID_CODES = Collections.unmodifiableSet(new LinkedHashSet<>(VALID_CODE_LIST));

    private PrimaryObjectResolver() {
    }

    public static String resolve(List<ObjectKind> kinds, String override) {
        if (override != null && !override.trim().isEmpty()) {
            String normalized = override.trim().toUpperCase(Locale.ROOT);
            if (!VALID_CODES.contains(normalized)) {
                throw new IllegalArgumentException(
                        "Invalid primaryObject override '" + override + "'. Valid values: " + VALID_CODE_LIST);
            }
            return normalized;
        }
        if (kinds == null || kinds.isEmpty()) {
            return TVC;
        }
        boolean hasTableLike = kinds.contains(ObjectKind.ENTITY) || kinds.contains(ObjectKind.VIEW);
        if (hasTableLike) {
            return TVC;
        }
        if (kinds.contains(ObjectKind.REPORT)) {
            return REPORTS;
        }
        if (kinds.contains(ObjectKind.DATASET)) {
            return DATASETS;
        }
        if (kinds.contains(ObjectKind.FILE) || kinds.contains(ObjectKind.FILEFOLDERS)) {
            return FILE_FOLDERS;
        }
        boolean hasFunctionLike = kinds.contains(ObjectKind.FUNCTION) || kinds.contains(ObjectKind.PROCEDURE);
        if (hasFunctionLike) {
            throw new IllegalArgumentException(
                    "Cannot resolve primaryObject for FUNCTION/PROCEDURE-only connectors. "
                            + "Add at least one of ENTITY, VIEW, REPORT, DATASET, FILE, or FILEFOLDERS.");
        }
        return TVC;
    }
}
