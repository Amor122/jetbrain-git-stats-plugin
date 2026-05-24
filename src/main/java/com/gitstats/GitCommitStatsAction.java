package com.gitstats;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitCommitStatsAction extends AnAction {
    private static final Pattern STAT_LINE_PATTERN = Pattern.compile("^\\s*(.+?)\\s*\\|\\s*(\\d+)(.*)$");
    private static final Pattern BINARY_FILE_PATTERN = Pattern.compile("^\\s*(.+?)\\s*\\|\\s*(\\d+)\\s*\\(Bin\\)$");
    private static final Pattern FILES_CHANGED_PATTERN = Pattern.compile("(\\d+)\\s+files?\\s+changed.*?(\\d+)\\s+insertion.*?(\\d+)\\s+deletion");

    public GitCommitStatsAction() {
        super("Git Commit Statistics...", "Show code change statistics for a git commit by hash", null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            Messages.showMessageDialog("No project found", "Error", Messages.getErrorIcon());
            return;
        }

        String commitHash = getCommitHashFromEvent(e);

        if (commitHash == null || commitHash.trim().isEmpty()) {
            commitHash = Messages.showInputDialog(
                project,
                "Enter commit hash:",
                "Git Commit Statistics",
                Messages.getQuestionIcon(),
                "",
                new InputValidator() {
                    @Override
                    public boolean checkInput(String input) {
                        return input != null && !input.trim().isEmpty() && input.length() >= 4;
                    }

                    @Override
                    public boolean canClose(String input) {
                        return checkInput(input);
                    }
                }
            );
        }

        if (commitHash == null || commitHash.trim().isEmpty()) {
            return;
        }

        VirtualFile root = findGitRoot(project);
        if (root == null) {
            Messages.showMessageDialog("No Git repository found in project.", "Error", Messages.getErrorIcon());
            return;
        }

        CommitStats stats = getCommitStats(commitHash.trim(), root.getPath());
        if (stats == null) {
            Messages.showMessageDialog("Failed to get commit statistics.\nMake sure:\n1. The commit hash is valid\n2. Git is installed and in PATH\n3. The project is a Git repository", "Error", Messages.getErrorIcon());
            return;
        }

        showStatsDialog(project, stats);
    }

    private void showStatsDialog(Project project, CommitStats stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("Commit: ").append(stats.getShortHash()).append("\n");
        sb.append("Author: ").append(stats.getAuthor()).append("\n");
        sb.append("Date: ").append(stats.getDate()).append("\n");
        sb.append("Message: ").append(stats.getMessage()).append("\n\n");
        sb.append("=== Change Statistics ===\n");
        sb.append("Files changed: ").append(stats.getFilesChanged()).append("\n");
        sb.append("Lines added: +").append(stats.getLinesAdded()).append("\n");
        sb.append("Lines deleted: -").append(stats.getLinesDeleted()).append("\n");
        sb.append("Net change: ").append(stats.getNetChange() >= 0 ? "+" : "").append(stats.getNetChange()).append("\n\n");

        if (!stats.getFileStatsMap().isEmpty()) {
            sb.append("=== File Details ===\n");
            for (CommitStats.FileStats fileStats : stats.getFileStatsMap().values()) {
                if (fileStats.isBinary()) {
                    sb.append("[BIN] ").append(fileStats.getFilePath()).append("\n");
                } else {
                    sb.append(fileStats.getFilePath())
                            .append(" (+").append(fileStats.getLinesAdded())
                            .append(" / -").append(fileStats.getLinesDeleted())
                            .append(")\n");
                }
            }
        }

        Messages.showMessageDialog(project, sb.toString(), "Commit Statistics - " + stats.getShortHash(), Messages.getInformationIcon());
    }

    @Nullable
    private CommitStats getCommitStats(@NotNull String commitHash, @NotNull String workDir) {
        try {
            java.io.File workDirFile = new java.io.File(workDir);

            String[] command = {"git", "-C", workDir, "show", "--stat=1000000", "--no-color", commitHash};
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDirFile);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();

            if (output.length() == 0) {
                return null;
            }

            return parseGitOutput(output.toString(), commitHash);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Nullable
    private CommitStats parseGitOutput(@NotNull String output, @NotNull String commitHash) {
        String[] lines = output.split("\n");
        if (lines.length < 4) {
            return null;
        }

        String hash = lines[0].trim();
        String author = lines[1].trim();
        String date = lines[2].trim();

        StringBuilder message = new StringBuilder();
        int statStartIndex = -1;
        for (int i = 3; i < lines.length; i++) {
            String line = lines[i];
            if (line.contains("|")) {
                statStartIndex = i;
                break;
            } else if (!line.trim().isEmpty()) {
                if (message.length() > 0) message.append(" ");
                message.append(line.trim());
            }
        }

        CommitStats stats = new CommitStats(hash, author, date, message.toString());

        if (statStartIndex >= 0) {
            int linesAdded = 0;
            int linesDeleted = 0;
            int filesChanged = 0;

            for (int i = statStartIndex; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                if (line.contains("files changed")) {
                    Matcher filesMatcher = FILES_CHANGED_PATTERN.matcher(line);
                    if (filesMatcher.find()) {
                        filesChanged = Integer.parseInt(filesMatcher.group(1));
                        String insertions = filesMatcher.group(2);
                        String deletions = filesMatcher.group(3);
                        linesAdded = insertions.equals("-") ? 0 : Integer.parseInt(insertions);
                        linesDeleted = deletions.equals("-") ? 0 : Integer.parseInt(deletions);
                    }
                    continue;
                }

                Matcher binaryMatcher = BINARY_FILE_PATTERN.matcher(line);
                if (binaryMatcher.matches()) {
                    filesChanged++;
                    stats.addFileStats(binaryMatcher.group(1), new CommitStats.FileStats(binaryMatcher.group(1), true));
                    continue;
                }

                Matcher matcher = STAT_LINE_PATTERN.matcher(line);
                if (matcher.matches()) {
                    String filePath = matcher.group(1).trim();
                    int changes = Integer.parseInt(matcher.group(2));
                    String rest = matcher.group(3);

                    if (!filePath.contains("|") && filePath.contains(" ")) {
                        continue;
                    }

                    int added = 0;
                    int deleted = 0;
                    if (rest.contains("+")) added = changes;
                    if (rest.contains("-")) deleted = changes;

                    filesChanged++;
                    linesAdded += added;
                    linesDeleted += deleted;

                    CommitStats.FileStats fileStats = new CommitStats.FileStats(filePath, false);
                    fileStats.setLinesAdded(added);
                    fileStats.setLinesDeleted(deleted);
                    stats.addFileStats(filePath, fileStats);
                }
            }

            stats.setLinesAdded(linesAdded);
            stats.setLinesDeleted(linesDeleted);
            stats.setFilesChanged(filesChanged);
        }

        return stats;
    }

    @Nullable
    private String getCommitHashFromEvent(@NotNull AnActionEvent e) {
        String hash;

        hash = tryGetFromCommitSelection(e);
        if (hash != null) return hash;

        hash = tryGetFromVcsLog(e);
        if (hash != null) return hash;

        hash = tryGetDataFromKey(e, "com.intellij.vcs.log.VcsLogDataKeys", "SELECTED_COMMITS");
        if (hash != null) return hash;

        hash = tryGetFromVcsLogUi(e);
        if (hash != null) return hash;

        return null;
    }

    @Nullable
    private String tryGetFromCommitSelection(@NotNull AnActionEvent e) {
        try {
            Class<?> dataKeysClass = Class.forName("com.intellij.vcs.log.VcsLogDataKeys");
            java.lang.reflect.Field field = dataKeysClass.getDeclaredField("VCS_LOG_COMMIT_SELECTION");
            field.setAccessible(true);
            Object key = field.get(null);

            if (key instanceof com.intellij.openapi.actionSystem.DataKey) {
                Object selection = e.getData((com.intellij.openapi.actionSystem.DataKey) key);
                if (selection != null) {
                    return extractHashFromSelection(selection);
                }
            }
        } catch (Exception ex) {
        }
        return null;
    }

    @Nullable
    private String extractHashFromSelection(@NotNull Object selection) {
        String[] unwrapMethods = {"getCommits", "getFirst", "getLeadCommit", "getSelectedCommit"};

        for (String methodName : unwrapMethods) {
            try {
                java.lang.reflect.Method method = selection.getClass().getMethod(methodName);
                Object result = method.invoke(selection);
                if (result != null) {
                    String hash = extractHashSafely(result);
                    if (hash != null) return hash;
                }
            } catch (Exception ex) {
            }
        }

        return extractHashSafely(selection);
    }

    @Nullable
    private String tryGetFromVcsLog(@NotNull AnActionEvent e) {
        try {
            Class<?> dataKeysClass = Class.forName("com.intellij.vcs.log.VcsLogDataKeys");
            java.lang.reflect.Field logField = dataKeysClass.getDeclaredField("VCS_LOG");
            logField.setAccessible(true);
            Object logKey = logField.get(null);

            if (logKey instanceof com.intellij.openapi.actionSystem.DataKey) {
                Object vcsLog = e.getData((com.intellij.openapi.actionSystem.DataKey) logKey);
                if (vcsLog != null) {
                    java.lang.reflect.Method getSelectedCommits = vcsLog.getClass().getMethod("getSelectedCommits");
                    Object commits = getSelectedCommits.invoke(vcsLog);
                    if (commits != null) {
                        return extractHashSafely(commits);
                    }
                }
            }
        } catch (Exception ex) {
        }
        return null;
    }

    @Nullable
    private String tryGetDataFromKey(@NotNull AnActionEvent e, @NotNull String className, @NotNull String fieldName) {
        try {
            Class<?> dataKeysClass = Class.forName(className);
            java.lang.reflect.Field field = dataKeysClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object key = field.get(null);

            if (key instanceof com.intellij.openapi.actionSystem.DataKey) {
                Object data = e.getData((com.intellij.openapi.actionSystem.DataKey) key);
                if (data != null) {
                    return extractHashSafely(data);
                }
            }
        } catch (ClassNotFoundException ex) {
        } catch (NoSuchFieldException ex) {
        } catch (Exception ex) {
        }
        return null;
    }

    @Nullable
    private String tryGetFromVcsLogUi(@NotNull AnActionEvent e) {
        try {
            Class<?> dataKeysClass = Class.forName("com.intellij.vcs.log.VcsLogDataKeys");
            java.lang.reflect.Field uiField = dataKeysClass.getDeclaredField("VCS_LOG_UI");
            uiField.setAccessible(true);
            Object uiKey = uiField.get(null);

            if (uiKey instanceof com.intellij.openapi.actionSystem.DataKey) {
                Object ui = e.getData((com.intellij.openapi.actionSystem.DataKey) uiKey);
                if (ui != null) {
                    return extractHashFromVcsLogUi(ui);
                }
            }
        } catch (Exception ex) {
        }
        return null;
    }

    @Nullable
    private String extractHashFromVcsLogUi(@NotNull Object ui) {
        try {
            java.lang.reflect.Method getSelectedCommitsMethod = ui.getClass().getMethod("getSelectedCommits");
            Object result = getSelectedCommitsMethod.invoke(ui);
            if (result != null) {
                return extractHashSafely(result);
            }
        } catch (Exception ex) {
        }

        try {
            java.lang.reflect.Method getSelectedDetailsMethod = ui.getClass().getMethod("getSelectedDetails");
            Object result = getSelectedDetailsMethod.invoke(ui);
            if (result != null) {
                return extractHashSafely(result);
            }
        } catch (Exception ex) {
        }

        return null;
    }

    @Nullable
    private String extractHashSafely(@NotNull Object data) {
        if (data instanceof Object[]) {
            Object[] arr = (Object[]) data;
            if (arr.length > 0) {
                return extractHashFromCommit(arr[0]);
            }
            return null;
        }

        if (data instanceof Iterable) {
            Iterable<?> iterable = (Iterable<?>) data;
            for (Object item : iterable) {
                if (item != null) {
                    return extractHashFromCommit(item);
                }
            }
            return null;
        }

        return extractHashFromCommit(data);
    }

    @Nullable
    private String extractHashFromCommit(@NotNull Object commit) {
        String[] methods = {"getId", "getHash", "getCommitId"};

        for (String methodName : methods) {
            try {
                java.lang.reflect.Method method = commit.getClass().getMethod(methodName);
                Object id = method.invoke(commit);
                if (id != null) {
                    String hashStr = tryConvertIdToHash(id);
                    if (hashStr != null) return hashStr;
                }
            } catch (Exception ex) {
            }
        }

        return null;
    }

    @Nullable
    private String tryConvertIdToHash(@NotNull Object id) {
        String[] strMethods = {"toString", "toShortString", "asString", "getString"};

        for (String methodName : strMethods) {
            try {
                java.lang.reflect.Method method = id.getClass().getMethod(methodName);
                Object result = method.invoke(id);
                if (result instanceof String) {
                    String str = (String) result;
                    if (str.length() >= 40 && isHexString(str.substring(0, 40))) {
                        return str.substring(0, 40);
                    }
                    if (str.length() >= 7 && isHexString(str)) {
                        return str;
                    }
                }
            } catch (Exception ex) {
            }
        }

        String str = id.toString();
        if (str.length() >= 40 && isHexString(str.substring(0, 40))) {
            return str.substring(0, 40);
        }
        if (str.length() >= 7 && isHexString(str)) {
            return str;
        }

        return null;
    }

    private boolean isHexString(String str) {
        if (str == null || str.isEmpty()) return false;
        for (char c : str.toCharArray()) {
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private VirtualFile findGitRoot(Project project) {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir != null) {
            VirtualFile gitDir = baseDir.findChild(".git");
            if (gitDir != null && gitDir.isDirectory()) {
                return baseDir;
            }
            VirtualFile parent = baseDir.getParent();
            if (parent != null) {
                gitDir = parent.findChild(".git");
                if (gitDir != null && gitDir.isDirectory()) {
                    return parent;
                }
            }
        }
        return baseDir;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        e.getPresentation().setEnabled(project != null);
        e.getPresentation().setVisible(true);
    }
}
