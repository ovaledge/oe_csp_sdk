
const {
    API_BASE,
    CONNECTOR_LIST_INITIAL_COUNT,
    CRAWLER_OPTION_TYPE_ORDER,
    CRAWLER_OPTION_TYPES_HIDDEN_FOR_SDK,
    CRAWLER_OPTION_CODES_HIDDEN_FOR_SDK,
    CRAWLER_OPTION_CATALOG,
    CRAWLER_OPTION_TOOLTIP_DETAILS,
    CONNECTOR_OBJECT_CATEGORY_ORDER,
    CONNECTOR_OBJECT_CATEGORY_TOOLTIPS
} = window.CONNECTOR_UI_CONSTANTS;
let selectedConnector = null;
let connectorAttributes = null;
let connectionConfig = {};
let currentConnectionInfoId = null;
let lastRequestParams = null;
let lastResponseData = null;
let allConnectorsList = [];
let connectorListShowAll = false;
let overwriteConfirmResolver = null;
/** Object kinds for Create Connector modal; loaded from GET /v1/generator/object-kinds (ObjectKind enum). */
let generatorObjectKinds = null;

/** Attribute keys hidden by default (matched case-insensitively in form). Canonical keys used when injecting defaults. */
var HIDDEN_ATTR_KEYS_LC = ['credentialmanager', 'description', 'connectoradminrole', 'securityroles', 'bridgeid'];
var CANONICAL_HIDDEN_ATTR_KEYS = ['credentialManager', 'description', 'connectoradminrole', 'securityroles', 'bridgeId'];

function getDefaultForHiddenAttr(key) {
    var keyLower = (key || '').toLowerCase();
    if (keyLower === 'credentialmanager') return '1';
    if (keyLower === 'description') return selectedConnector || '';
    if (keyLower === 'connectoradminrole') return 'ADM';
    if (keyLower === 'securityroles') return 'ADM';
    if (keyLower === 'bridgeid') return '';
    return '';
}

function isHiddenByDefaultAttr(key) {
    return key && HIDDEN_ATTR_KEYS_LC.indexOf(key.toLowerCase()) !== -1;
}

function generateRandomId() {
    return Math.floor(1000 + Math.random() * 9000);
}

function updateResultButtons() {
    const copyCurlBtn = document.getElementById('copyCurlBtn');
    const copyResponseBtn = document.getElementById('copyResponseBtn');
    const hasResponse = !!lastResponseData;
    const hasRequest = !!lastRequestParams;
    if (copyCurlBtn) copyCurlBtn.disabled = !hasRequest;
    if (copyResponseBtn) copyResponseBtn.disabled = !hasResponse;
}

function openConnectorGeneratorModal() {
    const modal = document.getElementById('connectorGeneratorModal');
    if (!modal) return;
    clearConnectorGeneratorError();
    modal.classList.remove('hidden');
    prefillRepoRootIfEmpty();
    const list = document.getElementById('connectorObjectsList');
    const placeholder = document.getElementById('connectorObjectsLabel');
    if (list) list.innerHTML = '';
    if (generatorObjectKinds && generatorObjectKinds.length > 0) {
        populateConnectorObjectKinds();
        return;
    }
    if (placeholder) {
        placeholder.textContent = 'Loading...';
        placeholder.classList.remove('hidden');
    }
    fetchObjectKinds()
        .then(kinds => {
            generatorObjectKinds = kinds;
            if (placeholder) placeholder.textContent = 'Select connector objects';
            populateConnectorObjectKinds();
            renderConnectorObjectPills();
            updateConnectorObjectsDropdownLabel();
        })
        .catch(err => {
            if (placeholder) {
                placeholder.textContent = 'Select connector objects';
                placeholder.classList.remove('hidden');
            }
            showConnectorGeneratorError('Failed to load connector object types: ' + (err.message || 'Unknown error'));
        });
    ensureDefaultManifestInputs();
    renderCrawlerOptionsCatalog();
    updateConnectorGeneratorActionButtons();
}

async function prefillRepoRootIfEmpty() {
    const repoRootInput = document.getElementById('repoRootInput');
    if (!repoRootInput) return;
    if (repoRootInput.value && repoRootInput.value.trim()) return;
    try {
        const response = await fetch(`${API_BASE}/generator/default-repo-root`);
        if (!response.ok) return;
        const payload = await response.json();
        if (payload && payload.repoRoot) {
            repoRootInput.value = payload.repoRoot;
        }
    } catch (e) {
        // ignore
    }
}

async function fetchObjectKinds() {
    const response = await fetch(`${API_BASE}/generator/object-kinds`);
    if (!response.ok) {
        throw new Error(response.statusText || 'HTTP ' + response.status);
    }
    const data = await response.json();
    return Array.isArray(data) ? data : [];
}

function closeConnectorGeneratorModal() {
    const modal = document.getElementById('connectorGeneratorModal');
    if (!modal) return;
    modal.classList.add('hidden');
    closeConnectorObjectsDropdown();
    resetConnectorGeneratorForm();
}

function resetConnectorGeneratorForm() {
    const nameInput = document.getElementById('connectorNameInput');
    const objectsList = document.getElementById('connectorObjectsList');
    const iconInput = document.getElementById('connectorIconInput');
    const dropdownLabel = document.getElementById('connectorObjectsLabel');
    if (nameInput) nameInput.value = '';
    if (objectsList) {
        const searchInput = objectsList.querySelector('.dropdown-search');
        if (searchInput) searchInput.value = '';
        const inputs = objectsList.querySelectorAll('input[type="checkbox"]');
        inputs.forEach(input => {
            input.checked = false;
        });
    }
    if (iconInput) iconInput.value = '';
    if (dropdownLabel) dropdownLabel.textContent = 'Select connector objects';
    clearConnectorGeneratorError();
    renderConnectorObjectPills();
    const refsContainer = document.getElementById('referencesContainer');
    if (refsContainer) refsContainer.innerHTML = '';
    ensureDefaultManifestInputs();
    renderCrawlerOptionsCatalog();
    updateConnectorGeneratorActionButtons();
}

function ensureDefaultManifestInputs() {
    const defaults = [
        ['manifestProtocolInput', 'REST']
    ];
    defaults.forEach(([id, val]) => {
        const el = document.getElementById(id);
        if (el && !el.value) el.value = val;
    });
    const protocolValue = document.getElementById('manifestProtocolInput')?.value || 'REST';
    setManifestProtocol(protocolValue);
}

function getReferenceSummary(card) {
    const type = (card.querySelector('.ref-type')?.value || 'Reference').trim();
    const title = (card.querySelector('.ref-title')?.value || '').trim();
    const url = (card.querySelector('.ref-url')?.value || '').trim();
    const secondary = title || url || 'Untitled reference';
    return { type, secondary };
}

function updateReferenceCardSummary(card) {
    if (!card) return;
    const summary = getReferenceSummary(card);
    const typeEl = card.querySelector('.ref-summary-type');
    const valueEl = card.querySelector('.ref-summary-value');
    if (typeEl) typeEl.textContent = summary.type;
    if (valueEl) valueEl.textContent = summary.secondary;
    updateReferenceCardPreview(card);
}

function updateReferenceCardPreview(card) {
    if (!card) return;
    const type = (card.querySelector('.ref-type')?.value || 'Reference').trim();
    const title = (card.querySelector('.ref-title')?.value || '').trim();
    const url = (card.querySelector('.ref-url')?.value || '').trim();
    const notes = (card.querySelector('.ref-text')?.value || '').trim();
    const previewEl = card.querySelector('.ref-preview-popover');
    if (!previewEl) return;
    previewEl.textContent =
        'Type: ' + type + '\n'
        + 'Title: ' + (title || '-') + '\n'
        + 'URL: ' + (url || '-') + '\n'
        + 'Notes: ' + (notes || '-');
}

function setActiveReferenceCard(activeCard) {
    const container = document.getElementById('referencesContainer');
    if (!container) return;
    container.querySelectorAll('.reference-card').forEach(card => {
        const expanded = card === activeCard;
        card.classList.toggle('expanded', expanded);
        const headerBtn = card.querySelector('.reference-card-header');
        if (headerBtn) headerBtn.setAttribute('aria-expanded', expanded ? 'true' : 'false');
        const body = card.querySelector('.reference-card-body');
        if (body) body.classList.toggle('hidden', !expanded);
    });
}

function removeReferenceCard(btn) {
    const card = btn.closest('.reference-card');
    if (!card) return;
    card.remove();
}

function addReferenceRowFromQuick() {
    const quickType = document.getElementById('quickRefType');
    const quickUrl = document.getElementById('quickRefUrl');
    const quickTitle = document.getElementById('quickRefTitle');
    const quickNotes = document.getElementById('quickRefNotes');
    const type = (quickType?.value || 'Official Docs').trim();
    const url = (quickUrl?.value || '').trim();
    const title = (quickTitle?.value || '').trim();
    const text = (quickNotes?.value || '').trim();
    if (!url) {
        showConnectorGeneratorError('Please enter a URL before adding a reference.');
        return;
    }
    clearConnectorGeneratorError();
    addReferenceRow({ type, url, title, text });
    if (quickUrl) quickUrl.value = '';
    if (quickTitle) quickTitle.value = '';
    if (quickNotes) quickNotes.value = '';
}

