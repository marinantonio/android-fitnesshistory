package com.am.fitnesshistory;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DailyTotalResult;
import com.google.android.gms.fitness.result.DataReadResult;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private GoogleApiClient googleApiClient;
    private DataType dataType;
    private DataType dataType1;
    private ArrayList<String> arrayList = new ArrayList<>();
    private TextView textView;
    private String textForTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.data_text);

        Button todaySteps = findViewById(R.id.today_steps);
        Button todayDistance = findViewById(R.id.today_distance);
        Button historySteps = findViewById(R.id.history_steps);
        Button historyDistance = findViewById(R.id.history_distance);

        todaySteps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dataType = DataType.TYPE_STEP_COUNT_DELTA;
                new GetDataForTodayTask().execute();
            }
        });

        todayDistance.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dataType = DataType.TYPE_DISTANCE_DELTA;
                new GetDataForTodayTask().execute();
            }
        });

        historySteps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dataType = DataType.TYPE_STEP_COUNT_DELTA;
                dataType1 = DataType.AGGREGATE_STEP_COUNT_DELTA;
                new GetHistoryDataTask().execute();
            }
        });

        historyDistance.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dataType = DataType.TYPE_DISTANCE_DELTA;
                dataType1 = DataType.AGGREGATE_DISTANCE_DELTA;
                new GetHistoryDataTask().execute();
            }
        });

        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Fitness.HISTORY_API)
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE))
                .addScope(new Scope(Scopes.FITNESS_LOCATION_READ))
                .addConnectionCallbacks(this)
                .enableAutoManage(this, 0, this)
                .build();

    }

    public void onConnected(@Nullable Bundle bundle) {
        Log.e(TAG, "onConnected");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.e(TAG, "onConnectionSuspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e(TAG, "onConnectionFailed");
    }

    /** Daily results */
    private void displayDataForToday() {
        arrayList.clear();
        DailyTotalResult result = Fitness.HistoryApi.readDailyTotal(googleApiClient, dataType).await(1, TimeUnit.MINUTES);
        showDataSet(result.getTotal());
    }

    /** Weekly results */
    private void displayHistoryData() {
        arrayList.clear();
        Calendar calendar = Calendar.getInstance();
        Date now = new Date();
        calendar.setTime(now);
        long endTime = calendar.getTimeInMillis();
        //-2 defines last two days
        calendar.add(Calendar.DAY_OF_YEAR, -2);
        long startTime = calendar.getTimeInMillis();

        /* I wanted to get a same results as on the fit page but in order to do that
         I should probably exclude current day, set time to midnight (to separate activity
         between days)

         I just didn't have too much time for testing but something like this might work
         long endTime = calendar.getTimeInMillis();
         calendar.set(Calendar.HOUR_OF_DAY, 0)
         calendar.add(Calendar.DAY_OF_YEAR, -2);
         long startTime = calendar.getTimeInMillis();

         I guess in practice apps use date pickers to get a start and end time values. */

        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(dataType, dataType1)
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();

        DataReadResult dataReadResult = Fitness.HistoryApi.readData(googleApiClient, readRequest).await(1, TimeUnit.MINUTES);

        if (dataReadResult.getBuckets().size() > 0) {
            Log.e(TAG, "Number of buckets: " + dataReadResult.getBuckets().size());
            for (Bucket bucket : dataReadResult.getBuckets()) {
                List<DataSet> dataSets = bucket.getDataSets();
                for (DataSet dataSet : dataSets) {
                    showDataSet(dataSet);
                }
            }
        }

        else if (dataReadResult.getDataSets().size() > 0) {
            Log.e(TAG, "Number of returned DataSets: " + dataReadResult.getDataSets().size());
            for (DataSet dataSet : dataReadResult.getDataSets()) {
                showDataSet(dataSet);
            }
        }
    }

    private void showDataSet(DataSet dataSet) {
        DateFormat dateFormat = DateFormat.getDateInstance();
        DateFormat timeFormat = DateFormat.getTimeInstance();

        for (DataPoint dp : dataSet.getDataPoints()) {
            arrayList.add("Type: " + dp.getDataType().getName() +
                    "\nStart: " + dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)) + " " + timeFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)) +
                    "\nEnd: " + dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)) + " " + timeFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
            for(Field field : dp.getDataType().getFields()) {
                arrayList.add("\nField: " + field.getName() + " Value: " + dp.getValue(field) + "\n\n");
            }
        }
    }

    /** ASYNC TASKS */
    private class GetDataForTodayTask extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... params) {
            displayDataForToday();
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            textForTextView = arrayList.toString().substring(1, arrayList.toString().length()-1);
            textView.setText(textForTextView);
        }
    }

    private class GetHistoryDataTask extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... params) {
            displayHistoryData();
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            textForTextView = arrayList.toString().substring(1, arrayList.toString().length()-1);
            textView.setText(textForTextView);
        }
    }
}
