package school.lg.overseas.school.utils;

import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import com.caimuhao.rxpicker.utils.T;
import com.liulishuo.filedownloader.BaseDownloadTask;
import com.liulishuo.filedownloader.FileDownloadLargeFileListener;
import com.liulishuo.filedownloader.FileDownloadListener;
import com.liulishuo.filedownloader.FileDownloadSampleListener;
import com.liulishuo.filedownloader.FileDownloader;
import com.liulishuo.filedownloader.model.FileDownloadStatus;
import com.liulishuo.filedownloader.util.FileDownloadUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Timer;
import java.util.TimerTask;

import constant.DownLoadBy;
import constant.UiType;
import listener.OnBtnClickListener;
import model.UiConfig;
import model.UpdateConfig;
import school.lg.overseas.school.MyApplication;
import school.lg.overseas.school.R;
import school.lg.overseas.school.ui.MainActivity;
import update.UpdateAppUtils;

/**
 * Author: Gary
 * Date: 2020/10/27 14:00
 * Description: apk更新下载FileDownloader相关处理以及更新进度状态栏相关处理
 */
public class NotificationUpdateApkUtils extends ContextWrapper {

    private NotificationManager manager;
    private Notification notification;
    private RemoteViews remoteViews;
    //通道ID，包中唯一
    public static final String id = "channel_update_apk";
    //通道用户可见名称
    public static final String name = "软件更新";
    public final static String INTENT_BUTTONID_TAG = "clickId";
    public final static String ACTION_BUTTON = "com.notification.intent.action.ButtonClick";

    //暂停状态标识
    private boolean isPause = false;
    /**
     * 暂停/停止 相关点击ID
     */
    public static final int BUTTON_CONTINUE_ID = 1;
    public static final int BUTTON_STOP_ID = 0;
    public static final int NOTIFI_CLICK_ID = -1;
    public static final int LAYOUT_ID = 2;

    //下载进度   最高为100
    private int progress = 0;

    private final int NOTIFICATION_ID = 0xa01;

    private String versionDescription = "软件细节优化";
    private BaseDownloadTask downloadTask;
    //downloadID.通过id暂停下载和结束下载
    public int downloadTaskId = 0;
    //安装包保存文件夹路径
    public String mSavePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/thinku";
    //安装包保存全名路径
    public String mSaveName = "";

    private final Context mContext;

    //handler执行每秒刷新一次数据（下载过程中才刷新）,暂停或结束时停止
    private final Handler handlerUpdate = new Handler();
    //按钮点击计时器  实现延迟点击计数的加减
    private final Timer timer = new Timer();
    //延迟点击计数，为0时点击布局才能做相关事件处理，为2时点击继续按钮才能做相关点击处理
    int countClick = 0;

    public NotificationUpdateApkUtils(Context base) {
        super(base);
        mContext = base;
    }

    private NotificationManager getManager() {
        if (manager == null) {
            manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        }
        return manager;
    }

