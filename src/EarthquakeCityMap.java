package module6;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.fhpotsdam.unfolding.UnfoldingMap;
import de.fhpotsdam.unfolding.data.Feature;
import de.fhpotsdam.unfolding.data.GeoJSONReader;
import de.fhpotsdam.unfolding.data.PointFeature;
import de.fhpotsdam.unfolding.geo.Location;
import de.fhpotsdam.unfolding.marker.AbstractShapeMarker;
import de.fhpotsdam.unfolding.marker.Marker;
import de.fhpotsdam.unfolding.marker.MultiMarker;
import de.fhpotsdam.unfolding.providers.Google;
import de.fhpotsdam.unfolding.providers.MBTilesMapProvider;
import de.fhpotsdam.unfolding.utils.MapUtils;
import parsing.ParseFeed;
import processing.core.PApplet;

public class EarthquakeCityMap extends PApplet {
	
	
	private static final long serialVersionUID = 1L;

	// if working without an internet connection
	private static final boolean offline = false;
	public static String mbTilesString = "blankLight-1-3.mbtiles";
	
	
	//feed with magnitude 2.5+ Earthquakes
	private String earthquakesURL = "http://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_week.atom";
	
	// The files containing city names and info and country names and info
	private String cityFile = "city-data.json";
	private String countryFile = "countries.geo.json";
	
	// The map
	private UnfoldingMap map;
	
	// Markers for each city
	private List<Marker> cityMarkers;
	// Markers for each earthquake
	private List<Marker> quakeMarkers;

	// A List of country markers
	private List<Marker> countryMarkers;
	
	private CommonMarker lastSelected;
	private CommonMarker lastClicked;
	private int quakesNearby;
	private double avgMagnitude;
	private Marker recentQuake;
	
	private int baseX;
	private int baseY;
	
	
	public void setup() {		
		// Initializing canvas and map tiles
		size(900, 700, OPENGL);
		
		if (offline) {
		    map = new UnfoldingMap(this, 200, 50, 650, 600, new MBTilesMapProvider(mbTilesString));
		    earthquakesURL = "2.5_week.atom";
		}
		else {
			map = new UnfoldingMap(this, 200, 50, 650, 600, new Google.GoogleMapProvider());
		}
		MapUtils.createDefaultEventDispatcher(this, map);

		// test data
		earthquakesURL = "data.atom";
		
		
		//  Reading in earthquake data and geometric properties
		List<Feature> countries = GeoJSONReader.loadData(this, countryFile);
		countryMarkers = MapUtils.createSimpleMarkers(countries);
		
		List<Feature> cities = GeoJSONReader.loadData(this, cityFile);
		cityMarkers = new ArrayList<Marker>();
		for(Feature city : cities) {
		  cityMarkers.add(new CityMarker(city));
		}
	    
	    List<PointFeature> earthquakes = ParseFeed.parseEarthquake(this, earthquakesURL);
	    quakeMarkers = new ArrayList<Marker>();
	    
	    for(PointFeature feature : earthquakes) {
		  //check if LandQuake
		  if(isLand(feature)) {
		    quakeMarkers.add(new LandQuakeMarker(feature));
		  }
		  // OceanQuakes
		  else {
		    quakeMarkers.add(new OceanQuakeMarker(feature));
		  }
	    }

	    // for debugging
	    //printQuakes();
	 		
	    // Add markers to map
	    map.addMarkers(quakeMarkers);
	    map.addMarkers(cityMarkers);
	    
	    sortAndPrint(quakeMarkers.size());
	    
	}
	
	
	
	public void draw() {
		background(0);
		map.draw();
		addKey();
		drawCityInfo();
	}
	
	public void drawCityInfo() {
		if (lastClicked == null) return;
		
		String magn = Double.toString(avgMagnitude);
		String quake = recentQuake == null ? "" : recentQuake.getStringProperty("title");
		
		fill(200, 200, 200);
		rect(baseX  - 67, baseY - 98, Math.max(textWidth(magn), textWidth(quake)) + 27, 93);
		
		fill(0, 0, 0);
		//textAlign(LEFT, CENTER);
		textSize(12);
		text("Quakes Nearby: ", baseX -60, baseY - 90);
		text(" " + quakesNearby, baseX -48, baseY - 75);
		
		fill(0, 0, 0);
		//textAlign(LEFT, CENTER);
		textSize(12);
		text("Average Magnitude: ", baseX - 60, baseY - 60);
		text(" " + magn, baseX - 48, baseY - 45);
		
		
		if (recentQuake ==null) return;
		fill(0, 0, 0);
		//textAlign(LEFT, CENTER);
		textSize(12);
		text("Most Recent Quake: " , baseX - 60, baseY - 30);
		text(" " + quake , baseX - 48, baseY - 15);
		
		
	}
	

