package com.gitstats;

import org.junit.Test;

import static org.junit.Assert.*;

public class CommitStatsTest {

    @Test
    public void testConstructorAndGetters() {
        CommitStats stats = new CommitStats("abc123def456", "John Doe", "2026-01-01", "Initial commit");

        assertEquals("abc123def456", stats.getCommitHash());
        assertEquals("John Doe", stats.getAuthor());
        assertEquals("2026-01-01", stats.getDate());
        assertEquals("Initial commit", stats.getMessage());
        assertEquals(0, stats.getLinesAdded());
        assertEquals(0, stats.getLinesDeleted());
        assertEquals(0, stats.getFilesChanged());
        assertTrue(stats.getFileStatsMap().isEmpty());
    }

    @Test
    public void testGetShortHash() {
        CommitStats stats = new CommitStats("abc123def456789012345678901234567890abcd", "Author", "Date", "Msg");
        assertEquals("abc123d", stats.getShortHash());

        CommitStats shortStats = new CommitStats("abc", "Author", "Date", "Msg");
        assertEquals("abc", shortStats.getShortHash());
    }

    @Test
    public void testSetters() {
        CommitStats stats = new CommitStats("hash", "Author", "Date", "Msg");

        stats.setLinesAdded(100);
        stats.setLinesDeleted(30);
        stats.setFilesChanged(5);

        assertEquals(100, stats.getLinesAdded());
        assertEquals(30, stats.getLinesDeleted());
        assertEquals(5, stats.getFilesChanged());
    }

    @Test
    public void testGetNetChange() {
        CommitStats stats = new CommitStats("hash", "Author", "Date", "Msg");
        stats.setLinesAdded(100);
        stats.setLinesDeleted(30);

        assertEquals(70, stats.getNetChange());
    }

    @Test
    public void testGetNetChangeNegative() {
        CommitStats stats = new CommitStats("hash", "Author", "Date", "Msg");
        stats.setLinesAdded(10);
        stats.setLinesDeleted(50);

        assertEquals(-40, stats.getNetChange());
    }

    @Test
    public void testGetNetChangeZero() {
        CommitStats stats = new CommitStats("hash", "Author", "Date", "Msg");
        stats.setLinesAdded(0);
        stats.setLinesDeleted(0);

        assertEquals(0, stats.getNetChange());
    }

    @Test
    public void testAddFileStats() {
        CommitStats stats = new CommitStats("hash", "Author", "Date", "Msg");

        CommitStats.FileStats file1 = new CommitStats.FileStats("src/Main.java", false);
        file1.setLinesAdded(20);
        file1.setLinesDeleted(5);

        CommitStats.FileStats file2 = new CommitStats.FileStats("image.png", true);

        stats.addFileStats("src/Main.java", file1);
        stats.addFileStats("image.png", file2);

        assertEquals(2, stats.getFileStatsMap().size());
        assertFalse(stats.getFileStatsMap().get("src/Main.java").isBinary());
        assertTrue(stats.getFileStatsMap().get("image.png").isBinary());
    }

    @Test
    public void testFileStatsGetters() {
        CommitStats.FileStats fileStats = new CommitStats.FileStats("src/Main.java", false);

        assertEquals("src/Main.java", fileStats.getFilePath());
        assertFalse(fileStats.isBinary());
        assertEquals(0, fileStats.getLinesAdded());
        assertEquals(0, fileStats.getLinesDeleted());
    }

    @Test
    public void testFileStatsSetters() {
        CommitStats.FileStats fileStats = new CommitStats.FileStats("src/Main.java", false);

        fileStats.setLinesAdded(50);
        fileStats.setLinesDeleted(10);

        assertEquals(50, fileStats.getLinesAdded());
        assertEquals(10, fileStats.getLinesDeleted());
        assertEquals(40, fileStats.getNetChange());
    }

    @Test
    public void testBinaryFileStats() {
        CommitStats.FileStats fileStats = new CommitStats.FileStats("logo.png", true);

        assertTrue(fileStats.isBinary());
        assertEquals(0, fileStats.getLinesAdded());
        assertEquals(0, fileStats.getLinesDeleted());
    }

    @Test
    public void testFileStatsNetChangeNegative() {
        CommitStats.FileStats fileStats = new CommitStats.FileStats("src/Old.java", false);
        fileStats.setLinesAdded(5);
        fileStats.setLinesDeleted(20);

        assertEquals(-15, fileStats.getNetChange());
    }
}
