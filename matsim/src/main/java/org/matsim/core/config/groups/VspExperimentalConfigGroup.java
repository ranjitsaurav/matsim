/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package org.matsim.core.config.groups;

import java.util.Map;
import java.util.TreeMap;

import org.matsim.core.config.Module;

/**
 * config group for experimental parameters. this group and its parameters should not be used outside of vsp.
 * @author dgrether
 */
public class VspExperimentalConfigGroup extends Module {

	private static final long serialVersionUID = 1L;

	public static final String GROUP_NAME = "vspExperimental";

	private static final String USE_ACTIVITY_DURATIONS = "useActivityDurations";

	private static final String INPUT_MZ05_FILE = "inputMZ05File";

	private static final String MODES_FOR_SUBTOURMODECHOICE = "modes";
	private static final String CHAIN_BASED_MODES = "chainBasedModes";

	private static final String OFFSET_WALK = "offsetWalk";
	
	
	private boolean useActivityDurations = true;
	
	private String inputMZ05File = "";
	
	private String modesForSubTourModeChoice = "car, pt";
	private String chainBasedModes = "car";

	private double offsetWalk = 6d;
	
	
	public VspExperimentalConfigGroup() {
		super(GROUP_NAME);
	}

	@Override
	protected Map<String, String> getComments() {
		Map<String,String> map = super.getComments();
		map.put(USE_ACTIVITY_DURATIONS, "Set this flag to false if the duration attribute of the activity should not be considered in QueueSimulation");
		
		map.put(INPUT_MZ05_FILE, "Set this filename of MZ05 daily analysis");
		
		map.put(MODES_FOR_SUBTOURMODECHOICE, "set the traffic mode option for subTourModeChoice");
		map.put(CHAIN_BASED_MODES, "set chainBasedModes, e.g. \"car,bike\", \"car\"");
		
		map.put(OFFSET_WALK, "set offset for mode \"walk\" in leg scoring function");
		return map;
	}

	@Override
	public String getValue(final String key) {
		if (USE_ACTIVITY_DURATIONS.equalsIgnoreCase(key)) {
			return Boolean.toString(this.isUseActivityDurations());
		} else if (INPUT_MZ05_FILE.equalsIgnoreCase(key))
			return this.getInputMZ05File();
		else if (MODES_FOR_SUBTOURMODECHOICE.equalsIgnoreCase(key))
			return this.getModesForSubTourModeChoice();
		else if (CHAIN_BASED_MODES.equalsIgnoreCase(key))
			return this.getChainBasedModes();
		else if (OFFSET_WALK.equalsIgnoreCase(key))
			return Double.toString(this.getOffsetWalk());
		throw new IllegalArgumentException(key);
	}

	@Override
	public void addParam(final String key, final String value) {
		if (USE_ACTIVITY_DURATIONS.equalsIgnoreCase(key)) {
			this.setUseActivityDurations(Boolean.parseBoolean(value));
		} else if (INPUT_MZ05_FILE.equalsIgnoreCase(key))
			this.setInputMZ05File(value);
		else if (MODES_FOR_SUBTOURMODECHOICE.equalsIgnoreCase(key))
			this.setModesForSubTourModeChoice(value);
		else if (CHAIN_BASED_MODES.equalsIgnoreCase(key))
			this.setChainBasedModes(value);
		else if (OFFSET_WALK.equalsIgnoreCase(key))
			this.setOffsetWalk(Double.parseDouble(value));
		else {
			throw new IllegalArgumentException(key);
		}
	}

	@Override
	protected final TreeMap<String, String> getParams() {
		TreeMap<String, String> map = new TreeMap<String, String>();
		map.put(USE_ACTIVITY_DURATIONS, getValue(USE_ACTIVITY_DURATIONS));
		
		map.put(INPUT_MZ05_FILE, getValue(INPUT_MZ05_FILE));
		
		map.put(MODES_FOR_SUBTOURMODECHOICE, getValue(MODES_FOR_SUBTOURMODECHOICE));
		map.put(CHAIN_BASED_MODES, getValue(CHAIN_BASED_MODES));
		
		map.put(OFFSET_WALK, getValue(OFFSET_WALK));
		return map;
	}

	public boolean isUseActivityDurations() {
		return useActivityDurations;
	}

	public void setUseActivityDurations(boolean useActivityDurations) {
		this.useActivityDurations = useActivityDurations;
	}

	public String getInputMZ05File() {
		return inputMZ05File;
	}

	public void setInputMZ05File(String inputMZ05File) {
		this.inputMZ05File = inputMZ05File;
	}

	public String getModesForSubTourModeChoice() {
		return modesForSubTourModeChoice;
	}

	public void setModesForSubTourModeChoice(String modesForSubTourModeChoice) {
		this.modesForSubTourModeChoice = modesForSubTourModeChoice;
	}

	public String getChainBasedModes() {
		return chainBasedModes;
	}

	public void setChainBasedModes(String chainBasedModes) {
		this.chainBasedModes = chainBasedModes;
	}

	public double getOffsetWalk() {
		return offsetWalk;
	}

	public void setOffsetWalk(double offsetWalk) {
		this.offsetWalk = offsetWalk;
	}

}