function addReferenceRow(initial = {}) {
    const container = document.getElementById('referencesContainer');
    if (!container) return;
    const cardId = 'reference-card-' + Date.now() + '-' + Math.floor(Math.random() * 1000);
    const card = document.createElement('div');
    card.className = 'reference-card';
    card.innerHTML = `
        <div class="reference-card-header" role="button" tabindex="0" aria-expanded="false" aria-controls="${cardId}">
            <span class="ref-summary-type"></span>
            <span class="ref-summary-value"></span>
            <span class="ref-summary-edit-icon" aria-hidden="true" title="Expand to edit">
                <svg viewBox="0 0 24 24" focusable="false">
                    <path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                        d="M12 20h9M16.5 3.5a2.12 2.12 0 013 3L8 18l-4 1 1-4 11.5-11.5z"/>
                </svg>
            </span>
            <button type="button" class="ref-preview-btn ref-preview-trigger" aria-label="Preview reference" title="Preview">
                <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                    <path d="M5 3h9l5 5v12a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zm8 1.5V9h4.5L13 4.5zM12 11c4.1 0 6.5 3.6 6.7 3.9a1 1 0 0 1 0 1.1c-.2.3-2.6 3.9-6.7 3.9S5.5 16.3 5.3 16a1 1 0 0 1 0-1.1C5.5 14.6 7.9 11 12 11zm0 1.8c-2.4 0-4 1.7-4.7 2.6.7.9 2.3 2.6 4.7 2.6s4-1.7 4.7-2.6c-.7-.9-2.3-2.6-4.7-2.6zm0 1a1.6 1.6 0 1 1 0 3.2 1.6 1.6 0 0 1 0-3.2z"/>
                </svg>
            </button>
            <button type="button" class="ref-remove-btn" aria-label="Remove this reference" title="Remove">
                <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                    <path fill="none" stroke="currentColor" stroke-width="2.25" stroke-linecap="round" d="M7 7l10 10M17 7L7 17"/>
                </svg>
            </button>
            <div class="ref-preview-popover" role="tooltip"></div>
        </div>
        <div class="reference-card-body hidden" id="${cardId}">
            <div class="reference-grid-row">
                <div class="form-group">
                    <label>Type</label>
                    <select class="ref-type">
                        <option>Official Docs</option>
                        <option>API Reference</option>
                        <option>GitHub</option>
                        <option>Community</option>
                        <option>Other</option>
                    </select>
                </div>
                <div class="form-group">
                    <label>Title</label>
                    <input type="text" class="ref-title" placeholder="Optional title">
                </div>
            </div>
            <div class="form-group">
                <label>URL</label>
                <input type="text" class="ref-url" placeholder="https://...">
            </div>
            <div class="form-group ref-notes-wrap">
                <label>Notes / Text</label>
                <textarea class="ref-text" rows="2" placeholder="Paste relevant research notes"></textarea>
            </div>
        </div>
    `;
    container.appendChild(card);

    const typeInput = card.querySelector('.ref-type');
    const titleInput = card.querySelector('.ref-title');
    const urlInput = card.querySelector('.ref-url');
    const textInput = card.querySelector('.ref-text');
    if (typeInput && initial.type) typeInput.value = initial.type;
    if (titleInput && initial.title) titleInput.value = initial.title;
    if (urlInput && initial.url) urlInput.value = initial.url;
    if (textInput && initial.text) {
        textInput.value = initial.text;
    }

    const header = card.querySelector('.reference-card-header');
    card.querySelector('.ref-preview-btn')?.addEventListener('click', (e) => e.stopPropagation());
    card.querySelector('.ref-remove-btn')?.addEventListener('click', (e) => {
        e.stopPropagation();
        e.preventDefault();
        removeReferenceCard(e.currentTarget);
    });
    header?.addEventListener('click', () => {
        const isExpanded = card.classList.contains('expanded');
        setActiveReferenceCard(isExpanded ? null : card);
    });
    header?.addEventListener('keydown', (event) => {
        if (event.key !== 'Enter' && event.key !== ' ') return;
        event.preventDefault();
        const isExpanded = card.classList.contains('expanded');
        setActiveReferenceCard(isExpanded ? null : card);
    });
    [typeInput, titleInput, urlInput].forEach(el => {
        if (!el) return;
        el.addEventListener('input', () => updateReferenceCardSummary(card));
        el.addEventListener('change', () => updateReferenceCardSummary(card));
    });
    updateReferenceCardSummary(card);
    setActiveReferenceCard(null);
}

function toggleProtocolOtherInput() {
    const select = document.getElementById('manifestProtocolInput');
    const other = document.getElementById('manifestProtocolOtherInput');
    const otherWrapper = document.getElementById('manifestProtocolOtherWrapper');
    if (!select || !other || !otherWrapper) return;
    if (select.value === 'Other') {
        otherWrapper.classList.remove('hidden');
    } else {
        otherWrapper.classList.add('hidden');
        other.value = '';
    }
}

function setManifestProtocol(value) {
    const input = document.getElementById('manifestProtocolInput');
    if (!input) return;
    input.value = value;
    document.querySelectorAll('.protocol-toggle-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.value === value);
    });
    toggleProtocolOtherInput();
    updateConnectorGeneratorActionButtons();
}

function renderCrawlerOptionsCatalog() {
    const container = document.getElementById('crawlerOptionsCatalog');
    if (!container) return;
    container.innerHTML = '';

    const groupedByType = new Map();
    CRAWLER_OPTION_CATALOG.forEach(item => {
        const key = item.optionType;
        if (!groupedByType.has(key)) groupedByType.set(key, []);
        groupedByType.get(key).push(item);
    });

    CRAWLER_OPTION_TYPE_ORDER.forEach(type => {
        if (CRAWLER_OPTION_TYPES_HIDDEN_FOR_SDK_SET.has(type)) {
            return;
        }
        const items = groupedByType.get(type);
        if (!items || items.length === 0) return;

        const section = document.createElement('div');
        section.className = 'crawler-type-section';

        const title = document.createElement('div');
        title.className = 'crawler-type-title';
        title.textContent = type.replace(/_/g, ' ');
        section.appendChild(title);

        const grid = document.createElement('div');
        grid.className = 'crawler-option-grid';

        items.forEach(item => {
            if (item.optionType === 'CRAWLER_OPTIONS' && CRAWLER_OPTION_CODES_HIDDEN_FOR_SDK_SET.has(item.code)) {
                return;
            }
            const label = document.createElement('label');
            label.className = 'capability-item crawler-option-item' + ((item.required || item.disabled) ? ' capability-item-readonly' : '');
            const tip = buildCrawlerOptionTooltip(item);
            label.dataset.tooltip = tip;
            const input = document.createElement('input');
            input.type = 'checkbox';
            input.className = 'crawler-option-checkbox';
            input.dataset.optionType = item.optionType;
            input.dataset.optionKey = item.code;
            input.checked = Boolean(item.defaultChecked || item.required);
            input.disabled = Boolean(item.required || item.disabled);

            const textWrap = document.createElement('span');
            textWrap.className = 'crawler-option-text';
            const name = document.createElement('span');
            name.className = 'crawler-option-name';
            name.textContent = item.code + ' - ' + item.name;
            textWrap.appendChild(name);

            label.appendChild(input);
            label.appendChild(textWrap);
            grid.appendChild(label);
        });
        section.appendChild(grid);
        container.appendChild(section);
    });
}

function buildCrawlerOptionTooltip(item) {
    const detail = CRAWLER_OPTION_TOOLTIP_DETAILS[item.code];
    const base = detail || item.description;
    const unsupported = item.disabled ? ' This option is currently not supported for SDK connectors.' : '';
    return base + unsupported + ' [' + item.optionType + ' | ' + item.category + ']';
}

function buildCrawlerOptionsFromForm() {
    const options = [];
    const dedupe = new Set();
    document.querySelectorAll('.crawler-option-checkbox:checked').forEach(input => {
        const optionType = (input.dataset.optionType || '').trim();
        const optionKey = (input.dataset.optionKey || '').trim();
        if (!optionType || !optionKey) return;
        const token = optionType + ':' + optionKey;
        if (dedupe.has(token)) return;
        dedupe.add(token);
        options.push({ optionType, optionKey });
    });
    // Keep mandatory crawler preferences always present.
    if (!dedupe.has('CRAWLER_PREFERENCE:S')) options.push({ optionType: 'CRAWLER_PREFERENCE', optionKey: 'S' });
    if (!dedupe.has('CRAWLER_PREFERENCE:C')) options.push({ optionType: 'CRAWLER_PREFERENCE', optionKey: 'C' });
    return options;
}

const CRAWLER_OPTION_TYPES_HIDDEN_FOR_SDK_SET = new Set(CRAWLER_OPTION_TYPES_HIDDEN_FOR_SDK);
const CRAWLER_OPTION_CODES_HIDDEN_FOR_SDK_SET = new Set(CRAWLER_OPTION_CODES_HIDDEN_FOR_SDK);

let activeConnectorObjectsCategory = null;

function populateConnectorObjectKinds() {
    const list = document.getElementById('connectorObjectsList');
    if (!list) return;
    list.innerHTML = '';

    const searchWrap = document.createElement('div');
    searchWrap.className = 'dropdown-search-wrap';

    const headerRow = document.createElement('div');
    headerRow.className = 'dropdown-header-row';

    const searchInput = document.createElement('input');
    searchInput.type = 'text';
    searchInput.className = 'dropdown-search';
    searchInput.placeholder = 'Search...';
    searchInput.setAttribute('aria-label', 'Search connector objects');
    searchInput.addEventListener('input', filterConnectorObjectsSearch);
    searchInput.addEventListener('click', (e) => e.stopPropagation());

    const actions = document.createElement('div');
    actions.className = 'dropdown-actions';
    const selectedCount = document.createElement('span');
    selectedCount.className = 'dropdown-selected-count';
    selectedCount.id = 'connectorObjectsSelectedCount';
    selectedCount.textContent = '0 selected';
    const clearBtn = document.createElement('button');
    clearBtn.type = 'button';
    clearBtn.className = 'dropdown-action-btn';
    clearBtn.textContent = 'Clear';
    clearBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        setConnectorObjectsChecked(false, { onlyVisible: false });
    });
    const selectAllBtn = document.createElement('button');
    selectAllBtn.type = 'button';
    selectAllBtn.className = 'dropdown-action-btn primary';
    selectAllBtn.textContent = 'Select all';
    selectAllBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        setConnectorObjectsChecked(true, { onlyVisible: true });
    });
    actions.appendChild(selectedCount);
    actions.appendChild(clearBtn);
    actions.appendChild(selectAllBtn);
    const closeBtn = document.createElement('button');
    closeBtn.type = 'button';
    closeBtn.className = 'dropdown-action-btn close';
    closeBtn.setAttribute('aria-label', 'Close connector objects selector');
    closeBtn.textContent = '✕';
    closeBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        closeConnectorObjectsDropdown();
    });
    actions.appendChild(closeBtn);

    headerRow.appendChild(searchInput);
    headerRow.appendChild(actions);
    searchWrap.appendChild(headerRow);

    const tabs = document.createElement('div');
    tabs.className = 'dropdown-tabs';
    tabs.id = 'connectorObjectsTabs';
    searchWrap.appendChild(tabs);

    list.appendChild(searchWrap);

    const optionsContainer = document.createElement('div');
    optionsContainer.className = 'dropdown-options';
    optionsContainer.id = 'connectorObjectsOptions';

    const kinds = generatorObjectKinds || [];
    const byCategory = {};
    kinds.forEach(kind => {
        const category = (kind.category || 'Other').trim() || 'Other';
        if (!byCategory[category]) byCategory[category] = [];
        byCategory[category].push(kind);
    });

    const allCategories = [
        ...CONNECTOR_OBJECT_CATEGORY_ORDER.filter(c => byCategory[c] && byCategory[c].length > 0),
        ...Object.keys(byCategory).filter(c => !CONNECTOR_OBJECT_CATEGORY_ORDER.includes(c))
    ];
    if (!activeConnectorObjectsCategory || !byCategory[activeConnectorObjectsCategory]) {
        activeConnectorObjectsCategory = allCategories[0] || null;
    }
    renderConnectorObjectsTabs(allCategories, byCategory);

    CONNECTOR_OBJECT_CATEGORY_ORDER.forEach(category => {
        const items = byCategory[category];
        if (!items || items.length === 0) return;
        const group = document.createElement('div');
        group.className = 'dropdown-category';
        group.dataset.category = category;
        const header = document.createElement('div');
        header.className = 'dropdown-category-header';
        header.textContent = category;
        header.setAttribute('aria-hidden', 'true');
        const categoryTooltip = CONNECTOR_OBJECT_CATEGORY_TOOLTIPS[category] || CONNECTOR_OBJECT_CATEGORY_TOOLTIPS['Storage'];
        if (categoryTooltip) header.title = categoryTooltip;
        group.appendChild(header);
        items.forEach(kind => {
            const value = kind.value || kind.displayName || '';
            const displayName = kind.displayName != null ? kind.displayName : value;
            const label = document.createElement('label');
            label.className = 'dropdown-option';
            label.dataset.searchText = (displayName + ' ' + value + ' ' + (category || '')).toLowerCase();
            if (kind.tooltip) label.title = kind.tooltip;
            const leftPart = document.createElement('span');
            leftPart.className = 'dropdown-option-left';
            const input = document.createElement('input');
            input.type = 'checkbox';
            input.value = value;
            input.addEventListener('change', () => {
                renderConnectorObjectPills();
                updateConnectorObjectsDropdownLabel();
            });
            leftPart.appendChild(input);
            const space = document.createElement('span');
            space.className = 'dropdown-option-space';
            space.setAttribute('aria-hidden', 'true');
            space.textContent = '\u00A0';
            leftPart.appendChild(space);
            label.appendChild(leftPart);
            const text = document.createElement('span');
            text.className = 'dropdown-option-label';
            text.textContent = displayName;
            label.appendChild(text);
            group.appendChild(label);
        });
        optionsContainer.appendChild(group);
    });
    Object.keys(byCategory).filter(c => !CONNECTOR_OBJECT_CATEGORY_ORDER.includes(c)).forEach(category => {
        const items = byCategory[category];
        const group = document.createElement('div');
        group.className = 'dropdown-category';
        group.dataset.category = category;
        const header = document.createElement('div');
        header.className = 'dropdown-category-header';
        header.textContent = category;
        header.setAttribute('aria-hidden', 'true');
        const categoryTooltipOther = CONNECTOR_OBJECT_CATEGORY_TOOLTIPS[category];
        if (categoryTooltipOther) header.title = categoryTooltipOther;
        group.appendChild(header);
        items.forEach(kind => {
            const value = kind.value || kind.displayName || '';
            const displayName = kind.displayName != null ? kind.displayName : value;
            const label = document.createElement('label');
            label.className = 'dropdown-option';
            label.dataset.searchText = (displayName + ' ' + value + ' ' + (category || '')).toLowerCase();
            if (kind.tooltip) label.title = kind.tooltip;
            const leftPart = document.createElement('span');
            leftPart.className = 'dropdown-option-left';
            const input = document.createElement('input');
            input.type = 'checkbox';
            input.value = value;
            input.addEventListener('change', () => {
                renderConnectorObjectPills();
                updateConnectorObjectsDropdownLabel();
            });
            leftPart.appendChild(input);
            const space = document.createElement('span');
            space.className = 'dropdown-option-space';
            space.setAttribute('aria-hidden', 'true');
            space.textContent = '\u00A0';
            leftPart.appendChild(space);
            label.appendChild(leftPart);
            const text = document.createElement('span');
            text.className = 'dropdown-option-label';
            text.textContent = displayName;
            label.appendChild(text);
            group.appendChild(label);
        });
        optionsContainer.appendChild(group);
    });
    list.appendChild(optionsContainer);
    updateConnectorObjectsSelectedCount();
    filterConnectorObjectsSearch();
}

