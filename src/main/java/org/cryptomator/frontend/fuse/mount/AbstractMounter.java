package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.frontend.fuse.AdapterFactory;
import org.cryptomator.frontend.fuse.FuseNioAdapter;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class AbstractMounter implements Mounter {

	@Override
	public synchronized Mount mount(Path directory, EnvironmentVariables envVars, boolean debug) throws CommandFailedException {
		FuseNioAdapter fuseAdapter = AdapterFactory.createReadWriteAdapter(directory, //
				AdapterFactory.DEFAULT_MAX_FILENAMELENGTH, //
				envVars.getFileNameTranscoder());
		try {
			CompletableFuture.runAsync(() -> fuseAdapter.mount(envVars.getMountPoint(), true, debug, envVars.getFuseFlags()), Executors.newSingleThreadExecutor())
					.whenComplete((voit, throwable) -> {
						//notify observer, i.e. afterMountExit.run();
						if (throwable != null) {
							//javadoc of whenComplete:
							//if this stage completed exceptionally and the supplied action throws an exception, then the returned stage completes exceptionally with this stage's exception.
							//hence, just throw something
							throw new RuntimeException(); //
						}
					}).get(1000, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (ExecutionException e) {
			e.printStackTrace();
			throw new CommandFailedException(e.getCause());
		} catch (TimeoutException e) {
			//up and runnning
			return createMountObject(fuseAdapter, envVars);
		}
		throw new CommandFailedException("Mounting failed for unknown reason.");
	}

	@Override
	public abstract String[] defaultMountFlags();

	@Override
	public abstract boolean isApplicable();

	protected abstract Mount createMountObject(FuseNioAdapter fuseNioAdapter, EnvironmentVariables envVars);
}
