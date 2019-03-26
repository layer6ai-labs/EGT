

//import ai.layer6.ml.core.utils.MLConcurrentUtils.Async;
//import com.google.common.collect.Lists;
//import com.google.common.collect.Maps;
//import com.google.common.collect.Sets;
//import landmark2018.common.Speedometer;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class EGTImpl implements Serializable {

	private static class FloatElement {
		int ind;
		float val;

		private FloatElement(int indexP, float valP) {
			ind = indexP;
			val = valP;
		}

		public int getIndex() {
			return ind;
		}

		public float getValue() {
			return val;
		}
	}

	private static final Comparator<Entry<MMSTNode>> DESCENDING_MMST = (o1, o2) -> {
		int r = Double.compare(o2.value.score, o1.value.score);
		if (r == 0)
			return Long.compare(o1.order, o2.order);
		return r;
	};

	public static class MMSTNode implements Serializable {
		int index;
		double score;

		public MMSTNode(int indexP, double scoreP) {
			index = indexP;
			score = scoreP;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			MMSTNode mmstNode = (MMSTNode) o;
			return index == mmstNode.index;
		}

		@Override
		public int hashCode() {
			return Objects.hash(index);
		}
	}

	MMSTNode[][] g;
	Map<String, Integer> hash2ind;
	String[] ind2hash;
	transient boolean[] indIsQuery;

	protected EGTImpl() {
	}

	public EGTImpl(MMSTNode[][] graph, Map<String, Integer> hash2indP) {

		g = graph;
		hash2ind = hash2indP;
		if (hash2ind != null) {
			ind2hash = new String[hash2ind.size()];
			indIsQuery = new boolean[hash2ind.size()];
			for (Map.Entry<String, Integer> entry : hash2ind.entrySet()) {
				ind2hash[entry.getValue()] = entry.getKey();
			}

		}
	}

	public EGTImpl deepCopyG() {
		MMSTNode[][] g2 = new MMSTNode[g.length][];
		IntStream.range(0, g2.length).parallel().forEach(i -> {
			g2[i] = new MMSTNode[g[i].length];
			for (int j = 0; j < g[i].length; j++) {
				MMSTNode node = g2[i][j];
				g2[i][j] = new MMSTNode(node.index, node.score);
			}
		});
		EGTImpl copy = shallowNewCopy();
		copy.g = g2;
		return copy;
	}

	public void sortAllNeighbors() {
		Comparator<MMSTNode> descendingScore = (o1, o2) -> Double
				.compare(o2.score, o1.score);
		IntStream.range(0, g.length).parallel()
				.forEach(i -> Arrays.sort(g[i], descendingScore));
	}

	public EGTImpl getSymmetric(int skip) {
		Map<Integer, LinkedHashMap<Integer, MMSTNode>> sym = new HashMap();
		for (int i = 0; i < g.length; i++) {
			LinkedHashMap<Integer, MMSTNode> set = new LinkedHashMap<>();
			for (int j = 0; j < g[i].length; j++) {
				set.put(g[i][j].index, g[i][j]);
			}
			sym.put(i, set);
		}
		for (int i = skip; i < g.length; i++) {
			for (MMSTNode node : g[i]) {
				if (node.index >= skip) {
					LinkedHashMap<Integer, MMSTNode> map = sym.get(node.index);
					if (map.containsKey(i) == false) {
						// make symmetric
						map.put(node.index, new MMSTNode(i, node.score));
					} else {
						MMSTNode nodeSym = map.get(i);
						map.put(node.index, new MMSTNode(i,
								Math.max(node.score, nodeSym.score)));
					}
				}
			}
		}
		EGTImpl s = shallowNewCopy();
		s.g = new MMSTNode[g.length][];

		IntStream.range(0, g.length).parallel().forEach(i -> {
			LinkedHashMap<Integer, MMSTNode> sy = sym.get(i);
			s.g[i] = new MMSTNode[sy.size()];
			sy.values().toArray(s.g[i]);
		});

		return s;
	}

	public EGTImpl getSubgraph(final int k) {
		EGTImpl sub = shallowNewCopy();
		sub.g = new MMSTNode[g.length][k];
		IntStream.range(0, g.length).parallel()
				.forEach(i -> System.arraycopy(g[i], 0, sub.g[i], 0, k));
		return sub;
	}

	public EGTImpl shallowNewCopy() {
		EGTImpl copy = new EGTImpl();
		copy.indIsQuery = indIsQuery;
		copy.ind2hash = ind2hash;
		copy.hash2ind = hash2ind;
		copy.g = g;
		return copy;

	}

	public void setNewHash2Ind(Map<String, Integer> hash2indNew) {
		if (hash2indNew.size() != hash2ind.size()) {
			throw new IllegalArgumentException(
					"new hash2ind map is different in size");
		}
		int n = hash2indNew.size();
		// compute ind swap
		int[] ind2indNew = new int[n];
		IntStream.range(0, n).parallel().forEach(i -> {
			ind2indNew[i] = hash2indNew.get(ind2hash[i]);
		});
		// swap all inds in g
		IntStream.range(0, g.length).forEach(i -> {
			for (MMSTNode node : g[i]) {
				node.index = ind2indNew[node.index];
			}
		});
		hash2ind = hash2indNew;
		// set new ind2hash
		ind2hash = new String[n];
		for (Map.Entry<String, Integer> entry : hash2indNew.entrySet()) {
			ind2hash[entry.getValue()] = entry.getKey();
		}
	}

	public void setQueryToSkip(String[] queryHash) {
		indIsQuery = new boolean[ind2hash.length];
		for (String q : queryHash) {
			Integer indQ = hash2ind.get(q);
			if (indQ != null) {
				indIsQuery[indQ] = true;
			}
		}
	}

	public MMSTNode[] parseNN(String[] parts) {
		MMSTNode[] nn = new MMSTNode[parts.length / 2];
		for (int i = 0; i < parts.length; i += 2) {
			String candidate = parts[i];
			int inliers = Integer.parseInt(parts[i + 1]);
			int indC = hash2ind.get(candidate);
			nn[i / 2] = new MMSTNode(indC, inliers);
		}
		return nn;
	}

	private static class Entry<K> {
		private final static AtomicLong seq = new AtomicLong(0);
		final long order;
		final K value;

		static void reset() {
			seq.set(0);
		}

		public Entry(final K valueP) {
			value = valueP;
			order = seq.incrementAndGet();
		}

	}

	//	THRESH 40
	//	prim [0.283679s]
	//			>> roxford5k: mAP E: 89.29, M: 80.71, H: 63.61
	//			>> roxford5k: mP@k[10] E: [95.74], M: [94.3], H: [83.88]
	public int[] primPaper(int query, int n, int thresh, boolean online) {
		PriorityQueue<Entry<MMSTNode>> H = new PriorityQueue<>(n,
				DESCENDING_MMST);
		LinkedHashSet<Integer> Qu = new LinkedHashSet<>();
		List<Integer> V = new ArrayList();

		Supplier<Boolean> it = () -> (n <= 0 || Qu.size() < n) && (H.size() > 0
				|| V.size() > 0);
		Predicate<Integer> onlineIsGood = (v) -> (online == false || (
				indIsQuery[v] == false || v == query));

		V.add(query);
		BitSet SQ = new BitSet(g.length);
		SQ.set(query);

		do {
			for (int v : V) {
				for (MMSTNode node : g[v]) {
					if (Qu.contains(node.index) == false) {
						H.add(new Entry(node));
					}
				}
			}
			V.clear();
			if (H.isEmpty()) {
				break;
			}

			do {
				int v = H.poll().value.index;
				if (Qu.contains(v) == false && onlineIsGood.test(v)
						&& SQ.get(v) == false) {
					V.add(v);
				}
				if (indIsQuery[v] == false && Qu.contains(v) == false) {
					Qu.add(v);
				}
				if (indIsQuery[v]) {
					SQ.set(v);
				}
			} while (it.get() && H.isEmpty() == false
					&& H.peek().value.score > thresh);

		} while (it.get());
		return Qu.stream().mapToInt(x -> x).toArray();

	}

	private LinkedHashSet<MMSTNode> prim(int query, int n, double thresh,
			boolean online) {
		PriorityQueue<Entry<MMSTNode>> H = new PriorityQueue<>(n,
				DESCENDING_MMST);
		BitSet S = new BitSet(g.length);
		LinkedHashSet<MMSTNode> Qu = new LinkedHashSet<>();
		List<Integer> V = new ArrayList();
		double[] E = new double[g.length];
		Arrays.fill(E, Double.NEGATIVE_INFINITY);

		Supplier<Boolean> it = () -> (n <= 0 || Qu.size() < n) && (H.size() > 0
				|| V.size() > 0);
		Predicate<MMSTNode> onlineIsGood = (v) -> (online == false || (
				indIsQuery[v.index] == false || v.index == query));

		V.add(query);
		S.set(query);
		do {
			for (int v : V) {
				for (MMSTNode node : g[v]) {
					if (S.get(node.index) == false
							&& E[node.index] < node.score) {
						H.add(new Entry(node));
						E[node.index] = node.score;
					}
				}
			}
			V.clear();
			if (H.isEmpty()) {
				break;
			}

			do {
				MMSTNode v = H.poll().value;
				if (indIsQuery[v.index] == false && Qu.contains(v) == false) {
					Qu.add(v);
				}
				if (S.get(v.index) == false && onlineIsGood.test(v)) {
					V.add(v.index);
				}
				S.set(v.index);
			} while (it.get() && H.isEmpty() == false
					&& H.peek().value.score > thresh);

		} while (it.get());

		return Qu;

	}

	public int[] primPaperEfficient(int query, int n, double thresh,
			boolean online) {
		LinkedHashSet<MMSTNode> Qu = prim(query, n, thresh, online);
		return Qu.stream().mapToInt(x -> x.index).toArray();

	}

	//	THRESH 40
	//	prim [0.357976s]
	//			>> roxford5k: mAP E: 89.26, M: 81.18, H: 64.27
	//			>> roxford5k: mP@k[10] E: [95.59], M: [94.75], H: [84.18]
	//

	public int[] knn(int query, int k) {

		MMSTNode[] nn = g[query];
		int[] result = new int[k <= 0 ? nn.length : Math.min(k, nn.length)];
		for (int i = 0; i < result.length; i++) {
			result[i] = nn[i].index;
		}
		return result;
	}

	public static EGTImpl readClusters(FloatElement[][] rankings) {
		Map<Integer, Integer> itemId2Ind = new HashMap();
		int qLen = rankings.length;
		// build just user(query)
		MMSTNode[][] gq = new MMSTNode[qLen][];
		AtomicInteger itemIdCounter = new AtomicInteger(qLen);
		for (int i = 0; i < rankings.length; i++) {
			if (rankings[i] != null) {
				gq[i] = new MMSTNode[rankings[i].length];
				for (int j = 0; j < rankings[i].length; j++) {
					FloatElement preds = rankings[i][j];
					int id = preds.getIndex();
					Integer ind = itemId2Ind.get(id);
					if (ind == null) {
						// first time encountering this item
						ind = itemIdCounter.getAndIncrement();
						itemId2Ind.put(id, ind);
					}
					gq[i][j] = new MMSTNode(ind, preds.getValue());
				}
			}
		}
		// build item
		List<MMSTNode>[] gi = new List[itemIdCounter.get() - qLen];
		for (int i = 0; i < rankings.length; i++) {
			if (rankings[i] != null) {
				for (int j = 0; j < rankings[i].length; j++) {
					FloatElement preds = rankings[i][j];
					int id = preds.getIndex();
					int ind = itemId2Ind.get(id) - qLen;
					if (gi[ind] == null) {
						gi[ind] = new ArrayList();
					}
					gi[ind].add(new MMSTNode(i, preds.getValue()));
				}
			}
		}
		// merge

		MMSTNode[][] g = new MMSTNode[itemIdCounter.get()][];
		System.arraycopy(gq, 0, g, 0, gq.length);
		for (int i = 0; i < gi.length; i++) {
			if (gi[i] != null) {
				int j = i + qLen;
				g[j] = gi[i].toArray(new MMSTNode[gi[i].size()]);
			}
		}
		EGTImpl egt = new EGTImpl(g, null);
		egt.indIsQuery = new boolean[g.length];
		Arrays.fill(egt.indIsQuery, 0, qLen, true);
		return egt;
	}

	public static EGTImpl readClusters(String clusterFile, int skip, boolean silent)
			throws IOException {
		Speedometer timer = Speedometer.generalTimer().tic();

		int count = 0;

		List<String> allLines = Files.readAllLines(Paths.get(clusterFile));
		allLines = allLines.subList(skip, allLines.size());
		if (!silent)
			timer.tocAndTic("read clusterFile");
		int n = allLines.size();
		MMSTNode[][] graph = new MMSTNode[n][];
		Map<String, Integer> hash2ind = new HashMap();
		String[] candidatesAndInliersLines = new String[n];
		for (int i = 0; i < n; i++) {
			String[] parts = allLines.get(i).split(",", 2);
			hash2ind.put(parts[0], i);
			candidatesAndInliersLines[i] = parts[1].trim();
		}
		if (!silent)
			timer.tocAndTic("read splits");
		count = n;

		Speedometer loopTimer = Speedometer.loopTimer("mmst parse", n).tic();
		final int prebuild_k = candidatesAndInliersLines[0].split(" ").length;
		for (int row = 0; row < n; row++) {
			String[] candidatesAndInliers = candidatesAndInliersLines[row]
					.split(" ");
			if (candidatesAndInliers.length != prebuild_k) {
				System.out
						.printf("warning: row[%d] has pairs [%d]!=[%d]in row 0\n",
								row, candidatesAndInliers.length / 2,
								prebuild_k / 2);
				throw new RuntimeException("inconsistent knn prebuidl file !");
			}
			graph[row] = new MMSTNode[candidatesAndInliers.length / 2];
			for (int i = 0; i < candidatesAndInliers.length; i += 2) {
				String candidate = candidatesAndInliers[i];
				int inliers = Integer.parseInt(candidatesAndInliers[i + 1]);
				Integer indC = hash2ind.get(candidate);
				if (indC == null) {
					indC = count;
					hash2ind.put(candidate, indC);
					count++;
				}
				graph[row][i / 2] = new MMSTNode(indC, inliers);
			}
			if (!silent)
				if ((row + 1) % 100000 == 0) {
					loopTimer.tocLoop(row);
				}
		}
		if (!silent)
			timer.tocAndTic("parsed candidates");
		return new EGTImpl(graph, hash2ind);
	}



}
