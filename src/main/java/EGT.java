import net.sourceforge.argparse4j.ArgumentParsers;
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

	public static void main(String[] args) throws IOException {
		ArgumentParser parser = ArgumentParsers.newFor("EGT").build()
				.description(
						"Runst the EGT image retrieval algorithm, for more information see our CVPR 2019 paper \"Explore-Exploit Graph Traversal for Image Retrieval\" .");
		parser.addArgument("input file").metavar("srcFile").type(String.class)
				.help("Source file path").dest("clusterFile");
		parser.addArgument("output file").metavar("outFile").type(String.class)
				.help("Output file path").dest("outFile");
		parser.addArgument("--skip").metavar("f").type(int.class).setDefault(0)
				.help("Number of header lines to skip");
		parser.addArgument("--silent").dest("silent").setDefault(false)
				.help("Number of header lines to skip");
		parser.addArgument("--k").dest("k").type(int.class)
				.setDefault(50).help("k: number of neighbor in kNN");
		parser.addArgument("--nq").metavar("nQ").dest("numQuery")
				.type(int.class).setDefault(70).help("nQ: number of query");
		try {
			Namespace res = parser.parseArgs(args);
			final String clusterFile = res.getString("clusterFile");
			final String outFile = res.getString("outFile");
			final int skip = res.getInt("skip");
			final boolean silent = res.getBoolean("silent");
			final int k = res.getInt("k");
			final int nQ = res.getInt("numQuery");

			// TODO: change to argparse
			final boolean SILENT = false;
			final ALGO PRIM = ALGO.EGT;
			final boolean SORT_BY_RANSAC = false;
			final int thresh = 40;
			int N = 1000;
			boolean MAKE_SYMMETRIC = true;

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
						.printf("\nRUNNING src[%s] prim[%s] make_sym[%b] sort_by_ransac[%b] thresh[%d] k[%d] N[%d]\n",
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
