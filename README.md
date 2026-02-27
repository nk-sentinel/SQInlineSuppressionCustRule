# SonarQube Inline Suppression Audit Plugin

A custom SonarQube plugin that detects and blocks inline suppression patterns in source code. It creates a **Blocker / Vulnerability** rule that prevents developers from silently bypassing static analysis through comments, annotations, or attributes.

## Why This Plugin?

SonarQube allows developers to suppress findings using inline mechanisms like `// NOSONAR` or `@SuppressWarnings("java:S106")`. While convenient, these suppressions can hide real vulnerabilities and undermine quality gates. This plugin makes every suppression visible as a Blocker issue, ensuring they go through proper review.

## Detected Suppression Patterns

| Pattern | Languages | Example |
|---------|-----------|---------|
| `NOSONAR` | All (16 languages) | `int x = 1; // NOSONAR` |
| `@SuppressWarnings` | Java, Kotlin | `@SuppressWarnings("java:S106")` |
| `@SuppressWarnings("all")` | Java, Kotlin | `@SuppressWarnings("all")` |
| `@SuppressWarnings` (multi-rule) | Java, Kotlin | `@SuppressWarnings({"java:S106", "java:S1186"})` |
| `@Suppress` | Kotlin | `@Suppress("kotlin:S1234")` |
| `[SuppressMessage]` | C#, VB.NET | `[SuppressMessage("SonarAnalyzer", "S1234")]` |

### Recognized SonarQube Rule Prefixes

The plugin recognizes rule references for all SonarQube language analyzers:

`java:`, `squid:`, `csharpsquid:`, `javascript:`, `typescript:`, `python:`, `kotlin:`, `php:`, `ruby:`, `go:`, `scala:`, `vbnet:`, `xml:`, `css:`, `web:`, `plsql:`, `tsql:`, `c:`, `cpp:`, `objc:`, `swift:`, `abap:`, `cobol:`, `flex:`

Bare rule IDs (e.g., `S1234`) are also detected as a fallback.

### What Is NOT Flagged

- `@SuppressWarnings("unchecked")` — standard Java compiler warnings without SonarQube rule references
- `@SuppressWarnings("deprecation")` — same as above
- `@SuppressLint(...)` — Android Lint suppressions (not SonarQube)

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
│  │                               │ Regex patterns:      │  │  │
│  │                               │  - NOSONAR           │  │  │
│  │                               │  - @SuppressWarnings │  │  │
│  │                               │  - @Suppress         │  │  │
│  │                               │  - [SuppressMessage] │  │  │
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

## CI/CD

This project includes a GitHub Actions workflow (`.github/workflows/ci.yml`) that runs automatically on:

- **Push** to `main` or `develop` branches
- **Pull requests** targeting `main` or `develop`
- **Manual trigger** via `workflow_dispatch`

The pipeline:
1. Builds the plugin with Java 17
2. Runs all unit tests
3. Uploads the plugin JAR as a build artifact (available for 30 days)
4. Caches Maven dependencies for faster subsequent builds

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
