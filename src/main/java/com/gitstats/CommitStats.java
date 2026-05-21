package com.gitstats;

import java.util.HashMap;
import java.util.Map;

public class CommitStats {
    private final String commitHash;
    private final String author;
    private final String date;
    private final String message;
    private int linesAdded;
    private int linesDeleted;
    private int filesChanged;
    private Map<String, FileStats> fileStatsMap;

    public CommitStats(String commitHash, String author, String date, String message) {
        this.commitHash = commitHash;
        this.author = author;
        this.date = date;
        this.message = message;
        this.fileStatsMap = new HashMap<>();
    }

    public String getCommitHash() {
        return commitHash;
    }

    public String getShortHash() {
        return commitHash.length() > 7 ? commitHash.substring(0, 7) : commitHash;
    }

    public String getAuthor() {
        return author;
    }

    public String getDate() {
        return date;
    }

    public String getMessage() {
        return message;
    }

    public int getLinesAdded() {
        return linesAdded;
    }

    public void setLinesAdded(int linesAdded) {
        this.linesAdded = linesAdded;
    }

    public int getLinesDeleted() {
        return linesDeleted;
    }

    public void setLinesDeleted(int linesDeleted) {
        this.linesDeleted = linesDeleted;
    }

    public int getFilesChanged() {
        return filesChanged;
    }

    public void setFilesChanged(int filesChanged) {
        this.filesChanged = filesChanged;
    }

    public Map<String, FileStats> getFileStatsMap() {
        return fileStatsMap;
    }

    public void addFileStats(String filePath, FileStats stats) {
        this.fileStatsMap.put(filePath, stats);
    }

    public int getNetChange() {
        return linesAdded - linesDeleted;
    }

    public static class FileStats {
        private final String filePath;
        private final boolean isBinary;
        private int linesAdded;
        private int linesDeleted;

        public FileStats(String filePath, boolean isBinary) {
            this.filePath = filePath;
            this.isBinary = isBinary;
        }

        public String getFilePath() {
            return filePath;
        }

        public boolean isBinary() {
            return isBinary;
        }

        public int getLinesAdded() {
            return linesAdded;
        }

        public void setLinesAdded(int linesAdded) {
            this.linesAdded = linesAdded;
        }

        public int getLinesDeleted() {
            return linesDeleted;
        }

        public void setLinesDeleted(int linesDeleted) {
            this.linesDeleted = linesDeleted;
        }

        public int getNetChange() {
            return linesAdded - linesDeleted;
        }
    }
}
