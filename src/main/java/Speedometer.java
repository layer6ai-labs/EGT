

import java.util.concurrent.TimeUnit;

public class Speedometer {

	private static int ONE_M = 1_000_000;

	private String name;
	private int n;
	private Boolean doPrint = null;

	private Stopwatch timer;

	public static Speedometer loopTimer(String nameP, int nP) {
		return new Speedometer(nameP, nP);
	}

	public static Speedometer loopTimer(String nameP) {
		return new Speedometer(nameP, 0);
	}

	public static Speedometer generalTimer() {
		return new Speedometer(null, -1);
	}

	private Speedometer(String nameP, int nP) {
		n = nP;
		timer = Stopwatch.createUnstarted();
		name = nameP;
	}

	public synchronized Speedometer tic() {
		timer.reset().start();
		return this;
	}

	public synchronized double tocAndTic(String format, Object... args) {
		double elapsed = toc(format, args);
		timer.reset().start();
		return elapsed;
	}

	public synchronized double toc(String format, Object... args) {
		double elapsed = timer.elapsed(TimeUnit.NANOSECONDS) * 1e-9;
		System.out.printf("%s [%fs]\n", String.format(format, args), elapsed);
		return elapsed;
	}

	private static String formatNano(long nano) {
		if (nano < 0) {
			return Long.toString(nano);
		}
		TimeUnit base = TimeUnit.NANOSECONDS;

		long days = base.toDays(nano);
		nano -= TimeUnit.DAYS.toNanos(days);
		long hours = base.toHours(nano);
		nano -= TimeUnit.HOURS.toNanos(hours);
		long minutes = base.toMinutes(nano);
		nano -= TimeUnit.MINUTES.toNanos(minutes);
		// long seconds = base.toSeconds(nano);
		double ns = nano * 1e-9;

		StringBuilder sb = new StringBuilder(64);
		sb.append(days);
		sb.append(" Days ");
		sb.append(hours);
		sb.append(" Hours ");
		sb.append(minutes);
		sb.append(" Minutes ");
		sb.append((float) ns);
		sb.append(" Seconds ");

		return sb.toString();
	}

	private static String formatSeconds(float secondsF) {
		if (secondsF < 0) {
			return Float.toString(secondsF);
		}
		TimeUnit base = TimeUnit.SECONDS;
		int s = (int) Math.floor(secondsF);
		// float remainder = (float) (secondsF - s);

		long days = base.toDays(s);
		s -= TimeUnit.DAYS.toSeconds(days);
		long hours = base.toHours(s);
		s -= TimeUnit.HOURS.toSeconds(hours);
		long minutes = base.toMinutes(s);
		s -= TimeUnit.MINUTES.toSeconds(minutes);
		long secondsL = base.toSeconds(s);

		StringBuilder sb = new StringBuilder(64);
		if (days > 0) {
			sb.append(days);
			sb.append(" days ");
		}
		if (hours > 0 || days > 0) {
			sb.append(hours);
			sb.append(" hr ");
		}
		if (hours > 0 || days > 0 || minutes > 0) {
			sb.append(minutes);
			sb.append(" min ");
		}
		sb.append(secondsL + " sec");

		return sb.toString();
	}

	private transient int _ctr = 0;
	private transient double _s = 0;

	public synchronized void tocLoop(int ctr) {
		double s = timer.elapsed(TimeUnit.NANOSECONDS) * 1e-9;
		double s_inc = s - _s;
		double ms = s * 1000;

		double cur_spd = (ctr == _ctr) ? 0 : (s_inc) / (ctr - _ctr);
		// float remain = (float) ((_ctr == ctr) ? s * n / ctr
		// : cur_spd * (n - ctr));
		float remain = (float) (ctr == 0 ?
				cur_spd * (n - ctr) :
				s / ctr * n - s);
		String remainStr = remain <= 0 ? "N/A" : formatSeconds(remain);
		// if (doPrint == null) {
		// if estimated < 1s don't even bother printing status
		doPrint = s > 0;
		// }
		_ctr = ctr;
		_s = s;
		// if (doPrint == false) {
		// return;
		// }
		System.out
				.printf("%s [%.2f%%] elapsed[%.1fs (+%.1fs)] amm [%.1f/s] cur_spd [%.1f/s] remain [%s]\n",
						name, 100.0f * ctr / n, s, s_inc,
						s == 0 ? 0f : (float) ctr / s,
						cur_spd == 0 ? 0f : 1 / cur_spd, remainStr);
	}

}