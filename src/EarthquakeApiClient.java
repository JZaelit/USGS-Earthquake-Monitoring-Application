import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;

public class EarthquakeApiClient {

    private static final String USGS_API_ENDPOINT = "https://earthquake.usgs.gov/fdsnws/event/1/query";

    /**
     * Retrieves earthquake data based on the specified parameters
     *
     * @param startDate start date for the search (yyyy-MM-dd)
     * @param endDate end date for the search (yyyy-MM-dd)
     * @param minMagnitude minimum magnitude of earthquakes to retrieve
     * @return List of Earthquake objects
     * @throws IOException if there's an error in the API request
     */
    public List<Earthquake> getEarthquakeData(String startDate, String endDate, double minMagnitude) throws IOException {
        // Building the API URL with parameters
        StringBuilder urlBuilder = new StringBuilder(USGS_API_ENDPOINT);
        urlBuilder.append("?format=geojson");
        urlBuilder.append("&starttime=").append(startDate);
        urlBuilder.append("&endtime=").append(endDate);
        urlBuilder.append("&minmagnitude=").append(minMagnitude);

        URL url = new URL(urlBuilder.toString());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("API request failed. Response Code: " + responseCode);
        }

        // Reading the response
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        String outFile = "ZaelitJason_sorted_05032025.txt";

        while ((line = reader.readLine()) != null) {
            response.append(line);
            Files.write(Paths.get(outFile), (line + System.lineSeparator()).getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        reader.close();

        // Parsing the JSON response
        return parseEarthquakeData(response.toString());
    }

    /**
     * Parses JSON response from the USGS API
     *
     * @param jsonResponse the JSON string from the API
     * @return List of Earthquake objects
     */
    private List<Earthquake> parseEarthquakeData(String jsonResponse) {
        List<Earthquake> earthquakes = new ArrayList<>();

        JSONObject jsonObject = new JSONObject(jsonResponse);
        JSONArray features = jsonObject.getJSONArray("features");

        for (int i = 0; i < features.length(); i++) {
            JSONObject feature = features.getJSONObject(i);
            JSONObject properties = feature.getJSONObject("properties");
            JSONObject geometry = feature.getJSONObject("geometry");
            JSONArray coordinates = geometry.getJSONArray("coordinates");

            double magnitude = properties.getDouble("mag");
            String place = properties.getString("place");
            long time = properties.getLong("time");
            double longitude = coordinates.getDouble(0);
            double latitude = coordinates.getDouble(1);
            double depth = coordinates.getDouble(2);

            Earthquake earthquake = new Earthquake(
                    magnitude,
                    place,
                    time,
                    latitude,
                    longitude,
                    depth
            );

            earthquakes.add(earthquake);
        }

        return earthquakes;
    }

    /**
     * Represents an earthquake event
     */
    public static class Earthquake {
        private final double magnitude;
        private final String location;
        private final long timestamp;
        private final double latitude;
        private final double longitude;
        private final double depth;

        public Earthquake(double magnitude, String location, long timestamp,
                          double latitude, double longitude, double depth) {
            this.magnitude = magnitude;
            this.location = location;
            this.timestamp = timestamp;
            this.latitude = latitude;
            this.longitude = longitude;
            this.depth = depth;
        }

        public double getMagnitude() {
            return magnitude;
        }

        public String getLocation() {
            return location;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getLocalTime() {
           Instant instance = Instant.ofEpochMilli(timestamp);
           return(instance.toString());

        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public double getDepth() {
            return depth;
        }

        @Override
        public String toString() {
            return "Earthquake{" +
                    "magnitude=" + magnitude +
                    ", location='" + location + '\'' +
                    ", timestamp=" + getLocalTime() +
                    ", latitude=" + latitude +
                    ", longitude=" + longitude +
                    ", depth=" + depth +
                    '}';
        }

        public String format() {
            return String.format("%s: Magnitude %.1f at %s (%.4f, %.4f)",
                    getLocalTime(), magnitude, location, latitude, longitude);
        }
    }

    public static class SortByTime implements Comparator<Earthquake> {
        @Override
        public int compare(Earthquake quake1, Earthquake quake2) {
            return(Long.compare(quake1.getTimestamp(), quake2.getTimestamp()));
        }
    }

    /**
     * Example usage of the EarthquakeApiClient
     * @throws InterruptedException
     */
    public static void main(String[] args) throws InterruptedException {

        EarthquakeApiClient client = new EarthquakeApiClient();
        HashMap<Integer, String> seenQuakes = new HashMap<>();
        List<Earthquake> earthquakes = null;
        ArrayList<String> northAmerica = new ArrayList<>();
        boolean watchingQuakes = true;

        LocalDate endDate = LocalDate.now().plusDays(2);

        LocalDate startDate = endDate.minusDays(5);

        while (watchingQuakes) {
            try {
                // Gets earthquakes with magnitude 5.0+ in the last 5 days

                earthquakes = client.getEarthquakeData(
                        startDate.toString(),
                        endDate.toString(),
                        5.0 // magnitude minimum 5.0
                        //0.0 // get all magnitudes
                );

                Collections.sort(earthquakes, new SortByTime());
                for (Earthquake earthquake : earthquakes) {
                    String data = earthquake.format(); // data ready to output
                    int hashcode = data.hashCode(); // String class hashcode
                    if(!seenQuakes.containsKey(hashcode)) {
                        System.out.println(data);
                    }
                    seenQuakes.put(hashcode, data); // autoboxing

                    if(filterQuake(earthquake)) {
                        northAmerica.add(data);
                    }

                }
            } catch (IOException ioe) {
                System.err.println("Error retrieving earthquake data: " + ioe.getMessage());
                ioe.printStackTrace();
            } catch (NullPointerException npe) {
                System.err.println("Error uninitialized object: " + npe.getMessage());
            }

            Thread.sleep(1000);

        }

        System.out.println("Nearby shaker(s): ");
        for(Earthquake eq : earthquakes) {
            if(eq.getLocation().contains("Julian"))
                System.out.println(eq.getLocation());
        }

        System.out.println("Filter for North America");
        for (String quake : northAmerica) {
            System.out.printf("%s \n", quake);
        }
    }

    public static boolean filterQuake(Earthquake data) {
        double longitude = data.getLongitude();
        double latitude = data.getLatitude();
        // North America: 7°N to 83°N latitude and from 52.5°W to 167°W
        return( latitude >= 7.0 && latitude <= 83.0 &&
                longitude >= -167.0 && longitude <= -52.5);
    }
}