function renderConnectorObjectsTabs(categories, byCategory) {
    const tabs = document.getElementById('connectorObjectsTabs');
    if (!tabs) return;
    tabs.innerHTML = '';
    categories.forEach(category => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'dropdown-tab';
        if (category === activeConnectorObjectsCategory) btn.classList.add('active');
        const tooltip = CONNECTOR_OBJECT_CATEGORY_TOOLTIPS[category];
        if (tooltip) btn.title = tooltip;
        const count = (byCategory[category] || []).length;
        btn.innerHTML = `<span class="dropdown-tab-label">${escapeHtml(category)}</span><span class="dropdown-tab-count">${count}</span>`;
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            activeConnectorObjectsCategory = category;
            renderConnectorObjectsTabs(categories, byCategory);
            filterConnectorObjectsSearch();
        });
        tabs.appendChild(btn);
    });
}

function filterConnectorObjectsSearch() {
    const list = document.getElementById('connectorObjectsList');
    const searchInput = list ? list.querySelector('.dropdown-search') : null;
    const optionsContainer = document.getElementById('connectorObjectsOptions');
    if (!searchInput || !optionsContainer) return;
    const q = (searchInput.value || '').trim().toLowerCase();
    const options = optionsContainer.querySelectorAll('.dropdown-option');
    const categories = optionsContainer.querySelectorAll('.dropdown-category');
    let visibleCount = 0;
    options.forEach(option => {
        const searchText = option.dataset.searchText || '';
        const show = q === '' || searchText.indexOf(q) !== -1;
        option.style.display = show ? '' : 'none';
        if (show) visibleCount++;
    });
    categories.forEach(group => {
        const categoryName = group.dataset.category || '';
        const groupOptions = group.querySelectorAll('.dropdown-option');
        const anyVisible = Array.from(groupOptions).some(o => o.style.display !== 'none');
        if (q) {
            group.style.display = anyVisible ? '' : 'none';
        } else {
            group.style.display = (anyVisible && (!activeConnectorObjectsCategory || categoryName === activeConnectorObjectsCategory)) ? '' : 'none';
        }
    });
    let noResults = optionsContainer.querySelector('.dropdown-no-results');
    if (q && visibleCount === 0) {
        if (!noResults) {
            noResults = document.createElement('div');
            noResults.className = 'dropdown-no-results';
            noResults.textContent = 'No matching options';
            optionsContainer.appendChild(noResults);
        }
        noResults.style.display = '';
    } else if (noResults) {
        noResults.style.display = 'none';
    }
}

function setConnectorObjectsChecked(checked, { onlyVisible }) {
    const list = document.getElementById('connectorObjectsList');
    if (!list) return;
    const optionsContainer = document.getElementById('connectorObjectsOptions');
    if (!optionsContainer) return;
    const inputs = optionsContainer.querySelectorAll('input[type="checkbox"]');
    inputs.forEach(input => {
        const option = input.closest('.dropdown-option');
        const visible = option ? option.style.display !== 'none' : true;
        if (!onlyVisible || visible) {
            input.checked = checked;
        }
    });
    renderConnectorObjectPills();
    updateConnectorObjectsDropdownLabel();
    updateConnectorObjectsSelectedCount();
    updateConnectorGeneratorActionButtons();
}

function updateConnectorObjectsSelectedCount() {
    const el = document.getElementById('connectorObjectsSelectedCount');
    if (!el) return;
    const selected = getSelectedConnectorObjects();
    el.textContent = `${selected.length} selected`;
}

