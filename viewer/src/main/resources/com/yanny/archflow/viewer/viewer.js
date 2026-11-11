(function () {
    'use strict';

    const VIEW_LABELS = {'outline': 'Outline', 'all-functions': 'All functions', 'dependencies': 'Dependencies'};
    const EDGE_KINDS = ['call', 'override', 'dependency'];
    const DEFAULT_KINDS = {'outline': ['call', 'override'], 'all-functions': ['call', 'override'], 'dependencies': ['dependency']};
    const FIT = {fit: true};
    const PADDING = 20;
    const FOCUS_ZOOM = 1.5;
    const HALO = 0.25;
    const BORDER = {width: 2, chosen: 3, faint: 0.45};
    // The label of a box is drawn above its bounds, so the box ELK places must also hold the labels of its children.
    const BOX_PADDING = '[top=32,left=14,bottom=14,right=14]';
    const TRAIL_LENGTH = 100;
    const METHOD_RADIUS = 12;
    const TAP_DISTANCE = 4;
    const MENU_PLACES = 10;
    const DECLARING_SITES = new Set(['parameter', 'return', 'supertype', 'declaration']);
    const CHART_LABEL = 70;
    const CHART_ROW = 60;
    const CHART_ZOOM = {min: 0.7, max: 1.5};

    const FONT = {method: 10, other: 11};
    const OPEN_LIMIT = 1000;

    cytoscape.use(cytoscapeElk);
    const elkWorker = startElkWorker();
    let layouts = 0;

    const state = {data: null, graph: null, byId: new Map(), children: new Map(), expanded: new Set(), kinds: new Set(), selected: null, partner: null, change: false, focus: null, lens: 'all', shown: null};
    const measure = document.createElement('canvas').getContext('2d');
    const cy = cytoscape({container: document.getElementById('graph'), style: styles(), autoungrabify: true, autounselectify: true});
    const chart = {cy: null, id: null};
    const trail = {entries: [], index: -1};
    let pendingReveal = null;
    let warned = null;

    const graphDrag = panOnDrag(cy);
    cy.on('tap', 'node, edge', event => {
        if (graphDrag.moved()) {
            return;
        }
        pick(event.target);
        const original = event.originalEvent;
        if (original && (original.ctrlKey || original.metaKey)) {
            openSource();
        }
    });
    cy.on('tap', event => {
        if (event.target === cy) {
            closeMenu();
        }
    });
    cy.on('cxttap', 'node, edge', event => {
        pick(event.target);
        openMenu(event.target, event.renderedPosition);
    });
    cy.on('viewport', closeMenu);
    document.getElementById('stage').addEventListener('contextmenu', event => event.preventDefault());
    document.addEventListener('mousedown', event => {
        if (!document.getElementById('menu').contains(event.target)) {
            closeMenu();
        }
    });
    document.getElementById('source').addEventListener('click', openSource);
    cy.on('dbltap', 'node', event => {
        const id = event.target.id();
        if (state.children.has(id)) {
            toggle(id);
        } else {
            openChart(id);
        }
    });
    document.getElementById('chart-back').addEventListener('click', closeChart);
    document.getElementById('details-chart').addEventListener('click', () => openChart(state.selected));
    document.getElementById('undo').addEventListener('click', () => stepTrail(-1));
    document.getElementById('redo').addEventListener('click', () => stepTrail(1));
    document.addEventListener('keydown', event => {
        const key = event.key.toLowerCase();
        if (event.key === 'Escape') {
            closeMenu();
            closeChart();
        } else if ((event.ctrlKey || event.metaKey) && (key === 'z' || key === 'y')) {
            event.preventDefault();
            stepTrail(key === 'y' || event.shiftKey ? 1 : -1);
        }
    });
    document.getElementById('view').addEventListener('change', event => showView(Number(event.target.value)));
    document.getElementById('expand-changes').addEventListener('click', () => expandChanges(false));
    document.getElementById('open-anyway').addEventListener('click', () => {
        const action = warned;
        hideWarning();
        action();
    });
    document.getElementById('lens').addEventListener('change', event => setLens(event.target.value));
    document.getElementById('collapse').addEventListener('click', () => {
        state.expanded.clear();
        state.focus = null;
        autoExpand();
        render(FIT);
    });
    document.getElementById('fit').addEventListener('click', () => cy.fit(undefined, PADDING));

    window.archflowTheme = function (theme) {
        document.documentElement.dataset.theme = theme;
        cy.style(styles());
        if (chart.cy) {
            chart.cy.style(chartStyles());
        }
    };

    window.archflowMessage = function (text) {
        const message = document.getElementById('message');
        message.textContent = text || '';
        message.hidden = !text;
    };

    window.archflowLoad = function (data) {
        window.archflowMessage(null);
        const compared = data.beforeCommit + ' ' + data.commit;
        if (data.beforeCommit && (!state.data || state.data.beforeCommit + ' ' + state.data.commit !== compared)) {
            state.lens = 'review';
        }
        state.data = data;
        document.getElementById('title').textContent = data.title;
        document.getElementById('legend-diff').hidden = !data.beforeCommit;
        document.title = 'Archflow: ' + data.title;
        const select = document.getElementById('view');
        select.replaceChildren(...data.views.map((view, index) => new Option(VIEW_LABELS[view.view] || view.view, index)));
        const outline = data.views.findIndex(view => view.view === 'outline');
        select.value = String(Math.max(outline, 0));
        showView(Math.max(outline, 0));
        if (pendingReveal) {
            const [file, line, exact, following] = pendingReveal;
            pendingReveal = null;
            window.archflowReveal(file, line, exact, following);
        }
    };

    window.archflowReveal = function (file, line, exact, following) {
        if (!state.graph) {
            pendingReveal = [file, line, exact, following];
            return;
        }
        const target = locate(file, line, exact);
        if (!target) {
            return;
        }
        if (target.id !== state.selected || target.partner !== state.partner) {
            if (!following) {
                closeChart();
                record(target.id, target.partner, target.forced);
            }
            reveal(target.id, target.partner, target.forced);
        }
        const note = document.getElementById('details-note');
        note.textContent = target.note || '';
        note.hidden = !target.note;
    };

    function showView(index) {
        const graph = state.data.views[index];
        state.graph = graph;
        state.byId = new Map(graph.nodes.map(node => [node.id, node]));
        state.children = new Map();
        for (const node of graph.nodes) {
            const parent = parentOf(node.id);
            if (parent) {
                if (!state.children.has(parent)) {
                    state.children.set(parent, []);
                }
                state.children.get(parent).push(node.id);
            }
        }
        state.kinds = new Set(DEFAULT_KINDS[graph.view] || EDGE_KINDS);
        state.expanded = new Set([...state.expanded].filter(id => state.byId.has(id)));
        state.focus = state.expanded.has(state.focus) ? state.focus : null;
        if (state.expanded.size === 0) {
            autoExpand();
        }
        renderKinds();
        renderChanges();
        renderLens();
        if (!applyLens(false)) {
            state.lens = 'all';
            renderLens();
            applyLens(true);
        }
        cy.elements().remove();
        render(FIT);
        select(state.byId.has(state.selected) ? state.selected : null, state.byId.has(state.partner) ? state.partner : null, state.change);
        const charted = chart.id && state.byId.get(chart.id);
        if (charted && charted.chart) {
            openChart(chart.id);
        } else {
            closeChart();
        }
    }

    function parentOf(id) {
        const node = state.byId.get(id);
        return node && node.parent && state.byId.has(node.parent) ? node.parent : null;
    }

    function ancestors(id) {
        const chain = [];
        for (let current = parentOf(id); current; current = parentOf(current)) {
            chain.unshift(current);
        }
        return chain;
    }

    function representative(id) {
        for (const ancestor of ancestors(id)) {
            if (!state.expanded.has(ancestor)) {
                return ancestor;
            }
        }
        return id;
    }

    function isVisible(id) {
        return representative(id) === id;
    }

    function contains(outer, inner) {
        return ancestors(inner).includes(outer);
    }

    function autoExpand() {
        let level = state.graph.nodes.filter(node => !parentOf(node.id)).map(node => node.id);
        while (level.length === 1 && state.children.has(level[0])) {
            state.expanded.add(level[0]);
            level = state.children.get(level[0]);
        }
    }

    function commonPackage() {
        let common = null;
        let level = state.graph.nodes.filter(node => !parentOf(node.id));
        while (level.length === 1 && level[0].kind === 'package') {
            common = level[0].id;
            level = (state.children.get(common) || []).map(id => state.byId.get(id));
        }
        return common;
    }

    function short(text) {
        const common = commonPackage();
        return common ? text.split(common + '.').join('') : text;
    }

    function toggle(id) {
        if (!state.children.has(id)) {
            return;
        }
        if (state.expanded.has(id)) {
            state.expanded.delete(id);
            state.focus = state.focus && (state.focus === id || contains(id, state.focus)) ? parentOf(id) : state.focus;
        } else {
            state.expanded.add(id);
            state.focus = id;
        }
        render({anchor: id});
    }

    function panOnDrag(view) {
        let start = null;
        let last = null;
        let moved = false;
        view.on('tapstart', 'node, edge', event => {
            start = {...event.renderedPosition};
            last = start;
            moved = false;
        });
        view.on('tapdrag', event => {
            if (last) {
                const position = event.renderedPosition;
                view.panBy({x: position.x - last.x, y: position.y - last.y});
                last = {...position};
                moved = moved || Math.hypot(position.x - start.x, position.y - start.y) > TAP_DISTANCE;
            }
        });
        view.on('tapend', () => {
            last = null;
        });
        return {moved: () => moved};
    }

    function pick(element) {
        const id = element.isEdge() ? element.source().id() : element.id();
        const partner = element.isEdge() ? element.target().id() : null;
        record(id, partner, false);
        if (state.lens === 'detail') {
            reveal(id, partner, false);
        } else {
            select(id, partner, false);
        }
    }

    function setLens(lens) {
        const previous = state.lens;
        state.lens = lens;
        if (!applyLens(false)) {
            state.lens = previous;
            renderLens();
            return;
        }
        render(FIT);
    }

    function renderLens() {
        const picker = document.getElementById('lens');
        const review = picker.querySelector('option[value="review"]');
        review.disabled = state.graph.changes.length === 0;
        if (review.disabled && state.lens === 'review') {
            state.lens = 'all';
        }
        picker.value = state.lens;
    }

    function applyLens(confirmed) {
        const shown = lensNodes();
        if (shown && shown.size > OPEN_LIMIT && !confirmed) {
            const lens = state.lens;
            const name = document.querySelector('#lens option[value="' + lens + '"]').text;
            warn(name + ' shows ' + shown.size + ' nodes, the layout may take a while.', () => {
                state.lens = lens;
                renderLens();
                applyLens(true);
                render(FIT, true);
            });
            return false;
        }
        state.shown = shown;
        if (shown) {
            shown.forEach(id => ancestors(id).forEach(ancestor => state.expanded.add(ancestor)));
        }
        return true;
    }

    function lensNodes() {
        const core = new Set();
        if (state.lens === 'review') {
            state.graph.nodes.filter(node => node.change && node.change !== 'inside').forEach(node => core.add(node.id));
            state.graph.edges.filter(edge => edge.change && state.kinds.has(edge.kind)).forEach(edge => {
                core.add(edge.from);
                core.add(edge.to);
            });
        } else if (state.lens === 'detail' && state.selected) {
            [state.selected, state.partner].filter(Boolean).forEach(id => core.add(id));
        } else {
            return null;
        }
        const inCore = id => core.has(id) || ancestors(id).some(ancestor => core.has(ancestor));
        const shown = new Set(state.graph.nodes.filter(node => inCore(node.id)).map(node => node.id));
        for (const edge of state.graph.edges) {
            if (state.kinds.has(edge.kind) && inCore(edge.from)) {
                shown.add(edge.to);
            }
            if (state.kinds.has(edge.kind) && inCore(edge.to)) {
                shown.add(edge.from);
            }
        }
        [...shown].forEach(id => ancestors(id).forEach(ancestor => shown.add(ancestor)));
        return shown;
    }

    function isShown(id) {
        return !state.shown || state.shown.has(id);
    }

    function warn(text, action) {
        warned = action;
        document.getElementById('open-warning-text').textContent = text;
        document.getElementById('open-warning').hidden = false;
    }

    function hideWarning() {
        warned = null;
        document.getElementById('open-warning').hidden = true;
    }

    function pickedEdges() {
        return state.graph.edges.filter(edge => (state.kinds.has(edge.kind) || isPicked(edge))
            && representative(edge.from) === state.selected && representative(edge.to) === state.partner);
    }

    function places() {
        if (state.partner) {
            const seen = new Set();
            return pickedEdges()
                .flatMap(edge => (edge.sites || []).map(site => ({...site, commit: commitOfEdge(edge)})))
                .filter(place => !seen.has(place.file + ':' + place.line) && seen.add(place.file + ':' + place.line));
        }
        const node = state.byId.get(state.selected);
        return node && node.file ? [{file: node.file, line: node.line, kind: node.kind, text: '', commit: commitOf(node.id)}] : [];
    }

    function canOpen() {
        return typeof window.archflowOpen === 'function';
    }

    function openSource() {
        const found = places();
        if (canOpen() && found.length === 1) {
            open(found[0]);
        }
    }

    function open(place) {
        window.archflowOpen(place.file, place.line, place.commit);
    }

    function renderSource() {
        const button = document.getElementById('source');
        const count = places().length;
        button.hidden = !canOpen();
        button.disabled = count !== 1;
        button.title = count > 1 ? 'Several places, pick one in the details or by right click' : 'Open in the editor (Ctrl+click)';
    }

    function openMenu(element, position) {
        const menu = document.getElementById('menu');
        const items = [];
        if (element.isNode()) {
            const node = state.byId.get(element.id());
            items.push(menuItem('Open source', openSource, canOpen() && Boolean(node.file)));
            if (node.chart) {
                items.push(menuItem('Flowchart', () => openChart(node.id), true));
            }
            if (state.children.has(node.id)) {
                items.push(menuItem(state.expanded.has(node.id) ? 'Collapse' : 'Expand', () => toggle(node.id), true));
            }
        } else {
            const found = places();
            found.slice(0, MENU_PLACES).forEach(place => items.push(menuItem(fileName(place.file) + ':' + place.line + '  ' + siteLabel(place),
                () => open(place), canOpen())));
            if (found.length > MENU_PLACES) {
                items.push(menuItem('… ' + (found.length - MENU_PLACES) + ' more in the details', null, false));
            }
        }
        if (items.length === 0) {
            return;
        }
        menu.replaceChildren(...items);
        menu.hidden = false;
        const stage = document.getElementById('stage');
        menu.style.left = Math.min(position.x, stage.clientWidth - menu.offsetWidth - PADDING) + 'px';
        menu.style.top = Math.min(position.y, stage.clientHeight - menu.offsetHeight - PADDING) + 'px';
    }

    function menuItem(text, action, enabled) {
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = text;
        button.disabled = !enabled;
        button.addEventListener('click', () => {
            closeMenu();
            action();
        });
        return button;
    }

    function siteLabel(site) {
        return (site.kind + ' ' + short(site.text)).trim();
    }

    function fileName(file) {
        return file.substring(file.lastIndexOf('/') + 1);
    }

    function closeMenu() {
        document.getElementById('menu').hidden = true;
    }

    function locate(file, line, exact) {
        if (exact) {
            const edges = state.graph.edges.filter(edge => (edge.sites || [])
                .some(site => site.file === file && site.line === line && !DECLARING_SITES.has(site.kind)));
            const edge = edges.find(candidate => state.kinds.has(candidate.kind)) || edges[0];
            if (edge) {
                return {id: edge.from, partner: edge.to, forced: !state.kinds.has(edge.kind), note: null};
            }
            const node = innermost(state.graph.nodes, file, line);
            if (node) {
                return {id: node.id, partner: null, forced: false, note: elsewhere(file, line, node)};
            }
        }
        const type = state.graph.nodes.find(node => node.kind === 'type' && node.file === file);
        const note = exact ? null : 'The file in the editor differs from the shown commit, so only its type is selected.';
        return type ? {id: type.id, partner: null, forced: false, note} : null;
    }

    function innermost(nodes, file, line) {
        return nodes.filter(node => node.file === file && node.line <= line && line < node.line + node.lines)
            .reduce((best, node) => !best || node.lines < best.lines ? node : best, null);
    }

    function elsewhere(file, line, node) {
        const all = state.data.views.find(view => view.view === 'all-functions');
        if (!all || all === state.graph) {
            return null;
        }
        const exact = innermost(all.nodes, file, line);
        return exact && exact.id !== node.id ? short(exact.id) + ' is not in this view, All functions shows it.' : null;
    }

    function go(id, partner, change) {
        record(id, partner, change);
        reveal(id, partner, change);
    }

    function reveal(id, partner, change) {
        const ends = [id, partner].filter(Boolean);
        state.selected = id;
        state.partner = partner;
        state.change = change;
        const recentred = state.lens === 'detail' && applyLens(false);
        if (state.lens === 'detail' && !recentred) {
            select(id, partner, change);
            return;
        }
        const closed = ends.flatMap(ancestors).filter(ancestor => !state.expanded.has(ancestor));
        if (closed.length > 0 || change || recentred) {
            closed.forEach(ancestor => state.expanded.add(ancestor));
            state.focus = parentOf(id);
            render(recentred ? FIT : {center: ends});
        } else {
            zoomTo(ends.reduce((collection, end) => collection.union(cy.getElementById(end)), cy.collection()));
        }
        select(id, partner, change);
    }

    function isPicked(edge) {
        return state.change && state.partner !== null && edge.from === state.selected && edge.to === state.partner;
    }

    function record(id, partner, change) {
        const current = trail.entries[trail.index];
        if (current && current.id === id && current.partner === partner && current.change === change) {
            return;
        }
        trail.entries.splice(trail.index + 1);
        trail.entries.push({id, partner, change});
        if (trail.entries.length > TRAIL_LENGTH) {
            trail.entries.shift();
        }
        trail.index = trail.entries.length - 1;
        renderTrail();
    }

    function stepTrail(delta) {
        const entry = trail.entries[trail.index + delta];
        if (!entry) {
            return;
        }
        trail.index += delta;
        renderTrail();
        if (state.byId.has(entry.id)) {
            reveal(entry.id, state.byId.has(entry.partner) ? entry.partner : null, entry.change);
        }
    }

    function renderTrail() {
        document.getElementById('undo').disabled = trail.index <= 0;
        document.getElementById('redo').disabled = trail.index >= trail.entries.length - 1;
    }

    function expandChanges(confirmed) {
        const opened = new Set(state.expanded);
        for (const node of state.graph.nodes) {
            if (node.change && node.change !== 'inside') {
                ancestors(node.id).forEach(ancestor => opened.add(ancestor));
            }
        }
        const visible = state.graph.nodes.filter(node => ancestors(node.id).every(ancestor => opened.has(ancestor))).length;
        if (visible > OPEN_LIMIT && confirmed !== true) {
            warn('Opening all changes shows ' + visible + ' nodes, the layout may take a while.', () => expandChanges(true));
            return;
        }
        state.expanded = opened;
        render(FIT, true);
    }

    function startElkWorker() {
        const source = document.getElementById('elk-source');
        const api = Object.getPrototypeOf(ELK.prototype);
        const direct = api.layout;
        const worker = {elk: null, busy: false, ready: false, bypass: false};
        if (!source || typeof Worker !== 'function') {
            return worker;
        }
        // elk.bundled.js installs its worker message handler only once an ELK is constructed inside the worker.
        const url = URL.createObjectURL(new Blob([source.textContent, ';new ELK();'], {type: 'text/javascript'}));
        const create = () => {
            const elk = new ELK({workerFactory: () => new Worker(url)});
            elk.layout = direct.bind(elk);
            return elk;
        };
        api.layout = function (graph, options) {
            if (worker.bypass || !worker.ready) {
                return direct.call(this, graph, options);
            }
            if (worker.busy) {
                worker.elk.terminateWorker();
                worker.elk = create();
            }
            const elk = worker.elk;
            worker.busy = true;
            const plain = JSON.parse(JSON.stringify(graph, (key, value) => key === '_cyEle' ? undefined : value));
            return elk.layout(plain, options).then(result => {
                copyLayout(graph, result);
                return graph;
            }).finally(() => {
                if (worker.elk === elk) {
                    worker.busy = false;
                }
            });
        };
        try {
            worker.elk = create();
            worker.elk.layout({id: 'probe', children: []}).then(() => {
                worker.ready = true;
            });
        } catch (error) {
            worker.elk = null;
        }
        return worker;
    }

    function copyLayout(target, result) {
        for (const [key, value] of Object.entries(result)) {
            if ((key === 'children' || key === 'edges') && Array.isArray(target[key])) {
                value.forEach((item, index) => copyLayout(target[key][index], item));
            } else {
                target[key] = value;
            }
        }
    }

    function runDirect(layout) {
        elkWorker.bypass = true;
        try {
            layout.run();
        } finally {
            elkWorker.bypass = false;
        }
    }

    function render(keep, confirmed) {
        const count = state.graph.nodes.filter(node => isShown(node.id) && isVisible(node.id)).length;
        if (count > OPEN_LIMIT && cy.nodes().length <= OPEN_LIMIT && confirmed !== true) {
            warn('This view shows ' + count + ' nodes, the layout may take a while.', () => render(keep, true));
            return;
        }
        const anchor = keep && keep.anchor ? cy.getElementById(keep.anchor) : cy.collection();
        const before = anchor.nonempty() ? anchor.renderedPosition() : null;
        const previous = new Map(cy.nodes().map(node => [node.id(), {...node.position()}]));
        const elements = new Map();
        for (const node of state.graph.nodes) {
            if (isShown(node.id) && isVisible(node.id)) {
                const children = (state.children.get(node.id) || []).filter(isShown);
                const collapsed = children.length > 0 && !state.expanded.has(node.id);
                const classes = [node.kind, node.change].filter(Boolean).join(' ');
                const text = label(node) + (collapsed ? ' [' + children.length + ']' : '');
                const size = node.kind === 'method' ? FONT.method : FONT.other;
                const from = [node.id, ...ancestors(node.id).reverse()].find(id => previous.has(id));
                elements.set(node.id, {group: 'nodes', classes, position: from ? {...previous.get(from)} : {x: 0, y: 0},
                    data: {id: node.id, label: text, parent: parentOf(node.id) || undefined, width: textWidth(text, size) + 12, height: size * 1.3 + 12}});
            }
        }
        const edges = new Map();
        for (const edge of state.graph.edges) {
            if ((!state.kinds.has(edge.kind) && !isPicked(edge)) || !isShown(edge.from) || !isShown(edge.to)) {
                continue;
            }
            const source = representative(edge.from);
            const target = representative(edge.to);
            if (source === target || contains(source, target) || contains(target, source)) {
                continue;
            }
            const key = edge.kind + '|' + source + '|' + target;
            if (!edges.has(key)) {
                edges.set(key, {kind: edge.kind, source, target, changes: new Set(), count: 0});
            }
            const entry = edges.get(key);
            entry.count++;
            entry.changes.add(edge.change || 'same');
        }
        for (const [key, edge] of edges) {
            elements.set(key, {group: 'edges', data: {id: key, kind: edge.kind, source: edge.source, target: edge.target, width: Math.min(1 + Math.log2(edge.count), 5)},
                classes: [edge.kind, edgeChange(edge.changes), inFocus(edge) ? null : 'background'].filter(Boolean).join(' ')});
        }
        update(elements);
        const layout = cy.layout({
            name: 'elk', nodeDimensionsIncludeLabels: false, fit: false, animate: false, useElkEdgeGeometry: true, elkEdgeRadius: 6,
            nodeLayoutOptions: node => node.isParent() ? {'elk.padding': BOX_PADDING} : undefined,
            elk: {
                'algorithm': 'layered',
                'elk.direction': 'RIGHT',
                'elk.hierarchyHandling': 'INCLUDE_CHILDREN',
                'elk.edgeRouting': 'ORTHOGONAL',
                'elk.layered.spacing.nodeNodeBetweenLayers': 50,
                'elk.spacing.nodeNode': 20
            }
        });
        const current = ++layouts;
        document.getElementById('busy').hidden = false;
        layout.one('layoutstop', () => {
            if (current === layouts) {
                document.getElementById('busy').hidden = true;
            }
            keepViewport(keep, before);
        });
        layout.run();
        highlightSelected();
    }

    function update(elements) {
        cy.batch(() => {
            cy.elements().filter(element => !elements.has(element.id())).remove();
            const added = [];
            for (const [id, json] of elements) {
                const element = cy.getElementById(id);
                if (element.empty()) {
                    added.push(json);
                    continue;
                }
                if (element.data('label') !== json.data.label || element.data('width') !== json.data.width) {
                    element.data({label: json.data.label, width: json.data.width, height: json.data.height});
                }
                if (element.classes().join(' ') !== json.classes) {
                    element.classes(json.classes);
                }
            }
            cy.add(added);
        });
    }

    function highlightSelected() {
        const node = elementOf(state.selected);
        const partner = elementOf(state.partner);
        if (node.empty()) {
            return;
        }
        const edges = partner.nonempty() ? node.edgesTo(partner) : node.connectedEdges();
        node.union(partner).addClass('chosen');
        edges.addClass('highlight');
    }

    function elementOf(id) {
        return id ? cy.getElementById(id) : cy.collection();
    }

    function inFocus(edge) {
        const focus = state.focus;
        return !focus || focus === edge.source || focus === edge.target || contains(focus, edge.source) || contains(focus, edge.target);
    }

    function keepViewport(keep, before) {
        if (keep && keep.fit) {
            cy.fit(undefined, PADDING);
            return;
        }
        const ids = keep && keep.anchor ? [keep.anchor] : (keep && keep.center) || [];
        const element = ids.reduce((collection, id) => collection.union(cy.getElementById(id)), cy.collection());
        if (element.empty()) {
            return;
        }
        if (before) {
            const after = element.renderedPosition();
            cy.panBy({x: before.x - after.x, y: before.y - after.y});
        } else {
            zoomTo(element);
        }
    }

    function zoomTo(element) {
        const box = element.boundingBox();
        const zoom = Math.min(FOCUS_ZOOM, (cy.width() - 2 * PADDING) / box.w, (cy.height() - 2 * PADDING) / box.h);
        const center = {x: box.x1 + box.w / 2, y: box.y1 + box.h / 2};
        cy.animate({zoom, pan: {x: cy.width() / 2 - center.x * zoom, y: cy.height() / 2 - center.y * zoom}, duration: 300});
    }

    function edgeChange(changes) {
        if (changes.size === 1 && !changes.has('same')) {
            return [...changes][0];
        }
        return changes.has('added') || changes.has('removed') ? 'changed' : null;
    }

    function label(node) {
        const parent = parentOf(node.id);
        if (node.kind === 'method') {
            return parent && state.byId.get(parent).kind === 'method' ? node.name : node.id.substring(node.id.indexOf('#') + 1);
        }
        if (node.kind === 'package' && parent) {
            return node.id.substring(parent.length + 1);
        }
        return node.name;
    }

    function select(id, partner, change) {
        state.selected = id;
        state.partner = partner;
        state.change = change;
        cy.edges().filter(edge => !state.kinds.has(edge.data('kind')) && !isPicked({from: edge.source().id(), to: edge.target().id()})).remove();
        cy.elements().removeClass('chosen');
        cy.edges().removeClass('highlight');
        highlightSelected();
        renderSource();
        document.getElementById('details-note').hidden = true;
        const section = document.getElementById('details');
        const node = id && state.byId.get(id);
        if (!node) {
            section.hidden = true;
            return;
        }
        section.hidden = false;
        if (partner) {
            renderEdgeDetails();
            return;
        }
        document.getElementById('details-chart').hidden = !node.chart;
        document.getElementById('details-name').textContent = label(node);
        const meta = document.getElementById('details-meta');
        meta.replaceChildren();
        meta.append(node.kind + ' ' + short(node.id));
        if (node.change) {
            const change = document.createElement('div');
            change.className = node.change;
            change.textContent = node.change + (node.previousId ? ' (was ' + short(node.previousId) + ')' : '');
            meta.append(change);
        }
        if (node.file) {
            const place = document.createElement('div');
            place.append(sourceLink(node.file, node.line, node.file + ':' + node.line, commitOf(node.id)));
            meta.append(place);
        }
        const flow = document.getElementById('details-flow');
        flow.replaceChildren(...(node.flow || []).map(line => flowLine(node, line)));
    }

    function renderEdgeDetails() {
        document.getElementById('details-chart').hidden = true;
        document.getElementById('details-name').textContent = label(state.byId.get(state.selected)) + ' → ' + label(state.byId.get(state.partner));
        const edges = pickedEdges();
        document.getElementById('details-meta').replaceChildren(edges.length + (edges.length === 1 ? ' edge' : ' edges'));
        const items = [];
        for (const edge of edges) {
            const header = document.createElement('li');
            header.className = ['edge', edge.change].filter(Boolean).join(' ');
            header.textContent = edge.kind + ' ' + short(edge.from) + ' → ' + short(edge.to);
            items.push(header);
            for (const site of edge.sites || []) {
                const item = document.createElement('li');
                const number = document.createElement('span');
                number.className = 'line';
                number.append(sourceLink(site.file, site.line, String(site.line), commitOfEdge(edge)));
                item.append(number, '  ' + siteLabel(site));
                items.push(item);
            }
        }
        document.getElementById('details-flow').replaceChildren(...items);
    }

    function flowLine(node, line) {
        const item = document.createElement('li');
        const number = document.createElement('span');
        number.className = 'line';
        if (line.line > 0) {
            number.append(sourceLink(node.file, line.line, String(line.line), commitOf(node.id)));
        }
        item.append(number, '  '.repeat(line.depth));
        const target = line.text.startsWith('call ') ? line.text.substring(5) : null;
        if (target && state.byId.has(target)) {
            const link = document.createElement('a');
            link.textContent = short(line.text);
            link.addEventListener('click', () => go(target, null, false));
            item.append(link);
        } else {
            item.append(short(line.text));
        }
        return item;
    }

    function sourceLink(file, line, text, commit) {
        if (!canOpen() || !file) {
            return document.createTextNode(text);
        }
        const link = document.createElement('a');
        link.textContent = text;
        link.addEventListener('click', () => open({file, line, commit}));
        return link;
    }

    function commitOfEdge(edge) {
        const commit = edge.change === 'removed' ? state.data.beforeCommit : state.data.commit;
        return commit || null;
    }

    function commitOf(id) {
        const node = state.byId.get(id);
        const commit = node && node.change === 'removed' ? state.data.beforeCommit : state.data.commit;
        return commit || null;
    }

    function openChart(id) {
        const node = state.byId.get(id);
        if (!node || !node.chart) {
            return;
        }
        chart.id = id;
        document.getElementById('chart').hidden = false;
        showLegend(true);
        document.getElementById('chart-title').textContent = short(node.id);
        if (!chart.cy) {
            chart.cy = cytoscape({container: document.getElementById('chart-graph'), style: chartStyles(), autoungrabify: true, autounselectify: true});
            const chartDrag = panOnDrag(chart.cy);
            chart.cy.on('tap', 'node.link', event => {
                if (chartDrag.moved()) {
                    return;
                }
                closeChart();
                go(event.target.data('target'), null, false);
            });
        }
        chart.cy.resize();
        const elements = node.chart.boxes.map(box => ({group: 'nodes', data: {id: 'b' + box.id, label: boxLabel(box), target: box.target},
            position: {x: 0, y: box.id * CHART_ROW},
            classes: [box.kind.toLowerCase(), box.target && state.byId.has(box.target) ? 'link' : null].filter(Boolean).join(' ')}));
        node.chart.arrows.forEach((arrow, index) => elements.push({group: 'edges', data: {id: 'a' + index, source: 'b' + arrow.from, target: 'b' + arrow.to}}));
        chart.cy.batch(() => {
            chart.cy.elements().remove();
            chart.cy.add(elements);
        });
        const layout = chart.cy.layout({
            name: 'elk', nodeDimensionsIncludeLabels: false, fit: false, animate: false, useElkEdgeGeometry: true, elkEdgeRadius: 6,
            elk: {
                'algorithm': 'layered',
                'elk.direction': 'DOWN',
                'elk.edgeRouting': 'ORTHOGONAL',
                'elk.layered.cycleBreaking.strategy': 'MODEL_ORDER',
                'elk.layered.considerModelOrder.strategy': 'NODES_AND_EDGES',
                'elk.layered.spacing.nodeNodeBetweenLayers': 30,
                'elk.spacing.nodeNode': 20
            }
        });
        layout.one('layoutstop', fitChart);
        runDirect(layout);
    }

    function fitChart() {
        const view = chart.cy;
        view.fit(undefined, PADDING);
        if (view.zoom() > CHART_ZOOM.max) {
            view.zoom(CHART_ZOOM.max);
            view.center();
        } else if (view.zoom() < CHART_ZOOM.min) {
            const start = view.getElementById('b0').position();
            view.zoom(CHART_ZOOM.min);
            view.pan({x: view.width() / 2 - start.x * CHART_ZOOM.min, y: PADDING * 2 - start.y * CHART_ZOOM.min});
        }
    }

    function closeChart() {
        chart.id = null;
        document.getElementById('chart').hidden = true;
        showLegend(false);
    }

    function showLegend(forChart) {
        document.getElementById('legend-graph').hidden = forChart;
        document.getElementById('legend-chart').hidden = !forChart;
    }

    function boxLabel(box) {
        const text = short(box.text);
        return text.length > CHART_LABEL ? text.substring(0, CHART_LABEL - 1) + '…' : text;
    }

    function renderKinds() {
        const fieldset = document.getElementById('kinds');
        const present = new Set(state.graph.edges.map(edge => edge.kind));
        const boxes = EDGE_KINDS.filter(kind => present.has(kind)).map(kind => {
            const box = document.createElement('input');
            box.type = 'checkbox';
            box.checked = state.kinds.has(kind);
            box.addEventListener('change', () => {
                if (box.checked) {
                    state.kinds.add(kind);
                } else {
                    state.kinds.delete(kind);
                }
                applyLens(true);
                render();
            });
            const labelElement = document.createElement('label');
            labelElement.append(box, ' ' + kind);
            return labelElement;
        });
        const legend = document.createElement('legend');
        legend.textContent = 'Edges';
        fieldset.replaceChildren(legend, ...boxes);
    }

    function renderChanges() {
        const list = document.getElementById('changes');
        list.replaceChildren(...state.graph.changes.map(change => {
            const item = document.createElement('li');
            item.className = change.change;
            item.textContent = short(change.text);
            item.addEventListener('click', () => go(change.target, change.to || null, Boolean(change.to)));
            return item;
        }));
        document.getElementById('changes-count').textContent = '(' + state.graph.changes.length + ')';
        document.getElementById('changes-section').hidden = state.graph.changes.length === 0;
        document.getElementById('expand-changes').hidden = state.graph.changes.length === 0;
    }

    function textWidth(text, size) {
        measure.font = size + 'px system-ui, sans-serif';
        return measure.measureText(text).width;
    }

    function chartWidth(node, padding) {
        return textWidth(node.data('label'), FONT.other) + padding;
    }

    function palette() {
        const css = getComputedStyle(document.documentElement);
        return name => css.getPropertyValue(name).trim();
    }

    function chartStyles() {
        const color = palette();
        return [
            {selector: 'node', style: {
                'label': 'data(label)', 'font-size': FONT.other, 'color': color('--text'), 'text-valign': 'center', 'text-halign': 'center',
                'shape': 'round-rectangle', 'width': node => chartWidth(node, 16), 'height': 26, 'padding': '0px',
                'border-width': 1, 'border-color': color('--border'), 'background-color': color('--node')
            }},
            {selector: 'node.start, node.end', style: {'shape': 'ellipse', 'width': node => chartWidth(node, 30), 'color': color('--muted')}},
            {selector: 'node.branch', style: {'shape': 'diamond', 'width': node => chartWidth(node, 50), 'height': 40,
                'background-color': color('--type')}},
            {selector: 'node.arm', style: {'background-opacity': 0, 'border-width': 0, 'font-style': 'italic', 'color': color('--muted')}},
            {selector: 'node.loop', style: {'shape': 'hexagon', 'width': node => chartWidth(node, 50), 'background-color': color('--package')}},
            {selector: 'node.call', style: {'color': color('--muted')}},
            {selector: 'node.call.link', style: {'color': color('--link'), 'border-color': color('--link')}},
            {selector: 'node.return', style: {'color': color('--muted')}},
            {selector: 'node.throw', style: {'border-color': color('--removed'), 'color': color('--removed')}},
            {selector: 'edge', style: {
                'curve-style': 'bezier', 'width': 1, 'line-color': color('--muted'), 'target-arrow-color': color('--muted'),
                'target-arrow-shape': 'triangle', 'arrow-scale': 0.8
            }}
        ];
    }

    function styles() {
        const color = palette();
        const text = color('--text');
        const muted = color('--muted');
        const border = color('--border');
        return [
            {selector: 'node', style: {
                'label': 'data(label)', 'font-size': FONT.other, 'color': text, 'text-valign': 'center', 'text-halign': 'center',
                'shape': 'round-rectangle', 'width': 'data(width)', 'height': 'data(height)',
                'border-width': BORDER.width, 'border-color': border, 'border-opacity': BORDER.faint, 'background-color': color('--node')
            }},
            {selector: 'node.package', style: {'background-color': color('--package'), 'shape': 'cut-rectangle'}},
            {selector: 'node.type', style: {'background-color': color('--type')}},
            {selector: 'node.method', style: {'font-size': FONT.method, 'corner-radius': METHOD_RADIUS}},
            {selector: 'node', style: {'padding': '0px'}},
            {selector: ':parent', style: {
                'text-valign': 'top', 'text-halign': 'center', 'text-margin-y': -4, 'padding': '12px',
                'background-opacity': 0.5, 'font-weight': 'bold'
            }},
            {selector: 'node.chosen', style: {'border-color': text, 'border-opacity': 1, 'border-width': BORDER.chosen}},
            {selector: 'node.added', style: {'border-color': color('--added')}},
            {selector: 'node.removed', style: {'border-color': color('--removed')}},
            {selector: 'node.changed', style: {'border-color': color('--changed')}},
            {selector: 'node.inside', style: {'border-color': color('--inside')}},
            {selector: 'edge', style: {
                'curve-style': 'bezier', 'width': 'data(width)', 'line-color': muted, 'target-arrow-color': muted,
                'target-arrow-shape': 'triangle', 'arrow-scale': 0.8, 'opacity': 0.8
            }},
            {selector: 'edge.override', style: {'line-style': 'dotted', 'target-arrow-shape': 'triangle-backcurve', 'target-arrow-fill': 'hollow'}},
            {selector: 'edge.dependency', style: {'line-style': 'dashed', 'target-arrow-shape': 'vee'}},
            {selector: 'edge.added', style: {'line-color': color('--added'), 'target-arrow-color': color('--added')}},
            {selector: 'edge.removed', style: {'line-color': color('--removed'), 'target-arrow-color': color('--removed')}},
            {selector: 'edge.changed', style: {'line-color': color('--changed'), 'target-arrow-color': color('--changed')}},
            {selector: 'edge.background', style: {'opacity': 0.3, 'width': 1}},
            {selector: 'edge.highlight', style: {'opacity': 1, 'z-compound-depth': 'top',
                'underlay-color': color('--link'), 'underlay-opacity': HALO, 'underlay-padding': 2}}
        ];
    }

    if (window.ARCHFLOW_DATA) {
        window.archflowLoad(window.ARCHFLOW_DATA);
    }
})();
