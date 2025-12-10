Objective

Your environment reports incompatible Java/Gradle versions (Gradle <= 24 required by your setup). This document explains how to ensure the project uses a compatible JDK (Java 24) for Gradle and how to set JAVA_HOME on macOS.

Checklist (what you'll do)
- Install JDK 24 (or provide a JDK 24 path you already have).
- Set JAVA_HOME to point to the JDK 24 install.
- Restart terminal/IDE or source an env file so Gradle picks up the correct Java.

Install JDK 24 on macOS (recommended via SDKMAN or Homebrew/Adoptium if available)

Option A — SDKMAN (recommended for multiple Java versions):

1) Install SDKMAN (if not already):

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
```

2) Install a Java 24 distribution (example id may vary):

```bash
sdk install java 24.0.0-tem    # replace with an available 24 distribution id if shown by `sdk list java`
```

3) Set it as default or use it for this shell:

```bash
sdk use java 24.0.0-tem         # for current shell only
# or
sdk default java 24.0.0-tem     # persistently
```

Option B — Adoptium / Homebrew (if JDK 24 packages exist):

```bash
brew install --cask temurin24   # if available
```

Set JAVA_HOME (manual)

If you already have a JDK 24 installed at /Library/Java/JavaVirtualMachines/your-jdk-24.jdk/Contents/Home, add this to your shell rc (zsh):

```bash
# Put in ~/.zshrc or source directly
export JAVA_HOME="/Library/Java/JavaVirtualMachines/your-jdk-24.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```

Verify

```bash
java -version
javac -version
echo $JAVA_HOME
```

Gradle wrapper

After setting JAVA_HOME (or using sdkman), run:

```bash
./gradlew --version
./gradlew clean build
```

If you see Gradle using the correct Java version, proceed to run the plugin dev task:

```bash
./gradlew runIde
```

If run fails with an IntelliJ plugin classloader issue (rare), re-open a new terminal and try again.

Notes

- If you cannot install Java 24, you can target the maximum compatible local Java (24). The important part is to ensure the Gradle daemon uses a matching JDK for your Gradle plugin versions.
- If you prefer, paste the output of `java -version` and `javac -version` here and I will guide the exact commands for your system.

