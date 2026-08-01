# Third-party notices

ProjectFlow V3.7.5 continues to use the following V3.6 libraries. Product-constitution enforcement, Prompt contract v3, Context Package v2, candidate work-result writes, local revalidation and V3.7.5 evaluation changes add no third-party runtime or test dependency and copy no community source code.

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

## Design references not redistributed

The V3.7.1/V3.7.2 design reviews consulted mature public implementations but copied no source and added no dependency. The full V3.7.2 adoption/rejection record is in `docs/projectflow-v3.7.2-open-source-research.md`.

- Aider RepoMap, Apache/MIT ecosystem implementation of token-budgeted repository selection and PageRank: https://github.com/paul-gauthier/aider/blob/main/aider/repomap.py
- Gitleaks, MIT, rule/keyword/entropy secret detection: https://github.com/gitleaks/gitleaks
- Yelp detect-secrets, Apache License 2.0, plugin and entropy-based secret detection: https://github.com/Yelp/detect-secrets
- TruffleHog, AGPL-3.0, verified secret-detector design; reference only: https://github.com/trufflesecurity/trufflehog
- Sourcegraph SCIP and official language indexers, Apache License 2.0: https://github.com/sourcegraph/scip, https://github.com/sourcegraph/scip-java
- PyDriller bounded Git traversal and CodeScene behavioral-hotspot concepts: https://pydriller.readthedocs.io/en/latest/ and https://codescene.io/docs/guides/technical/hotspots.html

GitNexus was reviewed only as a product reference and was not reused because its current license requires commercial licensing for commercial use. CodeBoarding and RepoAgent were reviewed as architecture/documentation references; their runtime and model orchestration were not embedded.
