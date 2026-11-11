# Archflow

Work plan for a tool that shows the architecture of a Java codebase as one interactive graph with levels, and shows
how that architecture changed between any two commits. It is a core library, a command line, a web viewer and an
IntelliJ plugin. The pilot is ALI's graph analysis (`ali/common/.../plugin/server/graph`, branch `ideas/graph`).

## Goal and ground rules

- **The diff is the product.** Showing the current architecture is the means. The purpose is to show what changed
  between two commits, and which commits changed it, without reading the code diff.
- **One graph with levels, not many diagrams.** A node is a black box that opens to the nodes it contains. A collapsed
  box whose inside changed says so, so a diff is read top-down by opening only what is marked.
- **The model holds everything, views are projections.** The extractor records every package, type, method, call,
  branch, loop and dependency. The outline, all functions, flow, dependencies and containment are projections of that
  one model. A new view is a new projection, never a change to the extractor.
- **Big methods are the outline.** The outline view keeps packages, types and every method of at least `minLines`
  lines (10 by default, set in the view options), each under its own name. A smaller method disappears and its calls
  are carried on to the outline nodes they reach. The all-functions view is the detailed one.
- **The analysed code needs no change.** Nothing is added to the analysed project: no annotation, no dependency, no
  build step.
- **Edges come only from the code.** Calls, dependencies and branches are read from the source, never declared.
- **Resolution goes as far as declared types.** The linker resolves a call from the source set alone: imports,
  supertypes, the declared types of fields, locals and parameters, and the return types of the source set's methods.
  There is no classpath and no compilation. Of several overloads, a call takes the first whose parameters accept the
  argument types it knows. A call that cannot be resolved this way is an unresolved call.
- **Branches only where they matter.** An `if`, `switch` or loop appears in a flow view only when it contains a call
  that the view shows. Its condition is shown as source text.
- **One extractor for every commit.** The working copy and the history go through the same code. Otherwise a diff
  shows the difference between two extractors, not between two commits.
- **Unknown is a value, not a failure.** A call whose target cannot be resolved stays in the model as an unresolved
  call and the view marks it. Extraction of the rest goes on. The outline hides unresolved calls together with
  external ones.
- **Outside calls are hidden, not lost.** A call into code outside the source set (the JDK, a library) is kept in the
  model as external. Views hide external calls unless their options say to show them.
- **Same input, same bytes.** A model is serialised in a stable order, so two runs on the same commit give identical
  output and a model can be cached by commit forever.
- **Extension points are designed, not grown.** A step whose code later steps extend (a projection, a kind of diff, a
  view) designs that place in the same step.
- **Bad architecture is raised, not worked around.** When a step shows code turning into spaghetti or a design that
  costs time or memory, it is pointed out with a proposal before the work goes on.
- **Tests read like the result.** Fixtures are small Java sources in the test tree. Each test compares a text dump of
  a model, a projection or a diff with the expected lines, one line per source line.

## Acceptance

- Pilot model: the dependencies between the pilot's classes show the layers that `GRAPH.md` describes (the graph knows
  no values, values know no bytecode, `Walker` asks its `Handler`s), and the flow of `Walker` shows its branches.
- Pilot history: on `ideas/graph`, the architecture log of `e80457fbe..4d9a713b0` in the outline view names, for each
  commit, the change its message describes. `4d9a713b0` shows `Context` and `Handler` added and `Walker` reaching
  instructions through `Handler`. A commit that changes no outline node and no edge between outline nodes shows
  nothing in the outline view.
- Plugin: a click on a node or on a listed change opens its source at its line. For an older commit it opens that
  commit's version of the file.

## Performance

- **No checkout.** A commit's sources are read from git objects, and the working copy is never touched.
- **Parse once per file content.** A file's syntax facts are cached by its content hash, so the model of the next
  commit parses only the files that changed. Facts hold names as written, never resolved targets, because a target
  depends on other files. Linking runs per commit and is cheap.
- **A commit's model is built once.** Models are cached by commit and source roots and are never invalidated.
- **Lay out only what is visible.** The viewer lays out the expanded part of the graph, not the whole model.
- **Measured as it grows.** From step 3 on, the CLI logs the time of a model and of a log over the pilot range.