function escapeHtml(str) {
    return String(str || '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function getSelectedConnectorObjects() {
    const list = document.getElementById('connectorObjectsList');
    if (!list) return [];
    const inputs = list.querySelectorAll('input[type="checkbox"]');
    return Array.from(inputs).filter(input => input.checked).map(input => input.value);
}

function renderConnectorObjectPills() {
    const pillList = document.getElementById('connectorObjectsPills');
    const placeholder = document.getElementById('connectorObjectsLabel');
    if (!pillList) return;
    const selected = getSelectedConnectorObjects();
    pillList.innerHTML = '';
    if (selected.length === 0) {
        pillList.classList.add('hidden');
        if (placeholder) {
            placeholder.textContent = 'Select connector objects';
            placeholder.classList.remove('hidden');
        }
        return;
    }
    if (placeholder) placeholder.classList.add('hidden');
    const kinds = generatorObjectKinds || [];
    selected.forEach(value => {
        const pill = document.createElement('span');
        pill.className = 'pill';
        const item = kinds.find(k => (k.value || k.displayName) === value);
        pill.textContent = item && item.displayName != null ? item.displayName : value;
        pillList.appendChild(pill);
    });
    pillList.classList.remove('hidden');
    updateConnectorObjectsSelectedCount();
}

let connectorObjectsMenuPositionListener = null;

function positionConnectorObjectsMenu() {
    const trigger = document.querySelector('#connectorObjectsDropdown .dropdown-toggle');
    const menu = document.getElementById('connectorObjectsList');
    if (!trigger || !menu || menu.classList.contains('hidden')) return;
    const rect = trigger.getBoundingClientRect();
    const gap = 8;
    const viewportPadding = 12;
    const preferredMaxHeight = Math.min(380, Math.floor(0.55 * window.innerHeight));

    const minMenuWidth = 480;
    const menuWidth = Math.max(rect.width, minMenuWidth);
    menu.style.position = 'fixed';
    menu.style.width = Math.min(menuWidth, window.innerWidth - 2 * viewportPadding) + 'px';
    menu.style.minWidth = Math.min(minMenuWidth, window.innerWidth - 2 * viewportPadding) + 'px';
    menu.style.left = rect.left + 'px';
    menu.style.right = 'auto';
    menu.style.zIndex = '1100';

    const spaceBelow = window.innerHeight - rect.bottom - gap - viewportPadding;
    const spaceAbove = rect.top - gap - viewportPadding;
    const openDown = spaceBelow >= preferredMaxHeight || spaceBelow >= spaceAbove;

    if (openDown) {
        menu.style.top = (rect.bottom + gap) + 'px';
        menu.style.bottom = 'auto';
        menu.style.maxHeight = Math.min(preferredMaxHeight, spaceBelow) + 'px';
    } else {
        menu.style.top = 'auto';
        menu.style.bottom = (window.innerHeight - rect.top + gap) + 'px';
        menu.style.maxHeight = Math.min(preferredMaxHeight, spaceAbove) + 'px';
    }

    let left = rect.left;
    const w = parseFloat(menu.style.width) || menuWidth;
    if (left + w > window.innerWidth - viewportPadding) {
        left = window.innerWidth - w - viewportPadding;
    }
    if (left < viewportPadding) {
        left = viewportPadding;
    }
    menu.style.left = left + 'px';
}

function toggleConnectorObjectsDropdown() {
    const menu = document.getElementById('connectorObjectsList');
    if (!menu) return;
    const isOpening = menu.classList.contains('hidden');
    menu.classList.toggle('hidden');
    if (isOpening) {
        positionConnectorObjectsMenu();
        if (connectorObjectsMenuPositionListener) {
            window.removeEventListener('scroll', connectorObjectsMenuPositionListener, true);
            window.removeEventListener('resize', connectorObjectsMenuPositionListener);
        }
        connectorObjectsMenuPositionListener = () => positionConnectorObjectsMenu();
        window.addEventListener('scroll', connectorObjectsMenuPositionListener, true);
        window.addEventListener('resize', connectorObjectsMenuPositionListener);
        const searchInput = menu.querySelector('.dropdown-search');
        if (searchInput) {
            searchInput.value = '';
            searchInput.focus();
            filterConnectorObjectsSearch();
        }
    } else {
        if (connectorObjectsMenuPositionListener) {
            window.removeEventListener('scroll', connectorObjectsMenuPositionListener, true);
            window.removeEventListener('resize', connectorObjectsMenuPositionListener);
            connectorObjectsMenuPositionListener = null;
        }
    }
}

function closeConnectorObjectsDropdown() {
    const menu = document.getElementById('connectorObjectsList');
    if (!menu) return;
    menu.classList.add('hidden');
    if (connectorObjectsMenuPositionListener) {
        window.removeEventListener('scroll', connectorObjectsMenuPositionListener, true);
        window.removeEventListener('resize', connectorObjectsMenuPositionListener);
        connectorObjectsMenuPositionListener = null;
    }
}

function updateConnectorObjectsDropdownLabel() {
    const placeholder = document.getElementById('connectorObjectsLabel');
    if (!placeholder) return;
    const selected = getSelectedConnectorObjects();
    if (selected.length === 0) {
        placeholder.textContent = 'Select connector objects';
        placeholder.classList.remove('hidden');
    } else {
        placeholder.classList.add('hidden');
    }
}

document.addEventListener('click', event => {
    const dropdown = document.getElementById('connectorObjectsDropdown');
    const menu = document.getElementById('connectorObjectsList');
    if (!dropdown || !menu) return;
    if (!dropdown.contains(event.target)) {
        menu.classList.add('hidden');
    }
});

document.addEventListener('keydown', event => {
    if (event.key !== 'Escape') return;
    const modal = document.getElementById('connectorGeneratorModal');
    if (modal && !modal.classList.contains('hidden')) {
        closeConnectorGeneratorModal();
    }
});

function showConnectorGeneratorError(message) {
    const errorEl = document.getElementById('connectorGeneratorError');
    if (errorEl) {
        // Keep inline container cleared; primary UX is toast.
        errorEl.textContent = '';
        errorEl.classList.add('hidden');
    }
    showConnectorToast(message, 'error');
}

function isConnectorGeneratorRequiredFieldsValid() {
    const connectorName = (document.getElementById('connectorNameInput')?.value || '').trim();
    const repoRoot = (document.getElementById('repoRootInput')?.value || '').trim();
    const selectedKinds = getSelectedConnectorObjects();
    const protocolSelected = (document.getElementById('manifestProtocolInput')?.value || '').trim();
    const protocolOther = (document.getElementById('manifestProtocolOtherInput')?.value || '').trim();
    const protocol = protocolSelected === 'Other' ? protocolOther : protocolSelected;
    const iconInput = document.getElementById('connectorIconInput');
    const hasIcon = Boolean(iconInput && iconInput.files && iconInput.files.length > 0);
    return Boolean(connectorName && repoRoot && selectedKinds.length > 0 && protocol && hasIcon);
}

function updateConnectorGeneratorActionButtons() {
    const modal = document.getElementById('connectorGeneratorModal');
    if (!modal || modal.classList.contains('hidden')) return;
    const canSubmit = isConnectorGeneratorRequiredFieldsValid();
    const publishBtn = document.getElementById('connectorGenerateBtn');
    const downloadBtn = document.getElementById('connectorDownloadBtn');
    if (publishBtn) publishBtn.disabled = !canSubmit;
    if (downloadBtn) downloadBtn.disabled = !canSubmit;
}

function showOverwriteConfirmDialog(message) {
    const modal = document.getElementById('overwriteConfirmModal');
    const messageEl = document.getElementById('overwriteConfirmMessage');
    if (!modal || !messageEl) {
        return Promise.resolve(false);
    }
    messageEl.textContent = message;
    modal.classList.remove('hidden');
    return new Promise(resolve => {
        overwriteConfirmResolver = resolve;
    });
}

function resolveOverwriteConfirm(confirmed) {
    const modal = document.getElementById('overwriteConfirmModal');
    if (modal) modal.classList.add('hidden');
    const resolver = overwriteConfirmResolver;
    overwriteConfirmResolver = null;
    if (resolver) resolver(Boolean(confirmed));
}

function clearConnectorGeneratorError() {
    const errorEl = document.getElementById('connectorGeneratorError');
    if (!errorEl) return;
    errorEl.textContent = '';
    errorEl.classList.add('hidden');
}

document.addEventListener('input', event => {
    const modal = document.getElementById('connectorGeneratorModal');
    if (!modal || modal.classList.contains('hidden')) return;
    if (modal.contains(event.target)) updateConnectorGeneratorActionButtons();
});

document.addEventListener('change', event => {
    const modal = document.getElementById('connectorGeneratorModal');
    if (!modal || modal.classList.contains('hidden')) return;
    if (modal.contains(event.target)) updateConnectorGeneratorActionButtons();
});

function showConnectorToast(message, type = 'error') {
    const container = document.getElementById('connectorToastContainer');
    if (!container || !message) return;
    const toast = document.createElement('div');
    toast.className = 'toast toast-' + type;
    toast.setAttribute('role', 'alert');
    toast.textContent = String(message);
    container.appendChild(toast);
    requestAnimationFrame(() => toast.classList.add('show'));

    const dismiss = () => {
        toast.classList.remove('show');
        setTimeout(() => toast.remove(), 180);
    };
    setTimeout(dismiss, 4200);
}

async function submitConnectorGenerator() {
    const submission = collectConnectorGeneratorSubmission({ requireRepoRoot: true });
    if (!submission) return;
    const { requestPayload, iconFile } = submission;

    const generateBtn = document.getElementById('connectorGenerateBtn');
    clearConnectorGeneratorError();
    showPageLoader('Generating connector...');
    if (generateBtn) generateBtn.disabled = true;

    try {
        const postGenerate = async (payload) => {
            const data = new FormData();
            data.append('request', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
            data.append('icon', iconFile);
            const response = await fetch(`${API_BASE}/generator/generate`, {
                method: 'POST',
                body: data
            });
            let errorMessage = '';
            if (!response.ok) {
                errorMessage = `Generation failed (${response.status})`;
                try {
                    const errPayload = await response.json();
                    if (errPayload && Array.isArray(errPayload.errors) && errPayload.errors.length > 0) {
                        errorMessage = errPayload.errors.join(' ');
                    } else if (errPayload && errPayload.message) {
                        errorMessage = errPayload.message;
                    }
                } catch (e) {
                    errorMessage = `Generation failed (${response.status})`;
                }
                return { ok: false, errorMessage };
            }
            const body = await response.json();
            return { ok: true, body };
        };

        let result = await postGenerate(requestPayload);
        if (!result.ok && result.errorMessage.includes('Target folder already exists:')) {
            hidePageLoader();
            const confirmed = await showOverwriteConfirmDialog(
                result.errorMessage + '\n\nDo you want to delete the existing module and regenerate it?'
            );
            if (confirmed) {
                showPageLoader('Generating connector...');
                requestPayload.overwriteExistingModule = true;
                result = await postGenerate(requestPayload);
            } else {
                showConnectorGeneratorError(
                    'Generation cancelled. Existing module was not modified. Confirm overwrite to replace it.'
                );
                return;
            }
        }

        if (!result.ok) {
            showConnectorGeneratorError(result.errorMessage || 'Connector generation failed.');
            return;
        }

        const payload = result.body;
        const resultContent = document.getElementById('resultContent');
        if (resultContent) {
            resultContent.textContent = JSON.stringify(payload, null, 2);
            resultContent.className = 'result-content';
            // For Create New Connector flow, allow response copy but keep cURL disabled.
            lastResponseData = payload;
            lastRequestParams = null;
            updateResultButtons();
        }
        closeConnectorGeneratorModal();
    } catch (error) {
        showConnectorGeneratorError(error.message || 'Connector generation failed.');
    } finally {
        if (generateBtn) generateBtn.disabled = false;
        hidePageLoader();
    }
}

function collectConnectorGeneratorSubmission(options = {}) {
    const requireRepoRoot = options.requireRepoRoot !== false;
    const nameInput = document.getElementById('connectorNameInput');
    const iconInput = document.getElementById('connectorIconInput');
    const repoRootInput = document.getElementById('repoRootInput');

    const connectorName = nameInput ? nameInput.value.trim() : '';
    const repoRoot = repoRootInput ? repoRootInput.value.trim() : '';
    const selectedKinds = getSelectedConnectorObjects();
    const protocolSelected = (document.getElementById('manifestProtocolInput')?.value || '').trim();
    const protocolOther = (document.getElementById('manifestProtocolOtherInput')?.value || '').trim();
    const manifestProtocol = protocolSelected === 'Other' ? protocolOther : protocolSelected;

    if (!connectorName) {
        showConnectorGeneratorError('Connector Name is required.');
        return;
    }
    if (requireRepoRoot && !repoRoot) {
        showConnectorGeneratorError('Repository Root (repoRoot) is required.');
        return;
    }
    if (selectedKinds.length === 0) {
        showConnectorGeneratorError('Please select at least one Connector Object.');
        return;
    }
    if (!manifestProtocol) {
        showConnectorGeneratorError('Protocol is required.');
        return;
    }

    const iconFile = iconInput && iconInput.files ? iconInput.files[0] : null;
    if (!iconFile) {
        showConnectorGeneratorError('Connector Icon is required.');
        return;
    }
    const allowed =
        iconFile.type === 'image/png' ||
        iconFile.type === 'image/jpeg' ||
        iconFile.type === 'image/svg+xml' ||
        (iconFile.name && /\.(png|jpe?g|svg)$/i.test(iconFile.name));
    if (!allowed) {
        showConnectorGeneratorError('Connector Icon must be PNG, JPG/JPEG, or SVG.');
        return;
    }
    if (iconFile.size > 200 * 1024) {
        showConnectorGeneratorError('Connector Icon size must be less than 200 KB.');
        return;
    }

    const references = Array.from(document.querySelectorAll('.reference-card')).map(card => ({
        type: (card.querySelector('.ref-type')?.value || '').trim(),
        title: (card.querySelector('.ref-title')?.value || '').trim(),
        url: (card.querySelector('.ref-url')?.value || '').trim(),
        text: (card.querySelector('.ref-text')?.value || '').trim()
    })).filter(r => r.type || r.title || r.url || r.text);

    for (const r of references) {
        if (!r.type) {
            showConnectorGeneratorError('Each reference requires a type.');
            return null;
        }
        if (!r.url && !r.text) {
            showConnectorGeneratorError('Each reference must contain URL or text.');
            return null;
        }
    }

    const requestPayload = {
        connectorName,
        repoRoot,
        overwriteExistingModule: false,
        objectKinds: selectedKinds,
        manifest: {
            connectorMaster: {
                oeDocs: '',
                shortDescription: connectorName + ' connector',
                protocol: manifestProtocol,
                oeConnCategory: 'Application Connectors',
                srcConnCategory: '',
                usageCostModel: 'Usage Based',
                active: true,
                crawling: !!document.getElementById('cmCrawlingInput')?.checked,
                querySheet: !!document.getElementById('cmQuerySheetInput')?.checked,
                dataAccess: false,
                autoLineage: false,
                profiling: false,
                dataQuality: false,
                authenticationTypes: [],
                credentialManagers: ['DATABASE']
            },
            crawlerOptions: buildCrawlerOptionsFromForm()
        },
        references
    };
    return { requestPayload, iconFile };
}

async function downloadConnectorGeneratorZip() {
    const submission = collectConnectorGeneratorSubmission({ requireRepoRoot: false });
    if (!submission) return;
    const { requestPayload, iconFile } = submission;
    const downloadBtn = document.getElementById('connectorDownloadBtn');

    clearConnectorGeneratorError();
    showPageLoader('Preparing connector zip...');
    if (downloadBtn) downloadBtn.disabled = true;

    try {
        const data = new FormData();
        data.append('request', new Blob([JSON.stringify(requestPayload)], { type: 'application/json' }));
        data.append('icon', iconFile);
        const response = await fetch(`${API_BASE}/generator/generate-download`, {
            method: 'POST',
            body: data
        });
        if (!response.ok) {
            let errorMessage = `Download failed (${response.status})`;
            try {
                const payload = await response.json();
                if (payload && Array.isArray(payload.errors) && payload.errors.length > 0) {
                    errorMessage = payload.errors.join(' ');
                } else if (payload && payload.message) {
                    errorMessage = payload.message;
                }
            } catch (e) {
                errorMessage = `Download failed (${response.status})`;
            }
            showConnectorGeneratorError(errorMessage);
            return;
        }

        const blob = await response.blob();
        const disposition = response.headers.get('Content-Disposition') || response.headers.get('content-disposition');
        const filename = getFilenameFromDisposition(disposition) || ((requestPayload.connectorName || 'connector') + '-connector.zip');
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(url);
    } catch (error) {
        showConnectorGeneratorError(error.message || 'Download failed.');
    } finally {
        if (downloadBtn) downloadBtn.disabled = false;
        hidePageLoader();
    }
}

function getFilenameFromDisposition(contentDisposition) {
    if (!contentDisposition) return '';
    const match = /filename="([^"]+)"/.exec(contentDisposition);
    return match && match[1] ? match[1] : '';
}


