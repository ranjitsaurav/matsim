/*
 * *********************************************************************** *
 * project: org.matsim.*                                                   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2015 by the members listed in the COPYING,        *
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
 * *********************************************************************** *
 */

package playground.boescpa.converters.osm.scheduleCreator;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.misc.Counter;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;

import java.io.*;
import java.util.*;

/**
 * The default implementation of PTStationCreator (using the Swiss-HAFAS-Schedule).
 *
 * @author boescpa
 */
public class PTScheduleCreatorDefaultV2 extends PTScheduleCreator {

	private static final double MINIMALOFFSETDEPARTURES = 8.0 * 60; // seconds

	private CoordinateTransformation transformWGS84toCH1903_LV03 = TransformationFactory.getCoordinateTransformation("WGS84", "CH1903_LV03");
	protected final Map<String, Integer> vehiclesUndefined = new HashMap<>();
	private final Set<Integer> bitfeldNummern = new HashSet<>();

	public PTScheduleCreatorDefaultV2(TransitSchedule schedule, Vehicles vehicles) {
		super(schedule, vehicles);
	}

	@Override
	public final void createSchedule(String osmFile, String hafasFolder, Network network, String vehicleFile) {
		log.info("Creating the schedule based on HAFAS...");

		// 1. Read all vehicles from vehicleFile:
		readVehicles(vehicleFile);
		// 2. Read all stops from HAFAS-BFKOORD_GEO
		readStops(hafasFolder + "/BFKOORD_GEO");
		// 3. Read all ids for work-day-routes from HAFAS-BITFELD
		readDays(hafasFolder + "/BITFELD");
		// 4. Create all lines from HAFAS-Schedule
		readLines(hafasFolder + "/FPLAN");
		// 5. Print undefined vehicles
		printVehiclesUndefined();
		// 6. Clean schedule
		removeNonUsedStopFacilities();
		uniteSameRoutesWithJustDifferentDepartures();
		cleanDepartures();
		cleanVehicles();

		log.info("Creating the schedule based on HAFAS... done.");
	}

	////////////////// Local Helpers /////////////////////