## Architecture

- **Modules.** `core` (model, extraction, linking, projections, diff, git access), `cli`, `viewer` (HTML and JS
  resources), `plugin` (IntelliJ Platform). Everything except `plugin` runs without IntelliJ, and the plugin shows
  the same viewer in JCEF.
- **Layers.**
  - The model knows no Java source and no git.
  - The extractor (JavaParser) turns one file into syntax facts.
  - The linker turns the facts of a source set into a model.
  - Projections and the diff work on models only.
  - The viewer receives JSON and knows no Java. `core` prepares it (`ViewerData`): every view, and for a diff the
    nodes and edges of both models marked with their change, the list of changes and the flow of each method.
- **Model.**
  - Nodes: package, type and method, each with its parent and its source location. A method also has its number of
    lines. The initializers of fields and the initializer blocks of a type are its methods `<clinit>` (static) and
    `<instance-init>`, and the accessors of a record's components are its methods even when not written.
  - Edges: call, dependency, and override from a method to each method it overrides or implements. A call through an
    interface or a superclass targets the declared method; views carry it on to the overriding methods.
  - A lambda or method reference implements the one abstract method of a source interface when its target type shows
    in the syntax: a field or local initialized with it, directly or as an argument of the initializer's call
    (`List<Handler> HANDLERS = List.of(Reads::execute, ...)`), or a `return` of a method returning that type. A
    method reference then overrides the interface method; a lambda becomes a method inside its enclosing method
    (`Walker#<clinit>()$lambda1`) with its own flow. A lambda whose target is outside the source set stays inline.
  - The flow of each method: its body reduced to calls, branches, loops, returns and throws, with the source text of
    every condition.
- **Views.** A view is a projection from a model to a model (`View`), so the dump, the viewer and the diff work on
  any view alike.
  - Its options are `minLines` and whether external calls show.
  - A view keeps a branch or loop only when it holds a call the view shows. Returns and throws stay in the model only.
  - A call to a hidden method is carried on to the visible methods it reaches, through further hidden methods and
    from a hidden method to the methods that override it.
- **Node identity.** A node is identified by its qualified name, and a method also by its signature. The diff matches
  nodes by identity, so a renamed or moved node shows as removed and added. The one exception is a type that loses
  one method and gains one of the same name: the two are one method whose parameters changed.
- **The list of changes** leaves out the edges of an added or removed node; the diff itself keeps them for the viewer.
- **Edges at any level.** The viewer draws an edge between the nearest visible ancestors of its two ends. This mapping
  is the only graph logic in JS. Everything else is computed in `core` and tested there.
- **Focus.** The last expanded node is the focus: edges that neither start nor end inside it fade into the background,
  and the edges of the selected node stand out. ELK routes the edges orthogonally.
- **Navigation.** Nodes cannot be dragged; a drag anywhere pans, so the layout stays the one ELK made. A change of an
  edge selects both its ends and the edge between them. Selections form a trail with undo and redo.
- **Source.** Every edge carries the places in the code it comes from: the line of a call, the declaration of an
  override, and for a dependency the supertype, field, parameter, return type, call or import that makes it (the model
  keeps these as sites). Ctrl+click on a node or an edge with one place opens it; a click on an edge lists its places
  in the details; a right click offers the places and the actions of a node.
- **Flowchart.** A method opens as a control flow chart over the graph (double click on a method without children, or
  the button in its details). `core` builds it from the flow of the view (`FlowChart`): a box per call, branch, arm,
  loop, return and throw; an `if` without `else` falls through. A click on a call returns to the graph at its target.
- **Git** goes through JGit in `core`, for the CLI and the plugin alike.
- **Plugin.** A tool window shows the viewer page in JCEF (the bundled plugin `com.intellij.modules.jcef`) and sends
  it the data of the chosen commits through `archflowLoad`. The page calls back through `archflowOpen(file, line,
  commit)`: without a commit the file of the working copy opens, with one a read-only copy of that commit's version.
  Source paths and `minLines` are project settings (`.idea/archflow.xml`); models are cached in the IDE's system
  directory. The platform's own `slf4j` is used, so the plugin does not bundle one.
