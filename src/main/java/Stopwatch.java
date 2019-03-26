import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class Stopwatch {
	private static final Ticker SYSTEM_TICKER = new Ticker();

	private static final class Ticker {
		/**
		 * Returns the number of nanoseconds elapsed since this ticker's fixed point of reference.
		 */
		public long read() {
			return System.nanoTime();
		}

	}

	private final Ticker ticker;
	private boolean isRunning;
	private long elapsedNanos;
	private long startTick;

	public static Stopwatch createUnstarted() {
		return new Stopwatch();
	}

	public static Stopwatch createStarted() {
		return new Stopwatch().start();
	}

	Stopwatch() {
		this.ticker = SYSTEM_TICKER;
	}

	public boolean isRunning() {
		return isRunning;
	}

	public Stopwatch start() {
		isRunning = true;
		startTick = ticker.read();
		return this;
	}

	public Stopwatch stop() {
		long tick = ticker.read();
		isRunning = false;
		elapsedNanos += tick - startTick;
		return this;
	}

	public Stopwatch reset() {
		elapsedNanos = 0;
		isRunning = false;
		return this;
	}

	private long elapsedNanos() {
		return isRunning ?
				ticker.read() - startTick + elapsedNanos :
				elapsedNanos;
	}

	public long elapsed(TimeUnit desiredUnit) {
		return desiredUnit.convert(elapsedNanos(), NANOSECONDS);
	}
}