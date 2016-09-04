package net.waterfoul.gooverlaydebug;

import android.app.Dialog;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.WindowManager;

import net.waterfoul.gooverlay.IAppHome;

public class HomeService extends Service {
    static final String LOG_TAG = "PluginService1";
    private Handler mHandler;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("SVC", "Start");
        return START_NOT_STICKY;
    }

    public void onDestroy() {
        Log.d("SVC", "Destroy");
        super.onDestroy();
    }

    public IBinder onBind(Intent intent) {
        Log.d("SVC", "Bind");
        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message message) {
                Dialog dialog = new Dialog(new ContextThemeWrapper(HomeService.this, R.style.AppTheme));
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                dialog.setContentView(R.layout.activity_settings_dialog);
                dialog.setTitle("Debug Settings");
                dialog.setCancelable(true);

                dialog.show();
            }
        };
        return addBinder;
    }

    private final IAppHome.Stub addBinder =
            new IAppHome.Stub() {

                @Override
                public String getSettingsIntent() throws RemoteException {
                    return "net.waterfoul.gooverlaydebug.SETTINGS";
                }

                @Override
                public void enable() throws RemoteException {

                }

                @Override
                public void disable() throws RemoteException {

                }
            };
}
