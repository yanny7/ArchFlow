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
  lines, each under its own name. A smaller method disappears and its calls are carried on to the outline nodes they
  reach. The all-functions view is the detailed one.
- **The analysed code needs no change.** Nothing is added to the analysed project: no annotation, no dependency, no
  build step.
- **Edges come only from the code.** Calls, dependencies and branches are read from the source, never declared.
- **Resolution goes as far as declared types.** The linker resolves a call from the source set alone: imports,
  supertypes, the declared types of fields, locals and parameters, and the return types of the source set's methods.
  There is no classpath and no compilation. A call that cannot be resolved this way is an unresolved call.
- **Branches only where they matter.** An `if`, `switch` or loop appears in a flow view only when it contains a call
  that the view shows. Its condition is shown as source text.
- **One extractor for every commit.** The working copy and the history go through the same code. Otherwise a diff
  shows the difference between two extractors, not between two commits.
- **Unknown is a value, not a failure.** A call whose target cannot be resolved stays in the model as an unresolved
  call and the view marks it. Extraction of the rest goes on.
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
  - The viewer receives JSON and knows no Java.
- **Model.**
  - Nodes: package, type and method, each with its parent and its source location. A method also has its number of
    lines.
  - Edges: call and dependency.
  - The flow of each method: its body reduced to calls, branches, loops, returns and throws, with the source text of
    every condition.
- **Node identity.** A node is identified by its qualified name, and a method also by its signature. The diff matches
  nodes by identity, so a renamed or moved node shows as removed and added.
- **Edges at any level.** The viewer draws an edge between the nearest visible ancestors of its two ends. This mapping
  is the only graph logic in JS. Everything else is computed in `core` and tested there.
- **Git** goes through JGit in `core`, for the CLI and the plugin alike.

## Toolchain

- **IDE target:** IntelliJ IDEA 2026.3 (build 263), the current release. The plugin's `since-build` is `263`.
- **Java 25** for every module. It is the LTS release and the runtime that IntelliJ 2026.3 runs on (JBR 25).
- **Build:** a Gradle 9 wrapper, multi-module, with the IntelliJ Platform Gradle Plugin 2.x for `plugin`.
- **Libraries:** JavaParser (extraction), JGit (git), JUnit (tests), pinned in `gradle/libs.versions.toml`.
  Cytoscape.js with the ELK layout (viewer), bundled in `viewer` so the plugin works offline.

## Steps

- [ ] 1. Model and extraction.
  - [x] 1a. Build: Gradle wrapper, the four modules, the toolchain above.
  - [x] 1b. Model: nodes, edges and flow in a stable order, with a text dump for tests.
  - [ ] 1c. Extraction of structure: packages, types and methods with their location and number of lines.
  - [ ] 1d. Extraction of flow: calls, branches, loops, returns and throws, with condition text.
  - [ ] 1e. Linking: resolution as far as declared types, unresolved calls, dependencies.
  - [ ] 1f. Calls through an interface or an overridden method (open question below).
- [ ] 2. Views: all functions, outline (methods by size), dependencies.
- [ ] 3. Diff: models of commits from git, the model as JSON for the cache and the viewer, diff of two models,
  architecture log, CLI.
- [ ] 4. Viewer: nested graph with expand and collapse, view switcher, diff overlay with the list of changes.
- [ ] 5. Pilot run on the ALI graph analysis (Acceptance: pilot model, pilot history).
- [ ] 6. Plugin: tool window with the viewer, navigation to source, commit picker, working copy kept current.

### Later
- `@Arch` annotations that name a node, set its level and give it an identity that survives renames and moves. Edges
  still come only from the code. The extractor recognises `@Arch` by its simple name in any package, so a project
  declares its own source-retention annotation and gains no dependency. One name on several types could form one
  logical node (instruction meaning = `Instructions` + `Arithmetic` + `Branches`).
- Rules checked against the model, such as the layers of `GRAPH.md` run as a test.
- A static HTML export of one diff, to attach to a review.
- Kotlin sources.

## Open questions

- Calls through an interface (`Walker` calls its handlers through `Handler`): an edge to the interface method only, or
  also to its implementations in the source set. Decided in step 1f, and the pilot needs the latter.
- The default of `minLines`, and whether size alone gives a useful outline. An interface method has no body, so the
  outline shows `Handler` only as a type. Shows in step 5.
- Source roots of an old commit whose layout differs from the current config. The config is read from that commit;
  whether that is enough shows in step 3.
- Renamed methods show as removed and added. Whether a heuristic match is worth it shows in step 5.
