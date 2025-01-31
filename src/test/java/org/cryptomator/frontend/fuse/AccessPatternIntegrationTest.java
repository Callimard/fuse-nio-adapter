package org.cryptomator.frontend.fuse;

import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import jnr.ffi.provider.jffi.ByteBufferMemoryIO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.impl.SimpleLogger;
import ru.serce.jnrfuse.FuseException;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.utils.WinPathUtils;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.US_ASCII;

public class AccessPatternIntegrationTest {

	static {
		System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "debug");
		System.setProperty(SimpleLogger.SHOW_DATE_TIME_KEY, "true");
		System.setProperty(SimpleLogger.DATE_TIME_FORMAT_KEY, "HH:mm:ss.SSS");
	}

	private FuseNioAdapter adapter;

	@BeforeEach
	void setup(@TempDir Path tmpDir) {
		Assumptions.assumeTrue(onWindowsWinFspInstalled(), "WinFSP seem not to be installed.");
		adapter = AdapterFactory.createReadWriteAdapter(tmpDir);
	}

	@Test
	@DisplayName("simulate TextEdit.app's access pattern during save")
	void testAppleAutosaveAccessPattern() {
		// echo "asd" > foo.txt
		FuseFileInfo fi1 = new MockFuseFileInfo();
		adapter.create("/foo.txt", 0644, fi1);
		adapter.write("/foo.txt", mockPointer(US_ASCII.encode("asd")), 3, 0, fi1);

		// mkdir foo.txt-temp3000
		adapter.mkdir("foo.txt-temp3000", 0755);

		// echo "asdasd" > foo.txt-temp3000/foo.txt
		FuseFileInfo fi2 = new MockFuseFileInfo();
		adapter.create("/foo.txt-temp3000/foo.txt", 0644, fi2);
		adapter.write("/foo.txt-temp3000/foo.txt", mockPointer(US_ASCII.encode("asdasd")), 6, 0, fi2);

		// mv foo.txt foo.txt-temp3001
		adapter.rename("/foo.txt", "/foo.txt-temp3001");

		// mv foo.txt-temp3000/foo.txt foo.txt
		adapter.rename("/foo.txt-temp3000/foo.txt", "/foo.txt");
		adapter.release("/foo.txt-temp3000/foo.txt", fi2);

		// rm -r foo.txt-temp3000
		adapter.rmdir("/foo.txt-temp3000");

		// rm foo.txt-temp3001
		adapter.release("/foo.txt", fi1);
		adapter.unlink("/foo.txt-temp3001");

		// cat foo.txt == "asdasd"
		ByteBuffer buf = ByteBuffer.allocate(7);
		FuseFileInfo fi3 = new MockFuseFileInfo();
		adapter.open("/foo.txt", fi3);
		int numRead = adapter.read("/foo.txt", mockPointer(buf), 7, 0, fi3);
		adapter.release("/foo.txt", fi3);
		Assertions.assertEquals(6, numRead);
		Assertions.assertArrayEquals("asdasd".getBytes(US_ASCII), Arrays.copyOf(buf.array(), numRead));
	}

	private boolean onWindowsWinFspInstalled() {
		try {
			return !WinPathUtils.getWinFspPath().isBlank();
		} catch (FuseException e) {
			//TODO: log?
			return false;
		}
	}

	@Test
	@DisplayName("create, move and delete symlinks")
	@DisabledOnOs(OS.WINDOWS) // Symlinks require either admin privileges or enabled developer mode on windows
	void testCreateMoveAndDeleteSymlinks() {
		// touch foo.txt
		FuseFileInfo fi1 = new MockFuseFileInfo();
		adapter.create("/foo.txt", 0644, fi1);

		// ln -s foo.txt bar.txt
		adapter.symlink("foo.txt", "/bar.txt");
		assertSymlinkTargetExists("/bar.txt", false);

		// mkdir test
		adapter.mkdir("test", 0755);

		// ln -s test test2
		adapter.symlink("test", "/test2");
		assertSymlinkTargetExists("/test2", true);

		// ln -sr ../foo.txt test/baz
		adapter.symlink("../foo.txt", "test/baz.txt");
		assertSymlinkTargetExists("/bar.txt", false);

		// move both to subdir
		adapter.rename("/foo.txt", "/test/foo.txt");
		adapter.rename("/bar.txt", "/test/bar.txt");
		assertSymlinkTargetExists("/test/bar.txt", false);

		// delete all
		adapter.unlink("/test2");
		adapter.unlink("/test/foo.txt");
		adapter.unlink("/test/bar.txt");
		adapter.unlink("/test/baz.txt");
		adapter.rmdir("/test");
	}

	private void assertSymlinkTargetExists(String symlink, boolean targetIsDirectory) {
		FuseFileInfo fi = new MockFuseFileInfo();
		int returnCode = targetIsDirectory ? adapter.opendir(symlink, fi) : adapter.open(symlink, fi);
		if (returnCode == 0) {
			int err = targetIsDirectory ? adapter.releasedir(symlink, fi) : adapter.release(symlink, fi);
		}
		Assertions.assertEquals(0, returnCode);
	}

	private static class MockFuseFileInfo extends FuseFileInfo {

		public MockFuseFileInfo() {
			super(Runtime.getSystemRuntime());
		}
	}

	private Pointer mockPointer(ByteBuffer buf) {
		return new ByteBufferMemoryIO(Runtime.getSystemRuntime(), buf);
	}

}
