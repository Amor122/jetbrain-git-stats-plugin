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
    private static final Pattern STAT_LINE_PATTERN = Pattern.compile("^(\\d+)\\s+(\\d+)\\s+(.+)$");
    private static final Pattern BINARY_FILE_PATTERN = Pattern.compile("^(\\d+)\\s+(\\d+)\\s+(.+)\\s+\\(Bin\\)$");

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

        String commitHash = Messages.showInputDialog(
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
            System.err.println("=== Git Debug Info ===");
            System.err.println("Work dir: " + workDir);
            System.err.println("Commit hash: " + commitHash);

            String[] command = {"git", "show", "--stat", "--format=%H%n%an%n%ad%n%s", "--date=iso", commitHash};
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new java.io.File(workDir));
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
            System.err.println("Exit code: " + exitCode);
            System.err.println("=== Git Output ===");
            System.err.println(output.toString());
            System.err.println("=== End Git Output ===");

            if (output.length() == 0) {
                System.err.println("ERROR: Git output is empty!");
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

                Matcher binaryMatcher = BINARY_FILE_PATTERN.matcher(line);
                if (binaryMatcher.matches()) {
                    filesChanged++;
                    stats.addFileStats(binaryMatcher.group(3), new CommitStats.FileStats(binaryMatcher.group(3), true));
                    continue;
                }

                Matcher matcher = STAT_LINE_PATTERN.matcher(line);
                if (matcher.matches()) {
                    int added = Integer.parseInt(matcher.group(1));
                    int deleted = Integer.parseInt(matcher.group(2));
                    String filePath = matcher.group(3);

                    if (!filePath.contains("|")) continue;

                    filesChanged++;
                    linesAdded += added;
                    linesDeleted += deleted;

                    String actualFilePath = filePath.split("\\|")[0].trim();
                    CommitStats.FileStats fileStats = new CommitStats.FileStats(actualFilePath, false);
                    fileStats.setLinesAdded(added);
                    fileStats.setLinesDeleted(deleted);
                    stats.addFileStats(actualFilePath, fileStats);
                }
            }

            stats.setLinesAdded(linesAdded);
            stats.setLinesDeleted(linesDeleted);
            stats.setFilesChanged(filesChanged);
        }

        return stats;
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
