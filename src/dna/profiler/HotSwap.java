package dna.profiler;

import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import dna.graph.Graph;
import dna.graph.datastructures.DataStructure;
import dna.graph.datastructures.DataStructure.AccessType;
import dna.graph.datastructures.DataStructure.ListType;
import dna.graph.datastructures.GraphDataStructure;
import dna.graph.datastructures.IDataStructure;
import dna.profiler.ProfilerMeasurementData.ProfilerDataType;
import dna.profiler.datatypes.ComparableEntry;
import dna.profiler.datatypes.ComparableEntryMap;
import dna.util.Config;
import dna.util.Log;

public class HotSwap {
	private static HotSwapMap slidingWindow = null;
	private static long lastFinishedBatch;
	private static int totalNumberOfBatches;
	private static boolean inFirstBatch = true;
	private static Map<Long, EnumMap<ListType, Class<? extends IDataStructure>>> manualSwitching = null;
	private static EnumMap<ListType, Class<? extends IDataStructure>> firstSwitch = null;
	private static int swapsDone = 0;

	private static double hotswapLowerBound = Config
			.getDouble("HOTSWAP_LOWER_BOUND");
	private static int hotswapWindowSize = Config.getInt("HOTSWAP_WINDOWSIZE");
	private static int maxNumberOfSwaps = Config
			.getInt("HOTSWAP_MAXNUMBER_OF_SWAPS");

	/**
	 * Three variables for storing the accesses onto underlying lists
	 */
	private static final int maxAccessListSize = Config
			.getInt("HOTSWAP_AMORTIZATION_COUNTER");
	private static ProfileEntry[] accessList;
	private static int currAccessListIndex = 0;

	public static void reset() {
		slidingWindow = new HotSwapMap();
		accessList = new ProfileEntry[maxAccessListSize];
		firstSwitch = null;
		inFirstBatch = true;
		swapsDone = 0;
	}

	public static void addNewResults() {
		if (slidingWindow == null) {
			reset();
		}

		if (maxNumberOfSwaps >= 0 && swapsDone >= maxNumberOfSwaps) {
			// We won't do more swaps, so don't add new results
			return;
		}

		if (!inFirstBatch
				|| Config
						.getBoolean("HOTSWAP_INCLUDE_FIRSTBATCH_FOR_EFFICIENCY_CHECK")) {
			ProfileEntry accesses = Profiler.getLastAccesses();
			accessList[currAccessListIndex] = accesses;
		}
		currAccessListIndex = (currAccessListIndex + 1) % maxAccessListSize;

		TreeSet<RecommenderEntry> latestRecommendations = Profiler
				.getRecommendations(Profiler.profilerDataTypeForHotSwap);
		if (latestRecommendations != null) {
			slidingWindow.put(latestRecommendations);
		}
		inFirstBatch = false;
	}

	private static int getNumberofAccumulatedAccesses(int amortizationCounter) {
		int res = 0;

		for (int i = maxAccessListSize - 1; (i >= 0 && amortizationCounter > 0); i--) {
			int index = (currAccessListIndex + i) % maxAccessListSize;
			if (accessList[index] != null) {
				res++;
			}
			amortizationCounter--;
		}

		return res;
	}

	private static ProfileEntry getAccumulatedAccesses(int amortizationCounter) {
		ProfileEntry res = new ProfileEntry();

		for (int i = maxAccessListSize - 1; (i >= 0 && amortizationCounter > 0); i--) {
			int index = (currAccessListIndex + i) % maxAccessListSize;
			if (accessList[index] != null) {
				res = res.add(accessList[index]);
			}
			amortizationCounter--;
		}

		return res;
	}

	public static void addForManualSwitching(long batchCount,
			EnumMap<ListType, Class<? extends IDataStructure>> newDatastructures) {
		if (manualSwitching == null) {
			manualSwitching = new TreeMap<>();
		}
		manualSwitching.put(batchCount, newDatastructures);
	}

	private static void doSwap(Graph g, GraphDataStructure newGDS) {
		GraphDataStructure gds = g.getGraphDatastructures();
		Log.info("        Old DS: " + gds.getStorageDataStructures(true));
		Log.info("        New DS: " + newGDS.getStorageDataStructures(true));
		DataStructure.disableContainsOnAddition();
		gds.switchDatastructures(newGDS, g);
		DataStructure.enableContainsOnAddition();

		if (firstSwitch == null) {
			firstSwitch = newGDS.getStorageDataStructures();
		}
		swapsDone++;
	}

	public static int getAmortizationCounter() {
		/**
		 * How many batches should we look into the future to see whether the
		 * currently used GDS performs worse than a new one, taking the costs of
		 * switching into account?
		 */
		long amortizationCounterInBatches = Config
				.getInt("HOTSWAP_AMORTIZATION_COUNTER");
		long maxNumberOfBatchesLeft = totalNumberOfBatches - lastFinishedBatch;
		int amortizationCounterToUse = (int) Math.min(
				amortizationCounterInBatches, maxNumberOfBatchesLeft);
		return amortizationCounterToUse;
	}

