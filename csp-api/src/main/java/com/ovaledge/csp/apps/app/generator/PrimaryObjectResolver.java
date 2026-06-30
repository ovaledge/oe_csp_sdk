package com.ovaledge.csp.apps.app.generator;

import com.ovaledge.csp.v3.core.apps.model.ObjectKind;
import java.util.List;
import java.util.Locale;

/**
 * Resolves {@code connectorMaster.primaryObject} from selected {@link ObjectKind}s.
 *
 * <p>Values are platform primary-object codes ({@code TVC}, {@code R}, {@code DS}, {@code FF})
 * used during connector registration and crawl/service flows.
 */
final class PrimaryObjectResolver {

    static final String TVC = "TVC";
    static final String REPORTS = "R";
    static final String DATASETS = "DS";
    static final String FILE_FOLDERS = "FF";

    private PrimaryObjectResolver() {
    }

    static String resolve(List<ObjectKind> kinds, String override) {
        if (override != null && !override.trim().isEmpty()) {
            return override.trim().toUpperCase(Locale.ROOT);
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
        return TVC;
    }
}
