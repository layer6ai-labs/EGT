import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class EGT {
	enum ALGO {
		EGT,
		KNN
	}

	static void logo() {
		System.out.println("\n.____                                  ________");
		System.out.println("|    |   _____  ___.__. ___________   /  _____/");
		System.out
				.println("|    |   \\__  \\<   |  |/ __ \\_  __ \\ /   __  \\");
		System.out.println(
				"|    |___ / __ \\\\___  \\  ___/|  | \\/ \\  |__\\  \\");
		System.out.println("|_______ (____  / ____|\\___  >__|     \\_____  /");
		System.out.println(
				"        \\/    \\/\\/         \\/               \\/\n");
	}

	public static void main(String[] args) throws IOException {
		logo();

		ArgumentParser parser = ArgumentParsers.newFor("EGT").build()
				.description(
						"EGT image retrieval algorithm.\nSee our CVPR 2019 paper \"Explore-Exploit Graph Traversal for Image Retrieval\".\n"
								+ "For updates and more, check out our github page https://github.com/layer6ai-labs/egt.")
				.defaultHelp(true);

		// positional
		parser.addArgument("input file").metavar("srcFile").type(String.class)
				.help("source file path").dest("srcFile");
		parser.addArgument("output file").metavar("outFile").type(String.class)
				.help("output file path").dest("outFile");

		// model parameter
		parser.addArgument("-k").metavar("k").dest("k").type(int.class)
				.setDefault(50).help("[int] number of neighbor in kNN");

		parser.addArgument("-q").metavar("numQuery").dest("numQuery")
				.type(int.class).setDefault(70).help("[int] number of query");

		parser.addArgument("-p").metavar("shortlist").dest("p").type(int.class)
				.setDefault(1000)
				.help("[int] number top results to apply algorithm (p in paper)");

		parser.addArgument("-t").metavar("tau").dest("thresh")
				.type(double.class).setDefault(40.0)
				.help("[float] threshold parameter tau");

		parser.addArgument("-H").metavar("header").dest("header")
				.type(int.class).setDefault(0).required(false)
				.help("number of header lines to skip");

		// model flags
		parser.addArgument("--sym").dest("symmetry").setDefault(true)
				.required(false).action(Arguments.storeTrue())
				.help("make kNN graph symmetric");
		parser.addArgument("--silent").dest("silent").setDefault(false)
				.required(false).action(Arguments.storeTrue())
				.help("number of header lines to skip");
		try {
			Namespace res = parser.parseArgs(args);
			final String clusterFile = res.getString("srcFile");
			final String outFile = res.getString("outFile");
			final int skip = res.getInt("header");
			final boolean silent = res.getBoolean("silent");
			final int k = res.getInt("k");
			final int N = res.getInt("p");
			final int nQ = res.getInt("numQuery");
			final boolean MAKE_SYMMETRIC = res.getBoolean("symmetry");
			final double thresh = res.getDouble("thresh");

			// TODO: change to argparse
			final boolean SILENT = false;
			final ALGO PRIM = ALGO.EGT;
			final boolean SORT_BY_RANSAC = false;

			EGTImpl egtSource = EGTImpl.readClusters(clusterFile, skip, silent);
			Speedometer timer = Speedometer.generalTimer().tic();
			Speedometer timerTotal = Speedometer.generalTimer().tic();

			if (SORT_BY_RANSAC)
				egtSource.sortAllNeighbors();
			final EGTImpl egt = MAKE_SYMMETRIC ?
					egtSource.getSubgraph(k).getSymmetric(nQ) :
					egtSource.getSubgraph(k);
			if (!SILENT)
				timer.tocAndTic("read cluster");
			int n = egt.g.length;

			List<String> queryLines = Files.lines(Paths.get(clusterFile))
					.limit(nQ).collect(Collectors.toList());

			AtomicInteger count = new AtomicInteger(0);
			Speedometer loopTimer = Speedometer.loopTimer("prim", n).tic();

			String[] queryHashes = new String[queryLines.size()];
			for (int i = 0; i < queryHashes.length; i++) {
				queryHashes[i] = queryLines.get(i).split(",", 2)[0];
			}
			egt.setQueryToSkip(queryHashes);

			//		for (int thresh : new int[]{thresh}) {
			if (SILENT == false)
				System.out
						.printf("\nRUNNING src[%s] prim[%s] make_sym[%b] sort_by_ransac[%b] thresh[%f] k[%d] N[%d]\n",
								clusterFile, PRIM.name(), MAKE_SYMMETRIC,
								SORT_BY_RANSAC, thresh, k, N);
			else
				//			System.out.printf("(%d,", PRINT_FOR_PGF_VAR_K ? k : thresh);
				timer.tic();
			try (BufferedWriter writer = new BufferedWriter(
					new FileWriter(outFile))) {
				writer.write("id,images\n");
				//			IntStream.range(0, queryLines.size()).forEach(i -> {
				for (int i = 0; i < queryLines.size(); i++) {
					String queryLine = queryLines.get(i);
					String[] queryParts = queryLine.split(",", 2);
					String hashQ = queryParts[0];
					int[] nodes = null;
					switch (PRIM) {

						case EGT:
							nodes = egt.primPaperEfficient(i, N, thresh, true);
							break;
						case KNN:
							nodes = egt.knn(i, N);
							break;
					}
					if (nodes.length != N) {
						//					EGT.MMSTNode[] test = egt.primPaper(i, k, nToRetrieve, thresh, online);
						//					System.out.printf("[WARN] did not reach nToRetrieve, len(nodes)=" + Integer.toString(nodes.length));
					}

					StringBuilder sb = new StringBuilder();
					sb.append(hashQ);
					sb.append(",");
					for (int j = 0; j < nodes.length; j++) {
						int node = nodes[j];
						if (j > 0) {
							sb.append(" ");
						}
						sb.append(egt.ind2hash[node]);
					}

					sb.append("\n");
					String line = sb.toString();
					synchronized (writer) {
						try {
							writer.write(line);
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					}
					int c = count.incrementAndGet();
					if (c % 100_000 == 0) {
						loopTimer.tocLoop(c);
					}
				}
			}
			if (!SILENT)
				timer.tocAndTic("prim");
			if (!SILENT)
				timerTotal.tocAndTic("\nprogram done");

		} catch (ArgumentParserException e) {
			parser.handleError(e);
		}
	}
}
