package net.waterfoul.gooverlay;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.ArrayList;

class appEnabledCheckListener implements View.OnClickListener {
    private int index = -1;
    private PackageAdapter adapter = null;
    public appEnabledCheckListener(int index, PackageAdapter adapter) {
        this.index = index;
        this.adapter = adapter;
    }
    @Override
    public void onClick (View v) {
        CheckBox checkBox = (CheckBox) v;
        adapter.getValue(index).setEnabled(checkBox.isChecked());
    }
}

class appSettingsClickListener implements View.OnClickListener {
    private int index = -1;
    private PackageAdapter adapter = null;
    private Activity activity;
    public appSettingsClickListener(int index, PackageAdapter adapter, Activity activity) {
        this.index = index;
        this.adapter = adapter;
        this.activity = activity;
    }
    @Override
    public void onClick (View v) {
        try {
            PackageManager packageManager = activity.getPackageManager();
            Intent intent = new Intent(adapter.getValue(index).AppHome.getSettingsIntent());
            List activities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            boolean isIntentSafe = activities.size() > 0;
            if(isIntentSafe) {
                activity.startActivity(intent);
            }
        } catch (RemoteException e) {}
    }
}

class PackageAdapter extends ArrayAdapter<Package> {
    private final ArrayList<Package> values;
    private final Activity activity;

    public PackageAdapter(Activity activity, ArrayList<Package> values) {
        super(activity, -1, values);
        this.activity = activity;
        this.values = values;
    }

    public Package getValue(int index) {
        return values.get(index);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View rowView = inflater.inflate(R.layout.main_activity_row, parent, false);
        TextView textView = (TextView) rowView.findViewById(R.id.appText);
        ImageView imageView = (ImageView) rowView.findViewById(R.id.appIcon);
        CheckBox checkBox = (CheckBox) rowView.findViewById(R.id.appEnabled);
        Button button = (Button) rowView.findViewById(R.id.appSettings);
        textView.setText(values.get(position).label);
        imageView.setImageDrawable(values.get(position).icon);
        checkBox.setChecked(values.get(position).getEnabled(true));
        checkBox.setOnClickListener(new appEnabledCheckListener(position, this));
        button.setOnClickListener(new appSettingsClickListener(position, this, activity));
        values.get(position).rowView = rowView;

        values.get(position).renderAppHome();

        return rowView;
    }
}

class OpServiceConnection implements ServiceConnection {
    private Package pkg = null;

    OpServiceConnection(Package pkg) {
        this.pkg = pkg;
    }

    public void onServiceConnected(
        ComponentName className,
        IBinder boundService
    ) {
        pkg.AppHome = IAppHome.Stub.asInterface((IBinder) boundService);
        pkg.renderAppHome();
    }

    public void onServiceDisconnected(ComponentName className) {
        pkg.AppHome = null;
    }
};

public class MainActivity extends ListActivity {
    static final String BUNDLE_EXTRAS_CATEGORY = "category";
    static final String LOG_TAG = "PluginApp";
    private static final int OVERLAY_PERMISSION_REQ_CODE = 1234;
    private static final int WRITE_STORAGE_REQ_CODE = 1236;

    private PackageBroadcastReceiver packageBroadcastReceiver;
    private IntentFilter packageFilter;
    private ArrayList<Package> packages;
    private PackageAdapter itemAdapter;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Prefs.init(this);
        setContentView(R.layout.main_activity);

        fillPluginList();
        checkPermissions();

        Button perms = (Button) this.findViewById(R.id.permissionsButton);
        perms.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent intent2 = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AppOpsManager appOps = (AppOpsManager) MainActivity.this.getSystemService(Context.APP_OPS_SERVICE);
                    int mode = appOps.checkOpNoThrow(
                            "android:get_usage_stats",
                            android.os.Process.myUid(),
                            MainActivity.this.getPackageName()
                    );
                    boolean granted = mode == AppOpsManager.MODE_ALLOWED;

