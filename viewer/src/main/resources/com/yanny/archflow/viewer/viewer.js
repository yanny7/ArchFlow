(function () {
    'use strict';

    const VIEW_LABELS = {'outline': 'Outline', 'all-functions': 'All functions', 'dependencies': 'Dependencies'};
    const EDGE_KINDS = ['call', 'override', 'dependency'];
    const DEFAULT_KINDS = {'outline': ['call', 'override'], 'all-functions': ['call', 'override'], 'dependencies': ['dependency']};
    const FIT = {fit: true};
    const PADDING = 20;

    cytoscape.use(cytoscapeElk);

    const state = {data: null, graph: null, byId: new Map(), children: new Map(), expanded: new Set(), kinds: new Set(), selected: null};
    const measure = document.createElement('canvas').getContext('2d');
    const cy = cytoscape({container: document.getElementById('graph'), style: styles()});

    cy.on('tap', 'node', event => select(event.target.id()));
    cy.on('dbltap', 'node', event => toggle(event.target.id()));
    document.getElementById('view').addEventListener('change', event => showView(Number(event.target.value)));
    document.getElementById('expand-changes').addEventListener('click', expandChanges);
    document.getElementById('collapse').addEventListener('click', () => {
        state.expanded.clear();
        autoExpand();
        render(FIT);
    });
    document.getElementById('fit').addEventListener('click', () => cy.fit(undefined, PADDING));

    window.archflowTheme = function (theme) {
        document.documentElement.dataset.theme = theme;
        cy.style(styles());
    };

    window.archflowMessage = function (text) {
        const message = document.getElementById('message');
        message.textContent = text || '';
        message.hidden = !text;
    };

    window.archflowLoad = function (data) {
        window.archflowMessage(null);
        state.data = data;
        document.getElementById('title').textContent = data.title;
        document.title = 'Archflow: ' + data.title;
        const select = document.getElementById('view');
        select.replaceChildren(...data.views.map((view, index) => new Option(VIEW_LABELS[view.view] || view.view, index)));
        const outline = data.views.findIndex(view => view.view === 'outline');
        select.value = String(Math.max(outline, 0));
        showView(Math.max(outline, 0));
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
        if (state.expanded.size === 0) {
            autoExpand();
        }
        renderKinds();
        renderChanges();
        render(FIT);
        select(state.byId.has(state.selected) ? state.selected : null);
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
        } else {
            state.expanded.add(id);
        }
        render({anchor: id});
    }

    function reveal(id) {
        ancestors(id).forEach(ancestor => state.expanded.add(ancestor));
        render({center: id});
        select(id);
    }

    function expandChanges() {
        for (const node of state.graph.nodes) {
            if (node.change && node.change !== 'inside') {
                ancestors(node.id).forEach(ancestor => state.expanded.add(ancestor));
            }
        }
        render(FIT);
    }

    function render(keep) {
        const anchor = keep && keep.anchor ? cy.getElementById(keep.anchor) : cy.collection();
        const before = anchor.nonempty() ? anchor.renderedPosition() : null;
        const elements = [];
        for (const node of state.graph.nodes) {
            if (isVisible(node.id)) {
                const collapsed = state.children.has(node.id) && !state.expanded.has(node.id);
                const classes = [node.kind, node.change, collapsed ? 'collapsed' : null].filter(Boolean).join(' ');
                const count = collapsed ? ' [' + state.children.get(node.id).length + ']' : '';
                elements.push({group: 'nodes', data: {id: node.id, label: label(node) + count, parent: parentOf(node.id) || undefined}, classes});
            }
        }
        const edges = new Map();
        for (const edge of state.graph.edges) {
            if (!state.kinds.has(edge.kind)) {
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
            elements.push({group: 'edges', data: {id: key, source: edge.source, target: edge.target, width: Math.min(1 + Math.log2(edge.count), 5)},
                classes: [edge.kind, edgeChange(edge.changes)].filter(Boolean).join(' ')});
        }
        cy.batch(() => {
            cy.elements().remove();
            cy.add(elements);
        });
        const layout = cy.layout({
            name: 'elk', nodeDimensionsIncludeLabels: true, fit: false, animate: false,
            elk: {
                'algorithm': 'layered',
                'elk.direction': 'RIGHT',
                'elk.hierarchyHandling': 'INCLUDE_CHILDREN',
                'elk.layered.spacing.nodeNodeBetweenLayers': 50,
                'elk.spacing.nodeNode': 20,
                'elk.padding': '[top=30,left=12,bottom=12,right=12]'
            }
        });
        layout.one('layoutstop', () => keepViewport(keep, before));
        layout.run();
        if (state.selected && cy.getElementById(state.selected).nonempty()) {
            cy.getElementById(state.selected).select();
        }
    }

    function keepViewport(keep, before) {
        if (keep && keep.fit) {
            cy.fit(undefined, PADDING);
            return;
        }
        const id = keep && (keep.anchor || keep.center);
        const element = id ? cy.getElementById(id) : cy.collection();
        if (element.empty()) {
            return;
        }
        if (before) {
            const after = element.renderedPosition();
            cy.panBy({x: before.x - after.x, y: before.y - after.y});
        } else {
            cy.animate({center: {eles: element}, duration: 200});
        }
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

    function select(id) {
        state.selected = id;
        cy.elements().unselect();
        const section = document.getElementById('details');
        const node = id && state.byId.get(id);
        if (!node) {
            section.hidden = true;
            return;
        }
        cy.getElementById(id).select();
        section.hidden = false;
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
            place.append(sourceLink(node.file, node.line, node.file + ':' + node.line));
            meta.append(place);
        }
        const flow = document.getElementById('details-flow');
        flow.replaceChildren(...(node.flow || []).map(line => flowLine(node, line)));
    }

    function flowLine(node, line) {
        const item = document.createElement('li');
        const number = document.createElement('span');
        number.className = 'line';
        if (line.line > 0) {
            number.append(sourceLink(node.file, line.line, String(line.line)));
        }
        item.append(number, '  '.repeat(line.depth));
        const target = line.text.startsWith('call ') ? line.text.substring(5) : null;
        if (target && state.byId.has(target)) {
            const link = document.createElement('a');
            link.textContent = short(line.text);
            link.addEventListener('click', () => reveal(target));
            item.append(link);
        } else {
            item.append(short(line.text));
        }
        return item;
    }

    function sourceLink(file, line, text) {
        if (typeof window.archflowOpen !== 'function' || !file) {
            return document.createTextNode(text);
        }
        const link = document.createElement('a');
        link.textContent = text;
        link.addEventListener('click', () => window.archflowOpen(file, line, commitOf(state.selected)));
        return link;
    }

    function commitOf(id) {
        const node = state.byId.get(id);
        const commit = node && node.change === 'removed' ? state.data.beforeCommit : state.data.commit;
        return commit || null;
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
            item.addEventListener('click', () => reveal(change.target));
            return item;
        }));
        document.getElementById('changes-count').textContent = '(' + state.graph.changes.length + ')';
        document.getElementById('changes-section').hidden = state.graph.changes.length === 0;
        document.getElementById('expand-changes').hidden = state.graph.changes.length === 0;
    }

    function fontSize(node) {
        return node.hasClass('method') ? 10 : 11;
    }

    function textWidth(node) {
        measure.font = fontSize(node) + 'px system-ui, sans-serif';
        return measure.measureText(node.data('label')).width;
    }

    function styles() {
        const css = getComputedStyle(document.documentElement);
        const color = name => css.getPropertyValue(name).trim();
        const text = color('--text');
        const muted = color('--muted');
        const border = color('--border');
        const theme = document.documentElement.dataset.theme;
        const dark = theme ? theme === 'dark' : window.matchMedia('(prefers-color-scheme: dark)').matches;
        return [
            {selector: 'node', style: {
                'label': 'data(label)', 'font-size': 11, 'color': text, 'text-valign': 'center', 'text-halign': 'center',
                'shape': 'round-rectangle', 'width': node => textWidth(node) + 12, 'height': node => fontSize(node) * 1.3 + 12,
                'border-width': 1, 'border-color': border, 'background-color': dark ? '#2f3237' : '#ffffff'
            }},
            {selector: 'node.package', style: {'background-color': dark ? '#33363c' : '#eef0f3'}},
            {selector: 'node.type', style: {'background-color': dark ? '#25364a' : '#e3eefb'}},
            {selector: 'node.method', style: {'font-size': 10}},
            {selector: 'node', style: {'padding': '0px'}},
            {selector: 'node.collapsed', style: {'border-width': 2}},
            {selector: ':parent', style: {
                'text-valign': 'top', 'text-halign': 'center', 'text-margin-y': -4, 'padding': '12px',
                'background-opacity': 0.5, 'font-weight': 'bold'
            }},
            {selector: 'node.added', style: {'border-color': color('--added'), 'border-width': 3}},
            {selector: 'node.removed', style: {'border-color': color('--removed'), 'border-width': 3, 'border-style': 'dashed', 'opacity': 0.75}},
            {selector: 'node.changed', style: {'border-color': color('--changed'), 'border-width': 3}},
            {selector: 'node.inside', style: {'border-color': color('--changed'), 'border-width': 2, 'border-style': 'dashed'}},
            {selector: 'node:selected', style: {'overlay-color': color('--link'), 'overlay-opacity': 0.15, 'overlay-padding': 4}},
            {selector: 'edge', style: {
                'curve-style': 'bezier', 'width': 'data(width)', 'line-color': muted, 'target-arrow-color': muted,
                'target-arrow-shape': 'triangle', 'arrow-scale': 0.8, 'opacity': 0.8
            }},
            {selector: 'edge.override', style: {'line-style': 'dotted', 'target-arrow-shape': 'triangle-backcurve', 'target-arrow-fill': 'hollow'}},
            {selector: 'edge.dependency', style: {'line-style': 'dashed'}},
            {selector: 'edge.added', style: {'line-color': color('--added'), 'target-arrow-color': color('--added'), 'width': 2.5}},
            {selector: 'edge.removed', style: {'line-color': color('--removed'), 'target-arrow-color': color('--removed'), 'line-style': 'dashed', 'width': 2.5}},
            {selector: 'edge.changed', style: {'line-color': color('--changed'), 'target-arrow-color': color('--changed'), 'width': 2.5}}
        ];
    }

    if (window.ARCHFLOW_DATA) {
        window.archflowLoad(window.ARCHFLOW_DATA);
    }
})();
