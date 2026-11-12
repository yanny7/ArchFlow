# Archflow

Archflow shows the architecture of a Java codebase as one interactive graph with levels, and how that architecture
changed between two commits, without reading the code diff. It comes as a core library, a command line, a web viewer
and an IntelliJ plugin.

## Features

- **One graph with levels.** Packages, types and methods are boxes that open to what they contain. Edges are calls,
  type dependencies and overrides, read from the source; an edge between closed boxes is merged into one.
- **Diff between commits.** Nodes and edges are marked added, removed, changed or changed inside, so a diff reads
  top-down by opening only what is marked. Show narrows the graph to the changes and their neighbours.
- **Flowcharts.** A method opens as a flowchart of its calls, branches, loops, returns and throws. For a changed
  method, the flowchart merges both versions and marks the added and removed steps.
- **Views.** All functions, an outline that hides short methods and carries calls through them, and type
  dependencies.
- **No change to the analysed code.** No annotation, dependency or build step; sources are read from git objects,
  so no commit is ever checked out.
- **IntelliJ plugin.** A tool window with the graph, a commit picker, "Show in Archflow" from the editor and from the
  VCS log, Follow caret, and Ctrl+click or F4 to open the source of a node or edge.

## Requirements

- Java 25
- IntelliJ IDEA 2026.3 or newer for the plugin

## Build

```sh
./gradlew build                  # compile every module and run the tests
./gradlew :cli:installDist       # install the CLI as cli/build/install/archflow/bin/archflow
./gradlew :plugin:runIde         # start an IDE with the plugin
./gradlew :plugin:publishLocal   # put the plugin zip and updatePlugins.xml into repository/
```

To install the plugin, add `repository/updatePlugins.xml` as a custom plugin repository in the IDE
(Settings → Plugins → Manage Plugin Repositories), or install the zip from disk.

## Command line

```text
archflow <command> [options]
  model [REV]          the view of a commit, or of the working copy without REV
  diff FROM [TO]       changes of the view from FROM to TO, or to the working copy without TO
  log FROM..TO         the commits of a range that change the view, with their changes
  html [REV | FROM TO] a page with every view of REV, or of the working copy, or of the diff FROM TO
```

For example, `archflow html HEAD~1 HEAD --path src/main/java` writes `archflow.html`, a self-contained page with
the diff of the last commit. Run `archflow` without arguments for every option.

## Modules

| Module   | Content                                                                          |
|----------|----------------------------------------------------------------------------------|
| `core`   | model, extraction (JavaParser), linking, views, diff, git (JGit), viewer data    |
| `cli`    | the command line                                                                 |
| `viewer` | the page: HTML, CSS, JavaScript and the vendored Cytoscape.js and ELK            |
| `plugin` | the IntelliJ Platform plugin                                                     |

## Limitations

- Calls are resolved from the source set alone, as far as declared types go: there is no classpath and no
  compilation. A call that cannot be resolved stays in the graph as unresolved.
- A method whose name changes shows as removed and added.
- Only Java sources are read.

## License

[MIT](LICENSE). The vendored viewer libraries keep their own licenses (MIT and EPL-2.0), listed in
[THIRD_PARTY.md](viewer/src/main/resources/com/yanny/archflow/viewer/lib/THIRD_PARTY.md).