	/**
	 * Reads all the vehicle types from the file specified.
	 *
	 * @param vehicleFile from which the vehicle-specifications will be read. For an example of file-structure see test/input/playground/boescpa/converters/osm/scheduleCreator/TestPTScheduleCreatorDefault/VehicleData.csv.
	 */
	protected void readVehicles(String vehicleFile) {
		log.info("  Read vehicles...");
		try {
			BufferedReader readsLines = new BufferedReader(new FileReader(vehicleFile));
			// read header 1 and 2
			readsLines.readLine();
			readsLines.readLine();
			// start the actual readout:
			String newLine = readsLines.readLine();
			while (newLine != null) {
				String[] newType = newLine.split(";");
				// The first line without a key breaks the readout.
				if (newType.length == 0) {
					break;
				}
				// Create the vehicle:
				Id<VehicleType> typeId = Id.create(newType[0].trim(), VehicleType.class);
				VehicleType vehicleType = vehicleBuilder.createVehicleType(typeId);
				vehicleType.setLength(Double.parseDouble(newType[1]));
				vehicleType.setWidth(Double.parseDouble(newType[2]));
				vehicleType.setAccessTime(Double.parseDouble(newType[3]));
				vehicleType.setEgressTime(Double.parseDouble(newType[4]));
				if ("serial".matches(newType[5])) {
					vehicleType.setDoorOperationMode(VehicleType.DoorOperationMode.serial);
				} else if ("parallel".matches(newType[5])) {
					vehicleType.setDoorOperationMode(VehicleType.DoorOperationMode.parallel);
				}
				VehicleCapacity vehicleCapacity = vehicleBuilder.createVehicleCapacity();
				vehicleCapacity.setSeats(Integer.parseInt(newType[6]));
				vehicleCapacity.setStandingRoom(Integer.parseInt(newType[7]));
				vehicleType.setCapacity(vehicleCapacity);
				vehicleType.setPcuEquivalents(Double.parseDouble(newType[8]));
				vehicleType.setDescription(newType[9]);
				vehicles.addVehicleType(vehicleType);
				// Read the next line:
				newLine = readsLines.readLine();
			}
			readsLines.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		log.info("  Read vehicles... done.");
	}

	protected void readStops(String BFKOORD_GEOFile) {
		log.info("  Read transit stops...");
		try {
			BufferedReader readsLines = new BufferedReader(new InputStreamReader(new FileInputStream(BFKOORD_GEOFile), "latin1"));
			String newLine = readsLines.readLine();
			while (newLine != null) {
				/*Spalte Typ Bedeutung
				1−7 INT32 Nummer der Haltestelle
				9−18 FLOAT X-Koordinate
				20−29 FLOAT Y-Koordinate
				31−36 INT16 Z-Koordinate (optional)
				38ff CHAR Kommentarzeichen "%"gefolgt vom Klartext des Haltestellennamens (optional zur besseren Lesbarkeit)*/
				Id<TransitStopFacility> stopId = Id.create(newLine.substring(0, 7), TransitStopFacility.class);
				double xCoord = Double.parseDouble(newLine.substring(8, 18));
				double yCoord = Double.parseDouble(newLine.substring(19, 29));
				Coord coord = this.transformWGS84toCH1903_LV03.transform(new CoordImpl(xCoord, yCoord));
				String stopName = newLine.substring(39, newLine.length());
				createStop(stopId, coord, stopName);
				newLine = readsLines.readLine();
			}
			readsLines.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		log.info("  Read transit stops... done.");
	}

	private void createStop(Id<TransitStopFacility> stopId, Coord coord, String stopName) {
		TransitStopFacility stopFacility = this.scheduleBuilder.createTransitStopFacility(stopId, coord, false);
		stopFacility.setName(stopName);
		this.schedule.addStopFacility(stopFacility);
		//log.info("Added " + schedule.getFacilities().get(stopId).toString());
	}

	protected void readDays(String BITFELD) {
		log.info("  Read bitfeld numbers...");
		try {
			BufferedReader readsLines = new BufferedReader(new InputStreamReader(new FileInputStream(BITFELD), "latin1"));
			String newLine = readsLines.readLine();
			while (newLine != null) {
				/*Spalte Typ Bedeutung
				1−6 INT32 Bitfeldnummer
				8−103 CHAR Bitfeld (Binärkodierung der Tage, an welchen Fahrt, in Hexadezimalzahlen notiert.)*/
				int bitfeldnummer = Integer.parseInt(newLine.substring(0, 6));
				String bitfeld = newLine.substring(7, 103);
				// The following char positions are only valuable for HAFAS CH_2014_140413_1341 and must be changed to the new "only work weeks" for new HAFAS-Sets...
				int matches = (bitfeld.charAt(1) == 'F')? 1 : 0;
				matches += (bitfeld.charAt(6) == 'F')? 1 : 0;
				matches += (bitfeld.charAt(8) == 'F')? 1 : 0;
				matches += (bitfeld.charAt(13) == 'F')? 1 : 0;
				matches += (bitfeld.charAt(15) == 'F')? 1 : 0;
				if (matches >= 4) { // if driven in at least four of the selected five work weeks, the bitfeld is selected...
				/*if (bitfeld.charAt(1) == 'F'
						&& bitfeld.charAt(6) == 'F'
						&& bitfeld.charAt(8) == 'F'
						&& bitfeld.charAt(13) == 'F'
						&& bitfeld.charAt(15) == 'F') {*/
					this.bitfeldNummern.add(bitfeldnummer);
				}
				newLine = readsLines.readLine();
			}
			readsLines.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.bitfeldNummern.add(0);
		log.info("  Read bitfeld numbers... done.");
	}

	protected void readLines(String FPLAN) {
		log.info("  Read transit lines...");
		try {
			Map<Id<TransitLine>,PtLineFPLAN> linesFPLAN = new HashMap<>();
			PtRouteFPLAN currentRouteFPLAN = null;

			Counter counter = new Counter("FPLAN line # ");
			BufferedReader readsLines = new BufferedReader(new InputStreamReader(new FileInputStream(FPLAN), "latin1"));
			String newLine = readsLines.readLine();
			while (newLine != null) {
				if (newLine.charAt(0) == '*') {
					if (newLine.charAt(1) == 'Z') {
						// Initialzeile neue Fahrt
						/*Spalte Typ Bedeutung
						1−2 CHAR *Z
						4−8 INT32 Fahrtnummer
						10−15 CHAR Verwaltung (6-stellig); Die Verwaltungsangabe darf
						keine Leerzeichen enthalten.
						17−21 INT16 leer // Tatsächlich unterscheidet dieser Eintrag noch verschiedene Fahrtvarianten...
						23−25 INT16 Taktanzahl; gibt die Anzahl der noch folgenden Takte
						an.
						27−29 INT16 Taktzeit in Minuten (Abstand zwischen zwei Fahrten).*/
						// Get the appropriate transit line...
						Id<TransitLine> lineId = Id.create(newLine.substring(9, 15).trim(), TransitLine.class);
						PtLineFPLAN lineFPLAN;
						if (linesFPLAN.containsKey(lineId)) {
							lineFPLAN = linesFPLAN.get(lineId);
						} else {
							lineFPLAN = new PtLineFPLAN(lineId);
							linesFPLAN.put(lineId, lineFPLAN);
						}
						// Create the new route in this line...
						int routeNr = 0;
						Id<TransitRoute> routeId = Id.create(newLine.substring(3, 8).trim() + "_" + String.format("%03d", routeNr), TransitRoute.class);
						while (lineFPLAN.getIdRoutesFPLAN().contains(routeId)) {
							routeNr++;
							routeId = Id.create(newLine.substring(3, 8).trim() + "_" + String.format("%03d", routeNr), TransitRoute.class);
						}
						int numberOfDepartures = 0;
						int cycleTime = 0;
						try {
							numberOfDepartures = Integer.parseInt(newLine.substring(22, 25));
							cycleTime = Integer.parseInt(newLine.substring(26, 29));
						} catch (Exception e) {	}
						currentRouteFPLAN = new PtRouteFPLAN(lineId, routeId, numberOfDepartures, cycleTime);
						lineFPLAN.addPtRouteFPLAN(currentRouteFPLAN);
					} else if (newLine.charAt(1) == 'T') {
						// Initialzeile neue freie Fahrt (Linien welche nicht nach Taktfahrplan fahren...)
						log.error("*T-Line in HAFAS discovered. Please implement appropriate read out.");
					} else if (newLine.charAt(1) == 'A' && newLine.charAt(3) == 'V') {
						if (currentRouteFPLAN != null) {
							int localBitfeldnr = 0;
							if (newLine.substring(22, 28).trim().length() > 0) {
								localBitfeldnr = Integer.parseInt(newLine.substring(22, 28));
							}
							if (!this.bitfeldNummern.contains(localBitfeldnr)) {
								// Linie gefunden, die nicht werk-täglich verkehrt... => Ignorieren wir...
								linesFPLAN.get(currentRouteFPLAN.getLineId()).removePtRouteFPLAN(currentRouteFPLAN);
								currentRouteFPLAN = null;
							}
						}
					} else if (newLine.charAt(1) == 'G') {
						// Verkehrsmittelzeile
						/*Spalte Typ Bedeutung
						1−2 CHAR *G
						4−6 CHAR Verkehrsmittel bzw. Gattung
						8−14 [#]INT32 (optional) Laufwegsindex oder Haltestellennummer,
							ab der die Gattung gilt.
						16−22 [#]INT32 (optional) Laufwegsindex oder Haltestellennummer,
							bis zu der die Gattung gilt.
						24−29 [#]INT32 (optional) Index für das x. Auftreten oder
						Abfahrtszeitpunkt // 26-27 hour, 28-29 minute
						31−36 [#]INT32 (optional) Index für das x. Auftreten oder
						Ankunftszeitpunkt*/
						if (currentRouteFPLAN != null) {
							// Vehicle Id:
							Id<VehicleType> typeId = Id.create(newLine.substring(3, 6).trim(), VehicleType.class);
							VehicleType vehicleType = vehicles.getVehicleTypes().get(typeId);
							if (vehicleType == null) {
								Integer occurances = vehiclesUndefined.get(typeId.toString());
								if (occurances == null) {
									vehiclesUndefined.put(typeId.toString(), 1);
								} else {
									vehiclesUndefined.put(typeId.toString(), occurances + 1);
								}
							}
							currentRouteFPLAN.setUsedVehicle(typeId, vehicleType);
							// First Departure:
							int hourFirstDeparture = Integer.parseInt(newLine.substring(25, 27));
							int minuteFirstDeparture = Integer.parseInt(newLine.substring(27, 29));
							currentRouteFPLAN.setFirstDepartureTime(hourFirstDeparture, minuteFirstDeparture);
						} /*else {
							log.error("*G-Line before appropriate *Z-Line.");
						}*/
					}
				} else if (newLine.charAt(0) == '+') { // Regionszeile (Bedarfsfahrten)
					// We don't have this transport mode in  MATSim (yet). => Delete Route and if Line now empty, delete Line.
					log.error("+-Line in HAFAS discovered. Please implement appropriate read out.");
				} else { // Laufwegzeile
					/*Spalte Typ Bedeutung
					1−7 INT32 Haltestellennummer
					9−29 CHAR (optional zur Lesbarkeit) Haltestellenname
					30−35 INT32 Ankunftszeit an der Haltestelle (lt. Ortszeit der
							Haltestelle) // 32-33 hour, 34-35 minute
					37−42 INT32 Abfahrtszeit an Haltestelle (lt. Ortszeit der
					Haltestelle) // 39-40 hour, 41-42 minute
					44−48 INT32 Ab dem Halt gültige Fahrtnummer (optional)
							50−55 CHAR Ab dem Halt gültige Verwaltung (optional)
							57−57 CHAR (optional) "X", falls diese Haltestelle auf dem
					Laufschild der Fahrt aufgeführt wird.*/
					if (currentRouteFPLAN != null) {
						double arrivalTime = 0;
						try {
							arrivalTime = Double.parseDouble(newLine.substring(31, 33)) * 60 * 60 +
									Double.parseDouble(newLine.substring(33, 35)) * 60;
						} catch (Exception e) {	}
						double departureTime = 0;
						try {
							departureTime = Double.parseDouble(newLine.substring(38, 40)) * 60 * 60 +
									Double.parseDouble(newLine.substring(40, 42)) * 60;
						} catch (Exception e) {	}
						Id<TransitStopFacility> stopId = Id.create(newLine.substring(0, 7), TransitStopFacility.class);
						TransitStopFacility stopFacility = schedule.getFacilities().get(stopId);
						currentRouteFPLAN.addStop(stopId, stopFacility, arrivalTime, departureTime);
					} /*else {
						log.error("Laufweg-Line before appropriate *Z-Line.");
					}*/
				}
				newLine = readsLines.readLine();
				counter.incCounter();
			}
			readsLines.close();
			counter.printCounter();
			// Create lines:
			for (Id<TransitLine> transitLine : linesFPLAN.keySet()) {
				TransitLine line = linesFPLAN.get(transitLine).createLine();
				if (line != null) {
					schedule.addTransitLine(line);
				}
			}
			// Create vehicles:
			for (TransitLine line : schedule.getTransitLines().values()) {
				for (TransitRoute route : line.getRoutes().values()) {
					for (Departure departure : route.getDepartures().values()) {
						Id<Vehicle> vehicleId = departure.getVehicleId();
						Id<VehicleType> vehicleTypeId = Id.create(vehicleId.toString().split("_")[0], VehicleType.class);
						VehicleType vehicleType = vehicles.getVehicleTypes().get(vehicleTypeId);
						vehicles.addVehicle(vehicleBuilder.createVehicle(vehicleId, vehicleType));
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		log.info("  Read transit lines... done.");
	}

	protected void printVehiclesUndefined() {
		for (String vehicleUndefined : vehiclesUndefined.keySet()) {
			log.warn("Undefined vehicle " + vehicleUndefined + " occured in " + vehiclesUndefined.get(vehicleUndefined) + " routes.");
		}
	}

	private void removeNonUsedStopFacilities() {
		// Collect all used stop facilities:
		Set<Id<TransitStopFacility>> usedStopFacilities = new HashSet<>();
		for (TransitLine line : this.schedule.getTransitLines().values()) {
			for (TransitRoute route : line.getRoutes().values()) {
				for (TransitRouteStop stop : route.getStops()) {
					usedStopFacilities.add(stop.getStopFacility().getId());
				}
			}
		}
		// Check all stop facilities if not used:
		Set<TransitStopFacility> unusedStopFacilites = new HashSet<>();
		for (Id<TransitStopFacility> facilityId : this.schedule.getFacilities().keySet()) {
			if (!usedStopFacilities.contains(facilityId)) {
				unusedStopFacilites.add(this.schedule.getFacilities().get(facilityId));
			}
		}
		// Remove all stop facilities not used:
		for (TransitStopFacility facility : unusedStopFacilites) {
			this.schedule.removeStopFacility(facility);
		}
	}

	private void cleanVehicles() {
		final Set<Id<Vehicle>> usedVehicles = new HashSet<>();
		for (TransitLine line : this.schedule.getTransitLines().values()) {
			for (TransitRoute route : line.getRoutes().values()) {
				for (Departure departure : route.getDepartures().values()) {
					usedVehicles.add(departure.getVehicleId());
				}
			}
		}
		final Set<Id<Vehicle>> vehicles2Remove = new HashSet<>();
		for (Id<Vehicle> vehicleId : this.vehicles.getVehicles().keySet()) {
			if (!usedVehicles.contains(vehicleId)) {
				vehicles2Remove.add(vehicleId);
			}
		}
		for (Id<Vehicle> vehicleId : vehicles2Remove) {
			if (!usedVehicles.contains(vehicleId)) {
				this.vehicles.removeVehicle(vehicleId);
			}
		}
	}

	private void cleanDepartures() {
		for (TransitLine line : this.schedule.getTransitLines().values()) {
			for (TransitRoute route : line.getRoutes().values()) {
				final Set<Double> departureTimes = new HashSet<>();
				final List<Departure> departuresToRemove = new ArrayList<>();
				for (Departure departure : route.getDepartures().values()) {
					double dt = departure.getDepartureTime();
					if (departureTimes.contains(dt)) {
						departuresToRemove.add(departure);
					} else {
						departureTimes.add(dt);
					}
				}
				for (Departure departure2Remove : departuresToRemove) {
					route.removeDeparture(departure2Remove);
				}
			}
		}
	}

	protected void uniteSameRoutesWithJustDifferentDepartures() {
		for (TransitLine line : this.schedule.getTransitLines().values()) {
			// Collect all route profiles
			final Map<String, List<TransitRoute>> routeProfiles = new HashMap<>();
			for (TransitRoute route : line.getRoutes().values()) {
				String routeProfile = route.getStops().get(0).getStopFacility().getId().toString();
				for (int i = 1; i < route.getStops().size(); i++) {
					//routeProfile = routeProfile + "-" + route.getStops().get(i).toString() + ":" + route.getStops().get(i).getDepartureOffset();
					routeProfile = routeProfile + "-" + route.getStops().get(i).getStopFacility().getId().toString();
				}
				List profiles = routeProfiles.get(routeProfile);
				if (profiles == null) {
					profiles = new ArrayList();
					routeProfiles.put(routeProfile, profiles);
				}
				profiles.add(route);
			}
			/*// Check profiles and if the same, add latter to former.
			for (List<TransitRoute> routesToUnite : routeProfiles.values()) {
				// Preparation
				List<TransitRoute> routesOrderedInDecreasingOrderOfDepartures = orderRoutesInDecreasingOrderOfDepartures(routesToUnite);
				Set<Tuple<Double, Double>> blockedTimeWindows = new HashSet<>();
				// First take the route with the most departures and block the time window covered by this route for any other departures.
				TransitRoute finalRoute = routesOrderedInDecreasingOrderOfDepartures.get(0);
				blockedTimeWindows.add(new Tuple<>(getEarliestDepartureTime(finalRoute), getLatestDepartureTime(finalRoute)));
				// Then go through all routes in decreasing order of departures and if the time window isn't blocked by any previously selected route, select this route, block it's time window and add its departures.
				for (int i = 1; i < routesOrderedInDecreasingOrderOfDepartures.size(); i++) {
					TransitRoute routeToAdd = routesOrderedInDecreasingOrderOfDepartures.get(i);
					double earliestDepartureToCheck = getEarliestDepartureTime(routeToAdd);
					double latestDepartureToCheck = getLatestDepartureTime(routeToAdd);
					for (Tuple<Double,Double> timeWindow : blockedTimeWindows) {
						if (!(latestDepartureToCheck <= timeWindow.getFirst() || earliestDepartureToCheck >= timeWindow.getSecond())) {
							routeToAdd = null;
						}
					}
					if (routeToAdd != null) {
						blockedTimeWindows.add(new Tuple<>(getEarliestDepartureTime(routeToAdd), getLatestDepartureTime(routeToAdd)));
						for (Departure departure : routeToAdd.getDepartures().values()) {
							finalRoute.addDeparture(departure);
						}
					}
					line.removeRoute(routesOrderedInDecreasingOrderOfDepartures.get(i));
				}
			}*/
			// Check profiles and if the same, add latter to former.
			for (List<TransitRoute> routesToUnite : routeProfiles.values()) {
				TransitRoute finalRoute = routesToUnite.get(0);
				for (int i = 1; i < routesToUnite.size(); i++) {
					TransitRoute routeToAdd = routesToUnite.get(i);
					for (Departure departure : routeToAdd.getDepartures().values()) {
						finalRoute.addDeparture(departure);
					}
					line.removeRoute(routeToAdd);
				}
			}
		}
	}

	private Double getEarliestDepartureTime(TransitRoute route) {
		double earliestDeparture = Double.MAX_VALUE;
		for (Departure departure : route.getDepartures().values()) {
			if (departure.getDepartureTime() < earliestDeparture) {
				earliestDeparture = departure.getDepartureTime();
			}
		}
		return earliestDeparture - (0.5 * MINIMALOFFSETDEPARTURES);
	}

	private Double getLatestDepartureTime(TransitRoute route) {
		double latestDeparture = Double.MIN_VALUE;
		for (Departure departure : route.getDepartures().values()) {
			if (departure.getDepartureTime() > latestDeparture) {
				latestDeparture = departure.getDepartureTime();
			}
		}
		return latestDeparture + (0.5 * MINIMALOFFSETDEPARTURES);
	}

	private List<TransitRoute> orderRoutesInDecreasingOrderOfDepartures(final List<TransitRoute> routes) {
		final List<TransitRoute> orderedRoutes = new ArrayList<>();
		final List<TransitRoute> routesToOrder = new ArrayList<>();
		routesToOrder.addAll(routes);

		for (int i = 0; i < routes.size(); i++) {
			TransitRoute routeWithMostDepartures = null;
			for (TransitRoute route : routesToOrder) {
				if (routeWithMostDepartures == null || route.getDepartures().size() > routeWithMostDepartures.getDepartures().size()) {
					routeWithMostDepartures = route;
				}
			}
			routesToOrder.remove(routeWithMostDepartures);
			orderedRoutes.add(routeWithMostDepartures);
		}

		return orderedRoutes;
	}
}