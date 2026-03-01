# SonarQube Inline Suppression Audit Plugin

A custom SonarQube plugin that detects and blocks inline suppression patterns in source code. It creates a **Blocker / Vulnerability** rule that prevents developers from silently bypassing static analysis through comments, annotations, or attributes.

## Why This Plugin?

SonarQube allows developers to suppress findings using inline mechanisms like `// NOSONAR` or `@SuppressWarnings("java:S106")`. While convenient, these suppressions can hide real vulnerabilities and undermine quality gates. This plugin makes every suppression visible as a Blocker issue, ensuring they go through proper review.

## Detected Suppression Patterns

| Pattern | Languages | Example |
|---------|-----------|---------|
| `NOSONAR` | All (16 languages) | `int x = 1; // NOSONAR` |
| `@SuppressWarnings` | Java, Kotlin, Scala | `@SuppressWarnings("java:S106")` |
| `@SuppressWarnings("all")` | Java, Kotlin, Scala | `@SuppressWarnings("all")` |
| `@SuppressWarnings` (multi-rule) | Java, Kotlin, Scala | `@SuppressWarnings({"java:S106", "java:S1186"})` |
| `@Suppress` | Kotlin | `@Suppress("kotlin:S1234")` |
| `[SuppressMessage]` | C# | `[SuppressMessage("SonarAnalyzer", "S1234")]` |
| `<SuppressMessage>` | VB.NET | `<SuppressMessage("SonarAnalyzer", "S1234")>` |

### Recognized SonarQube Rule Prefixes

The plugin recognizes rule references for all SonarQube language analyzers:

`java:`, `squid:`, `csharpsquid:`, `javascript:`, `typescript:`, `python:`, `kotlin:`, `php:`, `ruby:`, `go:`, `scala:`, `vbnet:`, `xml:`, `css:`, `web:`, `plsql:`, `tsql:`, `c:`, `cpp:`, `objc:`, `swift:`, `abap:`, `cobol:`, `flex:`

Bare rule IDs (e.g., `S1234`) are also detected as a fallback.

### What Is NOT Flagged

The plugin avoids false positives by ignoring:

- **Standard compiler warnings** — `@SuppressWarnings("unchecked")`, `@SuppressWarnings("deprecation")`, `@Suppress("UNUSED_PARAMETER")`
- **Non-SonarQube linter directives** — `@SuppressLint(...)` (Android), `eslint-disable`, `@ts-ignore`, `nolint`, `rubocop:disable`, `phpcs:ignore`, `stylelint-disable`
- **Non-Sonar [SuppressMessage] categories** — `[SuppressMessage("Microsoft.Design", "CA1062")]`, `[SuppressMessage("StyleCop", "SA1600")]`
- **NOSONAR inside string literals** — `let x = "NOSONAR is a keyword"` (not a comment, not a suppression)
- **NOSONAR as part of variable names** — `myNOSONARflag` (no word boundary match)
- **Annotations/attributes inside comments** — `// @SuppressWarnings("java:S106")` (comment text, not actual code)

### Known Limitations

- **Java `@SuppressWarnings("all")` self-suppression** — The Java analyzer honors `@SuppressWarnings("all")` at scope level and suppresses ALL issues in the annotated element, including our plugin's issue. This is a SonarQube platform limitation. The plugin applies a line-offset workaround (reporting on the adjacent line), which works for Kotlin and Scala but may still be suppressed by Java's scope-level handling.
- **NOSONAR line offset** — To avoid SonarQube's built-in `NoSonarFilter` (which suppresses ALL issues on NOSONAR lines, including ours), NOSONAR issues are reported on the line above (or below for line 1). The issue message includes the actual line number for reference.

## Supported Languages

The plugin creates a dedicated rule repository for each of the 16 supported languages:

Java, C#, Python, JavaScript, TypeScript, Kotlin, Go, PHP, Ruby, Scala, VB.NET, XML, CSS, HTML, C, C++

## SonarQube Compatibility

| SonarQube Version | Plugin API | Compatible |
|-------------------|-----------|------------|
| 2025.1 LTA        | 11.1.0    | Yes        |
| 2025.2 - 2025.6   | 11.3.0 - 13.4.2 | Yes |
| 2026.1+            | 13.4.3+   | Yes        |

The plugin is built against **Plugin API 10.11.0.2468**, ensuring forward compatibility with all SonarQube versions from 2025.1 onward.

