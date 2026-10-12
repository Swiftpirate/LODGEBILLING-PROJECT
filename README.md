# Lodge Billing

A JavaFX desktop app for lodge billing: guest bills, receipts, reports and user accounts.

## Requirements

- JDK 23 or later (JavaFX 25 needs it)
- No separate Gradle or JavaFX install is needed. The Gradle wrapper downloads both.

## Running from the command line

```sh
./gradlew run        # macOS / Linux
gradlew.bat run      # Windows
```

Run the tests with:

```sh
./gradlew test
```

## Running from IntelliJ IDEA

### Option 1: Gradle task (recommended)

Open the project folder in IntelliJ and let it import the Gradle build. Then open the
**Gradle** tool window and run **LodgeBilling > Tasks > application > run**.

This picks up the JavaFX modules and JVM arguments from `build.gradle.kts`, so you don't
need to set anything else up.

### Option 2: Application run configuration

1. **Run > Edit Configurations... > + > Application**
2. Fill in the fields:

   | Field             | Value                                                |
   |-------------------|------------------------------------------------------|
   | Name              | `LodgeApp`                                           |
   | Module / classpath| `LodgeBilling.main`                                  |
   | Main class        | `lodge.LodgeApp`                                     |
   | VM options        | `--enable-native-access=javafx.graphics`             |

3. Click **Apply**, then **Run**.

If IntelliJ reports missing JavaFX runtime components, use Option 1 instead. The Gradle
task puts JavaFX on the module path for you.
