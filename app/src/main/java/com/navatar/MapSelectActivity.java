package com.navatar;

import java.io.IOException;
import java.util.ArrayList;

import com.navatar.maps.MapService;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

public class MapSelectActivity extends Activity {
  private Spinner mapSpinner,campusSpinner;
  private TextView mapSelectTextView;
  private ArrayAdapter<String> mapArrayAdapter,campusArrayAdapter;
  private ArrayList<String> maplist;
  private MapService mapService;
  private Intent mapIntent;
  private PendingIntent pendingIntent;
  public static boolean ActivityDestryoed;
  private String[] campusNames;

  // Get user location
  private Button button;
  private TextView textDebug;
  private static final int MAX_LOCATION_SAMPLES = 5;
  private static final int MIN_LOCATION_ACCURACY = 20;
  private int locationSamples;
  private LocationManager locationManager;
  private LocationListener listener;
  private float accuracy;
  private double LocLat;
  private double LocLong;
  private boolean CampusAutoSelected;

  @Override
  protected void onDestroy() {

      super.onDestroy();
      if(mapService!=null)
       unbindService(mMapConnection);
      if(pendingIntent!=null)
          pendingIntent.cancel();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setTitle("Welcome to Navatar");
    setContentView(R.layout.map_select);


    mapIntent= new Intent(this, MapService.class);

    campusSpinner = (Spinner)findViewById(R.id.campusSpinner);

    campusArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,
            new ArrayList<String>());
    campusArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

    ArrayList<String> campuslist = new ArrayList<String>();
    CampusAutoSelected = false;

    try {
      // Add campuses to spinner
      campusNames = getAssets().list("maps");
      campuslist.add("Select a campus");
      for (int i=0;i<campusNames.length;i++){
        campuslist.add(campusNames[i].replaceAll("_"," "));
      }

      // Geo-fence ui variables
      textDebug = (TextView) findViewById(R.id.textView);
      button = (Button) findViewById(R.id.button);

      // Location manager for geo-fencing
      locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
      listener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
          locationSamples++;
          textDebug.append("\n Received location - ");

          // If you draw a circle centered at this location's latitude and longitude,
          // and with a radius equal to the accuracy(meters), then there is a 68%
          // probability that the true location is inside the circle
          accuracy = location.getAccuracy();
          textDebug.append("Accuracy: " + accuracy + " m");

          LocLat = location.getLatitude();
          LocLong = location.getLongitude();
          textDebug.append("\n " + LocLat + " " + LocLong);

          // If location sample is accurate enough to use
          if(accuracy <= MIN_LOCATION_ACCURACY) {
            textDebug.append("\n Accuracy requirement met (" + MIN_LOCATION_ACCURACY + ")");
            // Stop requesting locations
            //noinspection MissingPermission
            locationManager.removeUpdates(listener);
            textDebug.append("\n Stopped requesting location");

            // Send in location, get out name of campus if supported or nothing
            String foundCampus = null;

            ////////////////////////////////////////
            // Prototype
            double UNRMinLat =39.536837;
            double UNRMaxLat =39.550971;
            double UNRMinLong =-119.822549;
            double UNRMaxLong =-119.809963;


            // Scrugham eng mines prototype location check
            if(LocLat > UNRMinLat  && LocLat < UNRMaxLat &&
                    LocLong > UNRMinLong && LocLong < UNRMaxLong) {
              textDebug.append("\n AT UNR");
              foundCampus = "University_of_Nevada_Reno";
            }
            ////////////////////////////////////////

            // If location is on supported campus
            if (foundCampus != null){
              // Get index of located campusName for spinner selection
              for (int i=0;i<campusNames.length;i++){
                if (campusNames[i].equals(foundCampus)){
                  // Set flag for building auto locate to be attempted
                  CampusAutoSelected = true;

                  // Select campus
                  campusSpinner.setSelection(i+1); // +1 for select campus label at [0]
                }
              }
            }
            // Not found on available campuses
            else {
              textDebug.append("\n User not on supported campus");
            }
          }
          // Otherwise, continue getting location if max location samples has not been reached
          else if(locationSamples < MAX_LOCATION_SAMPLES) {
            textDebug.append("\n Accuracy too low");

            // Clear lat and long
            LocLat = Double.NaN;
            LocLong = Double.NaN;
          }
          // Failed to locate user
          else {
            textDebug.append("\n Accuracy too low after " + locationSamples + " samples");
            // Stop requesting locations
            //noinspection MissingPermission
            locationManager.removeUpdates(listener);
            textDebug.append("\n Stopped requesting location");

            // Clear lat and long
            LocLat = Double.NaN;
            LocLong = Double.NaN;
          }
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {

        }

        @Override
        public void onProviderEnabled(String s) {
          textDebug.append("\n GPS enabled");
        }

        @Override
        public void onProviderDisabled(String s) {
          textDebug.append("\n GPS disabled");
          Intent i = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
          startActivity(i);
        }
      };