## Technology Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| Java | 17 | Language runtime |
| SonarQube Plugin API | 10.11.0.2468 | Plugin framework (`provided` scope) |
| SLF4J | 2.0.12 | Logging (`provided` scope) |
| Maven | 3.8+ | Build tool |
| JUnit 5 | 5.10.2 | Unit testing |
| Mockito | 5.11.0 | Mocking framework |
| AssertJ | 3.25.3 | Fluent test assertions |
| Maven Compiler Plugin | 3.13.0 | Java 17 compilation |
| Maven Surefire Plugin | 3.2.5 | Test execution |
| Maven JAR Plugin | 3.4.1 | Plugin JAR packaging with manifest |

## Project Structure

```
SQInlineSuppressionCustRule/
├── pom.xml
├── README.md
├── .gitignore
├── .github/
│   └── workflows/
│       └── ci.yml                          # GitHub Actions CI/CD pipeline
└── src/
    ├── main/
    │   ├── java/org/sonar/plugins/inlinesuppression/
    │   │   ├── InlineSuppressionPlugin.java          # Plugin entry point
    │   │   ├── InlineSuppressionRulesDefinition.java  # Rule definitions (16 languages)
    │   │   ├── InlineSuppressionSensor.java           # File scanner
    │   │   └── SuppressionPatternRegistry.java        # Regex detection engine
    │   └── resources/org/sonar/plugins/inlinesuppression/rules/
    │       └── InlineSuppression.html                 # Rule description
    └── test/
        └── java/org/sonar/plugins/inlinesuppression/
            ├── InlineSuppressionPluginTest.java
            ├── InlineSuppressionRulesDefinitionTest.java
            ├── InlineSuppressionSensorTest.java
            └── SuppressionPatternRegistryTest.java
```

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│  SonarQube Server                                            │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  InlineSuppressionPlugin                               │  │
│  │  (registers extensions)                                │  │
│  │                                                        │  │
│  │  ┌─────────────────────────┐  ┌─────────────────────┐  │  │
│  │  │ RulesDefinition         │  │ Sensor               │  │  │
│  │  │                         │  │                      │  │  │
│  │  │ Creates 16 repos:       │  │ For each source file:│  │  │
│  │  │  suppression-audit-java │  │  1. Read content     │  │  │
│  │  │  suppression-audit-cs   │  │  2. Run regex engine │  │  │
│  │  │  suppression-audit-py   │  │  3. Report issues    │  │  │
│  │  │  ...                    │  │                      │  │  │
│  │  │                         │  │  Uses RuleKey per    │  │  │
│  │  │ Each repo has 1 rule:   │  │  file language       │  │  │
│  │  │  InlineSuppression      │  │                      │  │  │
│  │  │  (BLOCKER/VULNERABILITY)│  │                      │  │  │
│  │  └─────────────────────────┘  └──────────┬───────────┘  │  │
│  │                                          │              │  │
│  │                               ┌──────────▼───────────┐  │  │
│  │                               │ PatternRegistry      │  │  │
│  │                               │                      │  │  │
│  │                               │ Detection patterns:  │  │  │
│  │                               │  - NOSONAR           │  │  │
│  │                               │  - @SuppressWarnings │  │  │
│  │                               │  - @Suppress         │  │  │
│  │                               │  - [SuppressMessage] │  │  │
│  │                               │  - <SuppressMessage> │  │  │
│  │                               │                      │  │  │
│  │                               │ Filtering:           │  │  │
│  │                               │  - String literals   │  │  │
│  │                               │  - Comment context   │  │  │
│  │                               │                      │  │  │
│  │                               │ Rule ref extraction: │  │  │
│  │                               │  - Prefixed (java:S) │  │  │
│  │                               │  - Bare (S1234)      │  │  │
│  │                               │  - "all"             │  │  │
│  │                               └──────────────────────┘  │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

## Build Instructions

### Prerequisites

- **Java 17** or later
- **Maven 3.8** or later

### Build the Plugin

```bash
# Compile, run tests, and package the JAR
mvn clean package

# Skip tests (faster build)
mvn clean package -DskipTests
```

The plugin JAR will be generated at:

```
target/sonar-inline-suppression-plugin-1.0.0.jar
```

### Run Tests Only

```bash
mvn test
```

### Verify the JAR Manifest

After building, verify the SonarQube plugin manifest entries are present:

```bash
unzip -p target/sonar-inline-suppression-plugin-1.0.0.jar META-INF/MANIFEST.MF
```

Expected output should include:

```
Plugin-Key: inlinesuppression
Plugin-Class: org.sonar.plugins.inlinesuppression.InlineSuppressionPlugin
Plugin-Name: Inline Suppression Audit
Sonar-Version: 10.11.0.2468
```

## Installation