	@Override
	public void mouseMoved() {
		// clear the last selection
		if (lastSelected != null) {
			lastSelected.setSelected(false);
			lastSelected = null;
		
		}
		selectMarkerIfHover(quakeMarkers);
		selectMarkerIfHover(cityMarkers);
		//loop();
	}
	
	// If there is a marker selected 
	private void selectMarkerIfHover(List<Marker> markers) {
		// Abort if there's already a marker selected
		if (lastSelected != null) {
			return;
		}
		
		for (Marker m : markers) 
		{
			CommonMarker marker = (CommonMarker)m;
			if (marker.isInside(map,  mouseX, mouseY)) {
				lastSelected = marker;
				marker.setSelected(true);
				return;
			}
		}
	}
	

	public void mouseClicked() {
		baseX = mouseX;
		baseY = mouseY;
		recentQuake = null;
		boolean isEarthquake = false;
		boolean isCity = false;
		
		isEarthquake = checkEarthquakesForClick();
		if (!isEarthquake) {
			isCity = checkCitiesForClick();
		}
		if (!isCity && !isEarthquake) {
			unhideMarkers();
		}
		if (!isCity) lastClicked = null;
	}
	
	// Helper method that will check if a city marker was clicked on
	// and respond appropriately
	private boolean checkCitiesForClick() {
		//if (lastClicked != null) return false;
		// Loop over the earthquake markers to see if one of them is selected
		for (Marker marker : cityMarkers) {
			if (!marker.isHidden() && marker.isInside(map, mouseX, mouseY)) {
				lastClicked = (CommonMarker)marker;
				// Hide all the other earthquakes and hide
				for (Marker mhide : cityMarkers) {
					if (mhide != marker) {
						mhide.setHidden(true);
					}
				}
				
				quakesNearby = 0;
				avgMagnitude = 0;
				
				for (Marker mhide : quakeMarkers) {
					EarthquakeMarker quakeMarker = (EarthquakeMarker)mhide;
					
					if (quakeMarker.getDistanceTo(marker.getLocation()) 
							> quakeMarker.threatCircle()) {
						quakeMarker.setHidden(true);
					}
					else {
						quakeMarker.setHidden(false);
						quakesNearby++;
						avgMagnitude += quakeMarker.getMagnitude();
						
						if (recentQuake == null) recentQuake = mhide;
						else if ((quakeMarker.getStringProperty("age")).equals("Past Hour")) recentQuake = mhide;
					}
					
				}
				if (quakesNearby != 0) avgMagnitude /= quakesNearby;
				
				return true;
			}
		}		
		return false;
	}
	
	// Helper method that will check if an earthquake marker was clicked on
	// and respond appropriately
	private boolean checkEarthquakesForClick() {
		quakesNearby = 0;
		avgMagnitude = 0;
		//if (lastClicked != null) return false;
		// Loop over the earthquake markers to see if one of them is selected
		for (Marker m : quakeMarkers) {
			EarthquakeMarker marker = (EarthquakeMarker)m;
			if (!marker.isHidden() && marker.isInside(map, mouseX, mouseY)) {
				//lastClicked = marker;
				// Hide all the other earthquakes and hide
				for (Marker mhide : quakeMarkers) {
					if (mhide != marker) {
						mhide.setHidden(true);
					}
				}
				for (Marker mhide : cityMarkers) {
					if (mhide.getDistanceTo(marker.getLocation()) 
							> marker.threatCircle()) {
						mhide.setHidden(true);
					}
					else mhide.setHidden(false);
				}
				return true;
			}
		}
		return false;
	}
	
	// loop over and unhide all markers
	private void unhideMarkers() {
		for(Marker marker : quakeMarkers) {
			marker.setHidden(false);
		}
			
		for(Marker marker : cityMarkers) {
			marker.setHidden(false);
		}
	}
	
