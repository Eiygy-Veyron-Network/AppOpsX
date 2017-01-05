package com.zzzmode.appopsx.server;


import android.app.ActivityThread;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.os.ServiceManager;
import android.util.Log;

import com.android.internal.app.IAppOpsService;
import com.zzzmode.appopsx.common.OpsCommands;
import com.zzzmode.appopsx.common.OpsDataTransfer;
import com.zzzmode.appopsx.common.OpsResult;
import com.zzzmode.appopsx.common.PackageOps;
import com.zzzmode.appopsx.common.ParcelableUtil;
import com.zzzmode.appopsx.common.ReflectUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AppOpsMain implements OpsDataTransfer.OnRecvCallback {

    private static final int MSG_TIMEOUT = 1;
    private static final int DEFAULT_TIME_OUT_TIME = 1000 * 60 * 1; //1min

    public static void main(String[] args) {

        try {
            new AppOpsMain(args);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private OpsXServer server;
    private Handler handler;
    private volatile boolean isDeath = false;
    private final int timeOut = DEFAULT_TIME_OUT_TIME;

    private AppOpsMain(String[] args) throws IOException {
        server = new OpsXServer("/data/data/com.zzzmode.appopsx/com.zzzmode.appopsx.socket", this);

        try {

            HandlerThread thread1 = new HandlerThread("watcher-ups");
            thread1.start();
            handler = new Handler(thread1.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    switch (msg.what) {
                        case MSG_TIMEOUT:
                            try {
                                isDeath = true;
                                server.setStop();

                                System.out.println("timeout stop-----");
                                Process.killProcess(Process.myPid());
                                System.exit(0);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            break;
                    }

                }
            };

            server.run();
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("end ---- ");
    }


    private void handleCommand(OpsCommands.Builder builder) {
        String s = builder.getAction();
        if (OpsCommands.ACTION_GET.equals(s)) {
            runGet(builder);
        } else if (OpsCommands.ACTION_SET.equals(s)) {
            runSet(builder);
        } else if (OpsCommands.ACTION_RESET.equals(s)) {
            runReset(builder);
        }
    }

    private void runGet(OpsCommands.Builder getBuilder) {

        try {
            System.out.println("runGet "+Build.VERSION.SDK_INT);
            final IAppOpsService appOpsService = IAppOpsService.Stub.asInterface(
                    ServiceManager.getService(Context.APP_OPS_SERVICE));
            System.out.println("runGet service --->>>>> ");
            String packageName = getBuilder.getPackageName();

            int uid=getPackageUid(packageName,0);

            List opsForPackage = appOpsService.getOpsForPackage(uid, packageName, null);
            List<PackageOps> packageOpses = new ArrayList<>();
            if (opsForPackage != null) {
                for (Object o : opsForPackage) {
                    packageOpses.add(ReflectUtils.opsConvert(o));
                }
            }
            server.sendResult(ParcelableUtil.marshall(new OpsResult(packageOpses, null)));
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println(Log.getStackTraceString(e));
            try {
                server.sendResult(ParcelableUtil.marshall(new OpsResult(null, e)));
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }



    private void runSet(OpsCommands.Builder builder) {

        try {
            final IAppOpsService appOpsService = IAppOpsService.Stub.asInterface(
                    ServiceManager.getService(Context.APP_OPS_SERVICE));
            final int uid = getPackageUid(builder.getPackageName(), 0);

            appOpsService.setMode(builder.getOpInt(), uid, builder.getPackageName(), builder.getModeInt());
            server.sendResult(ParcelableUtil.marshall(new OpsResult(null, null)));
        } catch (Exception e) {
            e.printStackTrace();
            try {
                server.sendResult(ParcelableUtil.marshall(new OpsResult(null, e)));
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    private void runReset(OpsCommands.Builder builder) {
        try {
            final IAppOpsService appOpsService = IAppOpsService.Stub.asInterface(
                    ServiceManager.getService(Context.APP_OPS_SERVICE));
            final int uid = getPackageUid(builder.getPackageName(), 0);

            appOpsService.resetAllModes(uid,builder.getPackageName());
            server.sendResult(ParcelableUtil.marshall(new OpsResult(null, null)));
        } catch (Exception e) {
            e.printStackTrace();
            try {
                server.sendResult(ParcelableUtil.marshall(new OpsResult(null, e)));
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    @Override
    public void onMessage(byte[] bytes) {
        handler.removeCallbacksAndMessages(null);
        handler.removeMessages(MSG_TIMEOUT);
        if (!isDeath) {
            handler.sendEmptyMessageDelayed(MSG_TIMEOUT, timeOut);

            OpsCommands.Builder unmarshall = ParcelableUtil.unmarshall(bytes, OpsCommands.Builder.CREATOR);
            System.out.println("onMessage ---> !!!! " + unmarshall);
            handleCommand(unmarshall);
        }
    }

    private int getPackageUid(String packageName,int flag){
        int uid=0;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            List<Class> paramsType=new ArrayList<>();
            paramsType.add(String.class);
            paramsType.add(int.class);
            paramsType.add(int.class);
            List<Object> params=new ArrayList<>();
            params.add(packageName);
            params.add(PackageManager.MATCH_UNINSTALLED_PACKAGES);
            params.add(flag);
            uid= (int) ReflectUtils.invokMethod(ActivityThread.getPackageManager(),"getPackageUid",paramsType,params);
        }else {
            List<Class> paramsType=new ArrayList<>();
            paramsType.add(String.class);
            paramsType.add(int.class);
            List<Object> params=new ArrayList<>();
            params.add(packageName);
            params.add(flag);
            uid= (int) ReflectUtils.invokMethod(ActivityThread.getPackageManager(),"getPackageUid",paramsType,params);
        }

        return uid;
    }

}
