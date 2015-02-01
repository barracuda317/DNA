package dna.graph.generators.traffic;


import org.joda.time.DateTime;

import dna.updates.generators.traffic.Helpers;

public class TrafficConfig {
	
	private DateTime initDateTime;
	private int stepSize;
	
	// Auswahl des Modells und Modus
	private TrafficModel model;
	private String graphGeneratorName;
	private String batchGeneratorName;
	private TrafficModi modus;
	private double treshold; // Schwellwert, ab dem ein Knoten �berlastet ist

	// Parameter f�r Tages-Modus
	private boolean[] daySelection;
	private int timeRange;
	private DateTime holidayStart = new DateTime(2014,10,6,7,0,0);
	private int observationWeeks = 1;
	private int observationDays = Helpers.weekToDay(observationWeeks, daySelection);
	
	private int[] nodesFilter;
	
	//Verwende reale Maximalwerte oder Dummy-Maximalwerte
	private boolean dummyMax = false;
	
	// Parameter f�r die Simulation	
	private TrafficUpdate trafficUpdate;
	/**
	 * Basis-Konstruktor
	 * @param modus
	 * @param nodesFilter
	 * @param initDateTime
	 */
	private TrafficConfig(TrafficModi modus, TrafficModel model, double treshold, int[] nodesFilter, DateTime initDateTime ){
		this.modus = modus;
		this.model = model;
		this.treshold = treshold;
		this.nodesFilter = nodesFilter;
		this.initDateTime = initDateTime;
		
		// Namensgenerierung

		switch (model) {
		case CrossroadModel:
			graphGeneratorName = "CrossroadGraph";
			batchGeneratorName = "CrossroadBatch";
			break;
			
		case WayModel:
			graphGeneratorName = "InputWayGraph";
			batchGeneratorName = "InputWayBatch";
			break;
			
		case SensorModel:
			graphGeneratorName = "SensorGraph";
			batchGeneratorName = "SensorBatch";
			break;
		
		default:
			graphGeneratorName = "DefaultTrafficGraph";
			batchGeneratorName = "DefaultTrafficBatch";
			break;
		}
	}
	
	/**
	 * Konstruktor für den Continuous-Modus
	 * @param modus
	 * @param nodesFilter
	 * @param initDateTime
	 * @param stepsize
	 */
	private TrafficConfig(TrafficModi modus, TrafficModel model, double treshold, int[] nodesFilter, DateTime initDateTime, int stepsize){
		this(modus,model,treshold,nodesFilter,initDateTime);
		this.stepSize = stepsize;
	}
	/**
	 * Konstruktor für den TimeRange-Modus
	 * @param modus
	 * @param treshold
	 * @param nodesFilter
	 * @param initDateTime
	 * @param timeRange
	 * @param daySelection
	 * @param holidayStart
	 */
	private TrafficConfig(TrafficModi modus, TrafficModel model, double treshold, int[] nodesFilter, DateTime initDateTime, int timeRange, boolean[] daySelection, DateTime holidayStart){
		this(modus,model,treshold,nodesFilter,initDateTime);
		this.timeRange = timeRange;
		this.daySelection = daySelection;
		this.holidayStart = holidayStart;
	}
	
	/**
	 * Konsruktor für den Simulations-Modus
	 * @param modus
	 * @param treshold
	 * @param nodesFilter
	 * @param initDateTime
	 * @param trafficUpdate
	 */
	private TrafficConfig(TrafficModi modus, TrafficModel model, double treshold, int[] nodesFilter, DateTime initDateTime, TrafficUpdate trafficUpdate){
		this(modus,model,treshold,nodesFilter,initDateTime);
		this.trafficUpdate = trafficUpdate;
	}
	
	
	public static TrafficConfig getContinousConfig(TrafficModel model, double treshold, int[] nodesFilter, DateTime initDateTime, int stepsize){
		return new TrafficConfig(TrafficModi.Continuous,model,treshold, nodesFilter, initDateTime, stepsize);
	}
	
	public static TrafficConfig getDayTimeRangeConfig(TrafficModel model, double treshold, int[] nodesFilter, DateTime initDateTime, int timeRange, boolean[] daySelection, DateTime holidayStart){
		return new TrafficConfig(TrafficModi.DayTimeRange, model, treshold, nodesFilter, initDateTime, timeRange, daySelection, holidayStart);
	}
	
	public static TrafficConfig getAggregationConfig(){
		return null;
	}
	
	public static TrafficConfig getSimulationConfig(TrafficModel model, double treshold, int[] nodesFilter, DateTime initDateTime, TrafficUpdate trafficUpdate){
		return new TrafficConfig(TrafficModi.Simulation, model, treshold, nodesFilter, initDateTime, trafficUpdate);
	}
	
	public TrafficModi getModus(){
		return modus;
	}
	
	public String getGraphName(){
		return graphGeneratorName;
	}
	
	public String getBatchName(){
		return batchGeneratorName;
	}
	
	public DateTime getInitDateTime(){
		return initDateTime;
	}
	
	public int getStepSize(){
		return stepSize;
	}
	
	public int getTimeRange(){
		return timeRange;
	}
	
	public TrafficUpdate getTrafficUpdate(){
		return trafficUpdate;
	}
	
	public int[] getNodesFilter(){
		return nodesFilter;
	}
	
	public double getTreshold(){
		return treshold;
	}

}