function showPageLoader(message = 'Loading...') {
    const msgEl = document.getElementById('loaderMessage');
    const overlay = document.getElementById('pageLoader');
    if (msgEl) msgEl.textContent = message;
    if (overlay) overlay.classList.remove('hidden');
}

function hidePageLoader() {
    const overlay = document.getElementById('pageLoader');
    if (overlay) overlay.classList.add('hidden');
}

function setButtonsState(enabled) {
    const buttons = document.querySelectorAll('button:not(.modal-close)');
    buttons.forEach(btn => {
        if (btn.id === 'copyCurlBtn' || btn.id === 'copyResponseBtn') {
            return;
        }
        btn.disabled = !enabled;
        if (!enabled) {
            btn.style.opacity = '0.6';
            btn.style.cursor = 'not-allowed';
        } else {
            btn.style.opacity = '1';
            btn.style.cursor = 'pointer';
        }
    });
}


// Initialize after DOM is ready (defer to next tick so DOM and script are fully loaded).
function initConnectorsAndOAuth() {
    updateResultButtons();
    loadConnectors().then(() => {
        checkOAuthCallback();
    });
}
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initConnectorsAndOAuth);
} else {
    setTimeout(initConnectorsAndOAuth, 0);
}

let loadConnectorsSafetyTimer = null;

async function loadConnectors() {
    const connectorList = document.getElementById('connectorList');
    if (!connectorList) {
        console.warn('loadConnectors: #connectorList not found');
        return;
    }

    console.log('loadConnectors: fetching /v1/info');
    showPageLoader('Loading connectors...');

    // Safety: force-hide loader and show error if still loading after 20s (e.g. response body never completes).
    if (loadConnectorsSafetyTimer) clearTimeout(loadConnectorsSafetyTimer);
    loadConnectorsSafetyTimer = setTimeout(() => {
        loadConnectorsSafetyTimer = null;
        hidePageLoader();
        const cl = document.getElementById('connectorList');
        if (cl && !cl.querySelector('.connector-card') && !cl.querySelector('.error')) {
            cl.innerHTML = '<div class="error">Loading timed out. Check browser Console and that GET /v1/info returns JSON.</div>';
        }
    }, 20000);

    try {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 15000);
        const response = await fetch(`${API_BASE}/info`, { signal: controller.signal });
        clearTimeout(timeoutId);

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const text = await response.text();
        let data;
        try {
            data = text ? JSON.parse(text) : {};
        } catch (e) {
            throw new Error('Invalid JSON from /v1/info: ' + e.message);
        }

        const list = data.availableConnectors;
        if (Array.isArray(list) && list.length > 0) {
            allConnectorsList = list.map(connectorType => {
                const serverType = String(connectorType);
                return { serverType, displayName: formatConnectorDisplayName(serverType) };
            });
            connectorListShowAll = false;
            const searchInput = document.getElementById('connectorSearch');
            if (searchInput) searchInput.value = '';
            renderConnectorList();
            attachConnectorSearchListener();
        } else {
            connectorList.innerHTML = '<p>No connectors available.</p>';
            const footer = document.getElementById('connectorListFooter');
            if (footer) {
                footer.classList.remove('hidden');
                footer.innerHTML = '<span class="empty-state-text">Start by creating one.</span>';
                appendCreateConnectorFooterAction(footer);
            }
        }
        console.log('loadConnectors: done, hiding loader');
    } catch (error) {
        console.error('loadConnectors error:', error);
        const message = error.name === 'AbortError'
            ? 'Request timed out. Check that the server is running and /v1/info is reachable.'
            : error.message;
        connectorList.innerHTML =
            '<div class="error">Error loading connectors: ' + escapeHtml(message) +
            '<br><small>Ensure CSP-API is running and <code>GET ' + API_BASE + '/info</code> returns JSON.</small></div>';
    } finally {
        if (loadConnectorsSafetyTimer) {
            clearTimeout(loadConnectorsSafetyTimer);
            loadConnectorsSafetyTimer = null;
        }
        hidePageLoader();
    }
}

function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

function formatConnectorDisplayName(serverType) {
    if (!serverType) return '';
    return serverType
        .split(/[-_\s]+/)
        .map(part => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
        .join(' ');
}

function connectorMatchesSearch(connector, searchText) {
    if (!searchText || !searchText.trim()) return true;
    const q = searchText.trim().toLowerCase();
    return connector.serverType.toLowerCase().includes(q) ||
        connector.displayName.toLowerCase().includes(q);
}

function renderConnectorList() {
    const connectorList = document.getElementById('connectorList');
    const footer = document.getElementById('connectorListFooter');
    if (!connectorList || allConnectorsList.length === 0) return;

    const searchInput = document.getElementById('connectorSearch');
    const searchText = searchInput ? searchInput.value : '';
    const filtered = allConnectorsList.filter(c => connectorMatchesSearch(c, searchText));

    const limit = (!searchText.trim() && !connectorListShowAll && filtered.length > CONNECTOR_LIST_INITIAL_COUNT)
        ? CONNECTOR_LIST_INITIAL_COUNT
        : filtered.length;
    const toShow = filtered.slice(0, limit);

    connectorList.innerHTML = '';
    toShow.forEach(({ serverType, displayName }) => {
        const card = document.createElement('div');
        card.className = 'connector-card';
        card.dataset.serverType = serverType;
        if (selectedConnector === serverType) card.classList.add('selected');
        card.innerHTML = `
            <div class="connector-card-icon-wrap">
                <img src="${API_BASE}/connector/${encodeURIComponent(serverType)}/icon" alt="" loading="lazy" onerror="this.style.display='none'; var fb=this.nextElementSibling; if(fb) fb.classList.add('show');">
                <span class="connector-card-icon-fallback" aria-hidden="true">${escapeHtml(displayName.charAt(0).toUpperCase())}</span>
            </div>
            <h3>${escapeHtml(displayName)}</h3>
        `;
        card.onclick = () => selectConnector(serverType);
        connectorList.appendChild(card);
    });

    footer.classList.remove('hidden');
    footer.innerHTML = '';
    if (searchText.trim()) {
        const hint = document.createElement('span');
        hint.className = 'search-hint';
        hint.textContent = filtered.length === 0
            ? 'No connectors match your search.'
            : (filtered.length === 1 ? '1 connector' : filtered.length + ' connectors');
        footer.appendChild(hint);
        if (filtered.length === 0) {
            appendCreateConnectorFooterAction(footer);
        }
    } else if (filtered.length > CONNECTOR_LIST_INITIAL_COUNT && !connectorListShowAll) {
        const showAllLink = document.createElement('a');
        showAllLink.href = '#';
        showAllLink.className = 'show-all-link';
        showAllLink.textContent = `Show all (${filtered.length})`;
        showAllLink.onclick = (e) => {
            e.preventDefault();
            connectorListShowAll = true;
            renderConnectorList();
        };
        footer.appendChild(showAllLink);
    } else if (connectorListShowAll && filtered.length > CONNECTOR_LIST_INITIAL_COUNT) {
        const showLessLink = document.createElement('a');
        showLessLink.href = '#';
        showLessLink.className = 'show-all-link';
        showLessLink.textContent = 'Show less';
        showLessLink.onclick = (e) => {
            e.preventDefault();
            connectorListShowAll = false;
            renderConnectorList();
        };
        footer.appendChild(showLessLink);
    } else {
        footer.classList.add('hidden');
    }
}

function appendCreateConnectorFooterAction(container) {
    if (!container) return;
    const actions = document.createElement('div');
    actions.className = 'footer-actions';
    const createBtn = document.createElement('button');
    createBtn.type = 'button';
    createBtn.className = 'inline-create-connector-btn';
    createBtn.innerHTML = '<span aria-hidden="true">➕</span> Create New Connector';
    createBtn.addEventListener('click', openConnectorGeneratorModal);
    actions.appendChild(createBtn);
    container.appendChild(actions);
}

function attachConnectorSearchListener() {
    const searchInput = document.getElementById('connectorSearch');
    if (!searchInput) return;
    searchInput.addEventListener('input', () => {
        connectorListShowAll = true;
        renderConnectorList();
    });
    searchInput.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            searchInput.value = '';
            connectorListShowAll = false;
            renderConnectorList();
            searchInput.blur();
        }
    });
}

async function selectConnector(serverType) {
    selectedConnector = serverType;
    lastRequestParams = null;
    lastResponseData = null;
    updateResultButtons();
    currentConnectionInfoId = generateRandomId();
    console.log(`Generated new connectionInfoId: ${currentConnectionInfoId} for ${serverType}`);

    // Update UI
    document.querySelectorAll('.connector-card').forEach(card => {
        card.classList.remove('selected');
        if (card.dataset.serverType === serverType) {
            card.classList.add('selected');
        }
    });

    showPageLoader(`Loading ${serverType} attributes...`);

    // Load connector attributes
    try {
        const response = await fetch(`${API_BASE}/connector/${serverType}/attributes`);
        const data = await response.json();

        if (data.error) {
            showError(data.error);
            return;
        }

        connectorAttributes = data.attributes;
        renderConnectionForm(data.attributes);
        renderActions();

        document.getElementById('configPlaceholder').classList.add('hidden');
        document.getElementById('configSection').classList.remove('hidden');
        document.getElementById('actionsSection').classList.remove('hidden');
        document.getElementById('metadataSection').classList.remove('hidden');

        collapseSelectConnectorSection(serverType);

        // Update result view
        const resultContent = document.getElementById('resultContent');
        resultContent.innerHTML = `Connector <strong>${serverType}</strong> selected.\nConfigure connection and execute an action.`;
        resultContent.className = 'result-content';
        updateResultButtons();
    } catch (error) {
        showError(`Error loading connector attributes: ${error.message}`);
    } finally {
        hidePageLoader();
    }
}

function collapseSelectConnectorSection(connectorName) {
    const section = document.getElementById('selectConnectorSection');
    const nameEl = document.getElementById('selectConnectorSelectedName');
    if (!section || !nameEl) return;
    nameEl.textContent = connectorName ? `: ${connectorName}` : '';
    section.classList.add('collapsed');
}

function toggleSelectConnectorSection() {
    const section = document.getElementById('selectConnectorSection');
    if (!section) return;
    section.classList.toggle('collapsed');
}