                    if(!granted) {
                        intent2 = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                        startActivity(intent2);
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(MainActivity.this)) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
                }
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_STORAGE_REQ_CODE);
                }
            }
        });
        final Button start = (Button) this.findViewById(R.id.startBtn);
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, WatchPoGoRunningSvc.class);
                if(start.getText() == "Stop") {
                    start.setText("Start");
                    stopService(intent);
                } else {
                    start.setText("Stop");
                    startService(intent);
                }
            }
        });

        itemAdapter = new PackageAdapter(
            this,
            packages
        );
        setListAdapter(itemAdapter);

        packageBroadcastReceiver = new PackageBroadcastReceiver();
        packageFilter = new IntentFilter();
        packageFilter.addAction( Intent.ACTION_PACKAGE_ADDED  );
        packageFilter.addAction( Intent.ACTION_PACKAGE_REPLACED );
        packageFilter.addAction( Intent.ACTION_PACKAGE_REMOVED );
        packageFilter.addCategory( Intent.CATEGORY_DEFAULT );
        packageFilter.addDataScheme( "package" );
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == WRITE_STORAGE_REQ_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkPermissions();
            }
        }
    }

    protected void onStart() {
        super.onStart();
        Log.d( LOG_TAG, "onStart" );
        registerReceiver( packageBroadcastReceiver, packageFilter );
    }

    protected void onStop() {
        super.onStop();
        Log.d( LOG_TAG, "onStop" );
        unregisterReceiver( packageBroadcastReceiver );
    }

    private void checkPermissions() {
        LinearLayout perms = (LinearLayout) this.findViewById(R.id.permissionsLayout);
        //Check Permissions

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AppOpsManager appOps = (AppOpsManager) this.getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), this.getPackageName());
            boolean granted = mode == AppOpsManager.MODE_ALLOWED;

            if(!granted) {
                perms.setVisibility(View.VISIBLE);
                return;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            perms.setVisibility(View.VISIBLE);
        } else if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            perms.setVisibility(View.VISIBLE);
        } else {
            perms.setVisibility(View.GONE);
        }
    }

    private void fillPluginList() {
        packages = new ArrayList<>();

        PackageManager packageManager = getPackageManager();

        // Search for all apps which have the APP_HOME intent
        Intent baseIntent = new Intent( Intents.APP_HOME );
        baseIntent.setFlags( Intent.FLAG_DEBUG_LOG_RESOLUTION );
        List<ResolveInfo> list = packageManager.queryIntentServices(baseIntent, PackageManager.GET_RESOLVED_FILTER );

        for( int i = 0 ; i < list.size() ; ++i ) {
            // Information about the resolution
            ResolveInfo info = list.get(i);
            // Information about the service
            ServiceInfo sinfo = info.serviceInfo;

            if( sinfo != null ) {
                Package current = new Package();
                try {
                    current.icon = packageManager.getApplicationIcon(sinfo.packageName);
                    current.label = (String) sinfo.loadLabel(packageManager);
                    current.packageName = sinfo.packageName;

                    current.opServiceConnection = new OpServiceConnection(current);
                    Intent intent = new Intent(Intents.APP_HOME);
                    intent.setPackage(current.packageName);
                    bindService(intent, current.opServiceConnection, Context.BIND_AUTO_CREATE);

                } catch (PackageManager.NameNotFoundException e) {
                    Log.d(LOG_TAG, "Error while fetching icon/label for " + sinfo.packageName + ": " + e.getStackTrace());
                }

                packages.add(current);
            }
        }
    }

    class PackageBroadcastReceiver extends BroadcastReceiver {
        private static final String LOG_TAG = "PackageBroadcastReceive";

        public void onReceive(Context context, Intent intent) {
            Log.d( LOG_TAG, "onReceive: "+intent );
            packages.clear();
            fillPluginList();
            itemAdapter.notifyDataSetChanged();
        }
    }

    public void onDestroy() {
        super.onDestroy();

        for(int i = 0; i < packages.size(); i++) {
            if(packages.get(i).opServiceConnection != null) {
                unbindService(packages.get(i).opServiceConnection);
                packages.get(i).opServiceConnection = null;
            }
        }
    }
}