	// helper method to draw key in GUI
	private void addKey() {	
		fill(255, 250, 240);
		
		int xbase = 15;
		int ybase = 50;
		
		rect(xbase, ybase, 170, 250);
		
		fill(0);
		textAlign(LEFT, CENTER);
		textSize(12);
		text("Earthquake Key", xbase+15, ybase+25);
		
		fill(150, 30, 30);
		int tri_xbase = xbase + 35;
		int tri_ybase = ybase + 50;
		triangle(tri_xbase, tri_ybase-CityMarker.TRI_SIZE, tri_xbase-CityMarker.TRI_SIZE, 
				tri_ybase+CityMarker.TRI_SIZE, tri_xbase+CityMarker.TRI_SIZE, 
				tri_ybase+CityMarker.TRI_SIZE);

		fill(0, 0, 0);
		textAlign(LEFT, CENTER);
		text("City Marker", tri_xbase + 15, tri_ybase);
		
		text("Land Quake", xbase+50, ybase+70);
		text("Ocean Quake", xbase+50, ybase+90);
		text("Size ~ Magnitude", xbase+15, ybase+115);
		
		fill(255, 255, 255);
		ellipse(xbase+35, 
				ybase+70, 
				10, 
				10);
		rect(xbase+35-5, ybase+90-5, 10, 10);
		
		fill(color(255, 255, 0));
		ellipse(xbase+35, ybase+140, 12, 12);
		fill(color(0, 0, 255));
		ellipse(xbase+35, ybase+160, 12, 12);
		fill(color(255, 0, 0));
		ellipse(xbase+35, ybase+180, 12, 12);
		
		textAlign(LEFT, CENTER);
		fill(0, 0, 0);
		text("Shallow", xbase+50, ybase+140);
		text("Intermediate", xbase+50, ybase+160);
		text("Deep", xbase+50, ybase+180);

		text("Past hour", xbase+50, ybase+200);
		
		fill(255, 255, 255);
		int centerx = xbase+35;
		int centery = ybase+200;
		ellipse(centerx, centery, 12, 12);

		strokeWeight(2);
		line(centerx-8, centery-8, centerx+8, centery+8);
		line(centerx-8, centery+8, centerx+8, centery-8);
		
		
	}
	

	
	
	// Checks whether this quake occurred on land.  If it did, it sets the 
	// "country" property of its PointFeature to the country where it occurred
	// and returns true.
	private boolean isLand(PointFeature earthquake) {
		
		for (Marker country : countryMarkers) {
			if (isInCountry(earthquake, country)) {
				return true;
			}
		}
		
		// not inside any country
		return false;
	}
	
	// prints countries with number of earthquakes
	private void printQuakes() {
		int totalWaterQuakes = quakeMarkers.size();
		
		for (Marker country : countryMarkers) {
			String countryName = country.getStringProperty("name");
			int numQuakes = 0;
			
			for (Marker marker : quakeMarkers) {
				EarthquakeMarker eqMarker = (EarthquakeMarker)marker;
				
				if (eqMarker.isOnLand()) {
					if (countryName.equals(eqMarker.getStringProperty("country"))) {
						numQuakes++;
					}
				}
				
			}
			
			if (numQuakes > 0) {
				totalWaterQuakes -= numQuakes;
				System.out.println(countryName + ": " + numQuakes);
			}
		}
		System.out.println("OCEAN QUAKES: " + totalWaterQuakes);
	}
	
	
	
	// helper method to test whether a given earthquake is in a given country
	private boolean isInCountry(PointFeature earthquake, Marker country) {
		// getting location of feature
		Location checkLoc = earthquake.getLocation();

		// some countries represented it as MultiMarker
		// looping over SimplePolygonMarkers which make them up to use isInsideByLoc
		if(country.getClass() == MultiMarker.class) {
				
			// looping over markers making up MultiMarker
			for(Marker marker : ((MultiMarker)country).getMarkers()) {
					
				// checking if inside
				if(((AbstractShapeMarker)marker).isInsideByLocation(checkLoc)) {
					earthquake.addProperty("country", country.getProperty("name"));
						
					// return if is inside one
					return true;
				}
			}
		}
			
		// check if inside country represented by SimplePolygonMarker
		else if(((AbstractShapeMarker)country).isInsideByLocation(checkLoc)) {
			earthquake.addProperty("country", country.getProperty("name"));
			
			return true;
		}
		return false;
	}
	
	private void sortAndPrint(int numToPrint) {
		Object quakeArray[] = quakeMarkers.toArray();
		Arrays.sort(quakeArray);
		PrintWriter writer;
		for (int i = 0; i < Math.min(numToPrint, quakeMarkers.size()); ++i) {
			System.out.println(quakeArray[i]);
		}
	}

}
