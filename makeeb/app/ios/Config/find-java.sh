# Sourced by the Xcode "Build Kotlin framework" phases. Xcode does not load shell profiles, so
# find a JDK explicitly: an explicit JAVA_HOME, a registered JDK 17+, then Homebrew installs.
if [ -z "${JAVA_HOME:-}" ]; then
  if JH=$(/usr/libexec/java_home -v 17+ 2>/dev/null); then
    export JAVA_HOME="$JH"
  elif [ -d /opt/homebrew/opt/openjdk@21 ]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
  elif [ -d /opt/homebrew/opt/openjdk ]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home
  fi
fi
