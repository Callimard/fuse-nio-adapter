package org.cryptomator.frontend.fuse.mount;

import com.google.common.collect.ObjectArrays;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

class LinuxMounter implements Mounter {

	private static final boolean IS_LINUX = System.getProperty("os.name").toLowerCase().contains("linux");

	@Override
	public Mount mount(Path directory, EnvironmentVariables envVars, String... additionalMountParams) throws CommandFailedException {
		LinuxMount mount = new LinuxMount(directory, envVars);
		mount.mount(additionalMountParams);
		return mount;
	}

	@Override
	public boolean isApplicable() {
		return IS_LINUX;
	}

	private static class LinuxMount extends AbstractMount {

		private static final Path USER_HOME = Paths.get(System.getProperty("user.home"));
		private static final String DEFAULT_REVEALCOMMAND_LINUX = "xdg-open";

		private final ProcessBuilder revealCommand;
		private final ProcessBuilder unmountCommand;
		private final ProcessBuilder unmountForcedCommand;

		private LinuxMount(Path directory, EnvironmentVariables envVars) {
			super(directory, envVars);
			Path mountPoint = envVars.getMountPath();
			String[] command = envVars.getRevealCommand().orElse(DEFAULT_REVEALCOMMAND_LINUX).split("\\s+");
			this.revealCommand = new ProcessBuilder(ObjectArrays.concat(command, mountPoint.toString()));
			this.unmountCommand = new ProcessBuilder("fusermount", "-u", mountPoint.getFileName().toString());
			this.unmountCommand.directory(mountPoint.getParent().toFile());
			this.unmountForcedCommand = new ProcessBuilder("fusermount", "-u", "-z", mountPoint.getFileName().toString());
			this.unmountForcedCommand.directory(mountPoint.getParent().toFile());
		}

		@Override
		protected String[] getFuseOptions() {
			ArrayList<String> mountOptions = new ArrayList<>(8);
			mountOptions.add(("-oatomic_o_trunc"));
			try {
				mountOptions.add("-ouid=" + Files.getAttribute(USER_HOME, "unix:uid"));
				mountOptions.add("-ogid=" + Files.getAttribute(USER_HOME, "unix:gid"));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			mountOptions.add("-oauto_unmount");
			mountOptions.add("-ofsname=CryptoFs");
			return mountOptions.toArray(new String[mountOptions.size()]);
		}

		@Override
		public ProcessBuilder getRevealCommand() {
			return revealCommand;
		}

		@Override
		public ProcessBuilder getUnmountCommand() {
			return unmountCommand;
		}

		@Override
		public ProcessBuilder getUnmountForcedCommand() {
			return unmountForcedCommand;
		}
	}
}
