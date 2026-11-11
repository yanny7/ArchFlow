(function () {
    'use strict';

    const VIEW_LABELS = {'outline': 'Outline', 'all-functions': 'All functions', 'dependencies': 'Dependencies'};
    const EDGE_KINDS = ['call', 'override', 'dependency'];
    const DEFAULT_KINDS = {'outline': ['call', 'override'], 'all-functions': ['call', 'override'], 'dependencies': ['dependency']};
    const FIT = {fit: true};
    const PADDING = 20;
    const FOCUS_ZOOM = 1.5;
    const PULSE = {low: 0.08, high: 0.35, duration: 800};
    const REDUCED_MOTION = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const TRAIL_LENGTH = 100;
    const TAP_DISTANCE = 4;
    const CHART_LABEL = 70;
    const CHART_ROW = 60;
    const CHART_ZOOM = {min: 0.7, max: 1.5};

    cytoscape.use(cytoscapeElk);

    const state = {data: null, graph: null, byId: new Map(), children: new Map(), expanded: new Set(), kinds: new Set(), selected: null, partner: null, focus: null};
    const measure = document.createElement('canvas').getContext('2d');
    const cy = cytoscape({container: document.getElementById('graph'), style: styles(), autoungrabify: true, autounselectify: true});
    const chart = {cy: null, id: null};
    const pulse = {elements: cy.collection(), generation: 0};
    const trail = {entries: [], index: -1};

    const graphDrag = panOnDrag(cy);
    cy.on('tap', 'node', event => {
        if (graphDrag.moved()) {
            return;
        }
        record(event.target.id(), null);
        select(event.target.id(), null);
    });
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
            closeChart();
        } else if ((event.ctrlKey || event.metaKey) && (key === 'z' || key === 'y')) {
            event.preventDefault();
            stepTrail(key === 'y' || event.shiftKey ? 1 : -1);
        }
    });
    document.getElementById('view').addEventListener('change', event => showView(Number(event.target.value)));
    document.getElementById('expand-changes').addEventListener('click', expandChanges);
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
        state.data = data;
        document.getElementById('title').textContent = data.title;
        document.getElementById('legend-diff').hidden = !data.beforeCommit;
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
        state.focus = state.expanded.has(state.focus) ? state.focus : null;
        if (state.expanded.size === 0) {
            autoExpand();
        }
        renderKinds();
        renderChanges();
        render(FIT);
        select(state.byId.has(state.selected) ? state.selected : null, state.byId.has(state.partner) ? state.partner : null);
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
        view.on('tapstart', 'node', event => {
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

    function go(id, partner) {
        record(id, partner);
        reveal(id, partner);
    }

    function reveal(id, partner) {
        const ends = [id, partner].filter(Boolean);
        ends.forEach(end => ancestors(end).forEach(ancestor => state.expanded.add(ancestor)));
        state.focus = parentOf(id);
        render({center: ends});
        select(id, partner);
    }

    function record(id, partner) {
        const current = trail.entries[trail.index];
        if (current && current.id === id && current.partner === partner) {
            return;
        }
        trail.entries.splice(trail.index + 1);
        trail.entries.push({id, partner});
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
            reveal(entry.id, state.byId.has(entry.partner) ? entry.partner : null);
        }
    }

    function renderTrail() {
        document.getElementById('undo').disabled = trail.index <= 0;
        document.getElementById('redo').disabled = trail.index >= trail.entries.length - 1;
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
                classes: [edge.kind, edgeChange(edge.changes), inFocus(edge) ? null : 'background'].filter(Boolean).join(' ')});
        }
        cy.batch(() => {
            cy.elements().remove();
            cy.add(elements);
        });
        const layout = cy.layout({
            name: 'elk', nodeDimensionsIncludeLabels: true, fit: false, animate: false, useElkEdgeGeometry: true, elkEdgeRadius: 6,
            elk: {
                'algorithm': 'layered',
                'elk.direction': 'RIGHT',
                'elk.hierarchyHandling': 'INCLUDE_CHILDREN',
                'elk.edgeRouting': 'ORTHOGONAL',
                'elk.layered.spacing.nodeNodeBetweenLayers': 50,
                'elk.spacing.nodeNode': 20,
                'elk.padding': '[top=30,left=12,bottom=12,right=12]'
            }
        });
        layout.one('layoutstop', () => keepViewport(keep, before));
        layout.run();
        highlightSelected();
    }

    function highlightSelected() {
        stopPulse();
        const node = elementOf(state.selected);
        const partner = elementOf(state.partner);
        if (node.empty()) {
            return;
        }
        const edges = partner.nonempty() ? node.edgesTo(partner) : node.connectedEdges();
        node.union(partner).addClass('chosen');
        edges.addClass('highlight');
        if (!REDUCED_MOTION) {
            pulse.elements = partner.nonempty() ? node.union(partner).union(edges) : node;
            const generation = pulse.generation;
            pulse.elements.forEach(element => breathe(element, true, generation));
        }
    }

    function elementOf(id) {
        return id ? cy.getElementById(id) : cy.collection();
    }

    function breathe(element, up, generation) {
        element.animation({style: {'overlay-opacity': up ? PULSE.high : PULSE.low}, duration: PULSE.duration, easing: 'ease-in-out-sine'})
            .play().promise('completed').then(() => {
                if (pulse.generation === generation && !element.removed()) {
                    breathe(element, !up, generation);
                }
            });
    }

    function stopPulse() {
        pulse.generation++;
        const alive = pulse.elements.filter(element => !element.removed());
        alive.stop(true);
        alive.removeStyle('overlay-opacity');
        pulse.elements = cy.collection();
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

    function select(id, partner) {
        state.selected = id;
        state.partner = partner;
        cy.elements().removeClass('chosen');
        cy.edges().removeClass('highlight');
        highlightSelected();
        const section = document.getElementById('details');
        const node = id && state.byId.get(id);
        if (!node) {
            section.hidden = true;
            return;
        }
        section.hidden = false;
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
            link.addEventListener('click', () => go(target, null));
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
                go(event.target.data('target'), null);
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
            name: 'elk', nodeDimensionsIncludeLabels: true, fit: false, animate: false, useElkEdgeGeometry: true, elkEdgeRadius: 6,
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
        layout.run();
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
            item.addEventListener('click', () => go(change.target, change.to || null));
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

    function palette() {
        const css = getComputedStyle(document.documentElement);
        return name => css.getPropertyValue(name).trim();
    }

    function chartStyles() {
        const color = palette();
        return [
            {selector: 'node', style: {
                'label': 'data(label)', 'font-size': 11, 'color': color('--text'), 'text-valign': 'center', 'text-halign': 'center',
                'shape': 'round-rectangle', 'width': node => textWidth(node) + 16, 'height': 26, 'padding': '0px',
                'border-width': 1, 'border-color': color('--border'), 'background-color': color('--node')
            }},
            {selector: 'node.start, node.end', style: {'shape': 'ellipse', 'width': node => textWidth(node) + 30, 'color': color('--muted')}},
            {selector: 'node.branch', style: {'shape': 'diamond', 'width': node => textWidth(node) + 50, 'height': 40,
                'background-color': color('--type')}},
            {selector: 'node.arm', style: {'background-opacity': 0, 'border-width': 0, 'font-style': 'italic', 'color': color('--muted')}},
            {selector: 'node.loop', style: {'shape': 'hexagon', 'width': node => textWidth(node) + 50, 'background-color': color('--package')}},
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
                'label': 'data(label)', 'font-size': 11, 'color': text, 'text-valign': 'center', 'text-halign': 'center',
                'shape': 'round-rectangle', 'width': node => textWidth(node) + 12, 'height': node => fontSize(node) * 1.3 + 12,
                'border-width': 1, 'border-color': border, 'background-color': color('--node')
            }},
            {selector: 'node.package', style: {'background-color': color('--package')}},
            {selector: 'node.type', style: {'background-color': color('--type')}},
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
            {selector: 'node.chosen', style: {'overlay-color': color('--link'), 'overlay-opacity': 0.15, 'overlay-padding': 4}},
            {selector: 'edge', style: {
                'curve-style': 'bezier', 'width': 'data(width)', 'line-color': muted, 'target-arrow-color': muted,
                'target-arrow-shape': 'triangle', 'arrow-scale': 0.8, 'opacity': 0.8
            }},
            {selector: 'edge.override', style: {'line-style': 'dotted', 'target-arrow-shape': 'triangle-backcurve', 'target-arrow-fill': 'hollow'}},
            {selector: 'edge.dependency', style: {'line-style': 'dashed'}},
            {selector: 'edge.added', style: {'line-color': color('--added'), 'target-arrow-color': color('--added'), 'width': 2.5}},
            {selector: 'edge.removed', style: {'line-color': color('--removed'), 'target-arrow-color': color('--removed'), 'line-style': 'dashed', 'width': 2.5}},
            {selector: 'edge.changed', style: {'line-color': color('--changed'), 'target-arrow-color': color('--changed'), 'width': 2.5}},
            {selector: 'edge.background', style: {'opacity': 0.3, 'width': 1}},
            {selector: 'edge.highlight', style: {'opacity': 1, 'z-compound-depth': 'top', 'line-color': color('--link'), 'target-arrow-color': color('--link'),
                'overlay-color': color('--link'), 'overlay-padding': 4}}
        ];
    }

    if (window.ARCHFLOW_DATA) {
        window.archflowLoad(window.ARCHFLOW_DATA);
    }
})();