	private static ComparableEntryMap getSwappingCosts(
			GraphDataStructure currentGDS, GraphDataStructure recGDS) {
		/**
		 * Generate the costs for swapping, which is: for each changed list type
		 * the number of lists * (init + meanlistSize * add)
		 */

		ComparableEntryMap swappingCosts = ProfilerMeasurementData
				.getMap(ProfilerDataType.RuntimeBenchmark);
		for (ListType lt : ListType.values()) {
			if (recGDS.getListClass(lt) == currentGDS.getListClass(lt)) {
				continue;
			}

			int numberOfLists = Profiler.getNumberOfGeneratedLists(lt);
			double meanListSize = Profiler.getMeanSize(lt);
			int totalNumberOfElements = (int) (numberOfLists * meanListSize);

			ComparableEntry initCosts = recGDS.getCostData(lt, AccessType.Init,
					ProfilerDataType.RuntimeBenchmark);
			initCosts.setValues(numberOfLists, meanListSize, null);
			swappingCosts.add(initCosts.getMap());

			ComparableEntry addCosts = recGDS.getCostData(lt, AccessType.Add,
					ProfilerDataType.RuntimeBenchmark);
			addCosts.setValues(totalNumberOfElements, meanListSize, null);
			swappingCosts.add(addCosts.getMap());
		}
		return swappingCosts;
	}

	public static void trySwap(Graph g) {
		if (manualSwitching != null) {
			EnumMap<ListType, Class<? extends IDataStructure>> listTypes = manualSwitching
					.get(lastFinishedBatch);
			if (listTypes != null) {
				Log.info("     Should swap here according to manualSwitchingMap");
				GraphDataStructure newGDS = new GraphDataStructure(listTypes,
						null, null);
				doSwap(g, newGDS);
			}
			return;
		}

		if (lastFinishedBatch < Math.floor(hotswapLowerBound
				* hotswapWindowSize)) {
			return;
		}

		if (maxNumberOfSwaps >= 0 && swapsDone >= maxNumberOfSwaps) {
			Log.info("     maxNumberOfSwaps reached, won't swap no more");
			return;
		}

		int amortizationCounter = getAmortizationCounter();
		ProfileEntry accumulatedAccesses = getAccumulatedAccesses(amortizationCounter);
		int numberOfAccumulatedAccesses = getNumberofAccumulatedAccesses(amortizationCounter);
		double prefactor = 0;
		if (amortizationCounter > 0) {
			prefactor = amortizationCounter / numberOfAccumulatedAccesses;
		}

		TreeSet<RecommenderEntry> entrySet = slidingWindow.getRecommendations();
		for (RecommenderEntry entry : entrySet) {
			ComparableEntryMap lastOwnCosts = Profiler
					.getLastCosts(Profiler.profilerDataTypeForHotSwap);
			ComparableEntryMap recCosts = entry
					.getCosts(Profiler.profilerDataTypeForHotSwap);

			if (!entry.getDatastructures().equals(
					g.getGraphDatastructures().getStorageDataStructures())) {
				Log.info("     Recommendation based on "
						+ Profiler.profilerDataTypeForHotSwap
						+ " could swap to "
						+ entry.getGraphDataStructure()
								.getStorageDataStructures(true));
				Log.info("       " + Profiler.profilerDataTypeForHotSwap
						+ " costs in last batch with current combination: "
						+ lastOwnCosts + ", with recommended entry: "
						+ recCosts);

				GraphDataStructure currentGDS = g.getGraphDatastructures();
				GraphDataStructure newGDS = entry.getGraphDataStructure();

				if (isSwapEfficient(accumulatedAccesses, prefactor, currentGDS,
						newGDS)) {
					ComparableEntryMap swappingCosts = getSwappingCosts(
							currentGDS, newGDS);
					Log.info("       Swapping looks efficient, so do it now at RuntimeBenchmark costs of "
							+ swappingCosts);
					doSwap(g, newGDS);
					return;
				} else {
					Log.info("       Skip the swap, it is inefficient");
				}
			}
		}
	}

	private static boolean isSwapEfficient(ProfileEntry accesses,
			double prefactor, GraphDataStructure currentGDS,
			GraphDataStructure recGDS) {
		int amortizationCounterToUse = getAmortizationCounter();
		Log.debug("    Check whether the swap will be amortized within "
				+ amortizationCounterToUse + " batches by runtime costs");

		/**
		 * Generate the costs for the current state
		 */
		ComparableEntryMap currentStateCosts = accesses.combinedComplexity(
				ProfilerDataType.RuntimeBenchmark, currentGDS, null);

		/**
		 * Generate the costs for the recommended state
		 */
		ComparableEntryMap recStateCosts = accesses.combinedComplexity(
				ProfilerDataType.RuntimeBenchmark, recGDS, null);

		ComparableEntryMap swappingCosts = getSwappingCosts(currentGDS, recGDS);
		currentStateCosts.multiplyBy(prefactor);
		recStateCosts.multiplyBy(prefactor);

		Log.debug("        Total costs with current GDS: " + currentStateCosts
				+ ", total swapping costs: " + swappingCosts
				+ ", total costs with recommended GDS: " + recStateCosts);
		recStateCosts.add(swappingCosts);
		Log.debug("        Total costs with NEW GDS, incl swap: "
				+ recStateCosts);

		boolean isEfficient = recStateCosts.compareTo(currentStateCosts) < 0;
		return isEfficient;
	}

	public static void setLastFinishedBatch(long batchTimestamp) {
		lastFinishedBatch = batchTimestamp;
	}

	public static void setTotalNumberOfBatches(int n) {
		totalNumberOfBatches = n;
	}

	public static EnumMap<ListType, Class<? extends IDataStructure>> getFirstSwitch() {
		return firstSwitch;
	}

}