function renderConnectionForm(attributes) {
    const form = document.getElementById('connectionForm');
    form.innerHTML = '';

    if (!attributes || Object.keys(attributes).length === 0) {
        form.innerHTML = '<p>No connection attributes required for this connector.</p>';
        return;
    }

    // Sort attributes by sequenceDisplay if available
    const sortedAttributes = Object.entries(attributes).sort((a, b) => {
        const seqA = a[1].sequenceDisplay || 0;
        const seqB = b[1].sequenceDisplay || 0;
        return seqA - seqB;
    });

    sortedAttributes.forEach(([key, attr]) => {
        // Render HIDDEN attributes as hidden inputs so they are captured in the config
        if (attr.type === 'HIDDEN') {
            const hiddenInput = document.createElement('input');
            hiddenInput.type = 'hidden';
            hiddenInput.id = `attr_${key}`;
            hiddenInput.name = key;
            form.appendChild(hiddenInput);
            return;
        }

        // Hide by default: Credential Manager (1), Connector Description (connector name), Connector Admin Role (ADM), Security and Governance Roles (ADM), Select Bridge (No Bridge)
        if (isHiddenByDefaultAttr(key)) {
            const hiddenInput = document.createElement('input');
            hiddenInput.type = 'hidden';
            hiddenInput.id = `attr_${key}`;
            hiddenInput.name = key;
            hiddenInput.value = getDefaultForHiddenAttr(key) || '';
            form.appendChild(hiddenInput);
            return;
        }

        const formGroup = document.createElement('div');
        formGroup.className = 'form-group';

        const label = document.createElement('label');
        label.innerHTML = `${attr.name || key}${attr.required ? ' <span class="required">*</span>' : ''}`;
        if (attr.description) {
            label.title = attr.description;
        }

        let input;
        if (attr.type === 'VALUELIST' && attr.dropdownList) {
            input = document.createElement('select');
            attr.dropdownList.forEach(item => {
                const option = document.createElement('option');
                option.value = item.value || item.key;
                option.textContent = item.label || item.value || item.key;
                input.appendChild(option);
            });
        } else if (attr.type === 'PASSWORD' || (attr.masked && attr.type !== 'TEXT')) {
            input = document.createElement('input');
            input.type = 'password';
        } else {
            input = document.createElement('input');
            input.type = 'text';
        }

        input.id = `attr_${key}`;
        input.name = key;
        input.placeholder = attr.description || '';
        input.required = attr.required || false;

        formGroup.appendChild(label);
        formGroup.appendChild(input);
        form.appendChild(formGroup);
    });
}

// State management for metadata operations
let supportedObjectsData = null;
let currentMetadataActionType = null;

function renderActions() {
    const actionsGrid = document.getElementById('actionsGrid');
    actionsGrid.innerHTML = '';

    const actions = [
        {
            name: 'Validate Connection',
            endpoint: '/connection/validate',
            method: 'POST',
            needsBody: true,
            description: 'Validates the connection configuration'
        },
        {
            name: 'Get Supported Objects',
            endpoint: '/metadata/supported-objects',
            method: 'POST',
            needsBody: true,
            description: 'Gets supported objects (entities, reports, etc.)',
            callback: handleSupportedObjectsResponse
        },
        {
            name: 'Get Containers',
            endpoint: '/metadata/containers',
            method: 'POST',
            needsBody: true,
            description: 'Gets containers (companies, organizations, etc.)'
        }
    ];

    actions.forEach(action => {
        const btn = document.createElement('button');
        btn.className = 'action-btn';
        btn.textContent = action.name;
        btn.title = action.description || '';
        btn.onclick = () => executeAction(action);
        actionsGrid.appendChild(btn);
    });
}

function getConnectionConfig() {
    const config = {
        connectionInfoId: currentConnectionInfoId,
        serverType: selectedConnector,
        additionalAttributes: {}
    };

    if (connectorAttributes) {
        Object.keys(connectorAttributes).forEach(key => {
            const input = document.getElementById(`attr_${key}`);
            if (input && input.value) {
                config.additionalAttributes[key] = input.value;
            }
        });
        // Ensure hidden-by-default attributes are always sent with defaults (e.g. if API did not return them)
        CANONICAL_HIDDEN_ATTR_KEYS.forEach(function (canonicalKey) {
            var alreadySet = Object.keys(config.additionalAttributes).some(function (k) { return k.toLowerCase() === canonicalKey.toLowerCase(); });
            if (!alreadySet) {
                config.additionalAttributes[canonicalKey] = getDefaultForHiddenAttr(canonicalKey);
            }
        });
    }

    return config;
}

function handleSupportedObjectsResponse(data) {
    supportedObjectsData = data;
}

function showMetadataInputModal(type) {
    currentMetadataActionType = type;
    const modal = document.getElementById('metadataModal');
    const modalTitle = document.getElementById('modalTitle');
    const modalBody = document.getElementById('modalBody');

    modalBody.innerHTML = '';

    if (type === 'getObjects') {
        modalTitle.textContent = 'Get Objects Parameters';
        addEntityTypeInput();
        addModalInputWithAction('containerId', 'Container ID (e.g., company ID)', true, fetchContainersForModal);
    } else if (type === 'getFields') {
        modalTitle.textContent = 'Get Fields Parameters';
        addEntityTypeInput();
        addModalInputWithAction('containerId', 'Container ID', true, fetchContainersForModal);
        addModalInputWithAction('entityId', 'Entity/Object ID (e.g., Customer)', true, fetchObjectsForModal);
    } else if (type === 'executeQuery') {
        modalTitle.textContent = 'Execute Query Parameters';
        addEntityTypeInput();
        addModalInputWithAction('containerId', 'Container ID', true, fetchContainersForModal);
        addModalInputWithAction('entityId', 'Entity/Object ID', true, fetchObjectsForModal);
        addModalInputWithAction('fields', 'Fields (comma separated, e.g., Id, Name)', false, fetchFieldsForModal);
        addModalInput('limit', 'Limit (e.g., 100)', false, 'number');
        addModalInput('offset', 'Offset (e.g., 0)', false, 'number');
    }

    modal.classList.remove('hidden');
}

function addModalInput(name, label, required, type = 'text') {
    const modalBody = document.getElementById('modalBody');
    const group = document.createElement('div');
    group.className = 'form-group';

    const lbl = document.createElement('label');
    lbl.textContent = label + (required ? ' *' : '');

    const input = document.createElement('input');
    input.type = type;
    input.id = `modal_input_${name}`;
    input.name = name;
    if (required) input.required = true;

    group.appendChild(lbl);
    group.appendChild(input);
    modalBody.appendChild(group);
}

function addModalInputWithAction(name, label, required, actionFn, icon = '🔄') {
    const modalBody = document.getElementById('modalBody');
    const group = document.createElement('div');
    group.className = 'form-group';

    const lbl = document.createElement('label');
    lbl.textContent = label + (required ? ' *' : '');

    const wrapper = document.createElement('div');
    wrapper.className = 'input-with-action';

    const input = document.createElement('input');
    input.type = 'text';
    input.id = `modal_input_${name}`;
    input.name = name;
    input.placeholder = label;
    if (required) input.required = true;

    const btn = document.createElement('button');
    btn.className = 'icon-btn';
    btn.innerHTML = icon;
    btn.title = `Fetch ${label}`;
    btn.type = 'button';
    btn.onclick = () => actionFn(input, btn);

    wrapper.appendChild(input);
    wrapper.appendChild(btn);
    group.appendChild(lbl);
    group.appendChild(wrapper);
    modalBody.appendChild(group);
}

async function fetchContainersForModal(input, btn) {
    const originalIcon = btn.innerHTML;
    btn.innerHTML = '<div class="loading" style="width:16px; height:16px; border-width:2px;"></div>';
    btn.disabled = true;
    document.getElementById('modalSubmitBtn').disabled = true;

    try {
        const config = getConnectionConfig();
        const response = await fetch(`${API_BASE}/metadata/containers`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(config)
        });

        if (response.ok) {
            const data = await response.json();
            const containers = data.containers || [];

            if (containers.length === 0) {
                showError('No containers found');
            } else if (containers.length === 1) {
                input.value = containers[0].id || containers[0].name;
            } else {
                // Create a temporary select to replace the input
                const select = document.createElement('select');
                select.id = input.id;
                select.name = input.name;
                select.required = input.required;

                const defaultOpt = document.createElement('option');
                defaultOpt.value = '';
                defaultOpt.textContent = '-- Select Container --';
                select.appendChild(defaultOpt);

                containers.forEach(c => {
                    const opt = document.createElement('option');
                    opt.value = c.id || c.name;
                    opt.textContent = c.name || c.id;
                    select.appendChild(opt);
                });

                // Replace the input with the select in the DOM
                input.parentNode.replaceChild(select, input);
                // Store the select as the new input to avoid issues
                input = select;
                btn.remove();
            }
        } else {
            showError('Failed to fetch containers');
        }
    } catch (e) {
        console.error(e);
        showError('Error connecting to API');
    } finally {
        document.getElementById('modalSubmitBtn').disabled = false;
        if (btn && btn.parentNode) {
            btn.innerHTML = originalIcon;
            btn.disabled = false;
        }
    }
}

async function fetchObjectsForModal(input, btn) {
    const entityTypeInput = document.getElementById('modal_input_entityType');
    const containerIdInput = document.getElementById('modal_input_containerId');

    const entityType = entityTypeInput ? entityTypeInput.value : '';
    const containerId = containerIdInput ? containerIdInput.value : '';

    if (!entityType || !containerId) {
        showError('Please select Entity Type and Container ID first');
        if (!entityType && entityTypeInput) entityTypeInput.style.borderColor = 'red';
        if (!containerId && containerIdInput) containerIdInput.style.borderColor = 'red';
        return;
    }

    if (entityTypeInput) entityTypeInput.style.borderColor = '';
    if (containerIdInput) containerIdInput.style.borderColor = '';

    const originalIcon = btn.innerHTML;
    btn.innerHTML = '<div class="loading" style="width:16px; height:16px; border-width:2px;"></div>';
    btn.disabled = true;
    document.getElementById('modalSubmitBtn').disabled = true;

    try {
        const config = getConnectionConfig();
        let displayNameParam = '';
        // Best-effort: send displayName when we can extract it from the selected option.
        if (entityTypeInput && entityTypeInput.selectedOptions && entityTypeInput.selectedOptions.length) {
            const selectedOpt = entityTypeInput.selectedOptions[0];
            // Prefer explicit discriminator; fall back to visible label.
            const dn = selectedOpt.getAttribute('data-displayname') || selectedOpt.textContent;
            if (dn && dn.trim()) displayNameParam = `&displayName=${encodeURIComponent(dn.trim())}`;
        }
        const url = `${API_BASE}/metadata/objects?entityType=${encodeURIComponent(entityType)}&containerId=${encodeURIComponent(containerId)}${displayNameParam}`;
        const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(config)
        });

        if (response.ok) {
            const data = await response.json();
            const objects = data.objects || data.children || [];

            if (objects.length === 0) {
                showError('No objects found');
            } else if (objects.length === 1) {
                const obj = objects[0];
                input.value = obj.id || obj.name || obj.displayName || obj.typeName || '';
            } else {
                // Create a temporary select to replace the input
                const select = document.createElement('select');
                select.id = input.id;
                select.name = input.name;
                select.required = input.required;

                const defaultOpt = document.createElement('option');
                defaultOpt.value = '';
                defaultOpt.textContent = '-- Select Object --';
                select.appendChild(defaultOpt);

                objects.forEach(obj => {
                    const opt = document.createElement('option');
                    opt.value = obj.id || obj.name || obj.displayName || obj.typeName || '';
                    opt.textContent = obj.name || obj.displayName || obj.id || obj.typeName || '';
                    select.appendChild(opt);
                });

                // Replace the input with the select in the DOM
                input.parentNode.replaceChild(select, input);
                btn.remove();
            }
        } else {
            showError('Failed to fetch objects');
        }
    } catch (e) {
        console.error(e);
        showError('Error connecting to API');
    } finally {
        document.getElementById('modalSubmitBtn').disabled = false;
        if (btn && btn.parentNode) {
            btn.innerHTML = originalIcon;
            btn.disabled = false;
        }
    }
}

