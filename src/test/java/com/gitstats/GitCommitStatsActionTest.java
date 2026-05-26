package com.gitstats;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class GitCommitStatsActionTest {

    private GitCommitStatsAction action;

    @Before
    public void setUp() {
        action = new GitCommitStatsAction();
    }

    @Test
    public void testIsHexStringValid() {
        assertTrue(action.isHexString("abc123"));
        assertTrue(action.isHexString("ABC123"));
        assertTrue(action.isHexString("0123456789abcdef"));
        assertTrue(action.isHexString("deadbeef"));
        assertTrue(action.isHexString("DEADBEEF"));
        assertTrue(action.isHexString("a"));
        assertTrue(action.isHexString("0"));
    }

    @Test
    public void testIsHexStringInvalid() {
        assertFalse(action.isHexString(null));
        assertFalse(action.isHexString(""));
        assertFalse(action.isHexString("xyz"));
        assertFalse(action.isHexString("abc123g"));
        assertFalse(action.isHexString("hello world"));
        assertFalse(action.isHexString("abc 123"));
        assertFalse(action.isHexString("abc-123"));
    }

    @Test
    public void testParseGitOutputBasic() {
        String output = "commit abc123def456789012345678901234567890abcd\n" +
                "Author: John Doe <john@example.com>\n" +
                "Date:   2026-01-15 10:30:00 +0800\n" +
                "\n" +
                "    Add new feature\n" +
                "\n" +
                " src/Main.java | 10 +++++-----\n" +
                " src/Util.java |  5 ++---\n" +
                " 2 files changed, 7 insertions(+), 8 deletions(-)\n";

        CommitStats stats = action.parseGitOutput(output, "abc123");

        assertNotNull(stats);
        assertEquals("abc123def456789012345678901234567890abcd", stats.getCommitHash());
        assertEquals("John Doe <john@example.com>", stats.getAuthor());
        assertEquals("2026-01-15 10:30:00 +0800", stats.getDate());
        assertEquals("Add new feature", stats.getMessage());
        assertEquals(2, stats.getFilesChanged());
        assertEquals(7, stats.getLinesAdded());
        assertEquals(8, stats.getLinesDeleted());
        assertEquals(-1, stats.getNetChange());
    }

    @Test
    public void testParseGitOutputWithBinary() {
        String output = "commit abc123\n" +
                "Author: Jane <jane@example.com>\n" +
                "Date:   2026-02-01 14:00:00 +0800\n" +
                "\n" +
                "    Add image\n" +
                "\n" +
                " src/Main.java |  3 ++-\n" +
                " logo.png      |  Bin 0 -> 1234 bytes\n" +
                " 2 files changed, 2 insertions(+), 1 deletion(-)\n";

        CommitStats stats = action.parseGitOutput(output, "abc123");

        assertNotNull(stats);
        assertEquals(2, stats.getFilesChanged());
        assertEquals(2, stats.getLinesAdded());
        assertEquals(1, stats.getLinesDeleted());
        assertTrue(stats.getFileStatsMap().containsKey("logo.png"));
        assertTrue(stats.getFileStatsMap().get("logo.png").isBinary());
    }

    @Test
    public void testParseGitOutputOnlyAdditions() {
        String output = "commit abc123\n" +
                "Author: Test <test@test.com>\n" +
                "Date:   2026-03-01 00:00:00 +0000\n" +
                "\n" +
                "    New file\n" +
                "\n" +
                " newfile.txt | 50 ++++++++++++++++++++++++++\n" +
                " 1 file changed, 50 insertions(+)\n";

        CommitStats stats = action.parseGitOutput(output, "abc123");

        assertNotNull(stats);
        assertEquals(1, stats.getFilesChanged());
        assertEquals(50, stats.getLinesAdded());
        assertEquals(0, stats.getLinesDeleted());
        assertEquals(50, stats.getNetChange());
    }

    @Test
    public void testParseGitOutputOnlyDeletions() {
        String output = "commit abc123\n" +
                "Author: Test <test@test.com>\n" +
                "Date:   2026-03-01 00:00:00 +0000\n" +
                "\n" +
                "    Remove file\n" +
                "\n" +
                " oldfile.txt | 30 ------------------------------\n" +
                " 1 file changed, 30 deletions(-)\n";

        CommitStats stats = action.parseGitOutput(output, "abc123");

        assertNotNull(stats);
        assertEquals(1, stats.getFilesChanged());
        assertEquals(0, stats.getLinesAdded());
        assertEquals(30, stats.getLinesDeleted());
        assertEquals(-30, stats.getNetChange());
    }

    @Test
    public void testParseGitOutputMultilineMessage() {
        String output = "commit abc123\n" +
                "Author: Test <test@test.com>\n" +
                "Date:   2026-03-01 00:00:00 +0000\n" +
                "\n" +
                "    First line of message\n" +
                "    \n" +
                "    Second line of message\n" +
                "\n" +
                " file.txt | 5 ++---\n" +
                " 1 file changed, 2 insertions(+), 3 deletions(-)\n";

        CommitStats stats = action.parseGitOutput(output, "abc123");

        assertNotNull(stats);
        assertTrue(stats.getMessage().contains("First line of message"));
        assertTrue(stats.getMessage().contains("Second line of message"));
    }

    @Test
    public void testParseGitOutputEmptyOutput() {
        CommitStats stats = action.parseGitOutput("", "abc123");
        assertNull(stats);
    }

    @Test
    public void testParseGitOutputTooFewLines() {
        CommitStats stats = action.parseGitOutput("commit abc\nAuthor: Test", "abc123");
        assertNull(stats);
    }

    @Test
    public void testParseGitOutputNoStats() {
        String output = "commit abc123\n" +
                "Author: Test <test@test.com>\n" +
                "Date:   2026-03-01 00:00:00 +0000\n" +
                "\n" +
                "    Empty commit\n";

        CommitStats stats = action.parseGitOutput(output, "abc123");

        assertNotNull(stats);
        assertEquals(0, stats.getFilesChanged());
        assertEquals(0, stats.getLinesAdded());
        assertEquals(0, stats.getLinesDeleted());
        assertTrue(stats.getFileStatsMap().isEmpty());
    }

    @Test
    public void testParseGitOutputMultipleFiles() {
        String output = "commit abc123\n" +
                "Author: Test <test@test.com>\n" +
                "Date:   2026-03-01 00:00:00 +0000\n" +
                "\n" +
                "    Multiple changes\n" +
                "\n" +
                " src/main/java/App.java      | 20 ++++++++++----------\n" +
                " src/main/java/Util.java      |  8 ++------\n" +
                " src/test/java/AppTest.java   | 15 +++++++++++++++\n" +
                " config.properties            |  2 +-\n" +
                " 4 files changed, 23 insertions(+), 22 deletions(-)\n";

        CommitStats stats = action.parseGitOutput(output, "abc123");

        assertNotNull(stats);
        assertEquals(4, stats.getFilesChanged());
        assertEquals(23, stats.getLinesAdded());
        assertEquals(22, stats.getLinesDeleted());
        assertEquals(4, stats.getFileStatsMap().size());
    }

    @Test
    public void testParseGitOutputSingleFile() {
        String output = "commit abc123\n" +
                "Author: Test <test@test.com>\n" +
                "Date:   2026-03-01 00:00:00 +0000\n" +
                "\n" +
                "    Single file change\n" +
                "\n" +
                " README.md | 4 +++-\n" +
                " 1 file changed, 3 insertions(+), 1 deletion(-)\n";

        CommitStats stats = action.parseGitOutput(output, "abc123");

        assertNotNull(stats);
        assertEquals(1, stats.getFilesChanged());
        assertEquals(3, stats.getLinesAdded());
        assertEquals(1, stats.getLinesDeleted());
    }

    @Test
    public void testParseGitOutputHashPrefixStripped() {
        String output = "commit deadbeef1234567890\n" +
                "Author: Dev <dev@test.com>\n" +
                "Date:   2026-01-01 00:00:00 +0000\n" +
                "\n" +
                "    Test\n";

        CommitStats stats = action.parseGitOutput(output, "deadbeef");

        assertNotNull(stats);
        assertEquals("deadbeef1234567890", stats.getCommitHash());
        assertFalse(stats.getCommitHash().startsWith("commit"));
    }

    @Test
    public void testParseGitOutputAuthorPrefixStripped() {
        String output = "commit abc123\n" +
                "Author: Alice <alice@test.com>\n" +
                "Date:   2026-01-01 00:00:00 +0000\n" +
                "\n" +
                "    Test\n";

        CommitStats stats = action.parseGitOutput(output, "abc123");

        assertNotNull(stats);
        assertEquals("Alice <alice@test.com>", stats.getAuthor());
        assertFalse(stats.getAuthor().startsWith("Author:"));
    }

    @Test
    public void testParseGitOutputDatePrefixStripped() {
        String output = "commit abc123\n" +
                "Author: Test <test@test.com>\n" +
                "Date:   Mon Jan 1 00:00:00 2026 +0000\n" +
                "\n" +
                "    Test\n";

        CommitStats stats = action.parseGitOutput(output, "abc123");

        assertNotNull(stats);
        assertFalse(stats.getDate().startsWith("Date:"));
    }

    @Test
    public void testIsHexStringBoundary() {
        assertTrue(action.isHexString("0123456789"));
        assertTrue(action.isHexString("abcdef"));
        assertTrue(action.isHexString("ABCDEF"));
        assertFalse(action.isHexString("0x1234"));
        assertFalse(action.isHexString("12345678 90"));
    }

    @Test
    public void testParseGitOutputOnlyBinaryFiles() {
        String output = "commit abc123\n" +
                "Author: Test <test@test.com>\n" +
                "Date:   2026-03-01 00:00:00 +0000\n" +
                "\n" +
                "    Add binaries\n" +
                "\n" +
                " image.png  | Bin 0 -> 1234 bytes\n" +
                " data.bin   | Bin 0 -> 5678 bytes\n" +
                " 2 files changed\n";

        CommitStats stats = action.parseGitOutput(output, "abc123");

        assertNotNull(stats);
        assertTrue(stats.getFileStatsMap().containsKey("image.png"));
        assertTrue(stats.getFileStatsMap().get("image.png").isBinary());
        assertTrue(stats.getFileStatsMap().containsKey("data.bin"));
        assertTrue(stats.getFileStatsMap().get("data.bin").isBinary());
    }
}