      // Setup geofence button
      configure_button();
    } catch (IOException e) {
      e.printStackTrace();
    }

    campusArrayAdapter.addAll(campuslist);
    campusSpinner.setAdapter(campusArrayAdapter);
    campusSpinner.setOnItemSelectedListener(campusSpinnerSelected);
    maplist = new ArrayList<String>();
    maplist.add(0,"Select Building");
    mapArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,
            maplist);
    startService(mapIntent);
    bindService(mapIntent, mMapConnection, BIND_AUTO_CREATE);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    switch (requestCode){
      case 10:
        configure_button();
        break;
      default:
        break;
    }
  }

  void configure_button(){
    // If we don't have permission for location
    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        // Denied
        textDebug.append("\n Location permission denied");

        // Request permission (spam loop with onRequestPermissionsResult())
        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.INTERNET}, 10);
      }
      return;
    }
    // All below only executes if permissions granted
    textDebug.append("\n Location permission granted");

    // Setup button to request location
    button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        // Listen for one location update
        //noinspection MissingPermission
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, listener);
        textDebug.append("\n Requesting location");
        // Reset sample counter
        locationSamples = 0;
      }
    });
  }

  @Override
  protected void onResume(){
    super.onResume();

  }

  public OnItemSelectedListener campusSpinnerSelected = new OnItemSelectedListener() {
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
      // If a campus is selected
      if (position != 0) {
        String campusName = campusSpinner.getSelectedItem().toString();
        campusName=campusName.replaceAll(" ","_");
        setContentView(R.layout.map_select_new);
        setTitle("Select the building");
        mapSelectTextView = (TextView)findViewById(R.id.tvmapselect);
        mapSpinner = (Spinner) findViewById(R.id.mapSpinner);


        mapSpinner.setAdapter(mapArrayAdapter);
        mapSpinner.setOnItemSelectedListener(mapSpinnerItemSelected);
        mapArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mapIntent.putExtra("path",campusName);
        Intent defaultIntent = new Intent();
        pendingIntent = MapSelectActivity.this.createPendingResult(1,defaultIntent,PendingIntent.FLAG_ONE_SHOT);
        mapIntent.putExtra("pendingIntent",pendingIntent);
        startService(mapIntent);
        bindService(mapIntent, mMapConnection, BIND_AUTO_CREATE);
      }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
   };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Populate spinner with buildings
        super.onActivityResult(requestCode, resultCode, data);
        maplist.clear();
        maplist.add("Select a building");

        // Add found maps to spinner
        if(data.hasExtra("maps")) {

          maplist.addAll((ArrayList<String>) data.getSerializableExtra("maps"));

          // If campus was auto selected, try auto selecting building
          if (CampusAutoSelected) {
            // Send in location, get out name of building if supported or nothing
            String foundBuilding = null;

            ///////////////////////////////////////////////////
            // Prototype
            double ScrugMinLat = 39.539345;
            double ScrugMaxLat = 39.540135;
            double ScrugMinLong = -119.814162;
            double ScrugMaxLong = -119.812982;

            double AnsariMinLat = 39.539795;
            double AnsariMaxLat = 39.540270;
            double AnsariMinLong = -119.815059;
            double AnsariMaxLong = -119.814290;

            // Scrugham prototype location check
            if (LocLat > ScrugMinLat && LocLat < ScrugMaxLat &&
                    LocLong > ScrugMinLong && LocLong < ScrugMaxLong) {
              textDebug.append("\n AT SCRUGHAM" + LocLat + " " + LocLong);
              foundBuilding = "scrugham engineering mines";
            }

            // Ansari prototype location check
            if (LocLat > AnsariMinLat && LocLat < AnsariMaxLat &&
                    LocLong > AnsariMinLong && LocLong < AnsariMaxLong) {
              textDebug.append("\n AT ANSARI" + LocLat + " " + LocLong);
              foundBuilding = "ansari business building";
            }
            ////////////////////////////////////////////////////

            // If location in supported building
            if (foundBuilding != null) {
              // Get index of located building name for spinner selection
              for (int i = 1; i < maplist.size(); i++) { // skip building label at 0
                if (maplist.get(i).equals(foundBuilding)) {
                  // Select building
                  mapSpinner.setSelection(i);
                }
              }
            }
          }

        }
        pendingIntent = null;
    }


    OnItemSelectedListener mapSpinnerItemSelected = new OnItemSelectedListener() {
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
      // If building is selected
      if (position != 0) {
        mapService.setActiveMap(position - 1);
        Intent intent = new Intent(MapSelectActivity.this, NavigationSelectionActivity.class);
        startActivity(intent);
      }
    }
    public void onNothingSelected(AdapterView<?> arg0) {}
  };


    /** Defines callback for service binding, passed to bindService() */
  private ServiceConnection mMapConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
      MapService.MapBinder binder = (MapService.MapBinder) service;
      mapService = binder.getService();
    }
    @Override
    public void onServiceDisconnected(ComponentName name) {
      mapService = null;
    }
  };


}