    /**
     * app更新   应当检查用户是否给与软件通知权限，否则无法实现通知
     */
    @SuppressLint("SetTextI18n")
    public void updata() {
        //更新弹窗自定义
        UiConfig uiConfig = new UiConfig();
        //弹窗类型
        uiConfig.setUiType(UiType.CUSTOM);
        //自定义弹窗布局ID
        uiConfig.setCustomLayoutId(R.layout.dialog_update_apk);
        //更新按钮设置
        uiConfig.setUpdateBtnText("更新");
        //退出按钮设置
        uiConfig.setCancelBtnText("下次");
        //更新开始提示弹窗
        uiConfig.setDownloadingToastText("下载更新开始");

        //更新配置说明
        UpdateConfig updateConfig = new UpdateConfig();
        //是否调试，输出日志
        updateConfig.setDebug(true);
        //是否强制更新
        updateConfig.setForce(false);
        //服务器apk版本号
        updateConfig.setServerVersionCode(Integer.parseInt(MyApplication.VersionCode));
        //服务器apk版本名
        updateConfig.setServerVersionName(MyApplication.versionName);
        //是否显示通知栏进度  (自定义通知栏，不使用它的)
        updateConfig.setShowNotification(false);
        //更新下载方式
        updateConfig.setDownloadBy(DownLoadBy.APP);

        try {
            final Button[] buttonCancle = new Button[1];
            //对服务器更新描述做个非空判断，如果为空则显示基本更新文本
            if (!TextUtils.isEmpty(MyApplication.versionDescription) && !MyApplication.versionDescription.equals("")){
                versionDescription = MyApplication.versionDescription;
            }
            Log.i("更新描述", versionDescription);
            UpdateAppUtils.getInstance()
                    .apkUrl(MyApplication.apkPath)
                    .updateTitle("新版本功能介绍")         //弹窗标题
                    .updateContent(versionDescription)    //弹窗更新内容
                    .updateConfig(updateConfig)
                    .uiConfig(uiConfig)
                    .setOnInitUiListener((view, updateConfig1, uiConfig1) -> {
                        //最新版本号
                        TextView tvServerVersionName = view.findViewById(R.id.tv_update_server_version_name);
                        tvServerVersionName.setText(getResources().getString(R.string.str_newest_version) + MyApplication.versionName);
                        //本地版本号
                        TextView tvLocalVersionName = view.findViewById(R.id.tv_update_local_version_name);
                        tvLocalVersionName.setText(getResources().getString(R.string.str_local_version) + MyApplication.localVersionName);

                        buttonCancle[0] = view.findViewById(R.id.btn_update_cancel);
                        //自定义右上角退出按钮点击事件           注：如果强制更新应该设置本按钮不可见
                        ImageButton ibUpdataClose = view.findViewById(R.id.btn_update_close);
                        ibUpdataClose.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                buttonCancle[0].performClick();
                            }
                        });
                    })
                    .setUpdateBtnClickListener(new OnBtnClickListener() {
                        @Override
                        public boolean onClick() {
                            //不执行第三方UpdateAppUtils的更新方法，所以一律返回true
                            if (isNotificationEnabled(mContext)) {
                                //已获得权限直接开启下载
                                MyApplication.notificationUpdateApkUtils.startFileDownload();
                                Toast.makeText(mContext, "开始下载", Toast.LENGTH_SHORT).show();
                                buttonCancle[0].performClick();
                            } else {
                                //未获得权限进行权限页面跳转
                                Toast.makeText(mContext, "请给与系统显示通知权限，以获得通知栏更新进度条", Toast.LENGTH_SHORT).show();
                                PermissionUtil.gotoPermission(mContext);
                            }
                            return true;
                        }
                    })
                    .update();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 创建Notification
     */
    public void startNotification() {
        Intent intentNotifi = new Intent(ACTION_BUTTON);
        intentNotifi.putExtra(INTENT_BUTTONID_TAG, NOTIFI_CLICK_ID);
        PendingIntent pi = PendingIntent.getBroadcast(mContext, NOTIFI_CLICK_ID,
                intentNotifi, PendingIntent.FLAG_UPDATE_CURRENT);
        if (Build.VERSION.SDK_INT >= 26) {
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel mChannel = new NotificationChannel(id, name, importance);
            mChannel.enableLights(false);                                  //消息栏通知闪光灯设置
            mChannel.enableVibration(false);                               //消息栏通知震动设置
            mChannel.setVibrationPattern(new long[]{0});                   //消息栏通知振动模式设置
            mChannel.setSound(null, null);                                 //消息栏通知声音设置
            getManager().createNotificationChannel(mChannel);
            notification = new NotificationCompat.Builder(mContext, id)
                    .setSmallIcon(R.mipmap.logo_round)
                    .setWhen(System.currentTimeMillis())
                    .setContent(getContentView())
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .setChannelId(mChannel.getId())
                    .build();
            getManager().notify(NOTIFICATION_ID, notification);
        } else {
            notification = new NotificationCompat.Builder(mContext, id)
                    .setSmallIcon(R.mipmap.logo_round)
                    .setWhen(System.currentTimeMillis())
                    .setContent(getContentView())
                    .setDefaults(NotificationCompat.FLAG_ONLY_ALERT_ONCE)
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .build();
            getManager().notify(NOTIFICATION_ID, notification);
        }
        getManager().notify(NOTIFICATION_ID, notification);
        //通知栏启动后开启计时，1秒执行一次刷新数据
        handlerUpdate.postDelayed(runnable, 1000);
    }

    /**
     * 开启FileDownloader下载   （在fileDownLoader内部start监听中开启notification）
     */
    public void startFileDownload() {
        mSaveName = mSavePath + "/ThinkU" + MyApplication.versionName + ".apk";

        downloadTask = FileDownloader.getImpl().create(MyApplication.apkPath)
                .setPath(mSaveName)
                .setCallbackProgressTimes(300)      //设置整个下载过程中FileDownloadListener#progress最大回调次数
                .setMinIntervalUpdateSpeed(400)     //设置下载中刷新下载速度的最小间隔
                .setForceReDownload(true)           //强制重新下载，将会忽略检测文件是否健在
//                .addHeader("Accept-Encoding", "identity")
//                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2062.120 Safari/537.36")
                .setListener(fileDownloadSampleListener);
        downloadTaskId = downloadTask.start();
    }

    private FileDownloadSampleListener fileDownloadSampleListener = new FileDownloadSampleListener() {
        @Override
        protected void pending(BaseDownloadTask task, int soFarBytes, int totalBytes) {
            super.pending(task, soFarBytes, totalBytes);
            Log.i("update_pending:", "下载开始");
            //开始下载，创建notification
            startNotification();
        }

        @Override
        protected void paused(BaseDownloadTask task, int soFarBytes, int totalBytes) {
            super.paused(task, soFarBytes, totalBytes);
            Log.i("update_paused:", "下载暂停");
            isPause = true;
            getManager().notify(NOTIFICATION_ID, notification);
        }

        @Override
        protected void progress(BaseDownloadTask task, int soFarBytes, int totalBytes) {
            super.progress(task, soFarBytes, totalBytes);
            //下载过程中传递下载进度
            progress = (int) (soFarBytes * 100.0 / totalBytes);
//            Log.i("update_progress:", "下载中" + progress + "%");
        }

        @Override
        protected void blockComplete(BaseDownloadTask task) {
            super.blockComplete(task);
            Log.i("update_bloackcompleted:", "update_bloackcompleted");
        }

        @Override
        protected void completed(BaseDownloadTask task) {
            super.completed(task);
            isPause = false;
            countClick = 0;
            Log.i("update_countClickLess", String.valueOf(countClick));
            Log.i("update_completed:", "下载完成");
            //下载完成关闭notification并执行安装程序
            stopNotification();
            getManager().cancel(NOTIFICATION_ID);
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                getManager().deleteNotificationChannel(id);
            }
            installApk(mSaveName);
        }

        @Override
        protected void error(BaseDownloadTask task, Throwable e) {
            super.error(task, e);
            isPause = false;
            countClick = 0;
            Log.i("update_error:", "下载出错");
            Toast.makeText(mContext, "下载出错，请重试！", Toast.LENGTH_SHORT).show();
            delete_single();
            stopNotification();
        }

        @Override
        protected void warn(BaseDownloadTask task) {
            super.warn(task);
            Log.i("update_warn:", "update_bloackcompleted");
        }
    };

    /**
     * 暂停FileDowloader下载
     */
    public void pauseFileDownload() {
        FileDownloader.getImpl().pause(downloadTaskId);
        getManager().notify(NOTIFICATION_ID, notification);
    }

    /**
     * 删除当前downloadTaskId的记录
     */
    public void delete_single() {
        /**
         *  延迟点击计数设置为0，因为整个util在application中初始化，
         *  当不退出后台而重新进入下载更新时不会重新设置计数初始值，
         *  这样在点击事件判断时就会出错，所以在下载任务被终止与完成时设定计数为初始值0.
         */
        countClick = 0;
        Log.i("update_countClickLess", String.valueOf(countClick));
        //清除掉downloadid的下载任务
        boolean deleteData = FileDownloader.getImpl().clear(downloadTaskId, mSavePath);
        File targetFile = new File(mSaveName);
        boolean delete = false;
        //删除下载文件
        if (targetFile.exists()) {
            delete = targetFile.delete();
        }
        new File(FileDownloadUtils.getTempPath(mSaveName)).delete();
    }

    /**
     * apk安装
     *
     * @param apkPath apk 路径
     */
    public void installApk(String apkPath) {
        if (TextUtils.isEmpty(apkPath)) {
            return;
        } else if (!new File(apkPath).exists()) {
            Log.i("update_install", "安装包不存在");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Log.i("update_apkpath:", apkPath);
        File apkFile = new File(apkPath);
        // android 7.0 fileprovider 适配
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Uri contentUri = FileProvider.getUriForFile(mContext, getPackageName() + ".fileprovider", apkFile);
            intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
        } else {
            intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        this.startActivity(intent);
    }

    /**
     * 自定义的通知栏相关布局
     *
     * @return
     */
    private RemoteViews getContentView() {
        remoteViews = new RemoteViews(getPackageName(), R.layout.view_update_notification);
        ButtonBroadcastReceiver receiverPause = new ButtonBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_BUTTON);
        mContext.registerReceiver(receiverPause, intentFilter);
        Intent intentPause = new Intent(ACTION_BUTTON);
        intentPause.putExtra(INTENT_BUTTONID_TAG, BUTTON_CONTINUE_ID);
        Intent intenStop = new Intent(ACTION_BUTTON);
        intenStop.putExtra(INTENT_BUTTONID_TAG, BUTTON_STOP_ID);
        Intent intenLayout = new Intent(ACTION_BUTTON);
        intenLayout.putExtra(INTENT_BUTTONID_TAG, LAYOUT_ID);

        PendingIntent pendingIntentLayout = PendingIntent.getBroadcast(mContext, LAYOUT_ID, intenLayout, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pendingIntentPause = PendingIntent.getBroadcast(mContext, BUTTON_CONTINUE_ID, intentPause, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pendingIntentStop = PendingIntent.getBroadcast(mContext, BUTTON_STOP_ID, intenStop, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.rl_update_notification, pendingIntentLayout);
        remoteViews.setOnClickPendingIntent(R.id.btn_update_notification_stop, pendingIntentStop);
        remoteViews.setOnClickPendingIntent(R.id.btn_update_notification_continue, pendingIntentPause);
        return remoteViews;
    }

    /**
     * apk下载过程发送下载进度并更新
     *
     * @param progress
     */
    public void sendDownLoadProgress(int progress) {
        remoteViews.setTextViewText(R.id.tv_update_notification, "正在下载：" + progress + "%");
        remoteViews.setProgressBar(R.id.pb_update_notification, 100, progress, false);
        getManager().notify(NOTIFICATION_ID, notification);
    }

    /**
     * app下载完成,出错或点击停止时退出通知栏
     */
    public void stopNotification() {
        //通知栏退出时关闭计时刷新
        handlerUpdate.removeCallbacks(runnable);
        getManager().cancel(NOTIFICATION_ID);
    }


    /**
     * 通知栏被点击收到广播的相应事件处理
     */
    public class ButtonBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_BUTTON)) {
                int buttonid = intent.getIntExtra(INTENT_BUTTONID_TAG, 0);
                switch (buttonid) {
                    case BUTTON_CONTINUE_ID:
                        Log.i("update_buttonclick:", "继续按钮被点击");
                        //继续按钮点击事件
                        //下载暂停标识为true表示当前下载任务暂停，做继续下载操作
                        if (isPause) {
                            Log.i("update_pauseclick:", "继续被点击");
                            //判断下载进程是否在暂停过程中
                            if (FileDownloader.getImpl().getStatusIgnoreCompleted(downloadTaskId) == FileDownloadStatus.paused) {
                                //countClick = 2表示当前可继续，在内部执行减计数器，减到0时点击布局才能触发点击事件
                                if (countClick == 2) {
                                    isPause = false;
                                    Log.i("update_pauseclicktrue:", "执行继续方法");
                                    handlerUpdate.postDelayed(runnable, 1000);
                                    //继续下载切换进度条相关布局
                                    remoteViews.setViewVisibility(R.id.tv_update_notification_pause, View.GONE);
                                    remoteViews.setViewVisibility(R.id.btn_update_notification_stop, View.GONE);
                                    remoteViews.setViewVisibility(R.id.btn_update_notification_continue, View.GONE);
                                    remoteViews.setViewVisibility(R.id.tv_update_notification, View.VISIBLE);
                                    remoteViews.setViewVisibility(R.id.pb_update_notification, View.VISIBLE);
                                    timerLess();
                                    getManager().notify(NOTIFICATION_ID, notification);
                                    startFileDownload();

                                }
                            } else {
                                Toast.makeText(mContext, "请勿频繁点击！", Toast.LENGTH_SHORT).show();
                            }
                        }
                        break;
                    case BUTTON_STOP_ID:
                        isPause = false;
                        Log.i("update_buttonclick:", "停止按钮被点击");
                        //停止按钮点击事件:
                        delete_single();
                        stopNotification();
                        break;
                    case NOTIFI_CLICK_ID:
                        //通知栏条目点击事件
//                        Toast.makeText(mContext, "条目被点击", Toast.LENGTH_SHORT).show();
                        break;
                    case LAYOUT_ID:
                        Log.i("update_buttonclick:", "整个布局被点击");
                        //整个条目布局点击事件 只有在非暂停状态才能点击切换
                        //isPause为false时做暂停操作并切换布局
                        if (!isPause) {
                            Log.i("update_layoutclick:", "布局被点击");
                            //判断下载线程是否在下载过程中
                            if (FileDownloader.getImpl().getStatusIgnoreCompleted(downloadTaskId) == FileDownloadStatus.progress) {
                                //countClick = 0表示当前可暂停   在内部执行加计数器，加到2时点击继续才可以触发下载事件
                                if (countClick == 0) {
                                    isPause = true;
                                    Log.i("update_layoutclicktrue:", "执行暂停方法");
                                    handlerUpdate.removeCallbacks(runnable);
                                    //暂停下载切换到暂停相关按钮布局
                                    remoteViews.setTextViewText(R.id.tv_update_notification_pause, "正在下载：" + progress + "%");
                                    remoteViews.setTextViewText(R.id.tv_update_notification, "正在下载：" + progress + "%");
                                    remoteViews.setViewVisibility(R.id.tv_update_notification_pause, View.VISIBLE);
                                    remoteViews.setViewVisibility(R.id.btn_update_notification_stop, View.VISIBLE);
                                    remoteViews.setViewVisibility(R.id.btn_update_notification_continue, View.VISIBLE);
                                    remoteViews.setViewVisibility(R.id.tv_update_notification, View.GONE);
                                    remoteViews.setViewVisibility(R.id.pb_update_notification, View.GONE);
                                    remoteViews.setTextViewText(R.id.btn_update_notification_continue, "暂停中~");
                                    timerPlus();
                                    getManager().notify(NOTIFICATION_ID, notification);
                                    pauseFileDownload();
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
        }
    }

    /**
     * 1秒刷新一次通知栏。
     */
    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            sendDownLoadProgress(progress);
            handlerUpdate.postDelayed(this, 1000);
        }
    };

    /**
     * 加计时器    点击布局实现暂停效果时进行计数加
     */
    private void timerPlus() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                //做下载状态判断，如果在下载才执行加计时器方法（避免暂停计时过程中点击停止下载而计时器还在做更新）
                if (isPause) {
                    if (countClick < 2) {
                        countClick++;
                        Log.i("update_countClickPlus", String.valueOf(countClick));
                        if (countClick == 2) {
                            remoteViews.setTextViewText(R.id.btn_update_notification_continue, "继续下载");
                            getManager().notify(NOTIFICATION_ID, notification);
                        } else {
                            timerPlus();
                        }
                    }
                }
            }
        }, 1000);
    }

    /**
     * 减计时器    点击继续实现继续下载效果时进行计数减
     */
    private void timerLess() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!isPause) {
                    if (countClick > 0) {
                        countClick--;
                        Log.i("update_countClickLess", String.valueOf(countClick));
                        if (countClick == 0) {

                        } else {
                            timerLess();
                        }
                    }
                }
            }
        }, 1000);
    }

    /**
     * 检查是否拥有通知栏权限
     *
     * @param context
     * @return
     */
    private boolean isNotificationEnabled(Context context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //8.0手机以上
            if (((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).getImportance() == NotificationManager.IMPORTANCE_NONE) {
                return false;
            }
        }
        String CHECK_OP_NO_THROW = "checkOpNoThrow";
        String OP_POST_NOTIFICATION = "OP_POST_NOTIFICATION";

        AppOpsManager mAppOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        ApplicationInfo appInfo = context.getApplicationInfo();
        String pkg = context.getApplicationContext().getPackageName();
        int uid = appInfo.uid;

        Class appOpsClass = null;
        try {
            appOpsClass = Class.forName(AppOpsManager.class.getName());
            Method checkOpNoThrowMethod = appOpsClass.getMethod(CHECK_OP_NO_THROW, Integer.TYPE, Integer.TYPE,
                    String.class);
            Field opPostNotificationValue = appOpsClass.getDeclaredField(OP_POST_NOTIFICATION);

            int value = (Integer) opPostNotificationValue.get(Integer.class);
            return ((Integer) checkOpNoThrowMethod.invoke(mAppOps, value, uid, pkg) == AppOpsManager.MODE_ALLOWED);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}