1. Build the plugin JAR (see above).
2. Copy the JAR to your SonarQube server's extensions directory:

   ```bash
   cp target/sonar-inline-suppression-plugin-1.0.0.jar $SONARQUBE_HOME/extensions/plugins/
   ```

3. Restart SonarQube:

   ```bash
   $SONARQUBE_HOME/bin/<os>/sonar.sh restart
   ```

4. In SonarQube, go to **Quality Profiles** and activate the rule:
   - Navigate to the profile for each language you want to enforce
   - Search for **"Inline suppression"** in the rule search
   - Activate the rule `InlineSuppression` from the `suppression-audit-<language>` repository

5. Run a new analysis on your project. Any inline suppressions will appear as **Blocker Vulnerabilities**.

## Testing

The plugin includes **117 unit tests** covering:

- **Pattern detection** (96 tests) — NOSONAR (all comment styles, case-insensitive, string literal exclusion), `@SuppressWarnings`, `@Suppress`, `[SuppressMessage]`, `<SuppressMessage>`, multiline annotations, comment-context filtering, line-offset workarounds
- **Sensor integration** (11 tests) — file scanning, issue creation, language routing, error handling
- **Rule definitions** (10 tests) — repository creation for all 16 languages

Validated against a **14-language test project** with categorized true positive, false positive, and edge case scenarios. Detection accuracy: **99%** (1 known platform limitation with Java `@SuppressWarnings("all")`).

### Validation Test Project

A dedicated multi-language test project is available for end-to-end validation of the plugin after installation:

**Repository:** [SQInlineSuppressionCustRuleTestProj](https://github.com/ShadowOpenTech/SQInlineSuppressionCustRuleTestProj.git)

The test project contains 14 language-specific files (Java, Kotlin, C#, VB.NET, Python, JavaScript, TypeScript, Go, PHP, Ruby, Scala, HTML, CSS, XML) with categorized test cases:

- **True Positives** — suppression patterns the plugin must detect (NOSONAR comments, `@SuppressWarnings`, `@Suppress`, `[SuppressMessage]`, `<SuppressMessage>`, embedded NOSONAR in descriptive text)
- **False Positives** — patterns the plugin must ignore (compiler warnings, non-Sonar linter directives, string literals, variable names)
- **Edge Cases** — multiline annotations, whitespace in rule references, NOSONAR in block comments

#### Running the Test Scan

1. Install the plugin in SonarQube (see [Installation](#installation)).
2. Activate the `InlineSuppression` rule in Quality Profiles for all 14 languages.
3. Clone the test project and run the scanner:

   ```bash
   git clone https://github.com/ShadowOpenTech/SQInlineSuppressionCustRuleTestProj.git
   cd SQInlineSuppressionCustRuleTestProj
   sonar-scanner
   ```

   > **Note:** The project includes a pre-configured `sonar-project.properties` with project key `sq-plugin-test`. Update `sonar.host.url` and `sonar.token` as needed for your SonarQube instance.

4. Review the scan results in SonarQube:
   - All **true positive** lines should have **Blocker Vulnerability** issues
   - All **false positive** lines should have **no issues** from the `suppression-audit-*` repositories
   - Expected results: **102 issues**, **0 false positives** (1 known miss: Java `@SuppressWarnings("all")` — see [Known Limitations](#known-limitations))

## CI/CD

This project includes a GitHub Actions workflow (`.github/workflows/ci.yml`) that runs automatically on:

- **Push** to any branch
- **Pull requests** targeting `main` or `develop`
- **Manual trigger** via `workflow_dispatch`

The pipeline:
1. Builds the plugin with Java 17 (Temurin)
2. Runs all unit tests
3. Uploads the plugin JAR as a build artifact (retained for 30 days)
4. Uploads test results (retained for 14 days)
5. Creates a GitHub Release with the plugin JAR (pre-release for non-main branches)
6. Caches Maven dependencies for faster subsequent builds

See [.github/workflows/ci.yml](.github/workflows/ci.yml) for the full configuration.

## Future Improvements

The following patterns are planned for future releases:

| Pattern | Language | Description |
|---------|----------|-------------|
| `#pragma warning disable S1234` | C# | Pragma-based suppression |
| `@Generated` | Java | Generated code exclusion |
| `@javax.annotation.Generated` | Java | Generated code exclusion (legacy) |
| `@jakarta.annotation.Generated` | Java | Generated code exclusion (Jakarta) |
| `[GeneratedCode]` | C# | Generated code attribute |
| `[GeneratedCodeAttribute]` | C# | Generated code attribute (full name) |

## License

This is a custom plugin for internal use. Modify and distribute as needed for your organization.
