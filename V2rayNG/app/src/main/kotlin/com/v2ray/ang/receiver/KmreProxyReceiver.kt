package com.v2ray.ang.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ServerConfig
import com.v2ray.ang.service.V2RayServiceManager
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.Utils


class KmreProxyReceiver : BroadcastReceiver() {


    var mHost : String? = "192.168.1.101"
    var mPort : Int? = 1080

    val ACTION_OPEN_KMRE_PROXY = "cn.kylinos.kmre.action.OPEN_KMRE_PROXY"
    val ACTION_CLOSE_KMRE_PROXY = "cn.kylinos.kmre.action.CLOSE_KMRE_PROXY"
    val ACTION_PER_APP_PROXY_SET = "cn.kylinos.kmre.action.PER_APP_PROXY_SET"

    private val createConfigType by lazy {
        EConfigType.fromInt(EConfigType.SOCKS.value) ?: EConfigType.VMESS
    }

    private val settingsStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SETTING, MMKV.MULTI_PROCESS_MODE) }

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        Log.d(AppConfig.TAG_KMRE_PROXY, " onReceive action:" + action)
        if (action.equals(ACTION_OPEN_KMRE_PROXY)) {
            val bundle = intent?.getBundleExtra(AppConfig.KMRE_EXTRA_BUNDLE)
            mHost = bundle?.getString(AppConfig.KMRE_HOST, "192.168.1.101")
            mPort = bundle?.getInt(AppConfig.KMRE_PORT, 1080)
            val proxy_type = bundle?.getString(AppConfig.KMRE_PROXY_TYPE, "socks")
            V2RayServiceManager.setKmreProxyisOpen(true)
            startKmreProxy(mHost, mPort, proxy_type, context)
        } else if (action.equals(ACTION_CLOSE_KMRE_PROXY)) {
            V2RayServiceManager.setKmreProxyisOpen(false)
            V2RayServiceManager.stopV2rayPoint()
        } else if (action.equals(ACTION_PER_APP_PROXY_SET)) {
            val bundle = intent?.getBundleExtra(AppConfig.KMRE_EXTRA_BUNDLE)
            val enable = bundle?.getBoolean(AppConfig.KMRE_PER_APP_PROXY_ENABLE, false)
            Log.d(AppConfig.TAG_KMRE_PROXY, "ACTION_PER_APP_PROXY_SET enable:" + enable)
//            val defaultSharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(context) }
            if (enable == true) {
                val app_list = bundle?.getString(AppConfig.KMRE_PER_APP_PROXY, "")
                val black_list: Set<String>? = Gson().fromJson(app_list, Set::class.java) as Set<String>?
                Log.d(AppConfig.TAG_KMRE_PROXY, "ACTION_PER_APP_PROXY_SET black_list :" + black_list)
                settingsStorage?.encode(AppConfig.PREF_PER_APP_PROXY_SET, black_list)
                settingsStorage?.encode(AppConfig.PREF_PER_APP_PROXY, true)
//                defaultSharedPreferences.edit().putStringSet(AppConfig.PREF_PER_APP_PROXY_SET, black_list).apply()
//                defaultSharedPreferences.edit().putBoolean(AppConfig.PREF_PER_APP_PROXY, true).apply()
//                val enabled = defaultSharedPreferences.getBoolean(AppConfig.PREF_PER_APP_PROXY, false)
//                Log.d(AppConfig.TAG_KMRE_PROXY, "enabled :" + enabled)
            } else {
                settingsStorage?.encode(AppConfig.PREF_PER_APP_PROXY, false)
//                defaultSharedPreferences.edit().putBoolean(AppConfig.PREF_PER_APP_PROXY, false).apply()
            }
            if (V2RayServiceManager.isOpenForKmreProxy()) {
                Log.d(AppConfig.TAG_KMRE_PROXY, "restart kmre proxy")
                V2RayServiceManager.stopV2rayPoint()
                startKmreProxy(mHost, mPort, "socks", context)
            }
        }
    }

    private fun startKmreProxy(
        host: String?,
        port: Int?,
        proxy_type: String?,
        context: Context?
    ) {
        Log.d(
            AppConfig.TAG_KMRE_PROXY,
            "host:" + host + ",port:" + port + ",proxy_type:" + proxy_type
        )
        if (context != null) {
            var isRunning = false
            isRunning = V2RayServiceManager.isRunning()
            if (isRunning) {
                Log.d(AppConfig.TAG_KMRE_PROXY, "stop v2ray")
                V2RayServiceManager.stopV2rayPoint()
            }
            reloadServerConfig(host, port, proxy_type)
            val time = 3
            startV2ray(context, time)
        }
    }

    private fun reloadServerConfig(
        host: String?,
        port: Int?,
        proxy_type: String?
    ) {
        MmkvManager.clearAllServer()
        val guid = Utils.getUuid()
        val config =
            MmkvManager.decodeServerConfig(guid) ?: ServerConfig.create(createConfigType)
        config.remarks = proxy_type ?: "socks"
        config.outboundBean?.settings?.servers?.get(0)?.let { server ->
            server.address = host ?: "192.168.0.1"
            server.port = port!!
            server.users = null
        }
        MmkvManager.encodeServerConfig(guid, config)
        Log.d(AppConfig.TAG_KMRE_PROXY, "[onReceive] config:" + config)
    }

    private fun startV2ray(context: Context, time: Int) {
        if (time < 0)
            return
        if (!V2RayServiceManager.isRunning()) {
            VpnService.prepare(context)
            V2RayServiceManager.startV2Ray(context)
        } else {
            Handler(Looper.getMainLooper()).postDelayed( {
                startV2ray(context, time - 1)
            }, 500)
        }
    }
}