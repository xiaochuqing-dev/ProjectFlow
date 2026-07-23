# Third-party notices

ProjectFlow V3.6 directly uses the following additional libraries.

## SCIP Java Protocol

Artifact: `com.sourcegraph:scip-java-proto:0.12.3`

Project: https://github.com/sourcegraph/scip-java

Purpose: consume the official SCIP protobuf schema for language-agnostic Symbol, Definition, Reference and Relationship data.

License: Apache License 2.0.

ProjectFlow does not copy or reimplement language indexers. Each language-specific SCIP indexer remains an external tool with its own installation and runtime requirements.

## JGraphT Core

Artifact: `org.jgrapht:jgrapht-core:1.5.3`

Project: https://github.com/jgrapht/jgrapht

Purpose: PageRank and Label Propagation over the bounded SCIP-derived file dependency graph.

License: Eclipse Public License 2.0 or GNU Lesser General Public License 2.1.

ProjectFlow uses the Maven dependency without modifying or copying JGraphT source code.

JGraphT Core brings the following pinned transitive runtime dependencies:

- `org.jheaps:jheaps:0.14`, Apache License 2.0, https://www.jheaps.org
- `org.apfloat:apfloat:1.14.0`, MIT License, https://www.apfloat.org

The four pinned JARs add approximately 2.30 MiB to the resolved runtime before executable-JAR compression. They are pure Java and add no native binary or operating-system installation requirement.