- **From the code to the graph.** "Show in Archflow" in the editor menu, or "Follow caret" in the tool window, sends
  `archflowReveal(file, line, exact, following)`. The page selects an edge with a place on that line, else the
  innermost node that holds the line, else the type of the file. Lines are exact for the working copy, or when the
  editor text equals the file in the shown commit; otherwise only the type is selected. Following the caret keeps
  the trail of selections as it is.

## Toolchain

- **IDE target:** IntelliJ IDEA 2026.3 (build 263), the current release. The plugin's `since-build` is `263`.
- **Java 25** for every module. It is the LTS release and the runtime that IntelliJ 2026.3 runs on (JBR 25).
- **Build:** a Gradle 9 wrapper, multi-module, with the IntelliJ Platform Gradle Plugin 2.x for `plugin`.
  `./gradlew :plugin:verifyPlugin` runs the Plugin Verifier against 2026.3; it is not part of `build` (its first run
  downloads about 1.3 GB).
- **Version** is `0.1.<number of commits>`, so every commit gives the IDE a newer plugin.
  `./gradlew :plugin:publishLocal` puts the plugin zip and `updatePlugins.xml` into `repository/` (ignored by git);
  the IDE lists that file as a custom plugin repository and offers each newer build as an update.
- **Libraries:** JavaParser (extraction), JGit (git), Gson (JSON), JUnit (tests), pinned in
  `gradle/libs.versions.toml`. Cytoscape.js with the ELK layout (viewer), bundled in `viewer` so the plugin works
  offline; their versions and licenses are listed next to them in `THIRD_PARTY.md`.

## Steps

- [x] 1. Model and extraction.
  - [x] 1a. Build: Gradle wrapper, the four modules, the toolchain above.
  - [x] 1b. Model: nodes, edges and flow in a stable order, with a text dump for tests.
  - [x] 1c. Extraction of structure: packages, types and methods with their location and number of lines.
  - [x] 1d. Extraction of flow: calls, branches, loops, returns and throws, with condition text.
  - [x] 1e. Linking: resolution as far as declared types, unresolved calls, dependencies.
  - [x] 1f. Calls through an interface or an overridden method: override edges, initializers as methods.
- [x] 2. Views: all functions, outline (methods by size), dependencies.
- [x] 3. Diff and history.
  - [x] 3a. The model as JSON for the cache and the viewer.
  - [x] 3b. Sources of a commit from git objects and of the working copy, facts cached by file, models by commit.
  - [x] 3c. Diff of two models: nodes added, removed, changed or changed inside, edges added or removed.
  - [x] 3d. Architecture log of a range of commits, and the CLI with its timings.
  - [x] 3e. A removed and an added method of the same type and name are one changed method, with its edges kept.
- [x] 4. Viewer: nested graph with expand and collapse, view switcher, diff overlay with the list of changes, and the
  CLI writing it as one self-contained page of a commit or of a diff.
- [x] 5. Pilot run on the ALI graph analysis (Acceptance: pilot model, pilot history).
- [ ] 6. Plugin: tool window with the viewer, navigation to source, commit picker, working copy kept current. Built
  and passing the Plugin Verifier; the acceptance (a click opens the source) is checked by hand in the IDE.

### Later
- `@Arch` annotations that name a node, set its level and give it an identity that survives renames and moves. Edges
  still come only from the code. The extractor recognises `@Arch` by its simple name in any package, so a project
  declares its own source-retention annotation and gains no dependency. One name on several types could form one
  logical node (instruction meaning = `Instructions` + `Arithmetic` + `Branches`).
- Rules checked against the model, such as the layers of `GRAPH.md` run as a test.
- Kotlin sources.
- Data flow of a method (where values are defined and used). Needs facts about variables that extraction does not
  collect yet.

## Open questions

- A lambda passed as an argument to a source method whose parameter is a source interface is not recognized as an
  implementation, because the parameter type is known only after linking. Needed when a project registers handlers
  through such a method.
- Source roots are path prefixes given to the CLI, so the analysed project needs no config. An old commit whose
  layout differs needs other prefixes. The pilot never moved; a log across such a move is not supported yet.
- A method whose name changes shows as removed and added. Only a changed parameter list is paired (step 3e).
