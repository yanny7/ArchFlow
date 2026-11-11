# Archflow

Archflow shows the architecture of a Java codebase as one interactive graph with levels, and how that architecture
changed between two commits. It is a core library, a command line, a web viewer and an IntelliJ plugin. The pilot
codebase is ALI's graph analysis (`ali/common/.../plugin/server/graph`, branch `ideas/graph`).

## Commands

- `./gradlew build` compiles every module and runs the tests.
- `./gradlew :cli:installDist` installs the CLI as `cli/build/install/archflow/bin/archflow`. Run it without arguments
  for its usage. `html` writes one self-contained page (`model`, `diff` and `log` print text).
- `./gradlew :plugin:runIde` starts an IDE with the plugin.
- `./gradlew :plugin:verifyPlugin` runs the Plugin Verifier against IntelliJ IDEA 2026.3. It is not part of `build`;
  its first run downloads about 1.3 GB.
- `./gradlew :plugin:publishLocal` puts the plugin zip and `updatePlugins.xml` into `repository/` (ignored by git).
  The IDE lists `repository/updatePlugins.xml` as a custom plugin repository and offers each newer build as an update.
  The version is `0.1.<number of commits>`, so only a committed change reaches the IDE as an update.

## Toolchain

- Java 25 for every module; IntelliJ IDEA 2026.3 (build 263) is the IDE target and the plugin's `since-build`.
- Gradle 9 wrapper, multi-module, Groovy DSL; the IntelliJ Platform Gradle Plugin 2.x builds `plugin`.
- JavaParser (extraction), JGit (git), Gson (JSON), JUnit (tests), pinned in `gradle/libs.versions.toml`.
- Cytoscape.js, ELK (elkjs) and cytoscape-elk are vendored in `viewer/src/main/resources/.../lib`, so the plugin works
  offline; their versions and licenses are in `THIRD_PARTY.md` next to them.

## Ground rules

- **The diff is the product.** Showing the current architecture is the means; the purpose is to show what changed
  between two commits, and which commits changed it, without reading the code diff.
- **One graph with levels.** A node is a box that opens to the nodes it contains. A collapsed box whose inside changed
  says so, so a diff reads top-down by opening only what is marked.
- **The model holds everything, views are projections.** The extractor records every package, type, method, call,
  branch, loop and dependency. A new view is a new projection, never a change to the extractor.
- **The analysed code needs no change.** No annotation, dependency or build step is added to the analysed project.
- **Edges come only from the code.** Calls, dependencies and branches are read from the source, never declared.
- **Resolution goes as far as declared types.** The linker resolves a call from the source set alone: imports,
  supertypes, the declared types of fields, locals and parameters, and the return types of the source set's methods.
  There is no classpath and no compilation. Of several overloads, a call takes the first whose parameters accept the
  argument types it knows.
- **Unknown is a value, not a failure.** A call that cannot be resolved stays in the model as unresolved, and a call
  into code outside the source set stays as external. Extraction of the rest goes on.
- **One extractor for every commit.** The working copy and the history go through the same code.
- **Same input, same bytes.** A model is serialised in a stable order, so a model is cached by commit forever.
- **Core computes, the viewer shows.** `core` computes every view, diff, flow, flowchart and place in the code, and is
  tested there. The viewer's own graph logic is only the mapping of edges to the nearest visible ancestors and the
  visibility filters (focus and Show).
- **Bad architecture is raised, not worked around.** When code turns into spaghetti or a design costs time or memory,
  it is pointed out with a proposal before the work goes on.

## Architecture

- **Modules.** `core` (model, extraction, linking, projections, diff, git, viewer data), `cli`, `viewer` (the page:
  HTML, CSS, JS and the vendored libraries, assembled by `ViewerPage`), `plugin` (IntelliJ Platform). Everything
  except `plugin` runs without IntelliJ.
- **Layers in `core`** (`com.yanny.archflow`):
  - `model` knows no Java source and no git: nodes, edges, flows, sites, `FlowText`, `FlowChart`, `ModelDump`.
  - `facts` and `extract`: the extractor (JavaParser) turns one file into syntax facts. Facts hold names as written,
    never resolved targets, because a target depends on other files.
  - `link`: the linker turns the facts of a source set into a model (`TypeIndex`, `CallResolver`, `Linker`).
  - `view` and `diff` work on models only. `json` serialises a model. `git` reads sources through JGit. `history`
    caches facts and models and builds the architecture log. `present` turns models into the viewer's JSON
    (`ViewerData`).
- **Model.**
  - Nodes: package, type and method, each with its parent and source location; a method also has its number of
    lines. Field initializers and initializer blocks are the methods `<clinit>` (static) and `<instance-init>`; a
    record's component accessors are methods even when not written.
  - Edges: call, dependency, and override from a method to each method it overrides or implements. A call through an
    interface or a superclass targets the declared method; views carry it on to the overriding methods.
  - A lambda or method reference implements the one abstract method of a source interface when its target type shows
    in the syntax: a field or local initialised with it, directly or as an argument of the initializer's call, or a
    `return` of a method returning that type. A method reference then overrides the interface method; a lambda becomes
    a method inside its enclosing method (`Walker#<clinit>()$lambda1`) with its own flow.
  - Flow of each method: its body reduced to calls, branches, loops, returns and throws, with the source text of every
    condition.
  - Sites: every dependency edge keeps the places in the code that make it, each with its kind (call, field, import,
    parameter, return, supertype). Call and override places come from the flows and node locations.
