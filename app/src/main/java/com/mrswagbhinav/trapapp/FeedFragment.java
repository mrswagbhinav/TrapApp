package com.mrswagbhinav.trapapp;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Time;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class FeedFragment extends Fragment{

    RecyclerView recyclerView;
    RecyclerViewAdapter adapter;
    SwipeRefreshLayout swipeRefreshLayout;
    ProgressDialog dialog;
    VerticalSpaceItemDecoration itemDecoration = new VerticalSpaceItemDecoration(10);

    final String API_KEY = "AIzaSyCd0lSfjSIUAZpiiNgGLyTiwpDnfJGCwVg";
    private static final String TAG = "FeedFragment";

    boolean time = true;
    ArrayList<Trap> trapsList = new ArrayList<>();
    FirebaseFirestore db;
    FirebaseUser user;



    public FeedFragment(){
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View fragmentView = inflater.inflate(R.layout.fragment_feed, null);

        swipeRefreshLayout = fragmentView.findViewById(R.id.id_refreshViewFeed);
        recyclerView = fragmentView.findViewById(R.id.id_recyclerViewFeed);
        recyclerView.addItemDecoration(itemDecoration);

        dialog = new ProgressDialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        user = ((MainActivity) getActivity()).user;

        db = ((MainActivity)getActivity()).db;
        setData(db);

        ((MainActivity)getActivity()).buttonFilterFeed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(time) {
                    dialog.setMessage("Sorting by Distance");
                    dialog.show();
                    time = false;
                    setData(db);
                }
                else {
                    dialog.setMessage("Sorting by Date");
                    dialog.show();
                    time = true;
                    setData(db);
                }
            }
        });

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                setData(db);
                dialog.setMessage("Loading");
                dialog.show();
            }
        });

        recyclerView.addOnItemTouchListener(
                new RecyclerItemClickListener(getActivity(), recyclerView ,new RecyclerItemClickListener.OnItemClickListener() {
                    @Override public void onItemClick(View view, int position) {
                        if(trapsList.get(position).getHost().equals(user.getUid()))
                            createHostDialog(position).show();
                        else
                            createFeedDialog(position).show();
                    }

                    @Override public void onLongItemClick(View view, int position) {

                    }
                })
        );

        return fragmentView;
    }



    public AlertDialog createFeedDialog(int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final LayoutInflater dialogInflater = requireActivity().getLayoutInflater();
        View dialogView = dialogInflater.inflate(R.layout.feed_dialog, null);

        final TextView textViewDialogTitle = dialogView.findViewById(R.id.id_textViewDialogTitle);
        final TextView textViewDialogTime = dialogView.findViewById(R.id.id_textViewDialogTime);
        final TextView textViewDialogHost = dialogView.findViewById(R.id.id_textViewDialogHost);
        final TextView textViewDialogLocation = dialogView.findViewById(R.id.id_textViewDialogLocation);

        textViewDialogTitle.setText(trapsList.get(position).getTitle());
        textViewDialogTime.setText(getDate(trapsList.get(position).getTimestamp()));
        textViewDialogHost.setText(trapsList.get(position).getHost());
        textViewDialogLocation.setText(trapsList.get(position).getLocationAddress());

        final DocumentReference trapRef = db.collection("traps").document(trapsList.get(position).getId());

        builder.setView(dialogView)
                .setTitle("Settings")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        trapRef.update("commits", FieldValue.arrayUnion(user.getUid()));
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        trapRef.update("commits", FieldValue.arrayRemove(user.getUid()));
                        dialog.dismiss();
                    }
                });

        builder.setTitle(trapsList.get(position).getTitle());

        return builder.create();
    }

    public AlertDialog createHostDialog(int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final LayoutInflater dialogInflater = requireActivity().getLayoutInflater();
        View dialogView = dialogInflater.inflate(R.layout.host_dialog, null);

        builder.setView(dialogView)
                .setTitle("Settings")
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

        builder.setIcon(R.drawable.ic_settings_black_24dp);

        return builder.create();
    }

    public String getDate(Timestamp timestamp) {
        Date date = timestamp.toDate();
        String month = null;
        int day = date.getDate();
        int year = date.getYear() + 1900;
        DateFormat format = new SimpleDateFormat("hh:mm a");
        String time = format.format(date);

        switch (date.getMonth()) {
            case 0:
                month = "January";
                break;
            case 1:
                month = "February";
                break;
            case 2:
                month = "March";
                break;
            case 3:
                month = "April";
                break;
            case 4:
                month = "May";
                break;
            case 5:
                month = "June";
                break;
            case 6:
                month = "July";
                break;
            case 7:
                month = "August";
                break;
            case 8:
                month = "September";
                break;
            case 9:
                month = "October";
                break;
            case 10:
                month = "November";
                break;
            case 11:
                month = "December";
                break;
        }

        return month+" "+day+", "+year+" at "+time;
    }

    public void setData(final FirebaseFirestore db) {
        trapsList = new ArrayList<>();
        db.collection("traps")
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                Log.d(TAG, document.getId() + " => " + document.getData());
                                if(((Timestamp) document.get("time")).compareTo(Timestamp.now()) > 0) {     //check if the trap has already happened
                                    //use a fresh user instead of the one from MainActivirt
                                    if(((ArrayList) document.get("invites")).contains(user.getUid()) || document.get("host").equals(user.getUid())) {     //check if user is invited or hosting
                                        trapsList.add(0, new Trap((String) document.get("title"), (String) document.get("host"), (String) document.get("location_name"), (String) document.get("location_address"), (Timestamp) document.get("time"), (GeoPoint) document.get("geopoint"), document.getId()));
                                    }
                                }
                            }
                        }
                        else {
                            Log.d(TAG, "Error getting documents: ", task.getException());
                        }

                        if(time)
                            sortTime(trapsList);
                        else
                            sortDistance(trapsList);

                        if(swipeRefreshLayout.isRefreshing()) {
                            swipeRefreshLayout.setRefreshing(false);
                        }
                        dialog.dismiss();

                        adapter = new RecyclerViewAdapter(trapsList, db, getActivity());
                        recyclerView.setAdapter(adapter);
                        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
                    }
                });
    }

    private void sortDistance(ArrayList<Trap> traps) {
        try {
            String lat = String.valueOf(((MainActivity)getActivity()).latitude);
            String lng = String.valueOf(((MainActivity)getActivity()).longitude);

            for(int i = 0; i < traps.size()-1; i++) {
                int index = i;
                for(int j = i+1; j < traps.size(); j++) {

                    String destinationAddressJ = traps.get(j).getLocationAddress().replace(" ", "%20");
                    String destinationAddressI = traps.get(index).getLocationAddress().replace(" ", "%20");
                    String distanceURLJ = "https://maps.googleapis.com/maps/api/distancematrix/json?origins="+lat+","+lng+"&destinations="+destinationAddressJ+"&mode=driving&units=imperial&language=en&key="+API_KEY;
                    String distanceURLI = "https://maps.googleapis.com/maps/api/distancematrix/json?origins="+lat+","+lng+"&destinations="+destinationAddressI+"&mode=driving&units=imperial&language=en&key="+API_KEY;
                    int distanceJ = new getDistance().execute(distanceURLJ).get();
                    int distanceI = new getDistance().execute(distanceURLI).get();

                    if(distanceJ < distanceI) {
                        index = j;
                    }
                }
                Trap temp = traps.get(index);
                traps.set(index, traps.get(i));
                traps.set(i, temp);
            }

        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public class VerticalSpaceItemDecoration extends RecyclerView.ItemDecoration {

        private final int verticalSpaceHeight;

        public VerticalSpaceItemDecoration(int verticalSpaceHeight) {
            this.verticalSpaceHeight = verticalSpaceHeight;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                                   RecyclerView.State state) {
            outRect.bottom = verticalSpaceHeight;
        }
    }

    private void sortTime(ArrayList<Trap> traps) {
        for(int i = 0; i < traps.size()-1; i++) {
            int index = i;
            for(int j = i+1; j < traps.size(); j++) {

                Timestamp timeJ = traps.get(j).getTimestamp();
                Timestamp timeI = traps.get(i).getTimestamp();
                if(timeJ.compareTo(timeI) < 0) { //J < I
                    index = j;
                }
            }
            Trap temp = traps.get(index);
            traps.set(index, traps.get(i));
            traps.set(i, temp);
        }
    }

    private class getCurrentAddress extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String currentAddress = null;
            String s = null;
            JSONObject jsonObject;

            try {
                URL url = new URL(params[0]);
                URLConnection urlConnection = url.openConnection();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                String temp;

                while((temp = bufferedReader.readLine()) != null){
                    s += temp;
                }
                s = s.replace("null", "");

                jsonObject = new JSONObject(s);
                currentAddress = jsonObject.getJSONArray("results").getJSONObject(0).getString("formatted_address");


            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return currentAddress;
        }
    }//getAddress

    private class getDistance extends AsyncTask<String, Void, Integer> {
        @Override
        protected Integer doInBackground(String... params) {
            int distance = 0;
            String s = null;
            JSONObject jsonObject;
            try {
                URL url = new URL(params[0]);
                URLConnection urlConnection = url.openConnection();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                String temp;

                while((temp = bufferedReader.readLine()) != null){
                    s += temp;
                }
                s = s.replace("null", "");

                jsonObject = new JSONObject(s);
                distance = jsonObject.getJSONArray("rows").getJSONObject(0).getJSONArray("elements").getJSONObject(0).getJSONObject("distance").getInt("value");

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return distance;
        }
    }
}