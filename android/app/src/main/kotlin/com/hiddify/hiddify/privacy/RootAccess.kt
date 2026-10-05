package com.hiddify.hiddify.privacy

import java.io.File

/** Discovery never executes su, so opening settings cannot prompt for privileges. */
object RootAccess {
    fun findExecutable(path: String? = System.getenv("PATH")): String? {
        val directories = path.orEmpty().split(File.pathSeparator).filter { it.startsWith("/") } +
            listOf("/system/bin", "/system/xbin", "/sbin", "/su/bin", "/debug_ramdisk")
        return directories.distinct().map { File(it, "su") }
            .firstOrNull { it.isFile && it.canExecute() }?.absolutePath
    }
}