async function fetchFieldsForModal(input, btn) {
    const entityTypeInput = document.getElementById('modal_input_entityType');
    const containerIdInput = document.getElementById('modal_input_containerId');
    const entityIdInput = document.getElementById('modal_input_entityId');

    const entityType = entityTypeInput ? entityTypeInput.value : '';
    const containerId = containerIdInput ? containerIdInput.value : '';
    const entityId = entityIdInput ? entityIdInput.value : '';

    if (!entityType || !containerId || !entityId) {
        showError('Please select Entity Type, Container ID, and Entity ID first');
        if (!entityType && entityTypeInput) entityTypeInput.style.borderColor = 'red';
        if (!containerId && containerIdInput) containerIdInput.style.borderColor = 'red';
        if (!entityId && entityIdInput) entityIdInput.style.borderColor = 'red';
        return;
    }

    if (entityTypeInput) entityTypeInput.style.borderColor = '';
    if (containerIdInput) containerIdInput.style.borderColor = '';
    if (entityIdInput) entityIdInput.style.borderColor = '';

    const originalIcon = btn.innerHTML;
    btn.innerHTML = '<div class="loading" style="width:16px; height:16px; border-width:2px;"></div>';
    btn.disabled = true;
    document.getElementById('modalSubmitBtn').disabled = true;

    try {
        const config = getConnectionConfig();
        let displayNameParam = '';
        // Pass displayName parameter to disambiguate which ENTITY subtype to list/resolve.
        if (entityTypeInput && entityTypeInput.selectedOptions && entityTypeInput.selectedOptions.length) {
            const selectedOpt = entityTypeInput.selectedOptions[0];
            const dn = selectedOpt.getAttribute('data-displayname') || selectedOpt.textContent;
            if (dn && dn.trim()) {
                displayNameParam = `&displayName=${encodeURIComponent(dn.trim())}`;
            }
        }

        const url = `${API_BASE}/metadata/fields?entityType=${encodeURIComponent(entityType)}&containerId=${encodeURIComponent(containerId)}&entityId=${encodeURIComponent(entityId)}${displayNameParam}`;
        const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(config)
        });

        if (response.ok) {
            const data = await response.json();
            const fields = data.fields || data.children || [];

            if (fields.length === 0) {
                showError('No fields found');
            } else {
                // Select all fields by default, checking various possible field name properties
                const fieldNames = fields
                    .map(f => f.fieldName || f.name || f.id || f.displayName)
                    .filter(Boolean) // Remove any null/undefined/empty values
                    .join(', ');
                input.value = fieldNames;
            }
        } else {
            showError('Failed to fetch fields');
        }
    } catch (e) {
        console.error(e);
        showError('Error connecting to API');
    } finally {
        document.getElementById('modalSubmitBtn').disabled = false;
        if (btn && btn.parentNode) {
            btn.innerHTML = originalIcon;
            btn.disabled = false;
        }
    }
}

function addModalSelect(name, label, options, required) {
    const modalBody = document.getElementById('modalBody');
    const group = document.createElement('div');
    group.className = 'form-group';

    const lbl = document.createElement('label');
    lbl.textContent = label + (required ? ' *' : '');

    const select = document.createElement('select');
    select.id = `modal_input_${name}`;
    select.name = name;
    if (required) select.required = true;

    const defaultOpt = document.createElement('option');
    defaultOpt.value = '';
    defaultOpt.textContent = `-- Select ${label} --`;
    select.appendChild(defaultOpt);

    options.forEach(opt => {
        const o = document.createElement('option');
        o.value = opt.value;
        o.textContent = opt.label;
        select.appendChild(o);
    });

    group.appendChild(lbl);
    group.appendChild(select);
    modalBody.appendChild(group);
}

async function addEntityTypeInput() {
    // Always add a select
    addModalSelect('entityType', 'Entity Type', [], true);
    const select = document.getElementById('modal_input_entityType');

    if (supportedObjectsData && supportedObjectsData.supportedObjects) {
        populateEntityTypeSelect(select, supportedObjectsData.supportedObjects);
    } else {
        // Background fetch
        select.options[0].textContent = '-- Loading types... --';
        select.disabled = true;

        try {
            const config = getConnectionConfig();
            const response = await fetch(`${API_BASE}/metadata/supported-objects`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(config)
            });

            if (response.ok) {
                const data = await response.json();
                supportedObjectsData = data;
                populateEntityTypeSelect(select, data.supportedObjects || []);
                select.disabled = false;
                select.options[0].textContent = ' Select Entity Type ';
            } else {
                select.options[0].textContent = ' Error loading types ';
                // Fallback to text input if select fails? 
                // User specifically asked for dropdown, so maybe just log error
                console.error('Background fetch of supported objects failed');
            }
        } catch (e) {
            select.options[0].textContent = ' Error loading types ';
            console.error('Error in background fetch', e);
        }
    }
}

function populateEntityTypeSelect(select, items) {
    // Clear existing options except the first one
    while (select.options.length > 1) {
        select.remove(1);
    }

    items.forEach(obj => {
        const o = document.createElement('option');
        o.value = obj.typeName;
        o.textContent = obj.displayName || obj.typeName;
        // Best-effort discriminator for connectors that expose multiple subtypes under the same ObjectKind.
        if (obj.displayName) o.setAttribute('data-displayname', obj.displayName);
        select.appendChild(o);
    });
}

function closeMetadataModal() {
    document.getElementById('metadataModal').classList.add('hidden');
}

async function submitMetadataModal() {
    const inputs = document.querySelectorAll('#modalBody input, #modalBody select');
    const params = {};
    let isValid = true;

    inputs.forEach(input => {
        if (input.required && !input.value) {
            input.style.borderColor = 'red';
            isValid = false;
        } else {
            input.style.borderColor = '';
            params[input.name] = input.value;
        }
    });

    if (!isValid) return;

    closeMetadataModal();

    let action = {};
    if (currentMetadataActionType === 'getObjects') {
        let displayNameParam = '';
        // Best-effort: send displayName when we can extract it from the selected option.
        const entityTypeSelect = document.getElementById('modal_input_entityType');
        if (entityTypeSelect && entityTypeSelect.selectedOptions && entityTypeSelect.selectedOptions.length) {
            const selectedOpt = entityTypeSelect.selectedOptions[0];
            // Prefer explicit discriminator; fall back to visible label.
            const dn = selectedOpt.getAttribute('data-displayname') || selectedOpt.textContent;
            if (dn && dn.trim()) displayNameParam = `&displayName=${encodeURIComponent(dn.trim())}`;
        }
        action = {
            name: 'Get Objects',
            endpoint: `/metadata/objects?entityType=${params.entityType}&containerId=${params.containerId}${displayNameParam}`,
            method: 'POST'
        };
    } else if (currentMetadataActionType === 'getFields') {
        let displayNameParam = '';
        const entityTypeSelect = document.getElementById('modal_input_entityType');
        if (entityTypeSelect && entityTypeSelect.selectedOptions && entityTypeSelect.selectedOptions.length) {
            const selectedOpt = entityTypeSelect.selectedOptions[0];
            const dn = selectedOpt.getAttribute('data-displayname') || selectedOpt.textContent;
            if (dn && dn.trim()) displayNameParam = `&displayName=${encodeURIComponent(dn.trim())}`;
        }
        action = {
            name: 'Get Fields',
            endpoint: `/metadata/fields?entityType=${params.entityType}&containerId=${params.containerId}&entityId=${params.entityId}${displayNameParam}`,
            method: 'POST'
        };
    } else if (currentMetadataActionType === 'executeQuery') {
        let displayNameQueryParam = '';
        const entityTypeSelect = document.getElementById('modal_input_entityType');
        if (entityTypeSelect && entityTypeSelect.selectedOptions && entityTypeSelect.selectedOptions.length) {
            const selectedOpt = entityTypeSelect.selectedOptions[0];
            const dn = selectedOpt.getAttribute('data-displayname') || selectedOpt.textContent;
            if (dn && dn.trim()) displayNameQueryParam = `?displayName=${encodeURIComponent(dn.trim())}`;
        }
        action = {
            name: 'Execute Query',
            endpoint: `/query${displayNameQueryParam}`,
            method: 'POST',
            isQuery: true,
            queryParams: params
        };
    }

    executeAction(action);
}

async function executeAction(action) {
    const resultContent = document.getElementById('resultContent');

    lastResponseData = null;

    resultContent.innerHTML = `<div style="display:flex; flex-direction:column; align-items:center; justify-content:center; height:100%; gap:15px;"><div class="loading" style="width:40px; height:40px;"></div><span>Executing ${action.name}...</span></div>`;
    resultContent.className = 'result-content';
    updateResultButtons();

    setButtonsState(false);

    try {
        const config = getConnectionConfig();
        let url = `${API_BASE}${action.endpoint}`;
        let body = JSON.stringify(config);

        if (action.isQuery) {
            const queryRequest = {
                connectionConfig: config,
                entityType: action.queryParams.entityType,
                containerId: action.queryParams.containerId,
                entityId: action.queryParams.entityId,
                fields: action.queryParams.fields ? action.queryParams.fields.split(',').map(f => f.trim()) : [],
                filters: [],
                limit: action.queryParams.limit ? parseInt(action.queryParams.limit) : null,
                offset: action.queryParams.offset ? parseInt(action.queryParams.offset) : null
            };
            body = JSON.stringify(queryRequest);
        }

        const response = await fetch(url, {
            method: action.method,
            headers: {
                'Content-Type': 'application/json'
            },
            body: body
        });

        // Store for cURL and Copy
        lastRequestParams = {
            url: window.location.origin + url,
            method: action.method,
            headers: { 'Content-Type': 'application/json' },
            body: body
        };

        const data = await response.json();
        lastResponseData = data;
        updateResultButtons();

        if (response.ok) {
            // Handle OAuth flow when connector returns AUTH_REQUIRED or SUCCESS
            const msg = data && typeof data.message === 'string' ? data.message : '';
            if (action.name === 'Validate Connection') {
                if (msg.startsWith('AUTH_REQUIRED:')) {
                    const authUrl = msg.replace('AUTH_REQUIRED:', '');
                    localStorage.setItem('oauth_auth_state', JSON.stringify({
                        connector: selectedConnector,
                        config: config
                    }));
                    resultContent.innerHTML = `
                        <div class="info">
                            <p>Authorization Required. Opening popup...</p>
                            <button onclick="window.open('${authUrl}', 'oauthAuth', 'width=600,height=700')" class="action-btn">
                                Re-open Login Popup if blocked
                            </button>
                        </div>
                    `;
                    const authWindow = window.open(authUrl, 'oauthAuth', 'width=600,height=700');
                    if (!authWindow) {
                        resultContent.innerHTML += '<p style="color:red">Popup blocked! Please click the button above.</p>';
                    }
                    return;
                } else if (msg.startsWith('SUCCESS:')) {
                    const result = JSON.parse(msg.replace('SUCCESS:', ''));
                    resultContent.innerHTML = `
                        <div class="success">
                            <p><strong>Connection Successful!</strong></p>
                            <pre>${JSON.stringify(result, null, 2)}</pre>
                        </div>
                    `;
                    return;
                }
            }

            // Call callback if provided (for populating dropdowns)
            if (action.callback && typeof action.callback === 'function') {
                action.callback(data);
            }

            resultContent.textContent = JSON.stringify(data, null, 2);
            resultContent.className = 'result-content';
            updateResultButtons();
        } else {
            resultContent.textContent = `Error (${response.status}): ${JSON.stringify(data, null, 2)}`;
            resultContent.className = 'result-content error';
            updateResultButtons();
        }
    } catch (error) {
        resultContent.textContent = `Error: ${error.message}\n\nStack: ${error.stack}`;
        resultContent.className = 'result-content error';
        updateResultButtons();
    } finally {
        setButtonsState(true);
    }
}