- **Views** are projections from a model to a model (`View`): all functions, outline and dependencies.
  - Options: `minLines` (default 10) and whether external calls show.
  - The outline keeps packages, types and every method of at least `minLines` lines. A call to a hidden method is
    carried on to the visible methods it reaches, through further hidden methods and from a hidden method to the
    methods that override it. The outline hides unresolved calls unless external calls show.
  - A view keeps a branch or loop only when it holds a call the view shows. Returns and throws stay in the model only.
- **Diff.** Nodes are matched by identity: the qualified name, and for a method also the signature. A renamed or moved
  node is removed and added; the one exception is a type that loses one method and gains one of the same name, which
  is one method whose parameters changed. Nodes are added, removed, changed or changed inside; edges added or removed.
  The list of changes leaves out the edges of an added or removed node; the diff itself keeps them for the viewer.
- **Performance.** A commit's sources come from git objects; the working copy is never checked out. Facts are cached
  by file content, models by commit and source roots (bump `ModelSource.FORMAT` as its comment says).

## Viewer

- The page receives everything as JSON and knows no Java. Hooks: `archflowLoad(data)`, `archflowTheme(theme)`,
  `archflowMessage(text)`, `archflowReveal(file, line, exact, following)`; the host may provide `archflowOpen(file,
  line, commit)`. Data reaches the page through `ViewerPage.parse` (a `JSON.parse` of a string literal).
- An edge is drawn between the nearest visible ancestors of its two ends; edges merge between collapsed boxes.
- Each fact has its own visual channel: node kind by fill and shape, edge kind by line pattern and arrow, change by
  color only (node border, edge line), selection by a full border on nodes and a halo on edges. Unselected node
  borders are faint. A collapsed node shows `[n]` children. The legend explains every style.
- The last expanded node is the focus: edges that neither start nor end inside it fade.
- Show: Everything, Changes and neighbours (the changed nodes and the ends of changed edges) or Selection and
  neighbours (picking a neighbour moves the centre). Neighbours are one edge away over the enabled edge kinds; a
  selected box counts with everything inside it. Loading a diff of other commits than the shown ones switches to
  Changes and neighbours.
- Nodes cannot be dragged; a drag anywhere pans. Expanding keeps the zoom and holds the toggled node in place.
  Selections form a trail with undo and redo (buttons top right, Ctrl+Z, Ctrl+Shift+Z or Ctrl+Y).
- Source: Ctrl+click or the Source button opens a node, or an edge with one place; a click on an edge lists its places
  in the details; a right click offers places and node actions. A picked change of an edge selects both ends and draws
  that edge even when its kind is switched off.
- A method opens as a flowchart over the graph (double click on a method without children, or the details button).
- Layout: ELK lays out only the visible part, in a web worker (on the page's thread when no worker starts); a newer
  layout cancels an older one, and a render changes only the elements that appear or go. Any render of over 1000 nodes
  (Everything with many boxes open, Open changes, Show) asks first; a loaded diff whose Changes and neighbours would
  exceed it opens on Everything and offers it. Nothing animates continuously, since every frame redraws the whole
  canvas.
- Cytoscape's style cache is cleared only for the restyled element and its edges (`cheapStyleCache`), so a style on a
  box must never change what its contents inherit (visibility, opacity, display).
- The viewer has no automated tests; check it in a browser with a page from `archflow html`.

## Plugin

- A tool window shows the viewer page in JCEF (the bundled plugin `com.intellij.modules.jcef`) with a toolbar: Single
  commit, From (none or a commit), To (working copy or a commit), source path prefixes, `minLines`, Show and Follow
  caret. Single commit hides From and compares To with its first parent, or the working copy with HEAD.
- A commit is picked from a popup that searches every commit reachable from HEAD by hash prefix and message, loads
  them a page at a time as the list scrolls, and also accepts any revision git resolves (branch, tag, `HEAD~3`).
- "Show in Archflow" in the VCS log's context menu (the bundled plugin `com.intellij.modules.vcs`) shows one selected
  commit as a single commit, and several as the range from the lowest selected row to the highest.
- `archflowOpen` opens the working copy's file, or a read-only copy of the commit's version; a removed node or edge
  opens from the From commit.
- With To on the working copy, a saved `.java` file under the project rebuilds the model after 1.5 s.
- "Show in Archflow" (editor menu) and Follow caret call `archflowReveal`: the page selects an edge used on that line
  (a call, a field type or an import), else the innermost node holding the line, else the type of the file. A
  declaration line selects its method or type. Lines are exact for the working copy, or when the editor text equals
  the file in the To commit; otherwise only the type is selected. Following the caret leaves the trail as it is.
- Settings (paths, `minLines`, follow caret) are in `.idea/archflow.xml`; models are cached in the IDE's system
  directory. The platform's `slf4j` is used, so the plugin bundles none.

## Tests

- Fixtures are small Java sources in `core/src/test/resources/fixtures/<name>`. A test compares a text dump of a model,
  a projection, a diff or viewer data with the expected lines, one line per source line.

## Not supported

- A lambda passed as an argument to a source method whose parameter is a source interface is not recognised as an
  implementation, because the parameter type is known only after linking.
- Source roots are path prefixes, so a log across a commit that moves the sources needs other prefixes and is not
  supported.
- A method whose name changes shows as removed and added; only a changed parameter list is paired.
- Kotlin sources, data flow (where values are defined and used), rules checked against the model, and `@Arch`
  annotations naming nodes are not implemented.
