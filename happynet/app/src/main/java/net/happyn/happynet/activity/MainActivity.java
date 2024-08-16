package net.happyn.happynet.activity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;

import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.NestedScrollView;
import androidx.drawerlayout.widget.DrawerLayout;

import android.text.Html;
import android.text.method.LinkMovementMethod;

import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;
import com.orhanobut.logger.Logger;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import net.happyn.happynet.HappynetApplication;
import net.happyn.happynet.R;
import net.happyn.happynet.event.ConnectingEvent;
import net.happyn.happynet.event.ErrorEvent;
import net.happyn.happynet.event.LogChangeEvent;
import net.happyn.happynet.event.StartEvent;
import net.happyn.happynet.event.StopEvent;
import net.happyn.happynet.event.SupernodeDisconnectEvent;
import net.happyn.happynet.model.EdgeStatus;
import net.happyn.happynet.model.N2NSettingInfo;
import net.happyn.happynet.service.N2NService;
import net.happyn.happynet.storage.db.base.model.N2NSettingModel;
import net.happyn.happynet.template.BaseTemplate;
import net.happyn.happynet.template.CommonTitleTemplate;
import net.happyn.happynet.tool.IOUtils;
import net.happyn.happynet.tool.N2nTools;
import net.happyn.happynet.tool.ShareUtils;
import net.happyn.happynet.tool.ThreadUtils;


public class MainActivity extends BaseActivity {

    private N2NSettingModel mCurrentSettingInfo;
    private RelativeLayout mCurrentSettingItem;
    private TextView mCurrentSettingName;
    private TextView mLogAction;
    private NestedScrollView mScrollLogAction;
    private ImageView mConnectBtn;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mActionBarDrawerToggle;
    private LinearLayout mLeftMenu;
    private String logTxtPath;
    private CheckBox mStartAtBoot;

    private static final int REQUECT_CODE_SDCARD = 1;
    private static final int REQUECT_CODE_VPN = 2;
    private static final int REQUEST_CODE_VPN_FOR_START_AT_BOOT = 3;

