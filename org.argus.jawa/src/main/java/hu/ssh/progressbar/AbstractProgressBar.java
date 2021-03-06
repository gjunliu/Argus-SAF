package hu.ssh.progressbar;

public abstract class AbstractProgressBar implements ProgressBar {
	protected final long totalSteps;

	private long actualSteps = 0;
	private long startTime = 0;

	private long lastUpdate = 0;
	private int lastUpdatePercent = 0;

	protected AbstractProgressBar(final long totalSteps) {
		this.totalSteps = totalSteps;
	}

	@Override
	public void start() {
		refresh();
	}

	@Override
	public final void tickOne() {
		tick(1);
	}

	@Override
	public final void tick(final long steps) {
		setStartTimeIfNotStarted();

		actualSteps += steps;

		if (isRefreshNeeded()) {
			refresh();
		}
	}

	@Override
	public final void refresh() {
		setStartTimeIfNotStarted();

		final Progress progress = getProgress();

		lastUpdate = System.currentTimeMillis() / 1000;
		lastUpdatePercent = (int) (progress.getPercentage() * 100);

		updateProgressBar(progress);
	}

	@Override
	public void complete() {
		setStartTimeIfNotStarted();

		actualSteps = totalSteps;
		refresh();
		finishProgressBar();
	}

	private Progress getProgress() {
		return new Progress(totalSteps, actualSteps, System.currentTimeMillis() - startTime);
	}

	private void setStartTimeIfNotStarted() {
		if (startTime == 0) {
			startTime = System.currentTimeMillis();
		}
	}

	private boolean isRefreshNeeded() {
		return lastUpdate != System.currentTimeMillis() / 1000 || lastUpdatePercent != (int) (actualSteps * 100 / totalSteps);

	}

	protected abstract void updateProgressBar(final Progress progress);

	protected abstract void finishProgressBar();
}
