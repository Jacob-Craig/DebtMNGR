# DebtMNGR: Troubleshooting & Environment Gotchas

This document records common local environment issues, error signatures, root causes, and verified resolutions for both human contributors and AI agents.

---

## 1. Gradle Build Failure: Invalid `javaHome` / Missing `bin/java`

### Error Signature
```text
FAILURE: Build failed with an exception.

* What went wrong:
The supplied javaHome seems to be invalid. I cannot find the java executable. 
Tried location: /Users/<user>/Library/Java/JavaVirtualMachines/corretto-17.0.20.1/Contents/Home/bin/java
```

### Context & Root Cause
- The project is configured with a Java 17 toolchain in `build.gradle.kts`:
  ```kotlin
  java {
      toolchain {
          languageVersion = JavaLanguageVersion.of(17)
      }
  }
  ```
- Gradle scans the system's standard JDK directory (e.g., `~/Library/Java/JavaVirtualMachines/` on macOS).
- If a JDK directory exists but is incomplete or corrupted (for example, containing only utility binaries like `jfrconv` while missing `Contents/Home/bin/java`), Gradle's toolchain detector will select this broken directory and immediately abort the build.

### Resolution Steps

#### Option A: In IntelliJ IDEA (Recommended for local dev)
1. Open **Settings / Preferences** (`Cmd + ,` on macOS).
2. Navigate to **Build, Execution, Deployment → Build Tools → Gradle**.
3. In the **Gradle JVM** dropdown, select **Download JDK...**.
4. Choose **Version: 17** and **Vendor: Amazon Corretto** (or **Eclipse Temurin**), then click **Download**.
5. Ensure the new JDK (e.g., `corretto-17 (2)`) is selected as the Gradle JVM and click **Apply / OK**.

#### Option B: Terminal / Homebrew (CLI)
1. Install JDK 17 via Homebrew or an official package:
   ```bash
   brew install openjdk@17
   ```
2. Set your `JAVA_HOME` environment variable to the valid installation:
   ```bash
   export JAVA_HOME=$(/usr/libexec/java_home -v 17)
   ```

#### Cleanup Corrupted JDKs (Optional but Recommended)
Remove the corrupted JDK directory so Gradle's auto-discovery does not find it in future builds:
```bash
rm -rf ~/Library/Java/JavaVirtualMachines/corretto-17.0.20.1
rm -f ~/Library/Java/JavaVirtualMachines/.corretto-17.0.20.1.intellij
```

### Verification
Run the compilation task from the project root:
```bash
./gradlew build -x test
```
Expected output: `BUILD SUCCESSFUL`.
