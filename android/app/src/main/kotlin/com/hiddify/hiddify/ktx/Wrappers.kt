package com.hiddify.hiddify.ktx

import android.net.IpPrefix
import android.os.Build
import androidx.annotation.RequiresApi
import com.hiddify.core.libbox.RoutePrefix
import java.net.InetAddress

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun RoutePrefix.toIpPrefix() = IpPrefix(InetAddress.getByName(address()), prefix())