    @Override
    protected BaseTemplate createTemplate() {
        CommonTitleTemplate titleTemplate = new CommonTitleTemplate(this, getString(R.string.app_name));
        titleTemplate.mRightImg.setImageResource(R.mipmap.ic_add);
        titleTemplate.mRightImg.setVisibility(View.VISIBLE);
        titleTemplate.mRightImg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, SettingDetailsActivity.class);
                intent.putExtra("type", SettingDetailsActivity.TYPE_SETTING_ADD);
                startActivity(intent);
            }
        });

        titleTemplate.mLeftImg.setImageResource(R.mipmap.ic_menu);
        titleTemplate.mLeftImg.setVisibility(View.VISIBLE);
        titleTemplate.mLeftImg.setVisibility(View.VISIBLE);
        titleTemplate.mLeftImg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mDrawerLayout.isDrawerOpen(mLeftMenu)) {
                    mDrawerLayout.closeDrawer(mLeftMenu);
                } else {
                    mDrawerLayout.openDrawer(mLeftMenu);
                }
            }
        });

        return titleTemplate;
    }

    @Override
    protected void doOnCreate(Bundle savedInstanceState) {

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        mActionBarDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, null, R.string.open, R.string.close) {
            //菜单打开
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                drawerView.setClickable(true);
            }

            // 菜单关闭
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
            }
        };

        mDrawerLayout.setDrawerListener(mActionBarDrawerToggle);

        mLeftMenu = (LinearLayout) findViewById(R.id.ll_menu_left);

        mConnectBtn = (ImageView) findViewById(R.id.iv_connect_btn);
        mLogAction = (TextView) findViewById(R.id.tv_log_action);
        mScrollLogAction = (NestedScrollView) findViewById(R.id.scroll_log_action);

        if (N2NService.INSTANCE == null) {
            mConnectBtn.setImageResource(R.mipmap.ic_state_disconnect);
        } else {
            EdgeStatus.RunningStatus status = N2NService.INSTANCE.getCurrentStatus();
            if (status == EdgeStatus.RunningStatus.CONNECTED) {
                mConnectBtn.setImageResource(R.mipmap.ic_state_connect);
            } else if (status == EdgeStatus.RunningStatus.SUPERNODE_DISCONNECT) {
                mConnectBtn.setImageResource(R.mipmap.ic_state_supernode_diconnect);
            } else {
                mConnectBtn.setImageResource(R.mipmap.ic_state_disconnect);
            }
        }

        mConnectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mCurrentSettingName.getText().equals(getResources().getString(R.string.no_setting))) {
                    Toast.makeText(mContext, "no setting selected", Toast.LENGTH_SHORT).show();
                    return;
                }

                EdgeStatus.RunningStatus status = N2NService.INSTANCE == null ? EdgeStatus.RunningStatus.DISCONNECT : N2NService.INSTANCE.getCurrentStatus();
                if (N2NService.INSTANCE != null && status != EdgeStatus.RunningStatus.DISCONNECT && status != EdgeStatus.RunningStatus.FAILED) {
                    /* Asynchronous call */
                    mConnectBtn.setClickable(false);
                    mConnectBtn.setImageResource(R.mipmap.ic_state_connect_change);
                    N2NService.INSTANCE.stop(null);
                } else {
                    mConnectBtn.setClickable(false);
                    mConnectBtn.setImageResource(R.mipmap.ic_state_connect_change);
                    Intent vpnPrepareIntent = VpnService.prepare(MainActivity.this);
                    if (vpnPrepareIntent != null) {
                        startActivityForResult(vpnPrepareIntent, REQUECT_CODE_VPN);
                    } else {
                        onActivityResult(REQUECT_CODE_VPN, RESULT_OK, null);
                    }
                }
            }
        });


        mCurrentSettingItem = (RelativeLayout) findViewById(R.id.rl_current_setting_item);
        mCurrentSettingItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, ListActivity.class));
            }
        });

        mCurrentSettingName = (TextView) findViewById(R.id.tv_current_setting_name);
        mCurrentSettingName.setText(R.string.no_setting);

        mStartAtBoot = (CheckBox) findViewById(R.id.check_box_start_at_boot);
        SharedPreferences n2nSp = getSharedPreferences("happynet", Context.MODE_PRIVATE);
        if (n2nSp.getBoolean("start_at_boot", false))
            mStartAtBoot.setChecked(true);

        mStartAtBoot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mStartAtBoot.isChecked()) {
                    Intent vpnPrepareIntent = VpnService.prepare(MainActivity.this);
                    if (vpnPrepareIntent != null) {
                        startActivityForResult(vpnPrepareIntent, REQUEST_CODE_VPN_FOR_START_AT_BOOT);
                        return;
                    }
                }
                SharedPreferences n2nSp = getSharedPreferences("happynet", MODE_PRIVATE);
                n2nSp.edit().putBoolean("start_at_boot", mStartAtBoot.isChecked()).apply();
            }
        });
        initLeftMenu();

    }

    private void initLeftMenu() {
        TextView appVersion = (TextView) findViewById(R.id.tv_app_version);
        appVersion.setText(N2nTools.getVersionName(this));

        TextView appInfo = (TextView) findViewById(R.id.tv_app_info);
        String appInfoText = "<a href=\"https://github.com/switch-iot/hin2n\">hi2n2</a>";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appInfo.setText(Html.fromHtml(appInfoText, Html.FROM_HTML_MODE_COMPACT));
        } else {
            appInfo.setText(Html.fromHtml(appInfoText));
        }

        //appInfo.setText(Html.fromHtml(appInfoText));
        appInfo.setMovementMethod(LinkMovementMethod.getInstance());

        RelativeLayout shareItem = (RelativeLayout) findViewById(R.id.rl_share);
        shareItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Logger.d("shareItem onClick~");

                if (Build.VERSION.SDK_INT >= 23) {
                    List<String> requestList = new ArrayList<String>();
                    requestList.add(Permission.READ_EXTERNAL_STORAGE);
                    requestList.add(Permission.ACCESS_FINE_LOCATION);
                    requestList.add(Permission.READ_PHONE_STATE);
                    XXPermissions.with(MainActivity.this)
                            .permission(requestList).request(new OnPermissionCallback() {
                        @Override
                        public void onGranted(@NonNull List<String> permissions, boolean allGranted) {
                            ShareUtils.doOnClickShareItem(MainActivity.this);
                        }

                        @Override
                        public void onDenied(@NonNull List<String> permissions, boolean doNotAskAgain) {
                            Toast.makeText(MainActivity.this, "I NEED PERMISSIONS!", Toast.LENGTH_SHORT).show();
                        }
                    });
//                    AndPermission.with(MainActivity.this)
//                            .runtime()
//                            .permission(Permission.READ_EXTERNAL_STORAGE, Permission.ACCESS_FINE_LOCATION, Permission.READ_PHONE_STATE)
//                            .onGranted(new Action<List<String>>() {
//                                @Override
//                                public void onAction(List<String> data) {
//                                    ShareUtils.doOnClickShareItem(MainActivity.this);
//                                }
//                            })
//                            .onDenied(new Action<List<String>>() {
//                                @Override
//                                public void onAction(List<String> data) {
//                                    Toast.makeText(MainActivity.this, "I NEED PERMISSIONS!", Toast.LENGTH_SHORT).show();
//                                }
//                            }).start();
                } else {
                    ShareUtils.doOnClickShareItem(MainActivity.this);
                }

            }
        });
        shareItem.setVisibility(View.GONE);     // @TODO 暂时不显示

        RelativeLayout contactItem = (RelativeLayout) findViewById(R.id.rl_contact);
        contactItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ShareUtils.joinQQGroup(MainActivity.this);
            }
        });
        contactItem.setVisibility(View.GONE);     // @TODO 暂时不显示

        RelativeLayout feedbackItem = (RelativeLayout) findViewById(R.id.rl_feedback);
        feedbackItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, WebViewActivity.class);
                intent.putExtra(WebViewActivity.WEB_VIEW_TYPE, WebViewActivity.TYPE_WEB_VIEW_FEEDBACK);
                startActivity(intent);
            }
        });
        feedbackItem.setVisibility(View.GONE);     // @TODO 暂时不显示

        RelativeLayout checkUpdateItem = (RelativeLayout) findViewById(R.id.rl_check_update);
        checkUpdateItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                Beta.checkUpgrade();
            }
        });
        checkUpdateItem.setVisibility(View.GONE);     // @TODO 暂时不显示

        RelativeLayout aboutItem = (RelativeLayout) findViewById(R.id.rl_about);
        aboutItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent intent = new Intent(MainActivity.this, WebViewActivity.class);
                intent.putExtra(WebViewActivity.WEB_VIEW_TYPE, WebViewActivity.TYPE_WEB_VIEW_ABOUT);
                startActivity(intent);
            }
        });
    }


    @Override
    protected int getContentLayout() {
        return R.layout.activity_main;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUECT_CODE_VPN && resultCode == RESULT_OK) {
            Intent intent = new Intent(MainActivity.this, N2NService.class);
            Bundle bundle = new Bundle();
            N2NSettingInfo n2NSettingInfo = new N2NSettingInfo(mCurrentSettingInfo);
            bundle.putParcelable("n2nSettingInfo", n2NSettingInfo);
            intent.putExtra("Setting", bundle);

            startService(intent);
        } else if (requestCode == REQUEST_CODE_VPN_FOR_START_AT_BOOT) {
            mStartAtBoot = (CheckBox) findViewById(R.id.check_box_start_at_boot);
            if (mStartAtBoot.isChecked()) {
                if (resultCode == RESULT_OK) {
                    SharedPreferences n2nSp = getSharedPreferences("happynet", MODE_PRIVATE);
                    n2nSp.edit().putBoolean("start_at_boot", true).apply();
                } else {
                    mStartAtBoot.setChecked(false);
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences n2nSp = getSharedPreferences("happynet", MODE_PRIVATE);
        logTxtPath = n2nSp.getString("current_log_path", "");
        showLog();
        Long currentSettingId = n2nSp.getLong("current_setting_id", -1);
        if (currentSettingId != -1) {
            mCurrentSettingInfo = HappynetApplication.getInstance().getDaoSession().getN2NSettingModelDao().load((long) currentSettingId);
            if (mCurrentSettingInfo != null) {
                mCurrentSettingName.setText(mCurrentSettingInfo.getName());
                mStartAtBoot = (CheckBox) findViewById(R.id.check_box_start_at_boot);
                mStartAtBoot.setClickable(true);
            } else {
                mCurrentSettingName.setText(R.string.no_setting);
                mStartAtBoot = (CheckBox) findViewById(R.id.check_box_start_at_boot);
                mStartAtBoot.setClickable(false);
                mStartAtBoot.setChecked(false);
            }

            mConnectBtn.setVisibility(View.VISIBLE);
            if (N2NService.INSTANCE == null) {
                mConnectBtn.setImageResource(R.mipmap.ic_state_disconnect);
            } else {
                EdgeStatus.RunningStatus status = N2NService.INSTANCE.getCurrentStatus();
                if (status == EdgeStatus.RunningStatus.CONNECTED) {
                    mConnectBtn.setImageResource(R.mipmap.ic_state_connect);
                } else if (status == EdgeStatus.RunningStatus.SUPERNODE_DISCONNECT) {
                    mConnectBtn.setImageResource(R.mipmap.ic_state_supernode_diconnect);
                } else {
                    mConnectBtn.setImageResource(R.mipmap.ic_state_disconnect);
                }
            }
        } else {
            mStartAtBoot = (CheckBox) findViewById(R.id.check_box_start_at_boot);
            mStartAtBoot.setClickable(false);
            mStartAtBoot.setChecked(false);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences n2nSp = getSharedPreferences("happynet", MODE_PRIVATE);
        n2nSp.edit().putString("current_log_path", logTxtPath).apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onStartEvent(StartEvent event) {
        mConnectBtn.postDelayed(new Runnable() {
            @Override
            public void run() {
                mConnectBtn.setImageResource(R.mipmap.ic_state_connect);
                mConnectBtn.setClickable(true);
            }
        }, 200);

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onStopEvent(StopEvent event) {
        mConnectBtn.postDelayed(new Runnable() {
            @Override
            public void run() {
                mConnectBtn.setImageResource(R.mipmap.ic_state_disconnect);
                mConnectBtn.setClickable(true);
            }
        }, 200);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onErrorEvent(ErrorEvent event) {
        mConnectBtn.setVisibility(View.VISIBLE);
        mConnectBtn.setImageResource(R.mipmap.ic_state_disconnect);

        Toast.makeText(mContext, getString(R.string.toast_connect_failed), Toast.LENGTH_SHORT).show();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConnectingEvent(ConnectingEvent event) {
        mConnectBtn.setVisibility(View.GONE);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSupernodeDisconnectEvent(SupernodeDisconnectEvent event) {
        mConnectBtn.setVisibility(View.VISIBLE);
        mConnectBtn.setImageResource(R.mipmap.ic_state_supernode_diconnect);
        Toast.makeText(mContext, getString(R.string.toast_disconnect_and_retry), Toast.LENGTH_SHORT).show();
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onLogChangeEvent(final LogChangeEvent event) {
//        Toast.makeText(this,"LogChageEvent",Toast.LENGTH_SHORT).show();
        logTxtPath = event.getTxtPath();
        showLog();
    }

    private void showLog() {
        ThreadUtils.cachedThreadExecutor(new Runnable() {
            @Override
            public void run() {
                if (!TextUtils.isEmpty(logTxtPath)) {
                    final String logText = IOUtils.readTxtLimit(logTxtPath, 1024 * 2);
                    ThreadUtils.mainThreadExecutor(new Runnable() {
                        @Override
                        public void run() {
                            mLogAction.setText(logText);
                            mLogAction.post(new Runnable() {
                                @Override
                                public void run() {
                                    mScrollLogAction.fullScroll(NestedScrollView.FOCUS_DOWN);
                                }
                            });
                        }
                    });
                }
            }
        });
    }


}
