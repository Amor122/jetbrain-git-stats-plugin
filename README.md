# GitStats - IntelliJ Plugin

A plugin for JetBrains IDEs (IntelliJ IDEA, PyCharm, CLion, etc.) that displays code change statistics for Git commits directly from the Git Log context menu.

## Features

- Right-click any commit in the Git Log view to view its statistics
- Shows lines added, lines deleted, and net change
- Lists per-file change details
- Identifies binary file changes
- Compatible with IDE versions from 2021.1+

## Build

### Prerequisites

- JDK 11 or later
- Internet connection (Gradle will download dependencies)

### Build Command

```bash
./gradlew buildPlugin
```

On Windows:

```
gradlew.bat buildPlugin
```

The plugin distribution file will be generated at:

```
build/distributions/GitStats-1.0.0.zip
```

## Install

1. Open your JetBrains IDE (IntelliJ IDEA, PyCharm, CLion, etc.)
2. Go to **File > Settings > Plugins**
3. Click the gear icon at the top of the plugin list
4. Select **Install Plugin from Disk...**
5. Choose the generated `GitStats-1.0.0.zip` file
6. Restart the IDE when prompted

## Usage

1. Open the **Git** tool window (usually at the bottom of the IDE)
2. Switch to the **Log** tab
3. Right-click on any commit in the commit list
4. Select **Git Commit Statistics** from the context menu
<img width="277" height="563" alt="image" src="https://github.com/user-attachments/assets/5d5fc73d-09ce-453a-a481-1223d0cc2a22" />
6. A dialog will appear showing:
   - Commit hash, author, date, and message
   - Total files changed, lines added, lines deleted, and net change
   - Per-file breakdown of additions and deletions
   - Binary file indicators

## Compatibility

| Item | Requirement |
|------|-------------|
| IDE Build | 211+ (2021.1 or later) |
| Java | 11 |
| Required Plugin | Git4Idea |

Tested on PyCharm 2021.1, IntelliJ IDEA 2024.x, and CLion 2025.2.
