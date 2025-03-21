package com.github.tvbox.osc.ui.activity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.storage.StorageManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.DriveFolderFile;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.cache.StorageDrive;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.adapter.DriveAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.AlistDriveDialog;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.WebdavDialog;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.StorageDriveType;
import com.github.tvbox.osc.util.StringUtils;
import com.github.tvbox.osc.viewmodel.drive.AbstractDriveViewModel;
import com.github.tvbox.osc.viewmodel.drive.AlistDriveViewModel;
import com.github.tvbox.osc.viewmodel.drive.LocalDriveViewModel;
import com.github.tvbox.osc.viewmodel.drive.WebDAVDriveViewModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.obsez.android.lib.filechooser.ChooserDialog;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class DriveActivity extends BaseActivity {
    private LinearLayout titleLayout;
    private TextView txtTitle;
    private TvRecyclerView mGridView;
    private ImageButton btnAddServer;
    private ImageButton btnRemoveServer;
    private ImageButton btnSort;
    private DriveAdapter adapter = new DriveAdapter();
    private List<DriveFolderFile> drives = null;
    List<DriveFolderFile> searchResult = null;
    private AbstractDriveViewModel viewModel = null;
    private AbstractDriveViewModel backupViewModel = null;
    private int sortType = 0;
    private View footLoading;
    private boolean isInSearch = false;

    private boolean delMode = false;

    private Handler mHandler = new Handler();

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_drive;
    }

    @Override
    protected void init() {
        initView();
        initData();
        handleSendIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 在这里处理Intent
        handleSendIntent(intent);
    }

    private void handleSendIntent(Intent intent) {
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_VIEW.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                // 处理文本
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sharedText != null) {
                    Log.d("########", "handleSendIntent: " + sharedText);
                    // 使用sharedText
                }
            } else if (type.startsWith("image/")) {
                // 处理图片
                //                handleSharedImage(intent);
            } else if (type.startsWith("video")) {
                Uri uri = intent.getData();
                if (uri != null) {
                    Log.d("########", "action: " + action + " type:" + type + " uri:" + uri.toString());
                    playFile(uri.toString());
                }
            }
        }
    }

    private void initView() {
        EventBus.getDefault().register(this);

        titleLayout = findViewById(R.id.titleLayout);
        this.txtTitle = findViewById(R.id.textView);
        this.btnAddServer = findViewById(R.id.btnAddServer);
        this.mGridView = findViewById(R.id.mGridView);
        this.btnRemoveServer = findViewById(R.id.btnRemoveServer);
        this.btnSort = findViewById(R.id.btnSort);
        footLoading = getLayoutInflater().inflate(R.layout.item_search_lite, null);
        footLoading.findViewById(R.id.tvName).setVisibility(View.GONE);
        this.btnRemoveServer.setColorFilter(ContextCompat.getColor(mContext, R.color.color_FFFFFF));

        //标题栏添加点击返回事件
        titleLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });
        this.btnRemoveServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleDelMode();
            }
        });
        findViewById(R.id.btnHome).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DriveActivity.super.onBackPressed();
            }
        });
        this.btnSort.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                openSortDialog();
            }
        });
        this.btnAddServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                StorageDriveType.TYPE[] types = StorageDriveType.TYPE.values();
                SelectDialog<StorageDriveType.TYPE> dialog = new SelectDialog<>(DriveActivity.this);
                dialog.setTip("请选择存盘类型");
                dialog.setItemCheckDisplay(false);
                String[] typeNames = StorageDriveType.getTypeNames();
                dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<StorageDriveType.TYPE>() {
                    @Override
                    public void click(StorageDriveType.TYPE value, int pos) {
                        if (value == StorageDriveType.TYPE.LOCAL) {
                            if (Build.VERSION.SDK_INT >= 23) {
                                if (App.getInstance().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                                    ActivityCompat.requestPermissions(DriveActivity.this,
                                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                                    return;
                                }
                            }
                            openFilePicker();
                            dialog.dismiss();
                        } else if (value == StorageDriveType.TYPE.WEBDAV) {
                            openWebdavDialog(null);
                            dialog.dismiss();
                        } else if (value == StorageDriveType.TYPE.ALISTWEB) {
                            openAlistDriveDialog(null);
                            dialog.dismiss();
                        } else if (value == StorageDriveType.TYPE.SMB) {
                            getStoragePath(mContext, true);
                            dialog.dismiss();
                        }
                    }

                    @Override
                    public String getDisplay(StorageDriveType.TYPE val) {
                        return typeNames[val.ordinal()];
                    }
                }, new DiffUtil.ItemCallback<StorageDriveType.TYPE>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull StorageDriveType.TYPE oldItem, @NonNull @NotNull StorageDriveType.TYPE newItem) {
                        return oldItem.equals(newItem);
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull StorageDriveType.TYPE oldItem, @NonNull @NotNull StorageDriveType.TYPE newItem) {
                        return oldItem.equals(newItem);
                    }
                }, Arrays.asList(types), 0);
                dialog.show();
            }
        });
        this.mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, V7LinearLayoutManager.VERTICAL, false));
        this.mGridView.setSpacingWithMargins(AutoSizeUtils.mm2px(this.mContext, 10), 0);
        this.mGridView.setAdapter(this.adapter);
        this.adapter.bindToRecyclerView(this.mGridView);
        this.mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            public void onItemPreSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (position >= 0)
                    adapter.getData().get(position).isSelected = false;
            }

            public void onItemSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (position >= 0)
                    adapter.getData().get(position).isSelected = true;
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (delMode) {
                    DriveFolderFile selectedDrive = drives.get(position);
                    RoomDataManger.deleteDrive(selectedDrive.getDriveData().getId());
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_DRIVE_REFRESH));
                    return;
                }
                btnAddServer.setVisibility(View.GONE);
                btnRemoveServer.setVisibility(View.GONE);
                DriveFolderFile selectedItem = DriveActivity.this.adapter.getItem(position);

                if ((selectedItem == selectedItem.parentFolder || selectedItem.parentFolder == null) && selectedItem.name == null) {
                    returnPreviousFolder();
                    return;
                }
                if (viewModel == null) {
                    if (selectedItem.getDriveType() == StorageDriveType.TYPE.LOCAL) {
                        viewModel = new LocalDriveViewModel();
                    } else if (selectedItem.getDriveType() == StorageDriveType.TYPE.WEBDAV) {
                        viewModel = new WebDAVDriveViewModel();
                    } else if (selectedItem.getDriveType() == StorageDriveType.TYPE.ALISTWEB) {
                        viewModel = new AlistDriveViewModel();
                    }
                    viewModel.setCurrentDrive(selectedItem);
                    if (!selectedItem.isFile) {
                        loadDriveData();
                        return;
                    }
                }

                if (!selectedItem.isFile) {
                    viewModel.setCurrentDriveNote(selectedItem);
                    loadDriveData();
                } else {
                    // takagen99 - To only play media file
                    if (StorageDriveType.isVideoType(selectedItem.fileType)) {
                        DriveFolderFile currentDrive = viewModel.getCurrentDrive();
                        if (currentDrive.getDriveType() == StorageDriveType.TYPE.LOCAL)
                            playFile(currentDrive.name + selectedItem.getAccessingPathStr() + selectedItem.name);
                        else if (currentDrive.getDriveType() == StorageDriveType.TYPE.WEBDAV) {
                            JsonObject config = currentDrive.getConfig();
                            String targetPath = selectedItem.getAccessingPathStr() + selectedItem.name;
                            playFile(config.get("url").getAsString() + targetPath);
                        } else if (currentDrive.getDriveType() == StorageDriveType.TYPE.ALISTWEB) {
                            AlistDriveViewModel boxedViewModel = (AlistDriveViewModel) viewModel;

                            boxedViewModel.loadFile(selectedItem, new AlistDriveViewModel.LoadFileCallback() {
                                @Override
                                public void callback(String fileUrl) {
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            playFile(fileUrl);
                                        }
                                    });
                                }

                                @Override
                                public void fail(String msg) {
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast toast = Toast.makeText(mContext, msg, Toast.LENGTH_SHORT);
                                            toast.show();
                                        }
                                    });
                                }
                            });
                        }
                    } else {
                        Toast.makeText(DriveActivity.this, "Media Unsupported", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        setLoadSir(findViewById(R.id.mLayout));
    }

    private void playFile(String fileUrl) {
        VodInfo vodInfo = new VodInfo();
        vodInfo.name = "存储";
        vodInfo.playFlag = "drive";
        if (viewModel != null) {
            DriveFolderFile currentDrive = viewModel.getCurrentDrive();
            if (currentDrive != null && currentDrive.getDriveType() == StorageDriveType.TYPE.WEBDAV) {
                String credentialStr = currentDrive.getWebDAVBase64Credential();
                if (credentialStr != null) {
                    JsonObject playerConfig = new JsonObject();
                    JsonArray headers = new JsonArray();
                    JsonElement authorization = JsonParser.parseString(
                            "{ \"name\": \"authorization\", \"value\": \"Basic " + credentialStr + "\" }");
                    headers.add(authorization);
                    playerConfig.add("headers", headers);
                    vodInfo.playerCfg = playerConfig.toString();
                }
            }
        }
        vodInfo.seriesFlags = new ArrayList<>();
        vodInfo.seriesFlags.add(new VodInfo.VodSeriesFlag("drive"));
        vodInfo.seriesMap = new LinkedHashMap<>();
        VodInfo.VodSeries series = new VodInfo.VodSeries(fileUrl, "tvbox-drive://" + fileUrl);
        List<VodInfo.VodSeries> seriesList = new ArrayList<>();
        seriesList.add(series);
        vodInfo.seriesMap.put("drive", seriesList);
        Bundle bundle = new Bundle();
        bundle.putBoolean("newSource", true);
        bundle.putString("sourceKey", "_drive");
        bundle.putSerializable("VodInfo", vodInfo);
        // takagen99 - to play file here zzzzzzzzzzzzzzz
        jumpActivity(PlayActivity.class, bundle);
    }

    private void openSortDialog() {
        List<String> options = Arrays.asList("按名字升序", "按名字降序", "按修改时间升序", "按修改时间降序");
        int sort = Hawk.get(HawkConfig.STORAGE_DRIVE_SORT, 0);
        SelectDialog<String> dialog = new SelectDialog<>(DriveActivity.this);
        dialog.setTip("请选择列表排序方式");
        dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<String>() {
            @Override
            public void click(String value, int pos) {
                sortType = pos;
                Hawk.put(HawkConfig.STORAGE_DRIVE_SORT, pos);
                dialog.dismiss();
                loadDriveData();
            }

            @Override
            public String getDisplay(String val) {
                return val;
            }
        }, null, options, sort);
        dialog.show();
    }

    private Comparator<DriveFolderFile> sortComparator = new Comparator<DriveFolderFile>() {
        @Override
        public int compare(DriveFolderFile o1, DriveFolderFile o2) {
            switch (sortType) {
                case 1:
                    return Collator.getInstance(Locale.CHINESE).compare(o2.name.toUpperCase(Locale.CHINESE), o1.name.toUpperCase(Locale.CHINESE));
                case 2:
                    return Long.compare(o1.lastModifiedDate, o2.lastModifiedDate);
                case 3:
                    return Long.compare(o2.lastModifiedDate, o1.lastModifiedDate);
                default:
                    return Collator.getInstance(Locale.CHINESE).compare(o1.name.toUpperCase(Locale.CHINESE), o2.name.toUpperCase(Locale.CHINESE));
            }
        }
    };

    //    测试获取u盘或sd目录
    @NonNull
    public String getStoragePath(Context context, boolean isRemovable) {
        StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        Class<?> storageVolumeClazz = null;
        try {
            storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            Method getVolumeList = storageManager.getClass().getMethod("getVolumeList");
            Method getPath = storageVolumeClazz.getMethod("getPath");
            Method isRemovableMtd = storageVolumeClazz.getMethod("isRemovable");
            Object result = getVolumeList.invoke(storageManager);
            final int length = Array.getLength(result);
            List<String> msgList = new ArrayList<>();
            msgList.add("---length--" + length);
            for (int i = 0; i < length; i++) {
                Object storageVolumeElement = Array.get(result, i);
                msgList.add("---Object--" + storageVolumeElement + "i==" + i);
                String path = (String) getPath.invoke(storageVolumeElement);
                msgList.add("---path_total--" + path);
                boolean removable = (Boolean) isRemovableMtd.invoke(storageVolumeElement);
                if (isRemovable == removable) {
                    msgList.add("---path removable--" + path);
                    return path;
                }
            }

            //使用dialog显示打印内容
            int sort = Hawk.get(HawkConfig.STORAGE_DRIVE_SORT, 0);
            SelectDialog<String> dialog = new SelectDialog<>(DriveActivity.this);
            dialog.setTip("获取外部存储打印" + msgList.size());
            dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<String>() {
                @Override
                public void click(String value, int pos) {
                    dialog.dismiss();
                }

                @Override
                public String getDisplay(String val) {
                    return val;
                }
            }, null, msgList, sort);
            dialog.show();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    private void openFilePicker() {
        if (delMode) {
            toggleDelMode();
        }
        ChooserDialog dialog = new ChooserDialog(mContext, R.style.FileChooserStyle);
        dialog
                .withStringResources("选择一个文件夹", "确定", "取消")
                .titleFollowsDir(true)
                .displayPath(true)
                .enableDpad(true)
                //                .withStartFile("/")
                .withFilter(true, true)
                .withChosenListener(new ChooserDialog.Result() {
                    @Override
                    public void onChoosePath(String dir, File dirFile) {
                        String absPath = dirFile.getAbsolutePath();
                        for (DriveFolderFile drive : drives) {
                            if (drive.getDriveType() == StorageDriveType.TYPE.LOCAL && absPath.equals(drive.getDriveData().name)) {
                                Toast.makeText(mContext, "此文件夹之前已被添加到空间列表！", Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }
                        RoomDataManger.insertDriveRecord(absPath, StorageDriveType.TYPE.LOCAL, null);
                        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_DRIVE_REFRESH));
                    }
                }).show();
    }

    private void openWebdavDialog(StorageDrive drive) {
        WebdavDialog webdavDialog = new WebdavDialog(mContext, drive);
        EventBus.getDefault().register(webdavDialog);
        webdavDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                EventBus.getDefault().unregister(dialog);
            }
        });
        webdavDialog.show();
    }

    private void openAlistDriveDialog(StorageDrive drive) {
        AlistDriveDialog dialog = new AlistDriveDialog(mContext, drive);
        EventBus.getDefault().register(dialog);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                EventBus.getDefault().unregister(dialog);
            }
        });
        dialog.show();
    }

    public void toggleDelMode() {
        delMode = !delMode;
        if (delMode) {
            // takagen99: Added Theme Color
            //            this.btnRemoveServer.setColorFilter(ContextCompat.getColor(mContext, R.color.color_theme));
            this.btnRemoveServer.setColorFilter(getThemeColor());
        } else {
            this.btnRemoveServer.setColorFilter(ContextCompat.getColor(mContext, R.color.color_FFFFFF));
        }
        adapter.toggleDelMode(delMode);
    }

    private void initData() {
        this.txtTitle.setText(getString(R.string.act_drive));
        sortType = Hawk.get(HawkConfig.STORAGE_DRIVE_SORT, 0);
        btnSort.setVisibility(View.GONE);
        if (drives == null) {
            drives = new ArrayList<>();
            List<StorageDrive> storageDrives = RoomDataManger.getAllDrives();
            for (StorageDrive storageDrive : storageDrives) {
                DriveFolderFile drive = new DriveFolderFile(storageDrive);
                if (delMode)
                    drive.isDelMode = true;
                drives.add(drive);
            }
        }
        adapter.setNewData(drives);
        this.setSelectedItem(drives);
        this.btnAddServer.setVisibility(View.VISIBLE);
        this.btnRemoveServer.setVisibility(View.VISIBLE);
        showSuccess();
    }

    private void setSelectedItem(List<DriveFolderFile> list) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).isSelected) {
                int isIndex = i;
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mGridView.setSelection(isIndex);
                    }
                }, 50);
                return;
            }
        }
        mGridView.setSelection(0);
    }

    private void loadDriveData() {
        viewModel.setSortType(sortType);
        btnSort.setVisibility(View.VISIBLE);
        showLoading();
        String path = viewModel.loadData(new AbstractDriveViewModel.LoadDataCallback() {
            @Override
            public void callback(List<DriveFolderFile> list, boolean alreadyHasChildren) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showSuccess();
                        if (alreadyHasChildren) {
                            adapter.setNewData(viewModel.getCurrentDriveNote().getChildren());
                            setSelectedItem(viewModel.getCurrentDriveNote().getChildren());
                        } else {
                            adapter.setNewData(viewModel.getCurrentDriveNote().getChildren());
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    mGridView.setSelection(0);
                                }
                            }, 50);
                        }
                    }
                });
            }

            @Override
            public void fail(String message) {
                showSuccess();
                viewModel = null;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        if (StringUtils.isNotEmpty(path)) {
            this.txtTitle.setText(path);
        }
    }

    private void cancel() {
        OkGo.getInstance().cancelTag("drive");
    }

    private void returnPreviousFolder() {
        if (isInSearch && viewModel == null) {
            //if already in search list
            isInSearch = false;
            viewModel = backupViewModel;
            backupViewModel = null;
            if (viewModel == null) {
                //if no last view list, return to main menu
                initData();
            } else {
                //return to last view list
                loadDriveData();
            }
            return;
        }
        viewModel.getCurrentDriveNote().setChildren(null);
        viewModel.setCurrentDriveNote(viewModel.getCurrentDriveNote().parentFolder);
        if (viewModel.getCurrentDriveNote() == null) {
            if (isInSearch) {
                //if returns from a search result, back to search result
                this.txtTitle.setText("搜索结果");
                adapter.setNewData(searchResult);
                viewModel = null;
                return;
            }
            viewModel = null;
            initData();
            return;
        }
        loadDriveData();
    }

    @Override
    public void onBackPressed() {
        if (viewModel != null) {
            cancel();
            //            mGridView.onClick(mGridView.getChildAt(0));
            returnPreviousFolder();
            return;
        }
        if (!delMode)
            super.onBackPressed();
        else
            toggleDelMode();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_DRIVE_REFRESH) {
            drives = null;
            initData();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
