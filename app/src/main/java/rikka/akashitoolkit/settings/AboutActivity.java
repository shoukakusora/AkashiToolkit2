package rikka.akashitoolkit.settings;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.Toast;

import moe.shizuku.preference.Preference;
import moe.shizuku.preference.PreferenceFragment;
import rikka.akashitoolkit.BuildConfig;
import rikka.akashitoolkit.MainActivity;
import rikka.akashitoolkit.R;
import rikka.akashitoolkit.staticdata.ExpeditionList;
import rikka.akashitoolkit.staticdata.QuestList;
import rikka.akashitoolkit.support.CrashHandler;
import rikka.akashitoolkit.support.Settings;
import rikka.akashitoolkit.support.StaticData;
import rikka.akashitoolkit.sync.ExpeditionSyncManager;
import rikka.akashitoolkit.sync.QuestSyncManager;
import rikka.akashitoolkit.tools.SendReportActivity;
import rikka.akashitoolkit.ui.BaseActivity;

public class AboutActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.about);

        if (savedInstanceState == null) {
            AboutFragment fragment = new AboutFragment();

            getSupportFragmentManager().beginTransaction().replace(R.id.fragment,
                    fragment).commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();

                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class AboutFragment extends PreferenceFragment {
        @Override
        public void onStop() {
            super.onStop();
        }

        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            setPreferencesFromResource(R.xml.about, null);

            try {
                PackageInfo info = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);

                findPreference("version").setSummary(String.format(getActivity().getString(R.string.version_format), info.versionName, info.versionCode));
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            final Preference syncQuest = findPreference("sync_quest");
            if (syncQuest != null) {
                syncQuest.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (getActivity() == null) {
                            return true;
                        }
                        final android.content.Context appContext = getActivity().getApplicationContext();
                        final Handler handler = new Handler(Looper.getMainLooper());
                        syncQuest.setSummary("正在从 zh.kcwiki.cn 同步…");
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    final QuestSyncManager.Result result = QuestSyncManager.sync(appContext);
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (!isAdded() || getActivity() == null) {
                                                return;
                                            }
                                            syncQuest.setSummary("已同步 " + result.count + " 条任务数据");
                                            QuestList.clear();
                                            new AlertDialog.Builder(getActivity())
                                                    .setTitle("同步完成")
                                                    .setMessage("已从 zh.kcwiki.cn 更新 " + result.count + " 条任务数据。\n重启应用后生效。")
                                                    .setPositiveButton("立即重启", new DialogInterface.OnClickListener() {
                                                        @Override
                                                        public void onClick(DialogInterface dialog, int which) {
                                                            if (getActivity() == null) {
                                                                return;
                                                            }
                                                            Intent intent = new Intent(getActivity(), MainActivity.class);
                                                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                                            getActivity().startActivity(intent);
                                                            getActivity().finish();
                                                        }
                                                    })
                                                    .setNegativeButton("稍后", null)
                                                    .show();
                                        }
                                    });
                                } catch (final Exception e) {
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (!isAdded() || getActivity() == null) {
                                                return;
                                            }
                                            syncQuest.setSummary("同步失败：" + e.getMessage());
                                            Toast.makeText(getActivity(), "同步失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                                        }
                                    });
                                }
                            }
                        }).start();
                        return true;
                    }
                });
            }

            final Preference syncExp = findPreference("sync_expedition");
            if (syncExp != null) {
                syncExp.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (getActivity() == null) {
                            return true;
                        }
                        final android.content.Context appContext = getActivity().getApplicationContext();
                        final Handler handler = new Handler(Looper.getMainLooper());
                        syncExp.setSummary("正在从 zh.kcwiki.cn 同步…");
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    final ExpeditionSyncManager.Result result = ExpeditionSyncManager.sync(appContext);
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (!isAdded() || getActivity() == null) {
                                                return;
                                            }
                                            syncExp.setSummary("已同步 " + result.count + " 条远征数据");
                                            ExpeditionList.clear();
                                            new AlertDialog.Builder(getActivity())
                                                    .setTitle("同步完成")
                                                    .setMessage("已从 zh.kcwiki.cn 更新 " + result.count + " 条远征数据。\n重启应用后生效。")
                                                    .setPositiveButton("立即重启", new DialogInterface.OnClickListener() {
                                                        @Override
                                                        public void onClick(DialogInterface dialog, int which) {
                                                            restartApp();
                                                        }
                                                    })
                                                    .setNegativeButton("稍后", null)
                                                    .show();
                                        }
                                    });
                                } catch (final Exception e) {
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (!isAdded() || getActivity() == null) {
                                                return;
                                            }
                                            syncExp.setSummary("同步失败：" + e.getMessage());
                                            Toast.makeText(getActivity(), "同步失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                                        }
                                    });
                                }
                            }
                        }).start();
                        return true;
                    }
                });
            }

            /*
            findPreference("qq_group").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    ClipBoardUtils.putTextIntoClipboard(getActivity(), "437033068");
                    Toast.makeText(getActivity(), String.format(getString(R.string.copied_to_clipboard_format), "437033068"), Toast.LENGTH_SHORT).show();
                    return false;
                }
            });
            */
            findPreference("feedback").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @SuppressLint("DefaultLocale")
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (getActivity() == null) {
                        return true;
                    }
                    final String subject = "Akashi Toolkit 反馈";
                    final String body = String.format("系统版本: %s (API %d)\n设备型号: %s\n设备制造商: %s\n应用版本: %d\nFlavors: %s",
                            Build.VERSION.RELEASE,
                            Build.VERSION.SDK_INT,
                            Build.MODEL,
                            Build.MANUFACTURER,
                            StaticData.instance(getActivity()).versionCode,
                            BuildConfig.FLAVOR);
                    new AlertDialog.Builder(getActivity())
                            .setTitle("反馈")
                            .setItems(new CharSequence[]{
                                    "发送给现维护者\n" + SendReportActivity.MAINTAINER_EMAIL,
                                    "发送给原开发者\n" + SendReportActivity.DEVELOPER_EMAIL
                            }, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (getActivity() == null) {
                                        return;
                                    }
                                    if (which == 0) {
                                        SendReportActivity.sendEmail(getActivity(), subject, body,
                                                SendReportActivity.MAINTAINER_EMAIL, null);
                                    } else {
                                        SendReportActivity.sendEmail(getActivity(), subject, body,
                                                SendReportActivity.DEVELOPER_EMAIL,
                                                new String[]{SendReportActivity.DEVELOPER_QQ_EMAIL});
                                    }
                                }
                            })
                            .setNegativeButton("取消", null)
                            .show();
                    return false;
                }
            });

            /*
            if (FlavorsUtils.isPlay()) {
                findPreference("donate").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (((AboutActivity) getActivity()).mDonateHelper.isSuccess()) {
                            final DonateHelper.OnPurchaseSuccessListener listener = new DonateHelper.OnPurchaseSuccessListener() {
                                @Override
                                public void onSuccess(String sku) {
                                    if (isVisible()) {
                                        Toast.makeText(getActivity(), R.string.donate_gp_thanks, Toast.LENGTH_SHORT).show();
                                    }
                                }
                            };
                            new AlertDialog.Builder(getActivity())
                                    .setItems(new CharSequence[]{"1 USD", "2 USD", "5 USD", "10 USD"}, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            switch (which) {
                                                case 0:
                                                    ((AboutActivity) getActivity()).mDonateHelper.start(getActivity(), DonateHelper.SKU_DONATE_1, listener);
                                                    break;
                                                case 1:
                                                    ((AboutActivity) getActivity()).mDonateHelper.start(getActivity(), DonateHelper.SKU_DONATE_2, listener);
                                                    break;
                                                case 2:
                                                    ((AboutActivity) getActivity()).mDonateHelper.start(getActivity(), DonateHelper.SKU_DONATE_5, listener);
                                                    break;
                                                case 3:
                                                    ((AboutActivity) getActivity()).mDonateHelper.start(getActivity(), DonateHelper.SKU_DONATE_10, listener);
                                                    break;
                                            }
                                        }
                                    })
                                    .show();
                        } else {
                            new AlertDialog.Builder(getActivity())
                                    .setMessage(R.string.donate_gp_set_up_failed)
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show();
                        }
                        return false;
                    }
                });
            } else {
                ((PreferenceScreen) findPreference("screen")).removePreference(findPreference("donate"));
            }

            findPreference("donate_alipay").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    AlipayDonateUtils.startActivity(getActivity());
                    return false;
                }
            });

            if (FlavorsUtils.isPlay()
                    && !PackageUtils.isInstalled(getActivity(), AlipayDonateUtils.PACKAGENAME_ALIPAY)) {
                ((PreferenceScreen) findPreference("screen")).removePreference(findPreference("donate_alipay"));
            }
            */

            final Preference syncHome = findPreference("sync_home");
        if (syncHome != null) {
            syncHome.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (getActivity() == null) {
                        return true;
                    }
                    final Preference pref = preference;
                    final android.content.Context context = getActivity().getApplicationContext();
                    pref.setEnabled(false);
                    pref.setSummary("正在刷新主页…");
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            String message;
                            try {
                                int count = rikka.akashitoolkit.sync.DataSyncManager.syncHome(context);
                                message = "主页已刷新：" + count + " 条";
                            } catch (Exception e) {
                                message = "刷新失败：" + e.getMessage();
                            }
                            final String text = message;
                            if (getActivity() == null) {
                                return;
                            }
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    pref.setEnabled(true);
                                    pref.setSummary(text);
                                    Toast.makeText(getActivity(), text, Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }).start();
                    return true;
                }
            });
        }

        final Preference syncDevelopment = findPreference("sync_development");
        if (syncDevelopment != null) {
            syncDevelopment.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (getActivity() == null) {
                        return true;
                    }
                    final Preference pref = preference;
                    final android.content.Context context = getActivity().getApplicationContext();
                    pref.setEnabled(false);
                    pref.setSummary("正在刷新开发公式…");
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            String message;
                            try {
                                int count = rikka.akashitoolkit.sync.StatsSyncManager.syncDevelopment(context);
                                message = "开发公式已刷新：" + count + " 条";
                            } catch (Exception e) {
                                message = "刷新失败：" + e.getMessage();
                            }
                            final String text = message;
                            if (getActivity() == null) {
                                return;
                            }
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    pref.setEnabled(true);
                                    pref.setSummary(text);
                                    Toast.makeText(getActivity(), text, Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }).start();
                    return true;
                }
            });
        }

        final Preference syncConstruction = findPreference("sync_construction");
        if (syncConstruction != null) {
            syncConstruction.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (getActivity() == null) {
                        return true;
                    }
                    final Preference pref = preference;
                    final android.content.Context context = getActivity().getApplicationContext();
                    pref.setEnabled(false);
                    pref.setSummary("正在刷新建造公式…");
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            String message;
                            try {
                                int count = rikka.akashitoolkit.sync.StatsSyncManager.syncConstruction(context);
                                message = "建造公式已刷新：" + count + " 条";
                            } catch (Exception e) {
                                message = "刷新失败：" + e.getMessage();
                            }
                            final String text = message;
                            if (getActivity() == null) {
                                return;
                            }
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    pref.setEnabled(true);
                                    pref.setSummary(text);
                                    Toast.makeText(getActivity(), text, Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }).start();
                    return true;
                }
            });
        }

        final Preference syncDrop = findPreference("sync_drop");
        if (syncDrop != null) {
            syncDrop.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (getActivity() == null) {
                        return true;
                    }
                    final Preference pref = preference;
                    final android.content.Context context = getActivity().getApplicationContext();
                    pref.setEnabled(false);
                    pref.setSummary("正在重建掉落统计…");
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            String message;
                            try {
                                int count = rikka.akashitoolkit.sync.StatsSyncManager.syncDrop(context);
                                message = "掉落统计已刷新：" + count + " 个海域";
                            } catch (Exception e) {
                                message = "刷新失败：" + e.getMessage();
                            }
                            final String text = message;
                            if (getActivity() == null) {
                                return;
                            }
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    pref.setEnabled(true);
                                    pref.setSummary(text);
                                    Toast.makeText(getActivity(), text, Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }).start();
                    return true;
                }
            });
        }

        final Preference syncImprovement = findPreference("sync_improvement");
        if (syncImprovement != null) {
            syncImprovement.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (getActivity() == null) {
                        return true;
                    }
                    final Preference pref = preference;
                    final android.content.Context context = getActivity().getApplicationContext();
                    pref.setEnabled(false);
                    pref.setSummary("正在刷新改修数据…");
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            String message;
                            try {
                                int count = rikka.akashitoolkit.sync.DataSyncManager.syncImprovement(context);
                                message = "改修数据已刷新：" + count + " 件装备";
                            } catch (Exception e) {
                                message = "刷新失败：" + e.getMessage();
                            }
                            final String text = message;
                            if (getActivity() == null) {
                                return;
                            }
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    pref.setEnabled(true);
                                    pref.setSummary(text);
                                    Toast.makeText(getActivity(), text, Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }).start();
                    return true;
                }
            });
        }

        final Preference offlineCache = findPreference("offline_cache");
        if (offlineCache != null) {
            offlineCache.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (getActivity() == null) {
                        return true;
                    }
                    final Preference pref = preference;
                    final android.content.Context context = getActivity().getApplicationContext();
                    pref.setEnabled(false);
                    pref.setSummary("正在检查缺失图片…");

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            rikka.akashitoolkit.sync.ImageSyncManager.Result result =
                                    rikka.akashitoolkit.sync.ImageSyncManager.syncMissing(context,
                                            new rikka.akashitoolkit.sync.ImageSyncManager.Callback() {
                                                @Override
                                                public void onProgress(final int done, final int total, String current) {
                                                    if (getActivity() == null) {
                                                        return;
                                                    }
                                                    getActivity().runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            Preference p = findPreference("offline_cache");
                                                            if (p != null) {
                                                                p.setSummary("正在下载图片… " + done + "/" + total);
                                                            }
                                                        }
                                                    });
                                                }

                                                @Override
                                                public void onFinished(int downloaded, int failed, int missing) {
                                                }
                                            });

                            final int ok = result == null ? 0 : result.downloaded;
                            final int fail = result == null ? 0 : result.failed;
                            if (getActivity() == null) {
                                return;
                            }
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Preference p = findPreference("offline_cache");
                                    if (p != null) {
                                        p.setEnabled(true);
                                        p.setSummary(ok == 0 && fail == 0
                                                ? "图片已齐全，无需下载"
                                                : "已补齐 " + ok + " 张，失败 " + fail + " 张");
                                    }
                                    Toast.makeText(getActivity(),
                                            "图片刷新完成：成功 " + ok + "，失败 " + fail,
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }).start();
                    return true;
                }
            });
        }

        final Preference crashLog = findPreference("crash_log");
            if (crashLog != null) {
                crashLog.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (getActivity() == null) {
                            return true;
                        }
                        final String log = CrashHandler.readLast(getActivity());
                        new AlertDialog.Builder(getActivity())
                                .setTitle("上次崩溃日志")
                                .setMessage(log == null ? "没有崩溃记录。" : log)
                                .setPositiveButton(log == null ? "知道了" : "复制", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        if (log == null || getActivity() == null) {
                                            return;
                                        }
                                        ClipboardManager cm = (ClipboardManager) getActivity()
                                                .getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                                        cm.setPrimaryClip(ClipData.newPlainText("crash", log));
                                        Toast.makeText(getActivity(), "已复制崩溃日志", Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .setNegativeButton("清除记录", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        if (getActivity() != null) {
                                            CrashHandler.clear(getActivity());
                                        }
                                    }
                                })
                                .show();
                        return true;
                    }
                });
            }

            final Preference dataSource = findPreference("data_source_thanks");
            if (dataSource != null) {
                dataSource.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (getActivity() == null) {
                            return true;
                        }
                        final String text =
                                "本应用的数据与内容来自以下来源：\n\n"
                                        + "· 舰娘百科 zh.kcwiki.cn —— 舰娘、装备、任务、远征、海域等资料\n"
                                        + "· kcwiki 开放接口 api.kcwiki.org —— 游戏母数据、舰娘详情、海域目录\n"
                                        + "· upload.kcwiki.org —— 立绘与活动图片\n"
                                        + "· akashi-list.me —— 每日改修（可改修舰娘与曜日）参考\n"
                                        + "· db.kcwiki.cn —— 开发公式与掉落统计参考\n"
                                        + "· 原开源项目 github.com/kcwikizh/Akashi-Toolkit —— 应用本体\n\n"
                                        + "感谢舰娘百科编辑组与素材整理者的长期维护，感谢原开发者 Rikka 及所有贡献者。\n"
                                        + "本构建仅做数据刷新与系统适配，不含商业用途。";
                        new AlertDialog.Builder(getActivity())
                                .setTitle("数据来源与致谢")
                                .setMessage(text)
                                .setPositiveButton("知道了", null)
                                .show();
                        return true;
                    }
                });
            }

            final Preference donate = findPreference("donate_alipay");
            if (donate != null) {
                donate.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (getActivity() == null) {
                            return true;
                        }
                        int padding = (int) (16 * getResources().getDisplayMetrics().density);
                        ImageView imageView = new ImageView(getActivity());
                        imageView.setAdjustViewBounds(true);
                        imageView.setPadding(padding, padding, padding, padding);
                        imageView.setImageResource(R.drawable.donate_alipay_qr);
                        new AlertDialog.Builder(getActivity())
                                .setTitle("我要打钱")
                                .setView(imageView)
                                .setMessage("打开支付宝 → 扫一扫 → 扫描二维码\n感谢支持本应用的维护与数据更新。")
                                .setPositiveButton("知道了", null)
                                .show();
                        return true;
                    }
                });
            }

            final Preference developer = findPreference("developer_contact");
            if (developer != null) {
                developer.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (getActivity() == null) {
                            return true;
                        }
                        final String email = "rikkanyaaa+akashiToolkitFeedback@gmail.com";
                        new AlertDialog.Builder(getActivity())
                                .setTitle("开发者联系方式")
                                .setMessage("原作者 / 原开发者：Rikka\n邮箱：" + email
                                        + "\nGitHub：https://github.com/RikkaApps"
                                        + "\n\n现维护者反馈邮箱：shoukakusora@hotmail.com")
                                .setPositiveButton("复制开发者邮箱", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        if (getActivity() == null) {
                                            return;
                                        }
                                        ClipboardManager cm = (ClipboardManager) getActivity()
                                                .getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                                        cm.setPrimaryClip(ClipData.newPlainText("email", email));
                                        Toast.makeText(getActivity(), "已复制：" + email, Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .setNegativeButton("关闭", null)
                                .show();
                        return true;
                    }
                });
            }

            findPreference("version").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    getActivity().getWindow().getDecorView().removeCallbacks(clearClickCount);
                    getActivity().getWindow().getDecorView().postDelayed(clearClickCount, 3000);

                    click ++;

                    if (click == 10) {
                        Settings.instance(getActivity()).putBoolean(Settings.DEVELOPER, true);
                        Toast.makeText(getActivity(), "You are now developer.", Toast.LENGTH_SHORT).show();
                        getActivity().recreate();
                    }

                    return false;
                }
            });
        }

        private int click = 0;
        private Runnable clearClickCount = new Runnable() {
            @Override
            public void run() {
                click = 0;
            }
        };

        private void restartApp() {
            if (getActivity() == null) {
                return;
            }
            Intent intent = new Intent(getActivity(), MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            getActivity().startActivity(intent);
            getActivity().finish();
        }
    }
}