// AUTOMATION: Check for OAuth callback on load
async function checkOAuthCallback() {
    const urlParams = new URLSearchParams(window.location.search);
    const code = urlParams.get('code');
    const realmId = urlParams.get('realmId');

    if (code) {
        // If we are in the popup, send message to opener and close
        if (window.opener && window.opener !== window) {
            window.opener.postMessage({ type: 'OAUTH_AUTH_SUCCESS', code, realmId }, window.location.origin);
            window.close();
            return;
        }

        const savedState = localStorage.getItem('oauth_auth_state');
        if (savedState) {
            try {
                const { connector, config } = JSON.parse(savedState);
                localStorage.removeItem('oauth_auth_state'); // Clear it

                // Restore UI state
                await selectConnector(connector);

                // Populate fields from saved config
                Object.entries(config.additionalAttributes || {}).forEach(([key, val]) => {
                    const input = document.getElementById(`attr_${key}`);
                    if (input) input.value = val;
                });

                const resultContent = document.getElementById('resultContent');
                resultContent.innerHTML = `
                    <div class="success">
                        <p><strong>OAuth Code Received!</strong></p>
                        <p>Enter the code and any realm/company ID in your connector's form, then click <strong>Validate Connection</strong> to exchange and verify.</p>
                    </div>
                `;

                // Clean up URL without reloading
                window.history.replaceState({}, document.title, window.location.pathname);

            } catch (e) {
                console.error('Error restoring OAuth state', e);
            }
        }
    }
}

// Listener for popup messages (OAuth callback)
window.addEventListener('message', (event) => {
    if (event.origin !== window.location.origin) return;
    if (event.data.type === 'OAUTH_AUTH_SUCCESS') {
        const resultContent = document.getElementById('resultContent');
        if (resultContent) {
            resultContent.innerHTML = `
                <div class="success">
                    <p><strong>Authorization Received from Popup!</strong></p>
                    <p>Enter the code and any realm/company ID in your connector's form, then click <strong>Validate Connection</strong> to finish.</p>
                </div>
            `;
        }
    }
});

function showError(message) {
    const resultContent = document.getElementById('resultContent');
    resultContent.textContent = `Error: ${message}`;
    resultContent.className = 'result-content error';
    lastResponseData = { error: message };
    updateResultButtons();
}

async function copyToClipboard(text, btnId) {
    const markCopied = () => {
        const btn = document.getElementById(btnId);
        if (btn) {
            const originalContent = btn.innerHTML;
            btn.innerHTML = '<span>✅</span> Copied!';
            btn.classList.add('copy-success');
            setTimeout(() => {
                btn.innerHTML = originalContent;
                btn.classList.remove('copy-success');
            }, 2000);
        }
    };

    const fallbackCopy = () => {
        const ta = document.createElement('textarea');
        ta.value = text;
        ta.setAttribute('readonly', '');
        ta.style.position = 'fixed';
        ta.style.opacity = '0';
        ta.style.left = '-9999px';
        document.body.appendChild(ta);
        ta.focus();
        ta.select();
        let ok = false;
        try {
            ok = document.execCommand('copy');
        } catch (e) {
            ok = false;
        }
        document.body.removeChild(ta);
        return ok;
    };

    try {
        if (navigator.clipboard && window.isSecureContext) {
            await navigator.clipboard.writeText(text);
            markCopied();
            return;
        }
        if (fallbackCopy()) {
            markCopied();
            return;
        }
        showError('Copy failed. Please copy manually from the response panel.');
    } catch (err) {
        if (fallbackCopy()) {
            markCopied();
            return;
        }
        console.error('Failed to copy: ', err);
        showError('Copy failed. Please copy manually from the response panel.');
    }
}

function copyResponse() {
    if (!lastResponseData) return;
    copyToClipboard(JSON.stringify(lastResponseData, null, 2), 'copyResponseBtn');
}

function copyCurl() {
    if (!lastRequestParams) return;
    
    const { url, method, headers, body } = lastRequestParams;
    let curl = `curl -X ${method} '${url}'`;
    
    Object.entries(headers).forEach(([key, value]) => {
        curl += ` \\\n  -H '${key}: ${value}'`;
    });
    
    if (body && method !== 'GET') {
        curl += ` \\\n  -d '${body.replace(/'/g, "'\\''")}'`;
    }
    
    copyToClipboard(curl, 'copyCurlBtn');
}

    // Logs Section Management
    let logsEventSource = null;
    let autoScroll = true;
    let lastLogsHeight = 200; // Track last expanded height for restoration

    function toggleLogs() {
const logsSection = document.getElementById('logsSection');
const toggleBtn = document.getElementById('logsToggleBtn');
const resizer = document.getElementById('logsResizer');
if (!logsSection || !toggleBtn) return;

const isCollapsed = logsSection.classList.toggle('collapsed');

if (isCollapsed) {
    lastLogsHeight = logsSection.offsetHeight;
    logsSection.style.height = '45px';
    if (resizer) resizer.style.display = 'none';
} else {
    logsSection.style.height = (lastLogsHeight || 200) + 'px';
    if (resizer) resizer.style.display = 'flex';
}

toggleBtn.textContent = isCollapsed ? '▲' : '▼';
    }

    function clearLogs(event) {
if (event) event.stopPropagation();
const logsContent = document.getElementById('logsContent');
if (logsContent) logsContent.innerHTML = '<div class="log-entry">Logs cleared. Waiting for new logs...</div>';
    }

    function connectToLogs() {
const logsContent = document.getElementById('logsContent');
const statusIndicator = document.getElementById('logsStatusIndicator');
const statusText = document.getElementById('logsStatusText');

// Close existing connection if any
if (logsEventSource) {
    logsEventSource.close();
}

try {
    logsEventSource = new EventSource('/v1/logs/stream');

    logsEventSource.onopen = () => {
        if (statusIndicator) statusIndicator.classList.remove('disconnected');
        if (statusText) statusText.textContent = 'Connected';
        console.log('Logs stream connected');
    };

    logsEventSource.onmessage = (event) => {
        try {
            const logData = JSON.parse(event.data);
            if (logData.type === 'heartbeat') return;
            appendLogEntry(logData);
        } catch (e) {
            // If not JSON, treat as plain text
            appendLogEntry({ message: event.data, level: 'INFO' });
        }
    };

    logsEventSource.onerror = (error) => {
        console.error('Logs stream error:', error);
        if (statusIndicator) statusIndicator.classList.add('disconnected');
        if (statusText) statusText.textContent = 'Disconnected';

        // Attempt to reconnect after 5 seconds
        setTimeout(() => {
            if (logsEventSource && logsEventSource.readyState === EventSource.CLOSED) {
                if (statusText) statusText.textContent = 'Reconnecting...';
                connectToLogs();
            }
        }, 5000);
    };
} catch (error) {
    console.error('Failed to connect to logs stream:', error);
    if (statusIndicator) statusIndicator.classList.add('disconnected');
    if (statusText) statusText.textContent = 'Failed';
}
    }

    function appendLogEntry(logData) {
const logsContent = document.getElementById('logsContent');
if (!logsContent) return;

// Remove "waiting" message if present
if (logsContent.children.length === 1 && logsContent.textContent.includes('Waiting for')) {
    logsContent.innerHTML = '';
}

const logEntry = document.createElement('div');
logEntry.className = `log-entry ${logData.level || 'INFO'}`;

const timestamp = logData.timestamp || new Date().toISOString();
const level = logData.level || 'INFO';
const message = logData.message || '';
const fileName = logData.fileName || '';
const lineNumber = logData.lineNumber || '';

let locationHtml = '';
if (fileName && lineNumber) {
    locationHtml = `<span class="log-location">(${fileName}:${lineNumber})</span>`;
}

logEntry.innerHTML = `<span class="log-timestamp">${formatTimestamp(timestamp)}</span><span class="log-level">[${level}]</span>${locationHtml}${escapeHtml(message)}`;

logsContent.appendChild(logEntry);

// Auto-scroll to bottom if enabled
if (autoScroll) {
    logsContent.scrollTop = logsContent.scrollHeight;
}

// Limit log entries to prevent memory issues (keep last 500)
while (logsContent.children.length > 500) {
    logsContent.removeChild(logsContent.firstChild);
}
    }

    function formatTimestamp(timestamp) {
const date = new Date(timestamp);
const hours = String(date.getHours()).padStart(2, '0');
const minutes = String(date.getMinutes()).padStart(2, '0');
const seconds = String(date.getSeconds()).padStart(2, '0');
const ms = String(date.getMilliseconds()).padStart(3, '0');
return `${hours}:${minutes}:${seconds}.${ms}`;
    }

    // Setup logs scroll, resizer, and connect — run when DOM is ready (same as init).
    function initLogsAndResizer() {
        const logsContent = document.getElementById('logsContent');
        if (logsContent) {
            logsContent.addEventListener('scroll', () => {
                const isScrolledToBottom = logsContent.scrollHeight - logsContent.scrollTop <= logsContent.clientHeight + 50;
                autoScroll = isScrolledToBottom;
            });
        }

        const logsResizer = document.getElementById('logsResizer');
        const logsSection = document.getElementById('logsSection');
        const rightPanel = document.querySelector('.right-panel');
        let isResizing = false;
        let startY;
        let startHeight;
        if (logsResizer && logsSection && rightPanel) {
            logsResizer.addEventListener('mousedown', (e) => {
                if (logsSection.classList.contains('collapsed')) return;
                isResizing = true;
                startY = e.clientY;
                startHeight = logsSection.offsetHeight;
                document.body.style.cursor = 'ns-resize';
                document.body.style.userSelect = 'none';
            });
        }

        document.addEventListener('mousemove', (e) => {
            if (!isResizing) return;
            const dy = startY - e.clientY;
            const newHeight = startHeight + dy;
            const maxHeight = rightPanel ? rightPanel.offsetHeight * 0.8 : 400;
            if (logsSection && newHeight >= 100 && newHeight <= maxHeight) {
                logsSection.style.height = newHeight + 'px';
                lastLogsHeight = newHeight;
            }
        });

        document.addEventListener('mouseup', () => {
            if (isResizing) {
                isResizing = false;
                document.body.style.cursor = 'default';
                document.body.style.userSelect = 'auto';
            }
        });

        connectToLogs();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initLogsAndResizer);
    } else {
        setTimeout(initLogsAndResizer, 0);
    }

    // Clean up on page unload
    window.addEventListener('beforeunload', () => {
if (logsEventSource) {
    logsEventSource.close();
}
    });


