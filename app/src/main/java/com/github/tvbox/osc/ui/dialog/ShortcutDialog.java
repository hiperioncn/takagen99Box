package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.activity.HomeActivity;
import com.github.tvbox.osc.ui.adapter.ApiHistoryDialogAdapter;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.XXPermissions;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 快捷包名配置对话框
 *
 * @author Hiperion
 * @since 2024/02/07
 */
public class ShortcutDialog extends BaseDialog {
    private final ImageView ivQRCode;
    private final TextView tvAddress;
    private final EditText inputPackageName;

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_PACKAGE_NAME_CHANGE) {
            inputPackageName.setText((String) event.obj);
        }
    }

    public ShortcutDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_shortcut);
        setCanceledOnTouchOutside(true);
        ivQRCode = findViewById(R.id.ivQRCode);
        tvAddress = findViewById(R.id.tvAddress);
        inputPackageName = findViewById(R.id.input);
        inputPackageName.setText(Hawk.get(HawkConfig.SHORTCUT_PACKAGE, ""));

        findViewById(R.id.inputSubmit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newApi = inputPackageName.getText().toString().trim();
                // takagen99: Convert all to clan://localhost format
                //                if (newApi.startsWith("file://")) {
                //                    newApi = newApi.replace("file://", "clan://localhost/");
                //                } else if (newApi.startsWith("./")) {
                //                    newApi = newApi.replace("./", "clan://localhost/");
                //                }
                if (!newApi.isEmpty()) {
                    ArrayList<String> history = Hawk.get(HawkConfig.SHORTCUT_HISTORY, new ArrayList<String>());
                    if (!history.contains(newApi)) {
                        history.add(0, newApi);
                    }
                    if (history.size() > 20) {
                        history.remove(20);
                    }
                    Hawk.put(HawkConfig.SHORTCUT_HISTORY, history);
                    listener.onchange(newApi);
                    dismiss();
                }
            }
        });
        findViewById(R.id.apiHistory).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<String> history = Hawk.get(HawkConfig.SHORTCUT_HISTORY, new ArrayList<String>());
                if (history.isEmpty()) {
                    return;
                }
                String current = Hawk.get(HawkConfig.SHORTCUT_PACKAGE, "");
                int idx = 0;
                if (history.contains(current)) {
                    idx = history.indexOf(current);
                }
                ApiHistoryDialog dialog = new ApiHistoryDialog(getContext());
                dialog.setTip(HomeActivity.getRes().getString(R.string.dia_history_list));
                dialog.setAdapter(new ApiHistoryDialogAdapter.SelectDialogInterface() {
                    @Override
                    public void click(String value) {
                        inputPackageName.setText(value);
                        listener.onchange(value);
                        dialog.dismiss();
                    }

                    @Override
                    public void del(String value, ArrayList<String> data) {
                        Hawk.put(HawkConfig.SHORTCUT_HISTORY, data);
                    }
                }, history, idx);
                dialog.show();
            }
        });

        findViewById(R.id.storagePermission).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (XXPermissions.isGranted(getContext(), DefaultConfig.StoragePermissionGroup())) {
                    Toast.makeText(getContext(), "已获得存储权限", Toast.LENGTH_SHORT).show();
                } else {
                    XXPermissions.with(getContext())
                            .permission(DefaultConfig.StoragePermissionGroup())
                            .request(new OnPermissionCallback() {
                                @Override
                                public void onGranted(List<String> permissions, boolean all) {
                                    if (all) {
                                        Toast.makeText(getContext(), "已获得存储权限", Toast.LENGTH_SHORT).show();
                                    }
                                }

                                @Override
                                public void onDenied(List<String> permissions, boolean never) {
                                    if (never) {
                                        Toast.makeText(getContext(), "获取存储权限失败,请在系统设置中开启", Toast.LENGTH_SHORT).show();
                                        XXPermissions.startPermissionActivity((Activity) getContext(), permissions);
                                    } else {
                                        Toast.makeText(getContext(), "获取存储权限失败", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                }
            }
        });
        refreshQRCode();
    }

    private void refreshQRCode() {
        String address = ControlManager.get().getAddress(false);
        tvAddress.setText(String.format("手机/电脑扫描上方二维码或者直接浏览器访问地址\n%s", address));
        ivQRCode.setImageBitmap(QRCodeGen.generateBitmap(address, AutoSizeUtils.mm2px(getContext(), 300), AutoSizeUtils.mm2px(getContext(), 300)));
    }

    public void setOnListener(OnListener listener) {
        this.listener = listener;
    }

    OnListener listener = null;

    public interface OnListener {
        void onchange(String api);
    }
}