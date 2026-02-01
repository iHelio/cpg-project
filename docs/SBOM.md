# Software Bill of Materials (SBOM)

## Overview

A Software Bill of Materials (SBOM) is a formal, machine-readable inventory of all components, libraries, and dependencies that make up a software application. Think of it as a "nutrition label" for software - it provides transparency into exactly what ingredients are used in the final product.

## Why SBOM Matters

### Security & Vulnerability Management
- **Rapid Response**: When a vulnerability is discovered (e.g., Log4Shell), an SBOM allows you to instantly determine if your application is affected
- **Continuous Monitoring**: Security tools can scan your SBOM against vulnerability databases (NVD, OSV) to identify risks
- **Supply Chain Security**: Understand the full dependency tree, including transitive dependencies

### Compliance & Governance
- **Regulatory Requirements**: US Executive Order 14028 mandates SBOMs for software sold to federal agencies
- **License Compliance**: Track all open-source licenses to ensure compliance with organizational policies
- **Audit Trail**: Maintain records of what was deployed and when

### Operational Benefits
- **Reproducibility**: Know exactly what versions were used in any build
- **Dependency Management**: Identify outdated or deprecated libraries
- **Risk Assessment**: Evaluate the health and maintenance status of dependencies

## SBOM in This Project

### Format: CycloneDX

This project uses [CycloneDX](https://cyclonedx.org/), an OWASP-backed standard specifically designed for security use cases. CycloneDX supports:

- Component identification (name, version, purl, CPE)
- License information
- Vulnerability references
- Dependency relationships
- Hash values for integrity verification

### Generation

The SBOM is generated using the CycloneDX Maven plugin configured in `pom.xml`:

```xml
<plugin>
    <groupId>org.cyclonedx</groupId>
    <artifactId>cyclonedx-maven-plugin</artifactId>
    <version>2.8.0</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>makeAggregateBom</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Commands

Generate SBOM on demand:
```bash
./mvnw cyclonedx:makeAggregateBom
```

Generate SBOM as part of package phase:
```bash
./mvnw package
```

### Output Files

After generation, SBOM files are located in:

| File | Format | Description |
|------|--------|-------------|
| `target/bom.json` | JSON | Machine-readable, ideal for tools and APIs |
| `target/bom.xml` | XML | Alternative format, widely supported |

## SBOM Structure

### Metadata
```json
{
  "bomFormat": "CycloneDX",
  "specVersion": "1.5",
  "serialNumber": "urn:uuid:...",
  "metadata": {
    "timestamp": "2026-01-19T13:47:21Z",
    "tools": [{ "name": "CycloneDX Maven plugin", "version": "2.8.0" }],
    "component": {
      "name": "cpg",
      "version": "0.0.1-SNAPSHOT",
      "type": "application"
    }
  }
}
```

### Components
Each dependency is documented with:

| Field | Description | Example |
|-------|-------------|---------|
| `type` | Component type | `library` |
| `group` | Maven groupId | `org.springframework.boot` |
| `name` | Maven artifactId | `spring-boot-starter-web` |
| `version` | Version number | `3.4.1` |
| `purl` | Package URL (unique identifier) | `pkg:maven/org.springframework.boot/spring-boot@3.4.1` |
| `licenses` | License information | `Apache-2.0` |
| `hashes` | Integrity checksums | SHA-256, MD5 |

### Dependencies
The dependency graph shows relationships:
```json
{
  "dependencies": [
    {
      "ref": "pkg:maven/com.ihelio/cpg@0.0.1-SNAPSHOT",
      "dependsOn": [
        "pkg:maven/org.springframework.boot/spring-boot-starter-web@3.4.1",
        "pkg:maven/org.kie/kie-dmn-core@9.44.0.Final"
      ]
    }
  ]
}
```

## Using the SBOM

### Vulnerability Scanning

Scan with OWASP Dependency-Check:
```bash
./mvnw dependency-check:check
```

Scan with Grype (Anchore):
```bash
grype sbom:target/bom.json
```

Scan with Trivy:
```bash
trivy sbom target/bom.json
```

### License Analysis

Check licenses with Maven:
```bash
./mvnw license:check
```

### CI/CD Integration

Example GitHub Actions workflow:
```yaml
- name: Generate SBOM
  run: ./mvnw cyclonedx:makeAggregateBom

- name: Upload SBOM
  uses: actions/upload-artifact@v4
  with:
    name: sbom
    path: target/bom.json

- name: Scan for vulnerabilities
  uses: anchore/scan-action@v3
  with:
    sbom: target/bom.json
```

### SBOM Storage & Distribution

Best practices for SBOM management:

1. **Version Control**: Store SBOMs alongside releases in artifact repositories
2. **Attestation**: Sign SBOMs with Sigstore/cosign for authenticity
3. **API Access**: Publish to dependency-track or similar platforms for continuous monitoring
4. **Customer Delivery**: Include SBOM with software distributions for transparency

## Current Project SBOM Summary

| Metric | Value |
|--------|-------|
| Total Components | ~110 |
| Direct Dependencies | ~17 |
| Transitive Dependencies | ~93 |
| Primary Framework | Spring Boot 3.4.1 |
| Java Version | 21 |

### Key Dependencies

| Category | Library | Version |
|----------|---------|---------|
| Web Framework | Spring Boot Starter Web | 3.4.1 |
| JSON Processing | Jackson Databind | 2.18.2 |
| Validation | Jakarta Validation | 3.0.2 |
| DMN Engine | KIE DMN Core | 9.44.0.Final |
| MCP Server | Spring AI MCP Server WebMVC | 1.1.2 |
| Expression Language | FEEL Engine | 9.44.0.Final |
| Logging | Logback Classic | 1.5.12 |
| Testing | JUnit Jupiter | 5.11.4 |

## References

- [CycloneDX Specification](https://cyclonedx.org/specification/overview/)
- [NTIA SBOM Minimum Elements](https://www.ntia.gov/sites/default/files/publications/sbom_minimum_elements_report_0.pdf)
- [OWASP Dependency-Track](https://dependencytrack.org/)
- [US Executive Order 14028](https://www.whitehouse.gov/briefing-room/presidential-actions/2021/05/12/executive-order-on-improving-the-nations-cybersecurity/)
